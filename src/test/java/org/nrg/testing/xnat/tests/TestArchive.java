package org.nrg.testing.xnat.tests;

import org.nrg.testing.file.FileIO;
import org.nrg.testing.xnat.BaseXnatRestTest;
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
    private final File sessionZip = FileIO.getDataFile("mr_1.zip");

    @BeforeClass
    public void disableSiteAnonScript() {
        restDriver.disableSiteAnonScript(mainAdminUser);
    }

    @AfterClass(alwaysRun = true)
    public void enableSiteAnonScript() {
        restDriver.enableSiteAnonScript(mainAdminUser);
    }

    @BeforeMethod
    public void createTestProject() {
        project = new Project();
        restDriver.createProject(mainUser, project);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteTestProject() {
        project = new Project();
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @Test
    public void testWebQCGeneration() {
        final Subject subject = new Subject(project);
        final ImagingSession session = new MRSession(project, subject).date(LocalDate.parse("2001-01-01"));
        final Scan scan1 = new MRScan(session, "1").seriesDescription("localizer").quality("usable");
        final Scan scan2 = new MRScan(session, "2").seriesDescription("localizer").quality("questionable");
        final Resource scan1Resource = new ScanResource(project, subject, session, scan1).folder("DICOM").format("DICOM");
        final Resource scan2Resource = new ScanResource(project, subject, session, scan2).folder("DICOM").format("DICOM");

        for (int i = 1; i < 6; i++) {
            final File dicomFile = FileIO.getDataFile(String.format("mr_1/%d.dcm", i));
            scan1Resource.addResourceFile(new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile)));
            scan2Resource.addResourceFile(new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile)));
        }

        restDriver.createSubject(mainUser, subject);

        mainCredentials().given().queryParam("triggerPipelines", true).put(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        restDriver.waitForAutoRun(mainUser, 60, session);

        mainCredentials().queryParam("file_content", "ORIGINAL").queryParam("index", 0).
                get(restDriver.resourceFilesUrl(new ScanResource(project, subject, session, scan1).folder("SNAPSHOTS"))).
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

        restDriver.createSubject(mainUser, subject);

        // call fixScanTypes
        // all other $commonSeriesDescription scans in this project have been labeled as '$scanType', so these should be to.
        mainCredentials().queryParam("fixScanTypes", true).put(restDriver.subjectAssessorUrl(mr1)).then().assertThat().statusCode(200);

        final Scan[] readScans = mainCredentials().given().queryParam("format", "json").get(restDriver.sessionScansUrl(mr1)).jsonPath().getObject("ResultSet.Result", Scan[].class);
        assertEquals(mr1.getScans().size(), readScans.length);

        for (Scan scan : readScans) {
            assertEquals(scanType, scan.getType());
        }
    }

    @Test
    public void testImportToArchive() {
        final ImagingSession session = readMr1("1", "MR1");

        mainCredentials().given().
                queryParam("triggerPipelines", false).
                queryParam("dest", "/archive").
                queryParam("project", project.getId()).
                queryParam("subject", session.getSubject().getLabel()).
                queryParam("session", session.getLabel()).
                queryParam("overwrite", "append").
                multiPart(sessionZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        restDriver.validateResource(mainUser, session.getScans().get(0).getScanResources().get(0)); // should have been created by session importer
    }

    @Test
    public void testBasicArchiveFromPrearc() {
        final ImagingSession session = readMr1("SPP_0x220790", "SPP_0x220790_MR2");

        final String prearcUrl = mainCredentials().given().
                queryParam("triggerPipelines", false).
                queryParam("dest", "/prearchive/projects/" + project.getId()).
                queryParam("overwrite", "append").
                multiPart(sessionZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200).
                and().extract().body().asString().trim();

        mainCredentials().given().
                queryParam("triggerPipelines", false).
                queryParam("src", prearcUrl).
                queryParam("overwrite", "append").
                post(formatRestUrl("services/archive")).
                then().assertThat().statusCode(200);

        restDriver.validateResource(mainUser, session.getScans().get(0).getScanResources().get(0)); // should have been created by session importer
    }

    @Test
    public void testBasicArchiveFromPrearcWParams() {
        final ImagingSession session = readMr1("1", "MR1");
        session.getScans().get(0).setId("ARC_TEST");

        final String prearcUrl = mainCredentials().given().
                queryParam("triggerPipelines", false).
                queryParam("dest", "/prearchive/projects/" + project.getId()).
                queryParam("overwrite", "append").
                multiPart(sessionZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200).
                and().extract().body().asString().trim();

        mainCredentials().given().
                queryParam("triggerPipelines", false).
                queryParam("overwrite", "append").
                queryParam("src", prearcUrl).
                queryParam("xnat:mrSessionData/project", project.getId()).
                queryParam("xnat:mrSessionData/subject_id", session.getSubject().getLabel()).
                queryParam("xnat:mrSessionData/label", session.getLabel()).
                queryParam("xnat:mrSessionData/scans/scan[0][@xsi:type=xnat:mrScanData]/type", session.getScans().get(0).getId()).
                post(formatRestUrl("services/archive")).
                then().assertThat().statusCode(200);

        restDriver.validateResource(mainUser, session.getScans().get(0).getScanResources().get(0)); // should have been created by session importer
    }

    private ImagingSession readMr1(String subjectLabel, String sessionLabel) {
        final Subject subject = new Subject(project, subjectLabel);
        final ImagingSession session = new MRSession(project, subject, sessionLabel);
        final Scan scan = new MRScan(session, "1");
        final Resource scanDicom = new ScanResource(project, subject, session, scan).folder("DICOM");
        for (int i = 1; i <= 6; i++) {
            final File dicomFile = FileIO.getDataFile(String.format("mr_1/%d.dcm", i));
            new ResourceFile(scanDicom, dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile));
        }
        return session;
    }

}
