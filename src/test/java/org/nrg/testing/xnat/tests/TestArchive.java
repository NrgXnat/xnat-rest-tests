package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.DeprecatedIn;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatMrscandata;
import org.nrg.xdat.om.XnatPetscandata;
import org.nrg.xnat.enums.MergeBehavior;
import org.nrg.xnat.importer.XnatArchivalRequest;
import org.nrg.xnat.importer.importers.DefaultImporterRequest;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.ScanResource;
import org.testng.annotations.*;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

import static org.nrg.testing.TestGroups.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

public class TestArchive extends BaseXnatRestTest {
    private static final String QUERY_PARAM_XSI_TYPE        = "xsiType";
    private static final String QUERY_PARAM_NOTE            = "note";
    private static final String QUERY_PARAM_IMAGE_SCAN_NOTE = XnatImagescandata.SCHEMA_ELEMENT_NAME + "/note";
    private static final String QUERY_PARAM_MR_SCAN_NOTE    = XnatMrscandata.SCHEMA_ELEMENT_NAME + "/note";
    // XNAT-7014 Change to implement this was rolled back for 1.8.6 release. Should be pushed back in for 1.8.7.
    // private static final String QUERY_PARAM_CT_SCAN_NOTE    = XnatCtscandata.SCHEMA_ELEMENT_NAME + "/note";
    private static final String PARAM_VALUE_NOTE1           = "This is note 1";
    private static final String PARAM_VALUE_NOTE2           = "This is note 2";
    private static final String PARAM_VALUE_NOTE3           = "This is note 3";
    private static final String PARAM_VALUE_NOTE4           = "This is note 4";
    private static final String PARAM_VALUE_BAD_NOTE        = "It would be bad if this was ever set";
    private static final String SCAN_ID_1                   = "1";
    private static final String SCAN_ID_2                   = "2";
    private static final String SCAN_TYPE_LOCALIZER         = "localizer";
    private static final String SCAN_TYPE_MAPPED            = "MAPPED";
    private static final String JSON_WRAPPER                = "ResultSet.Result";
    private static final String MESSAGE_BAD_XSI_TYPE        = "Specified xsiType differs from existing xsiType";

    private final File    sessionZip = getDataFile("mr_1.zip");
    private       Project project;

    @BeforeClass
    public void disableSiteAnonScript() {
        mainAdminInterface().disableSiteAnonScript();
    }

    @AfterClass(alwaysRun = true)
    public void enableSiteAnonScript() {
        mainAdminInterface().enableSiteAnonScript();
    }

    @BeforeMethod
    public void createTestProject() {
        project = new Project();
        mainInterface().createProject(project);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteTestProject() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @Test
    @DeprecatedIn(Xnat_1_8_0.class)
    public void testWebQCGeneration() {
        final Subject subject = new Subject(project);
        final ImagingSession session = new MRSession(project, subject).date(LocalDate.parse("2001-01-01"));
        final Scan scan1 = new MRScan(session, SCAN_ID_1).seriesDescription(SCAN_TYPE_LOCALIZER).quality("usable");
        final Scan scan2 = new MRScan(session, SCAN_ID_2).seriesDescription(SCAN_TYPE_LOCALIZER).quality("questionable");
        final Resource scan1Resource = new ScanResource(project, subject, session, scan1).folder("DICOM").format("DICOM");
        final Resource scan2Resource = new ScanResource(project, subject, session, scan2).folder("DICOM").format("DICOM");

        for (int i = 1; i < 6; i++) {
            final File dicomFile = getDataFile(String.format("mr_1/%d.dcm", i));
            scan1Resource.addResourceFile(new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile)));
            scan2Resource.addResourceFile(new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile)));
        }

        mainInterface().createSubject(subject);

        mainQueryBase().queryParam("triggerPipelines", true).put(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        mainInterface().waitForAutoRun(session, 60);

        mainQueryBase().queryParam("file_content", "ORIGINAL").queryParam("index", 0).
                get(mainInterface().resourceFilesUrl(new ScanResource(project, subject, session, scan1).folder("SNAPSHOTS"))).
                then().assertThat().statusCode(200);
    }

    @Test
    public void testFixScanTypes() {
        final Subject subject = new Subject(project);
        final ImagingSession mr1 = new MRSession(project, subject, "MR1").date(LocalDate.parse("2001-01-01"));
        final Scan mr1Scan1 = new MRScan(mr1, SCAN_ID_1).seriesDescription(SCAN_TYPE_LOCALIZER).quality("usable"); // unmapped type
        final Scan mr1Scan2 = new MRScan(mr1, SCAN_ID_2).seriesDescription(SCAN_TYPE_LOCALIZER).quality("questionable"); // unmapped type
        new ScanResource(project, subject, mr1, mr1Scan1).folder("DICOM").format("DICOM");
        new ScanResource(project, subject, mr1, mr1Scan2).folder("DICOM").format("DICOM");

        final ImagingSession mr2 = new MRSession(project, subject, "MR2").date(LocalDate.parse("2001-01-02"));
        new MRScan(mr2, SCAN_ID_1).seriesDescription(SCAN_TYPE_LOCALIZER).type(SCAN_TYPE_MAPPED).quality("usable"); // mapped type!

        mainInterface().createSubject(subject);

        // call fixScanTypes
        // all other $commonSeriesDescription scans in this project have been labeled as '$scanType', so these should be too.
        mainQueryBase().queryParam("fixScanTypes", true).put(mainInterface().subjectAssessorUrl(mr1)).then().assertThat().statusCode(200);

        final Scan[] readScans = mainInterface().jsonQuery().get(mainInterface().sessionScansUrl(mr1)).jsonPath().getObject(JSON_WRAPPER, Scan[].class);
        assertEquals(mr1.getScans().size(), readScans.length);

        for (Scan scan : readScans) {
            assertEquals(SCAN_TYPE_MAPPED, scan.getType());
        }
    }

    @SuppressWarnings("CommentedOutCode")
    @Test(groups = {ARCHIVE, SMOKE})
    public void testSetScanProperties() {
        final Subject        subject   = new Subject(project);
        final ImagingSession mr1       = new MRSession(project, subject, "MR1").date(LocalDate.parse("2001-01-01"));
        final Scan           scan1     = new MRScan(mr1, SCAN_ID_1).seriesDescription(SCAN_TYPE_LOCALIZER).quality("usable");

        mainInterface().createSubject(subject);

        final String scanUrl = mainInterface().scanUrl(scan1);
        mainQueryBase().put(scanUrl).then().assertThat().statusCode(200);

        final Scan retrievedScan1 = getScan(subject, mr1);
        assertEquals(retrievedScan1.getId(), SCAN_ID_1);
        assertEquals(retrievedScan1.getSeriesDescription(), SCAN_TYPE_LOCALIZER);
        assertNull(retrievedScan1.getNote());

        // Test that updating scan works with scan XSI type
        mainQueryBase().queryParam(QUERY_PARAM_XSI_TYPE, XnatMrscandata.SCHEMA_ELEMENT_NAME)
                       .queryParam(QUERY_PARAM_NOTE, PARAM_VALUE_NOTE1)
                       .put(scanUrl).then().assertThat().statusCode(200);

        final Scan retrievedScan2 = getScan(subject, mr1);
        assertEquals(retrievedScan2.getNote(), PARAM_VALUE_NOTE1);

        // Test that updating scan works *without* scan XSI type
        mainQueryBase().queryParam(QUERY_PARAM_NOTE, PARAM_VALUE_NOTE2)
                       .put(scanUrl).then().assertThat().statusCode(200);

        final Scan retrievedScan3 = getScan(subject, mr1);
        assertEquals(retrievedScan3.getNote(), PARAM_VALUE_NOTE2);

        // Test that updating scan fails with wrong scan XSI type
        mainQueryBase().queryParam(QUERY_PARAM_XSI_TYPE, XnatPetscandata.SCHEMA_ELEMENT_NAME)
                       .queryParam(QUERY_PARAM_NOTE, PARAM_VALUE_BAD_NOTE)
                       .put(scanUrl).then()
                       .assertThat().statusCode(409).and()
                       .body(Matchers.containsString(MESSAGE_BAD_XSI_TYPE));

        // Make sure that the note property didn't change
        final Scan retrievedScan4 = getScan(subject, mr1);
        assertEquals(retrievedScan4.getNote(), PARAM_VALUE_NOTE2);

        // Test that updating scan works with XML path with image scan XSI type
        mainQueryBase().queryParam(QUERY_PARAM_IMAGE_SCAN_NOTE, PARAM_VALUE_NOTE3)
                       .put(scanUrl).then().assertThat().statusCode(200);

        final Scan retrievedScan5 = getScan(subject, mr1);
        assertEquals(retrievedScan5.getNote(), PARAM_VALUE_NOTE3);

        // Test that updating scan works with XML path with MR scan XSI type
        mainQueryBase().queryParam(QUERY_PARAM_MR_SCAN_NOTE, PARAM_VALUE_NOTE4)
                       .put(scanUrl).then().assertThat().statusCode(200);

        final Scan retrievedScan6 = getScan(subject, mr1);
        assertEquals(retrievedScan6.getNote(), PARAM_VALUE_NOTE4);

        /*
        // XNAT-7014 Change to implement this was rolled back for 1.8.6 release. Should be pushed back in for 1.8.7.
        // Test that updating scan fails with XML path with CT scan XSI type
        mainQueryBase().queryParam(QUERY_PARAM_CT_SCAN_NOTE, PARAM_VALUE_BAD_NOTE)
                       .put(scanUrl).then()
                       .assertThat().statusCode(409).and()
                       .body(Matchers.containsString(MESSAGE_BAD_XSI_TYPE));

        // Make sure that the note property didn't change
        final Scan retrievedScan7 = getScan(subject, mr1);
        assertEquals(retrievedScan7.getNote(), PARAM_VALUE_NOTE4);
        */
    }

    @Test(groups = {SMOKE, IMPORTER})
    @Basic
    public void testImportToArchive() {
        final ImagingSession session = readMr1(SCAN_ID_1, "MR1");

        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        destArchive().
                        param("project", project.getId()). // want to explicitly test this parameter
                        param("subject", session.getSubject().getLabel()).
                        session(session.getLabel()).
                        overwrite(MergeBehavior.APPEND).
                        file(sessionZip)
        );

        restDriver.validateResource(mainUser, session.getScans().get(0).getScanResources().get(0)); // should have been created by session importer
    }

    @Test(groups = {IMPORTER, PREARCHIVE})
    public void testBasicArchiveFromPrearc() {
        final ImagingSession session = readMr1("SPP_0x220790", "SPP_0x220790_MR2");

        final String prearcUrl = mainInterface().callImporter(new DefaultImporterRequest().
                triggerPipelines(false).
                destPrearchive(project).
                overwrite(MergeBehavior.APPEND).
                file(sessionZip)
        );

        mainInterface().requestArchival(
                new XnatArchivalRequest().
                        triggerPipelines(false).
                        src(prearcUrl).
                        overwrite(MergeBehavior.APPEND)
        );

        restDriver.validateResource(mainUser, session.getScans().get(0).getScanResources().get(0)); // should have been created by session importer
    }

    @Test(groups = {SMOKE, IMPORTER, PREARCHIVE})
    @Basic
    public void testBasicArchiveFromPrearcWParams() {
        final ImagingSession session = readMr1(SCAN_ID_1, "MR1");
        session.getScans().get(0).setId("ARC_TEST");

        final String prearcUrl = mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        destPrearchive(project).
                        overwrite(MergeBehavior.APPEND).
                        file(sessionZip)
        );

        mainInterface().requestArchival(
                new XnatArchivalRequest().
                        triggerPipelines(false).
                        overwrite(MergeBehavior.APPEND).
                        src(prearcUrl).
                        param("xnat:mrSessionData/project", project.getId()).
                        param("xnat:mrSessionData/subject_id", session.getSubject().getLabel()).
                        param("xnat:mrSessionData/label", session.getLabel()).
                        param("xnat:mrSessionData/scans/scan[0][@xsi:type=xnat:mrScanData]/type", session.getScans().get(0).getId())
        );

        restDriver.validateResource(mainUser, session.getScans().get(0).getScanResources().get(0)); // should have been created by session importer
    }

    private ImagingSession readMr1(String subjectLabel, String sessionLabel) {
        final Subject subject = new Subject(project, subjectLabel);
        final ImagingSession session = new MRSession(project, subject, sessionLabel);
        final Scan scan = new MRScan(session, SCAN_ID_1);
        final Resource scanDicom = new ScanResource(project, subject, session, scan).folder("DICOM");
        for (int i = 1; i <= 6; i++) {
            final File dicomFile = getDataFile(String.format("mr_1/%d.dcm", i));
            new ResourceFile(scanDicom, dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile));
        }
        return session;
    }

    private Scan getScan(final Subject subject, final ImagingSession session) {
        final List<Scan> retrievedScans = mainInterface().readScans(project, subject, session);
        assertEquals(1, retrievedScans.size());
        return retrievedScans.get(0);
    }
}
