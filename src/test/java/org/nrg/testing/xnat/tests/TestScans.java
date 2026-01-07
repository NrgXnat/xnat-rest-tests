package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.ScanResource;
import org.nrg.xnat.versions.Xnat_1_8_6;
import org.nrg.xnat.versions.Xnat_1_9_3;
import org.testng.annotations.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

import static org.nrg.testing.TestGroups.SCANS;
import static org.nrg.testing.TestGroups.VALIDATION;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.AssertJUnit.assertNull;

@Test(groups = SCANS)
public class TestScans extends BaseXnatRestTest {
    @Test(groups = VALIDATION)
    @AddedIn(Xnat_1_8_6.class)
    public void testImplicitScanTypeChangeValidation() {
        final Project tempProject = registerTempProject();
        final Subject subject = new Subject(tempProject);
        final MRSession session = new MRSession(tempProject, subject);
        session.extension(new SessionImportExtension(session, getDataFile("scan1.zip")));
        mainInterface().createProject(tempProject);
        final Scan initialScan = mainInterface().readScans(tempProject, subject, session).get(0);
        assertEquals(DataType.MR_SCAN.getXsiType(), initialScan.getXsiType());
        mainInterface().queryBase().queryParam("xnat:ctScanData/note","test").put(mainInterface().scanUrl(initialScan))
                .then().assertThat().statusCode(200); // silently failing is not good, but XNAT-7176 is too painful to fix
        final Scan scanPostUpdate = mainInterface().readScans(tempProject, subject, session).get(0);
        assertEquals(DataType.MR_SCAN.getXsiType(), scanPostUpdate.getXsiType());
        assertNull(scanPostUpdate.getNote());
        mainInterface().deleteSubject(tempProject, subject);
    }

    // For scans created in UI, default scan type is "Unknown"
    private final static String DEFAULT_SCAN_TYPE = "Unknown";

    @Test
    @AddedIn(Xnat_1_9_3.class)
    public void testPullDataFromHeadersSession() {
        final Project project = registerTempProject();

        mainInterface().createProject(project);

        final Subject subject = new Subject(project);
        final ImagingSession session = new MRSession(project, subject).date(LocalDate.parse("2015-09-03"));

        final Scan scan1 = new MRScan(session, "1").seriesDescription("sag_flair").type(DEFAULT_SCAN_TYPE).quality("usable");
        final Resource scan1Resource = new ScanResource(project, subject, session, scan1).folder("DICOM").format("DICOM");

        mainInterface().createSubject(subject);
        Scan retrievedScan = mainInterface().readScans(project, subject, session).get(0);
        assertEquals(DEFAULT_SCAN_TYPE, retrievedScan.getType());

        // add DICOM file to scan resource
        final File dicomFile = getDataFile("mr_1/1.dcm");
        final ResourceFile resourceFile = new ResourceFile(scan1Resource, "1.dcm");
        mainQueryBase().multiPart(dicomFile).put(mainInterface().resourceFileUrl(scan1Resource, resourceFile)).then().assertThat().statusCode(200);

        // scan type is still Unknown
        retrievedScan = mainInterface().readScans(project, subject, session).get(0);
        assertEquals(DEFAULT_SCAN_TYPE, retrievedScan.getType());

        // until we do pullDataFromHeaders
        mainQueryBase().queryParam("pullDataFromHeaders", "true").put(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        // now scan type has been updated from DICOM metadata
        retrievedScan = mainInterface().readScans(project, subject, session).get(0);
        assertNotEquals(DEFAULT_SCAN_TYPE, retrievedScan.getType());
    }

    @Test
    @AddedIn(Xnat_1_9_3.class)
    public void testPullDataFromHeadersScan() {
        final Project project = registerTempProject();

        mainInterface().createProject(project);

        final Subject subject = new Subject(project);
        final ImagingSession session = new MRSession(project, subject).date(LocalDate.parse("2015-09-03"));
        // Using scan type "unknown" because that's what the UI uses if the user creates a scan manually

        final Scan scan1 = new MRScan(session, "1").seriesDescription("ax-20-flip").type(DEFAULT_SCAN_TYPE).quality("usable");
        final Resource scan1Resource = new ScanResource(project, subject, session, scan1).folder("DICOM").format("DICOM");
        final Scan scan2 = new MRScan(session, "2").seriesDescription("ax-25-flip").type(DEFAULT_SCAN_TYPE).quality("usable");
        final Resource scan2Resource = new ScanResource(project, subject, session, scan2).folder("DICOM").format("DICOM");

        mainInterface().createSubject(subject);
        List<Scan> retrievedScans = mainInterface().readScans(project, subject, session);
        retrievedScans.forEach(scan -> assertEquals(DEFAULT_SCAN_TYPE, scan.getType()));

        // add DICOM file to scan resources
        final File dicomFile1 = getDataFile("scan1/000000.dcm");
        final ResourceFile resourceFile1 = new ResourceFile(scan1Resource, "000000.dcm");
        mainQueryBase().multiPart(dicomFile1).put(mainInterface().resourceFileUrl(scan1Resource, resourceFile1)).then().assertThat().statusCode(200);

        final File dicomFile2 = getDataFile("scan2/000000.dcm");
        final ResourceFile resourceFile2 = new ResourceFile(scan1Resource, "000000.dcm");
        mainQueryBase().multiPart(dicomFile2).put(mainInterface().resourceFileUrl(scan2Resource, resourceFile2)).then().assertThat().statusCode(200);

        // scan type is still Unknown
        retrievedScans = mainInterface().readScans(project, subject, session);
        retrievedScans.forEach(scan -> assertEquals(DEFAULT_SCAN_TYPE, scan.getType()));

        // until we do pullDataFromHeaders
        mainQueryBase().queryParam("pullDataFromHeaders", "true").put(mainInterface().scanUrl(scan1)).then().assertThat().statusCode(200);

        // now scan type for 1 (but not 2) has been updated from DICOM metadata
        retrievedScans = mainInterface().readScans(project, subject, session);
        final Scan rs1 = retrievedScans.stream().filter(s -> "1".equals(s.getId())).findAny().get();
        final Scan rs2 = retrievedScans.stream().filter(s -> "2".equals(s.getId())).findAny().get();
        assertNotEquals(DEFAULT_SCAN_TYPE, rs1.getType());
        assertEquals(DEFAULT_SCAN_TYPE, rs2.getType());
    }
}
