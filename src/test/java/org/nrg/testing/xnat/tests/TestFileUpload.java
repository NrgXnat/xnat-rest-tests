package org.nrg.testing.xnat.tests;

import io.restassured.response.Response;
import org.nrg.testing.*;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xdat.bean.XnatMrsessiondataBean;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.*;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.zip.ZipFile;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

public class TestFileUpload extends BaseXnatRestTest {

    private final SimpleDateFormat americanDate = new SimpleDateFormat("MM/dd/yyyy");
    private final File testZip = getDataFile("mr_1.zip");
    private final File testTarGz = getDataFile("mr_1.tar.gz");
    private final File testTar = getDataFile("mr_1.tar");
    private final File testTgz = getDataFile("mr_1.tgz");
    private final File dicomFile1 = getDataFile("mr_1/1.dcm");
    private final File dicomFile2 = getDataFile("mr_1/2.dcm");
    private final File dicomFile3 = getDataFile("mr_1/3.dcm");
    private final File dummyFile = getDataFile("dummy.txt");
    private Project project;
    private Subject subject;
    private ImagingSession session;

    @BeforeClass
    public void disableSiteAnon() {
        mainAdminInterface().disableSiteAnonScript();
    }

    @BeforeMethod
    public void setupFileUploadProject() {
        project = testSpecificProject;
        subject = new Subject(project, "1").gender(Gender.MALE);
        session = new MRSession(project, subject, "MR1").date(LocalDate.parse("2000-01-01"));
        mainInterface().createProject(project);
        TimeUtils.sleep(1000); // cache update
    }

    @AfterMethod(alwaysRun = true)
    public void removeFileUploadProject() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @AfterClass(alwaysRun = true)
    public void enableSiteAnon() {
        mainAdminInterface().enableSiteAnonScript();
    }

    @Test
    @Basic
    public void testImageUploadWResourcePrecreate() {
        final Scan scan1 = new MRScan(session, "1").seriesDescription("LOCALIZER").quality("usable");
        new MRScan(session, "2").seriesDescription("localizer").quality("questionable");
        final ResourceFile resourceFile = new ResourceFile().name(dicomFile1.getName()).extension(new SimpleResourceFileExtension(dicomFile1));
        final Resource scanResource = new ScanResource(project, subject, session, scan1).folder("DICOM").addResourceFile(resourceFile);

        for (Scan scan : session.getScans()) {
            mainInterface().createScan(project, subject, session, scan);
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
            mainInterface().createScan(project, subject, session, scan);
        }

        restDriver.validateResource(mainUser, scanResource);
    }

    @Test
    public void testResourcesUpload() throws IOException {
        assertEquals(TimeUtils.MM_DD_YYYY.format(session.getDate()), americanDate.format(readMrBean().getDate()));

        final ResourceFile sessionResourceFile = new ResourceFile().name(dicomFile1.getName()).extension(new SimpleResourceFileExtension(dicomFile1));
        final Resource     sessionResource     = new SubjectAssessorResource(project, subject, session, "TEST").addResourceFile(sessionResourceFile);
        final ResourceFile subjectResourceFile = new ResourceFile().name(dicomFile2.getName()).extension(new SimpleResourceFileExtension(dicomFile2));
        final Resource     subjectResource     = new SubjectResource(project, subject, "TEST").addResourceFile(subjectResourceFile);
        final ResourceFile projectResourceFile = new ResourceFile().name(dicomFile3.getName()).extension(new SimpleResourceFileExtension(dicomFile3));
        final Resource     projectResource     = new ProjectResource(project, "TEST").addResourceFile(projectResourceFile);

        for (Resource resource : new Resource[]{sessionResource, subjectResource, projectResource}) {
            final ResourceFile file = resource.getResourceFiles().get(0);
            mainCredentials().multiPart(file.getExtension().getJavaFile()).put(mainInterface().resourceFileUrl(resource, file)).then().assertThat().statusCode(200);
        }

        restDriver.validateResource(mainUser, sessionResource);
        restDriver.validateResource(mainUser, subjectResource);
        restDriver.validateResource(mainUser, projectResource);

        final File downloadedZip = restDriver.saveBinaryResponseToFile(
                mainCredentials().queryParam("format", "zip").queryParam("compression", 0).
                        get(mainInterface().resourceFilesUrl(sessionResource)).then().assertThat().statusCode(200).and().extract().response()
        );

        final Path unzippedFolder = Paths.get(Settings.TEMP_SUBDIR, "resourcesUpload");

        FileIOUtils.unzip(unzippedFolder.toFile(), downloadedZip);

        TestNgUtils.assertBinaryFilesEqual(
                dicomFile1,
                unzippedFolder.resolve(String.format("%s/resources/%s/files/%s", session.getLabel(), sessionResource.getFolder(), dicomFile1.getName())).toFile()
        );

        assertEquals(TimeUtils.MM_DD_YYYY.format(session.getDate()), americanDate.format(readMrBean().getDate()));

        // test sub directory access

        final String noSubdir = "1.dcm";
        final String oneSubdir = "sub/2.dcm";
        final String twoSubdirs = "sub/folder/3.dcm";
        final Resource subdirResource = new ProjectResource(project, "TEST2");
        final String subdirFilesUrl = mainInterface().resourceFilesUrl(subdirResource);

        for (String path : new String[]{noSubdir, oneSubdir, twoSubdirs}) {
            mainCredentials().multiPart(dicomFile3).put(CommonStringUtils.formatUrl(subdirFilesUrl, path)).then().assertThat().statusCode(200);
        }

        assertEquals(2, getJsonTableSize(mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(subdirFilesUrl, "sub") + "/")));
        assertEquals(1, getJsonTableSize(mainCredentials().queryParam("format", "json").queryParam("recursive", false).get(CommonStringUtils.formatUrl(subdirFilesUrl, "sub") + "/")));

        final File downloadedZip2 = restDriver.saveBinaryResponseToFile(mainCredentials().queryParam("format", "zip").get(CommonStringUtils.formatUrl(subdirFilesUrl, "sub") + "/"));
        assertEquals(2, new ZipFile(downloadedZip2).size());

        mainCredentials().delete(CommonStringUtils.formatUrl(subdirFilesUrl, "sub/folder") + "/").then().assertThat().statusCode(200);
        assertEquals(1, getJsonTableSize(mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(subdirFilesUrl, "sub") + "/")));
    }

    @Test
    public void testReconstructionUpload() {
        final String reconUrl = CommonStringUtils.formatUrl(mainInterface().subjectAssessorUrl(session), "reconstructions/1_MR1_2");
        mainCredentials().queryParam("format", "xml").queryParam("req_format", "qs").queryParam("type", "LOCALIZER").put(reconUrl).then().assertThat().statusCode(200);
        mainCredentials().multiPart(dicomFile3).put(CommonStringUtils.formatUrl(reconUrl, "resources/TEST/files/3.dcm")).then().assertThat().statusCode(200);

        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(reconUrl, "resources/TEST/files/3.dcm"), dicomFile3);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(reconUrl, "files/3.dcm"), dicomFile3);
    }

    @Test
    public void testTextIMGUploadWOPrecreate() {
        final Scan scan1 = new MRScan(session, "1").seriesDescription("LOCALIZER").quality("usable");
        final Scan scan2 = new MRScan(session, "2").seriesDescription("localizer").quality("questionable");
        mainInterface().createScan(project, subject, session, scan1);
        mainInterface().createScan(project, subject, session, scan2);
        final String scanUrl = mainInterface().scanUrl(scan1);

        mainCredentials().multiPart(dicomFile2).put(CommonStringUtils.formatUrl(scanUrl, "resources/TEST/files/3.dcm")).then().assertThat().statusCode(200);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(scanUrl, "resources/TEST/files/3.dcm"), dicomFile2);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(scanUrl, "files/3.dcm"), dicomFile2);
    }


    @Test
    public void testManQCUpload() {
        final String assessorUrl = mainInterface().sessionAssessorUrl(new ManualQC(project, subject, session, "MR1_ManualQC"));
        mainCredentials().queryParam("xsiType", DataType.MANUAL_QC.getXsiType()).queryParam("xnat:qcManualAssessorData/pass", true).put(assessorUrl).then().assertThat().statusCode(201);
        mainCredentials().multiPart(dicomFile1).put(CommonStringUtils.formatUrl(assessorUrl, "out/resources/DICOM/files/1.dcm")).then().assertThat().statusCode(200);

        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(assessorUrl, "out/resources/DICOM/files/1.dcm"), dicomFile1);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(assessorUrl, "out/files/1.dcm"), dicomFile1);
    }

    @Test
    @Basic
    public void testScanUpload() {
        final Scan scan = new MRScan(session, "MR1_scan1");
        mainInterface().createScan(project, subject, session, scan);
        final String scanUrl = mainInterface().scanUrl(scan);

        mainCredentials().multiPart(dicomFile1).put(CommonStringUtils.formatUrl(scanUrl, "resources/DICOM/files/1.dcm")).then().assertThat().statusCode(200);

        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(scanUrl, "resources/DICOM/files/1.dcm"), dicomFile1);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(scanUrl, "files/1.dcm"), dicomFile1);
    }

    @Test
    public void testReconUpload() {
        final String reconUrl = CommonStringUtils.formatUrl(mainInterface().subjectAssessorUrl(session), "reconstructions/MR1_recon1");
        mainCredentials().put(reconUrl).then().assertThat().statusCode(200);
        mainCredentials().multiPart(dicomFile1).put(CommonStringUtils.formatUrl(reconUrl, "resources/DICOM/files/1.dcm")).then().assertThat().statusCode(200);

        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(reconUrl, "resources/DICOM/files/1.dcm"), dicomFile1);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(reconUrl, "files/1.dcm"), dicomFile1);
    }

    @Test
    public void testMRResourceUpload() {
        final String sessionUrl = mainInterface().subjectAssessorUrl(session);
        mainCredentials().multiPart(dicomFile2).put(CommonStringUtils.formatUrl(sessionUrl, "resources/DICOM/files/1.dcm"));

        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(sessionUrl, "resources/DICOM/files/1.dcm"), dicomFile2);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(sessionUrl, "files/1.dcm"), dicomFile2);
    }

    @Test
    public void testSubjectResourceUpload() {
        final String subjectUrl = mainInterface().subjectUrl(subject);
        mainCredentials().multiPart(dicomFile3).put(CommonStringUtils.formatUrl(subjectUrl, "resources/TEST1/files/1.dcm"));

        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(subjectUrl, "resources/TEST1/files/1.dcm"), dicomFile3);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(subjectUrl, "files/1.dcm"), dicomFile3);
    }

    @Test
    @Basic
    public void testProjectResourceUpload() {
        final String projectUrl = mainInterface().projectUrl(project);
        mainCredentials().multiPart(dicomFile3).put(CommonStringUtils.formatUrl(projectUrl, "resources/TEST1/files/1.dcm"));

        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(projectUrl, "resources/TEST1/files/1.dcm"), dicomFile3);
        restDriver.validateUpload(mainUser, CommonStringUtils.formatUrl(projectUrl, "files/1.dcm"), dicomFile3);
    }

    @Test
    public void testMRResourceUploadZip() {
        uploadAndExtractToSession(testZip);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testMRResourceUploadTar() {
        uploadAndExtractToSession(testTar);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testMRResourceUploadTarGz() {
        uploadAndExtractToSession(testTarGz);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testMRResourceUploadTgz() {
        uploadAndExtractToSession(testTgz);
    }

    @Test
    @Basic
    public void testScanResourceUploadZip() {
        uploadAndExtractToScan(testZip);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testScanResourceUploadTar() {
        uploadAndExtractToScan(testTar);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testScanResourceUploadTarGz() {
        uploadAndExtractToScan(testTarGz);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testScanResourceUploadTgz() {
        uploadAndExtractToScan(testTgz);
    }

    @Test
    public void testSubjectResourceUploadZip() {
        uploadAndExtractToSubject(testZip);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSubjectResourceUploadTar() {
        uploadAndExtractToSubject(testTar);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSubjectResourceUploadTarGz() {
        uploadAndExtractToSubject(testTarGz);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSubjectResourceUploadTgz() {
        uploadAndExtractToSubject(testTgz);
    }

    @Test
    public void testProjectResourceUploadZip() {
        uploadAndExtractToProject(testZip);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testProjectResourceUploadTar() {
        uploadAndExtractToProject(testTar);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testProjectResourceUploadTarGz() {
        uploadAndExtractToProject(testTarGz);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testProjectResourceUploadTgz() {
        uploadAndExtractToProject(testTgz);
    }

    private void uploadAndExtractToScan(File compressedFile) {
        final Scan scan = new MRScan(session, "MR1_scan1");
        mainInterface().createScan(project, subject, session, scan);
        final String scanUrl = mainInterface().scanUrl(scan);
        uploadAndExtract(scanUrl, compressedFile);
    }

    private void uploadAndExtractToSession(File compressedFile) {
        final String sessionUrl = mainInterface().subjectAssessorUrl(session);
        uploadAndExtract(sessionUrl, compressedFile);
    }

    private void uploadAndExtractToSubject(File compressedFile) {
        final String subjectUrl = mainInterface().subjectUrl(subject);
        uploadAndExtract(subjectUrl, compressedFile);
    }

    private void uploadAndExtractToProject(File compressedFile) {
        final String projectUrl = mainInterface().projectUrl(project);
        uploadAndExtract(projectUrl, compressedFile);
    }
    
    private void uploadAndExtract(String url, File compressedFile) {
        mainCredentials().multiPart(compressedFile)
                .queryParam("extract", "true")
                .post(CommonStringUtils.formatUrl(url, "resources/TEST/files"))
                .then().assertThat().statusCode(200);

        for (String name : Arrays.asList("1.dcm", "2.dcm", "3.dcm", "4.dcm", "5.dcm", "6.dcm")) {
            String fname = "mr_1/" + name;
            restDriver.validateUpload(mainUser,
                    CommonStringUtils.formatUrl(url, "resources/TEST/files/" + fname),
                    getDataFile(fname));
        }
    }

    /**
     * Checks the the totalRecords for an MR Session.
     * Test will first test against an empty MR session.
     * Then, the test will upload 3 new resources and test again.
     */
    @Test
    public void testMRResourcesTotalRecordsCount() {
        final String sessionUrl = mainInterface().subjectAssessorUrl(session);

        // Test Records Count with ?file_stats=true
        assertEquals(0, getTotalRecords(mainCredentials().queryParam("format", "json").queryParam("file_stats", true).get(CommonStringUtils.formatUrl(sessionUrl, "resources"))));

        // Test Records Count
        assertEquals(0, getTotalRecords(mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(sessionUrl, "resources"))));

        // Test Records Count with ?file_stats=true
        assertEquals(0, getTotalRecords(mainCredentials().queryParam("format", "json").queryParam("file_stats", false).get(CommonStringUtils.formatUrl(sessionUrl, "resources"))));

        // Upload three new resources to the MR1 experiment
        for (File dicomFile : new File[]{dicomFile1, dicomFile2, dicomFile3}) {
            final char fileNum = dicomFile.getName().charAt(0);
            mainCredentials().multiPart(dicomFile).
                    put(CommonStringUtils.formatUrl(sessionUrl, "resources/TEST" + fileNum, "files", fileNum + ".dcm")).then().assertThat().statusCode(200);
        }

        // Test Records Count with ?file_stats=true
        assertEquals(3, getTotalRecords(mainCredentials().queryParam("format", "json").queryParam("file_stats", true).get(CommonStringUtils.formatUrl(sessionUrl, "resources"))));

        // Test Records Count
        assertEquals(3, getTotalRecords(mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(sessionUrl, "resources"))));

        // Test Records Count with ?file_stats=true
        assertEquals(3, getTotalRecords(mainCredentials().queryParam("format", "json").queryParam("file_stats", false).get(CommonStringUtils.formatUrl(sessionUrl, "resources"))));
    }

    private XnatMrsessiondataBean readMrBean() {
        try {
            return (XnatMrsessiondataBean)LegacyComparison.readElementFromReponse(mainCredentials().queryParam("format", "xml").get(mainInterface().subjectAssessorUrl(session)).asInputStream());
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
