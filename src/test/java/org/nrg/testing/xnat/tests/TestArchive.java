package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.DeprecatedIn;
import org.nrg.testing.xnat.BaseXnatRestTest;
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

import static org.testng.AssertJUnit.assertEquals;

public class TestArchive extends BaseXnatRestTest {

    private Project project;
    private final File sessionZip = getDataFile("mr_1.zip");

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
        final Scan scan1 = new MRScan(session, "1").seriesDescription("localizer").quality("usable");
        final Scan scan2 = new MRScan(session, "2").seriesDescription("localizer").quality("questionable");
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
        final String commonSeriesDescription = "localizer";
        final String scanType = "MAPPED";

        final Subject subject = new Subject(project);
        final ImagingSession mr1 = new MRSession(project, subject, "MR1").date(LocalDate.parse("2001-01-01"));
        final Scan mr1Scan1 = new MRScan(mr1, "1").seriesDescription(commonSeriesDescription).quality("usable"); // unmapped type
        final Scan mr1Scan2 = new MRScan(mr1, "2").seriesDescription(commonSeriesDescription).quality("questionable"); // unmapped type
        new ScanResource(project, subject, mr1, mr1Scan1).folder("DICOM").format("DICOM");
        new ScanResource(project, subject, mr1, mr1Scan2).folder("DICOM").format("DICOM");

        final ImagingSession mr2 = new MRSession(project, subject, "MR2").date(LocalDate.parse("2001-01-02"));
        new MRScan(mr2, "1").seriesDescription(commonSeriesDescription).type(scanType).quality("usable"); // mapped type!

        mainInterface().createSubject(subject);

        // call fixScanTypes
        // all other $commonSeriesDescription scans in this project have been labeled as '$scanType', so these should be too.
        mainQueryBase().queryParam("fixScanTypes", true).put(mainInterface().subjectAssessorUrl(mr1)).then().assertThat().statusCode(200);

        final Scan[] readScans = mainInterface().jsonQuery().get(mainInterface().sessionScansUrl(mr1)).jsonPath().getObject("ResultSet.Result", Scan[].class);
        assertEquals(mr1.getScans().size(), readScans.length);

        for (Scan scan : readScans) {
            assertEquals(scanType, scan.getType());
        }
    }

    @Test
    @Basic
    public void testImportToArchive() {
        final ImagingSession session = readMr1("1", "MR1");

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

    @Test
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

    @Test
    @Basic
    public void testBasicArchiveFromPrearcWParams() {
        final ImagingSession session = readMr1("1", "MR1");
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
        final Scan scan = new MRScan(session, "1");
        final Resource scanDicom = new ScanResource(project, subject, session, scan).folder("DICOM");
        for (int i = 1; i <= 6; i++) {
            final File dicomFile = getDataFile(String.format("mr_1/%d.dcm", i));
            new ResourceFile(scanDicom, dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile));
        }
        return session;
    }

}
