package org.nrg.testing.xnat.tests;

import com.jayway.restassured.response.Response;
import org.joda.time.LocalDate;
import org.nrg.testing.CommonUtils;
import org.nrg.testing.LegacyComparison;
import org.nrg.testing.file.FileIO;
import org.nrg.testing.util.TestNgUtils;
import org.nrg.testing.xnat.BaseRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.extensions.SimpleResourceFileExtension;
import org.nrg.xdat.bean.XnatMrsessiondataBean;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pojo.DataType;
import org.nrg.xnat.pojo.Project;
import org.nrg.xnat.pojo.Subject;
import org.nrg.xnat.pojo.experiments.ImagingSession;
import org.nrg.xnat.pojo.experiments.Scan;
import org.nrg.xnat.pojo.experiments.assessors.ManualQC;
import org.nrg.xnat.pojo.experiments.scans.MRScan;
import org.nrg.xnat.pojo.experiments.sessions.MRSession;
import org.nrg.xnat.pojo.resources.*;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.zip.ZipFile;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

public class TestFileUpload extends BaseRestTest {

    private final SimpleDateFormat americanDate = new SimpleDateFormat("MM/dd/yyyy");
    private final File testZip = FileIO.getDataFile("mr_1.zip");
    private final File dicomFile1 = FileIO.getDataFile("mr_1/1.dcm");
    private final File dicomFile2 = FileIO.getDataFile("mr_1/2.dcm");
    private final File dicomFile3 = FileIO.getDataFile("mr_1/3.dcm");
    private final File dummyFile = FileIO.getDataFile("dummy.txt");
    private Project project;
    private Subject subject;
    private ImagingSession session;

    @BeforeClass
    public void disableSiteAnon() {
        restDriver.disableSiteAnonScript(mainAdminUser);
    }

    @BeforeMethod
    public void setupFileUploadProject() {
        project = testSpecificProject;
        subject = new Subject(project, "1").gender(Gender.MALE);
        session = new MRSession(project, subject, "MR1").date(new LocalDate("2000-01-01"));
        restDriver.createProject(mainUser, project);
    }

    @AfterMethod(alwaysRun = true)
    public void removeFileUploadProject() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @AfterClass(alwaysRun = true)
    public void enableSiteAnon() {
        restDriver.enableSiteAnonScript(mainAdminUser);
    }

    @Test
    public void testImageUploadWResourcePrecreate() {
        final Scan scan1 = new MRScan(session, "1").seriesDescription("LOCALIZER").quality("usable");
        new MRScan(session, "2").seriesDescription("localizer").quality("questionable");
        final ResourceFile resourceFile = new ResourceFile().name(dicomFile1.getName()).extension(new SimpleResourceFileExtension(dicomFile1));
        final Resource scanResource = new ScanResource(project, subject, session, scan1).folder("DICOM").addResourceFile(resourceFile);

        for (Scan scan : session.getScans()) {
            restDriver.createScan(mainUser, project, subject, session, scan);
        }

        restDriver.validateResource(mainUser, scanResource);
    }

    @Test
    public void testTextUploadWResourcePrecreate() {
        final Scan scan1 = new MRScan(session, "1").seriesDescription("LOCALIZER").quality("usable");
        new MRScan(session, "2").seriesDescription("localizer").quality("questionable");
        final ResourceFile resourceFile = new ResourceFile().name(dummyFile.getName()).extension(new SimpleResourceFileExtension(dummyFile));
        final Resource scanResource = new ScanResource(project, subject, session, scan1).folder("DICOM").addResourceFile(resourceFile);

        for (Scan scan : session.getScans()) {
            restDriver.createScan(mainUser, project, subject, session, scan);
        }

        restDriver.validateResource(mainUser, scanResource);
    }

    @Test
    public void testResourcesUpload() throws IOException {
        assertEquals(session.getDate().toString("MM/dd/yyyy"), americanDate.format(readMrBean().getDate()));

        final ResourceFile sessionResourceFile = new ResourceFile().name(dicomFile1.getName()).extension(new SimpleResourceFileExtension(dicomFile1));
        final Resource     sessionResource     = new SubjectAssessorResource(project, subject, session, "TEST").addResourceFile(sessionResourceFile);
        final ResourceFile subjectResourceFile = new ResourceFile().name(dicomFile2.getName()).extension(new SimpleResourceFileExtension(dicomFile2));
        final Resource     subjectResource     = new SubjectResource(project, subject, "TEST").addResourceFile(subjectResourceFile);
        final ResourceFile projectResourceFile = new ResourceFile().name(dicomFile3.getName()).extension(new SimpleResourceFileExtension(dicomFile3));
        final Resource     projectResource     = new ProjectResource(project, "TEST").addResourceFile(projectResourceFile);

        for (Resource resource : new Resource[]{sessionResource, subjectResource, projectResource}) {
            final ResourceFile file = resource.getResourceFiles().get(0);
            mainCredentials().multiPart(file.getExtension().getJavaFile()).put(restDriver.resourceFileUrl(resource, file)).then().assertThat().statusCode(200);
        }

        restDriver.validateResource(mainUser, sessionResource);
        restDriver.validateResource(mainUser, subjectResource);
        restDriver.validateResource(mainUser, projectResource);

        final File downloadedZip = restDriver.saveBinaryResponseToFile(
                mainCredentials().queryParam("format", "zip").queryParam("compression", 0).
                        get(restDriver.resourceFilesUrl(sessionResource)).then().assertThat().statusCode(200).and().extract().response()
        );

        final Path unzippedFolder = Paths.get(Settings.TEMP_SUBDIR, "resourcesUpload");

        FileIO.unzip(unzippedFolder, downloadedZip);

        TestNgUtils.assertBinaryFilesEqual(
                dicomFile1,
                unzippedFolder.resolve(String.format("%s/resources/%s/files/%s", session.getLabel(), sessionResource.getFolder(), dicomFile1.getName())).toFile()
        );

        assertEquals(session.getDate().toString("MM/dd/yyyy"), americanDate.format(readMrBean().getDate()));

        // test sub directory access

        final String noSubdir = "1.dcm";
        final String oneSubdir = "sub/2.dcm";
        final String twoSubdirs = "sub/folder/3.dcm";
        final Resource subdirResource = new ProjectResource(project, "TEST2");
        final String subdirFilesUrl = restDriver.resourceFilesUrl(subdirResource);

        for (String path : new String[]{noSubdir, oneSubdir, twoSubdirs}) {
            mainCredentials().multiPart(dicomFile3).put(CommonUtils.formatUrl(subdirFilesUrl, path)).then().assertThat().statusCode(200);
        }

        assertEquals(2, getJsonTableSize(mainCredentials().queryParam("format", "json").get(CommonUtils.formatUrl(subdirFilesUrl, "sub") + "/")));
        assertEquals(1, getJsonTableSize(mainCredentials().queryParam("format", "json").queryParam("recursive", false).get(CommonUtils.formatUrl(subdirFilesUrl, "sub") + "/")));

        final File downloadedZip2 = restDriver.saveBinaryResponseToFile(mainCredentials().queryParam("format", "zip").get(CommonUtils.formatUrl(subdirFilesUrl, "sub") + "/"));
        assertEquals(2, new ZipFile(downloadedZip2).size());

        mainCredentials().delete(CommonUtils.formatUrl(subdirFilesUrl, "sub/folder") + "/").then().assertThat().statusCode(200);
        assertEquals(1, getJsonTableSize(mainCredentials().queryParam("format", "json").get(CommonUtils.formatUrl(subdirFilesUrl, "sub") + "/")));
    }

    @Test
    public void testReconstructionUpload() {
        final String reconUrl = CommonUtils.formatUrl(restDriver.subjectAssessorUrl(session), "reconstructions/1_MR1_2");
        mainCredentials().queryParam("format", "xml").queryParam("req_format", "qs").queryParam("type", "LOCALIZER").put(reconUrl).then().assertThat().statusCode(200);
        mainCredentials().multiPart(dicomFile3).put(CommonUtils.formatUrl(reconUrl, "resources/TEST/files/3.dcm")).then().assertThat().statusCode(200);

        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(reconUrl, "resources/TEST/files/3.dcm"), dicomFile3);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(reconUrl, "files/3.dcm"), dicomFile3);
    }

    @Test
    public void testTextIMGUploadWOPrecreate() {
        final Scan scan1 = new MRScan(session, "1").seriesDescription("LOCALIZER").quality("usable");
        final Scan scan2 = new MRScan(session, "2").seriesDescription("localizer").quality("questionable");
        restDriver.createScan(mainUser, project, subject, session, scan1);
        restDriver.createScan(mainUser, project, subject, session, scan2);
        final String scanUrl = restDriver.scanUrl(scan1);

        mainCredentials().multiPart(dicomFile2).put(CommonUtils.formatUrl(scanUrl, "resources/TEST/files/3.dcm")).then().assertThat().statusCode(200);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(scanUrl, "resources/TEST/files/3.dcm"), dicomFile2);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(scanUrl, "files/3.dcm"), dicomFile2);
    }


    @Test
    public void testManQCUpload() {
        final String assessorUrl = restDriver.sessionAssessorUrl(new ManualQC(project, subject, session, "MR1_ManualQC"));
        mainCredentials().queryParam("xsiType", DataType.MANUAL_QC.getXsiType()).queryParam("xnat:qcManualAssessorData/pass", true).put(assessorUrl).then().assertThat().statusCode(201);
        mainCredentials().multiPart(dicomFile1).put(CommonUtils.formatUrl(assessorUrl, "out/resources/DICOM/files/1.dcm")).then().assertThat().statusCode(200);

        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(assessorUrl, "out/resources/DICOM/files/1.dcm"), dicomFile1);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(assessorUrl, "out/files/1.dcm"), dicomFile1);
    }

    @Test
    public void testScanUpload() {
        final Scan scan = new MRScan(session, "MR1_scan1");
        restDriver.createScan(mainUser, project, subject, session, scan);
        final String scanUrl = restDriver.scanUrl(scan);

        mainCredentials().multiPart(dicomFile1).put(CommonUtils.formatUrl(scanUrl, "resources/DICOM/files/1.dcm")).then().assertThat().statusCode(200);

        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(scanUrl, "resources/DICOM/files/1.dcm"), dicomFile1);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(scanUrl, "files/1.dcm"), dicomFile1);
    }

    @Test
    public void testReconUpload() {
        final String reconUrl = CommonUtils.formatUrl(restDriver.subjectAssessorUrl(session), "reconstructions/MR1_recon1");
        mainCredentials().put(reconUrl).then().assertThat().statusCode(200);
        mainCredentials().multiPart(dicomFile1).put(CommonUtils.formatUrl(reconUrl, "resources/DICOM/files/1.dcm")).then().assertThat().statusCode(200);

        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(reconUrl, "resources/DICOM/files/1.dcm"), dicomFile1);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(reconUrl, "files/1.dcm"), dicomFile1);
    }

    @Test
    public void testMRResourceUpload() {
        final String sessionUrl = restDriver.subjectAssessorUrl(session);
        mainCredentials().multiPart(dicomFile2).put(CommonUtils.formatUrl(sessionUrl, "resources/DICOM/files/1.dcm"));

        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(sessionUrl, "resources/DICOM/files/1.dcm"), dicomFile2);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(sessionUrl, "files/1.dcm"), dicomFile2);
    }

    @Test
    public void testSubjectResourceUpload() {
        final String subjectUrl = restDriver.subjectUrl(subject);
        mainCredentials().multiPart(dicomFile3).put(CommonUtils.formatUrl(subjectUrl, "resources/TEST1/files/1.dcm"));

        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(subjectUrl, "resources/TEST1/files/1.dcm"), dicomFile3);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(subjectUrl, "files/1.dcm"), dicomFile3);
    }

    @Test
    public void testProjectResourceUpload() {
        final String projectUrl = restDriver.projectUrl(project);
        mainCredentials().multiPart(dicomFile3).put(CommonUtils.formatUrl(projectUrl, "resources/TEST1/files/1.dcm"));

        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(projectUrl, "resources/TEST1/files/1.dcm"), dicomFile3);
        restDriver.validateUpload(mainUser, CommonUtils.formatUrl(projectUrl, "files/1.dcm"), dicomFile3);
    }


    /**
     * Checks the the totalRecords for an MR Session.
     * Test will first test against an empty MR session.
     * Then, the test will upload 3 new resources and test again.
     */
    @Test
    public void testMRResourcesTotalRecordsCount() {
        final String sessionUrl = restDriver.subjectAssessorUrl(session);

        // Test Records Count with ?file_stats=true
        assertEquals(0, getTotalRecords(mainCredentials().queryParam("format", "json").queryParam("file_stats", true).get(CommonUtils.formatUrl(sessionUrl, "resources"))));

        // Test Records Count
        assertEquals(0, getTotalRecords(mainCredentials().queryParam("format", "json").get(CommonUtils.formatUrl(sessionUrl, "resources"))));

        // Test Records Count with ?file_stats=true
        assertEquals(0, getTotalRecords(mainCredentials().queryParam("format", "json").queryParam("file_stats", false).get(CommonUtils.formatUrl(sessionUrl, "resources"))));

        // Upload three new resources to the MR1 experiment
        for (File dicomFile : new File[]{dicomFile1, dicomFile2, dicomFile3}) {
            final char fileNum = dicomFile.getName().charAt(0);
            mainCredentials().multiPart(dicomFile).
                    put(CommonUtils.formatUrl(sessionUrl, "resources/TEST" + fileNum, "files", fileNum + ".dcm")).then().assertThat().statusCode(200);
        }

        // Test Records Count with ?file_stats=true
        assertEquals(3, getTotalRecords(mainCredentials().queryParam("format", "json").queryParam("file_stats", true).get(CommonUtils.formatUrl(sessionUrl, "resources"))));

        // Test Records Count
        assertEquals(3, getTotalRecords(mainCredentials().queryParam("format", "json").get(CommonUtils.formatUrl(sessionUrl, "resources"))));

        // Test Records Count with ?file_stats=true
        assertEquals(3, getTotalRecords(mainCredentials().queryParam("format", "json").queryParam("file_stats", false).get(CommonUtils.formatUrl(sessionUrl, "resources"))));
    }

    private XnatMrsessiondataBean readMrBean() {
        try {
            return (XnatMrsessiondataBean)LegacyComparison.readElementFromReponse(mainCredentials().queryParam("format", "xml").get(restDriver.subjectAssessorUrl(session)).asInputStream());
        } catch (Exception e) {
            fail("Failed to read MR bean: " + e);
            return null;
        }
    }

    private int getJsonTableSize(Response response) {
        return response.jsonPath().getList("ResultSet.Result").size();
    }

    private int getTotalRecords(Response response) {
        return response.jsonPath().getInt("ResultSet.totalRecords");
    }

}
