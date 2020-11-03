package org.nrg.testing.xnat.tests;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.log4j.Logger;
import org.hamcrest.Matchers;
import org.nrg.testing.CommonStringUtils;
import org.nrg.testing.TestNgUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.DisallowXnatVersion;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.versions.*;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.SiteConfig;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ScanResource;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.*;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.testng.AssertJUnit.*;

public class TestImport extends BaseXnatRestTest {

    private final Project project = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final File testZip = getDataFile("mr_1.zip");
    private final String subjectFromTestZip = "SPP_0x220790";
    private final String sessionFromTestZip = "SPP_0x220790_MR2";
    private final File scan1First11Files = getDataFile("scan1_first_11_files.zip");
    private final File scan1Last5Files = getDataFile("scan1_last_5_files.zip"); // disjoint in files from scan1First11Files
    private final File scan1Last6Files = getDataFile("scan1_last_6_files.zip"); // contains 1 DICOM file in common with scan1First11Files
    private final File scan1 = getDataFile("scan1.zip");
    private final File scan2 = getDataFile("scan2.zip"); // same study instance UID as scan1
    private final File scan2DiffUID = getDataFile("scan2_diffUID.zip"); // same as scan2, but with different study instance UID
    private int mrCount = 0;
    private static final Logger LOGGER = Logger.getLogger(TestImport.class);

    @BeforeClass
    public void setupImportProject() {
        restDriver.createProject(mainUser, project);
        restDriver.disableSiteAnonScript(mainAdminUser);
    }

    @BeforeMethod(alwaysRun = true) // clear out prearchive/archive for each test
    @AfterClass(alwaysRun = true) // ... and then clear them out when we're all done
    public void clearArchives() {
        try {
            restDriver.clearPrearchiveSessions(mainUser, project);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
        try {
            restDriver.clearProject(mainUser, project);
            TimeUtils.sleep(1000);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDownImportTests() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
        restDriver.enableSiteAnonScript(mainAdminUser);
        setCrossModalityMergePrevention(true);
    }

    @Test
    public void testDataProject() {
        // services/import?dest=/archive/projects/{PROJECT} POST

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", "/archive/projects/" + project.getId()).
                multiPart(testZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        final Subject subject = new Subject(project, subjectFromTestZip);
        final ImagingSession session = new MRSession(project, subject, sessionFromTestZip);

        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    @Test
    public void testDataSubject() {
        // services/import?dest=/archive/projects/{PROJECT}/subjects/SUBJECT POST

        final Subject subject = new Subject(project, "SUBJ1");

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", String.format("/archive/projects/%s/subjects/%s", project.getId(), subject.getLabel())).
                multiPart(testZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        final ImagingSession session = new MRSession(project, subject, sessionFromTestZip);

        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    @Test
    public void testDataProjectExperimentNew() {
        // services/import?dest=/archive/projects/{PROJECT}/subjects/SUBJECT/experiments/EXPERIMENT POST

        final Subject subject = new Subject(project, "SUBJ2");
        final ImagingSession session = new MRSession(project, subject, newLabel());

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel())).
                multiPart(testZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        restDriver.deleteSubjectAssessor(mainUser, session);

    }

    @Test
    public void testDataProjectExperimentOldRes() {
        // services/import?dest=/archive/projects/{PROJECT}/subjects/SUBJECT/experiments/EXPERIMENT POST

        final Subject subject = new Subject(project, "SUBJ_0002");
        final MRSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));
        final String resourceUrl = CommonStringUtils.formatUrl(restDriver.subjectAssessorUrl(session), "/resources/TEST/files/files.zip");

        // create subject & session with custom date
        restDriver.createSubject(mainUser, subject);

        // add misc resource
        mainCredentials().multiPart(testZip).put(resourceUrl).then().assertThat().statusCode(200);

        // upload scan
        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("overwrite", "append").
                queryParam("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel())).
                multiPart(testZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        // test scan upload
        mainCredentials().queryParam("format", "json").get(restDriver.sessionScansUrl(session)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        // test session date, should *not* be overwritten
        assertEquals(
                DateTimeFormatter.ISO_DATE.format(session.getDate()),
                mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(restDriver.subjectUrl(subject), "experiments")).
                        jsonPath().getString(String.format("ResultSet.Result.find { it.label == '%s' }.date", session.getLabel()))
        );

        restDriver.validateUpload(mainUser, resourceUrl, testZip);
        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    /**
     * Tests merging MR scans into an existing PET session... this should fail
     */
    @Test
    public void testDataProjectExperimentNewExptXSIOverwriteFDeleteF() {
        setCrossModalityMergePrevention(false);

        final Subject subject = new Subject(project, "SUBJ3");
        final ImagingSession session = new PETSession(project, subject).date(LocalDate.parse("2000-01-01"));

        restDriver.createSubject(mainUser, subject);

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel())).
                multiPart(testZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(409);

        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    /**
     * Tests merging MR scans into an existing PET session with overwrite=delete... this should fail unconditionally prior to 1.7.7 and conditionally afterward
     */
    @Test
    public void testDataProjectExperimentNewExptXSIOverwriteTDeleteF() {
        setCrossModalityMergePrevention(true);

        final Subject subject = new Subject(project, "SUBJ_03");
        final ImagingSession session = new PETSession(project, subject).date(LocalDate.parse("2000-01-01"));

        restDriver.createSubject(mainUser, subject);

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("overwrite", "delete").
                queryParam("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel())).
                multiPart(testZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(409);

        if (XnatVersionList.testedVersionFollows(Xnat_1_7_6.class)) {
            setCrossModalityMergePrevention(false);
            mainCredentials().
                    queryParam("triggerPipelines", false).
                    queryParam("overwrite", "delete").
                    queryParam("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel())).
                    multiPart(testZip).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode(200);

            final List<SubjectAssessor> subjectAssessors = restDriver.readSubjectAssesssors(mainUser, project, subject);
            assertEquals(1, subjectAssessors.size());

            final ImagingSession retrievedSessionRepresentation = (ImagingSession) subjectAssessors.get(0);
            assertEquals(DataType.PET_SESSION, retrievedSessionRepresentation.getDataType());

            assertEquals(DataType.MR_SCAN.getXsiType(), retrievedSessionRepresentation.findScan("1").getXsiType());
        }

        restDriver.deleteSubjectAssessor(mainUser, session);
    }


    @Test
    public void testDataDuplicateScanFilesOverwriteFDeleteF() {
        final Subject subject = new Subject(project, "SUBJ5");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));

        for (int i = 0; i < 2; i++) {
            mainCredentials().
                    queryParam("triggerPipelines", false).
                    queryParam("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel())).
                    multiPart(testZip).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode((i == 0) ? 200 : 409); // Should work only the first time
        }

        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    @Test
    public void testDataDuplicateScanFilesOverwriteTDeleteF() {
        final Subject subject = new Subject(project, "SUBJ_5");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));

        for (int i = 0; i < 2; i++) {
            final Map<String, Object> params = new HashMap<>();
            params.put("triggerPipelines", false);
            params.put("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel()));
            if (i == 1) params.put("overwrite", "append");

            mainCredentials().
                    queryParams(params).
                    multiPart(testZip).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode((i == 0) ? 200 : 409); // Should work only the first time
        }

        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    /**
     * This used to fail.  But now it should succeed.  Reimporting the same files is OK.
     */
    @Test
    public void testDataDuplicateScanFilesOverwriteTDeleteFWithFileFlag() {
        final Subject subject = new Subject(project, "SUBJ_05");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));

        for (int i = 0; i < 2; i++) {
            final Map<String, Object> params = new HashMap<>();
            params.put("triggerPipelines", false);
            params.put("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel()));
            if (i == 1) {
                params.put("overwrite", "append");
                params.put("overwrite_files", true);
            }

            mainCredentials().
                    queryParams(params).
                    multiPart(testZip).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode(200); // Should work either time
        }

        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    @Test
    public void testDataDuplicateScanFilesOverwriteTDeleteT() {
        final Subject subject = new Subject(project, "SUBJ_005");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));

        for (int i = 0; i < 2; i++) {
            final Map<String, Object> params = new HashMap<>();
            params.put("triggerPipelines", false);
            params.put("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel()));
            if (i == 1) {
                params.put("overwrite", "delete");
            }

            mainCredentials().
                    queryParams(params).
                    multiPart(testZip).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode(200); // Should work either time
        }

        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    @Test
    public void testDataScan2partsFilesOverwriteTDeleteF() {
        final Subject subject = new Subject(project, "SUBJ5");
        final ImagingSession session = new MRSession(project, subject, newLabel());
        final Scan scan = new MRScan(session, "1");

        for (int i = 0; i < 2; i++) {
            final Map<String, Object> params = new HashMap<>();
            params.put("triggerPipelines", false);
            params.put("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel()));
            if (i == 1) {
                params.put("overwrite", "append");
            }

            mainCredentials().
                    queryParams(params).
                    multiPart((i == 0) ? scan1First11Files : scan1Last5Files).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode(200); // Should work either time
        }

        mainCredentials().
                queryParam("format", "json").
                queryParam("all", true).
                get(CommonStringUtils.formatUrl(restDriver.scanUrl(scan), "files")).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(16)); // full scan1
    }

    @Test
    public void testDataScan2partsOverlapFilesOverwriteTDeleteT() {
        final Subject subject = new Subject(project, "SUBJ5");
        final ImagingSession session = new MRSession(project, subject, newLabel());
        final Scan scan = new MRScan(session, "1");

        for (int i = 0; i < 2; i++) {
            final Map<String, Object> params = new HashMap<>();
            params.put("triggerPipelines", false);
            params.put("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel()));
            if (i == 1) {
                params.put("overwrite", "delete");
            }

            mainCredentials().
                    queryParams(params).
                    multiPart((i == 0) ? scan1First11Files : scan1Last6Files).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode(200); // Should work either time
        }

        mainCredentials().
                queryParam("format", "json").
                queryParam("all", true).
                get(CommonStringUtils.formatUrl(restDriver.scanUrl(scan), "files")).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(16)); // full scan1
    }

    @Test
    public void testDataScan2partsOverlapFilesOverwriteTDeleteF() {
        final Subject subject = new Subject(project, "SUBJ5");
        final ImagingSession session = new MRSession(project, subject, newLabel());

        for (int i = 0; i < 2; i++) {
            final Map<String, Object> params = new HashMap<>();
            params.put("triggerPipelines", false);
            params.put("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel()));
            if (i == 1) {
                params.put("overwrite", "append");
            }

            mainCredentials().
                    queryParams(params).
                    multiPart((i == 0) ? scan1First11Files : scan1Last6Files).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode((i == 0) ? 200 : 409); // fails with overwrite=append and duplicate files
        }
    }

    @Test
    public void testPrearcMergeNewScan() {
        final String timestamp = "20000101_100001";
        final String session = newLabel();

        for (int i = 0; i < 2; i++) {
            mainCredentials().
                    queryParam("triggerPipelines", false).
                    queryParam("overwrite", "append").
                    queryParam("dest", String.format("/prearchive/projects/%s/%s/%s", project.getId(), timestamp, session)).
                    multiPart((i == 0) ? scan1 : scan2).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode(200);
        }

        for (int i = 1; i <= 2; i++) {
            restDriver.validateUpload(mainUser, formatRestUrl("prearchive/projects", project.getId(), timestamp, session, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm"), getDataFile(String.format("scan%d/000000.dcm", i)));
        }
    }

    @Test
    public void testPrearcMergeDiffUID() {
        final String timestamp = "20000101_100001";
        final String session = newLabel();

        for (int i = 0; i < 2; i++) {
            mainCredentials().
                    queryParam("triggerPipelines", false).
                    queryParam("overwrite", "append").
                    queryParam("dest", String.format("/prearchive/projects/%s/%s/%s", project.getId(), timestamp, session)).
                    multiPart((i == 0) ? scan1 : scan2DiffUID).
                    post(formatRestUrl("services/import")).
                    then().assertThat().statusCode(200);
        }

        for (int i = 1; i <= 2; i++) {
            restDriver.validateUpload(mainUser, formatRestUrl("prearchive/projects", project.getId(), timestamp, session, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm"),
                    getDataFile(String.format("%s/000000.dcm", (i == 1) ? "scan1" : "scan2_diffUID")));
        }
    }

    /**
     * This mimics the behavior of the upload assistant on a non-autoarchive project.
     */
    @Test
    public void testPrearcGradualImport() {
        final List<String> responseUris = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            responseUris.add(
                    mainCredentials().
                            queryParam("triggerPipelines", false).
                            queryParam("import-handler", "DICOM-zip").
                            queryParam("dest", "/prearchive/projects/" + project.getId()).
                            multiPart((i == 0) ? scan1 : scan2).
                            post(formatRestUrl("services/import")).
                            then().assertThat().statusCode(200).
                            and().extract().response().asString().trim()
            );
        }

        final String responseUri = responseUris.get(0);

        assertEquals(responseUri, responseUris.get(1));

        assertEquals(responseUri, mainCredentials().queryParam("action", "commit").post(restDriver.formatXnatUrl(responseUris.get(0))).asString());

        for (int i = 1; i < 3; i++) {
            TestNgUtils.assertBinaryFilesEqual(
                    getDataFile(String.format("scan%d/000000.dcm", i)),
                    restDriver.saveBinaryResponseToFile(mainCredentials().get(restDriver.formatXnatUrl(responseUri, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm")))
            );
        }
    }

    /**
     * This mimics a session sent from DICOM Browser into Unassigned, moved to a valid project, and archived.
     */
    @Test
    public void testUnassignedManualArchiveGradualImport() {
        final List<String> sessionUris = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            sessionUris.add(
                    mainCredentials().
                            queryParam("triggerPipelines", false).
                            queryParam("import-handler", "DICOM-zip").
                            queryParam("dest", "/prearchive").
                            multiPart((i == 0) ? scan1 : scan2).
                            post(formatRestUrl("services/import")).
                            then().assertThat().statusCode(200).
                            and().extract().response().asString().trim()
            );
        }

        final String unassignedUrl = sessionUris.get(0);

        assertEquals(unassignedUrl, sessionUris.get(1));

        mainAdminCredentials().queryParam("action", "build").post(restDriver.formatXnatUrl(unassignedUrl)).then().assertThat().statusCode(200);

        mainAdminCredentials().queryParam("action", "move").queryParam("newProject", project.getId()).post(restDriver.formatXnatUrl(unassignedUrl)).then().assertThat().statusCode(301);

        final String newSessionUrl = unassignedUrl.replace("Unassigned", project.getId());

        final String archiveUrl = mainCredentials().queryParam("action", "commit").queryParam("AA", true).post(restDriver.formatXnatUrl(newSessionUrl)).
                then().assertThat().statusCode(301).and().extract().response().asString().trim();

        assertFalse(unassignedUrl.equals(archiveUrl));
        assertTrue(archiveUrl.startsWith("/data/archive"));

        restDriver.waitForAutoRun(restDriver.readProject(mainUser, project.getId()).getSubjects().get(0).getSessions().get(0));

        for (int i = 1; i < 3; i++) {
            TestNgUtils.assertBinaryFilesEqual(
                    getDataFile(String.format("scan%d/000000.dcm", i)),
                    restDriver.saveBinaryResponseToFile(mainCredentials().get(restDriver.formatXnatUrl(archiveUrl, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm")))
            );
        }
    }

    /**
     * This mimics the behavior of the upload assistant on an auto archive project.
     */
    @Test
    public void testManualArchiveGradualImport() {
        final List<String> sessionUris = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            sessionUris.add(
                    mainCredentials().
                            queryParam("triggerPipelines", false).
                            queryParam("import-handler", "DICOM-zip").
                            queryParam("dest", "/prearchive/projects/" + project.getId()).
                            multiPart((i == 0) ? scan1 : scan2).
                            post(formatRestUrl("services/import")).
                            then().assertThat().statusCode(200).
                            and().extract().response().asString().trim()
            );
        }

        final String sessionUrl = sessionUris.get(0);

        assertEquals(sessionUrl, sessionUris.get(1));

        final String archiveUrl = mainCredentials().queryParam("action", "commit").queryParam("AA", true).post(restDriver.formatXnatUrl(sessionUrl)).
                then().assertThat().statusCode(301).and().extract().response().asString().trim();

        assertFalse(sessionUrl.equals(archiveUrl));
        assertTrue(archiveUrl.startsWith("/data/archive"));

        for (int i = 1; i < 3; i++) {
            TestNgUtils.assertBinaryFilesEqual(
                    getDataFile(String.format("scan%d/000000.dcm", i)),
                    restDriver.saveBinaryResponseToFile(mainCredentials().get(restDriver.formatXnatUrl(archiveUrl, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm")))
            );
        }
    }

    @Test
    public void testDataProjectExperimentNewScan() {
        final Subject subject = new Subject(project, "SUBJ_0009");
        restDriver.createSubject(mainUser, subject);
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("overwrite", "append").
                queryParam("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel())).
                multiPart(scan1).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        final Scan expectedScan = new MRScan(session, "1");
        final Resource expectedScanResource = new ScanResource(project, subject, session, expectedScan).folder("DICOM");

        final int scan1FileCount = mainCredentials().queryParam("format", "json").get(restDriver.resourceFilesUrl(expectedScanResource)).jsonPath().getInt("ResultSet.Result.size()");

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("overwrite", "append").
                queryParam("dest", String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel())).
                multiPart(scan2).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        mainCredentials().queryParam("format", "json").get(restDriver.sessionScansUrl(session)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(2));

        mainCredentials().queryParam("format", "json").get(restDriver.resourceFilesUrl(expectedScanResource)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(scan1FileCount));
    }

    @Test
    public void testNonPathBasedImport() {
        final String jsessionId = mainCredentials().get(formatRestUrl("JSESSION")).then().assertThat().statusCode(200).and().extract().response().asString();
        final String transactionId = "s" + Calendar.getInstance().getTimeInMillis();

        RestAssured.given().sessionId(jsessionId).
                queryParam("overwrite", "append").
                queryParam("http-session-listener", transactionId).
                queryParam("triggerPipelines", false).
                queryParam("project", project.getId()).
                queryParam("subject", "SUBJ_0010").
                queryParam("session", newLabel()).
                multiPart(testZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        RestAssured.given().sessionId(jsessionId).queryParam("format", "json").get(formatRestUrl("status", transactionId)).then().
                assertThat().body("msgs.get(0).last().status", Matchers.equalTo("COMPLETED"));
    }

    @Test
    public void testModifiedSeriesUIDs() {
        // 5 DICOM files from the original scan will be uploaded.
        // one frame has been modified to change the series UID.
        // that file should be uploaded first creating scan 1
        // the second file should be uploaded and generate a new scan (1-MR1) because it has a different series UID
        // the third file will match the second file, and should be added to 1-MR1.
        // the fourth and fifth files will be uploaded together and should go into scan 1.
        final Subject subject = new Subject(project, "SUB5");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));
        final Scan scan1 = new MRScan(session, "1");
        final Scan scan1MR1 = new MRScan(session, "1-MR1");
        final String archiveUrl = String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel());

        // upload frame 1, should create scan 1
        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", archiveUrl).
                multiPart(getDataFile("scan_mod/000001.dcm.zip")).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        // confirm that the data went where we expected
        mainCredentials().get(restDriver.scanUrl(scan1)).then().assertThat().statusCode(200);
        mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(restDriver.scanUrl(scan1), "files")).then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        // upload frame 2, should create scan 1-MR1
        TimeUtils.sleep(1000); // wait for 1s so cache backup dir is distinct
        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", archiveUrl).
                queryParam("overwrite", "delete").
                multiPart(getDataFile("scan_mod/000002.dcm.zip")).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        // confirm that the data went where we expected
        mainCredentials().get(restDriver.scanUrl(scan1MR1)).then().assertThat().statusCode(200);
        mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(restDriver.scanUrl(scan1MR1), "files")).then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        // upload frame 3, should be added to scan 1-MR1
        TimeUtils.sleep(1000); // wait for 1s so cache backup dir is distinct
        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", archiveUrl).
                queryParam("overwrite", "delete").
                multiPart(getDataFile("scan_mod/000003.dcm.zip")).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        // confirm that the data went where we expected
        mainCredentials().get(restDriver.scanUrl(scan1MR1)).then().assertThat().statusCode(200);
        mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(restDriver.scanUrl(scan1MR1), "files")).then().assertThat().body("ResultSet.Result", Matchers.hasSize(2));

        // upload frame 4 & 5, should be added to scan 1
        TimeUtils.sleep(1000); // wait for 1s so cache backup dir is distinct
        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", archiveUrl).
                queryParam("overwrite", "delete").
                multiPart(getDataFile("scan_mod/000004_000005.zip")).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        // confirm that the data went where we expected
        mainCredentials().get(restDriver.scanUrl(scan1)).then().assertThat().statusCode(200);
        mainCredentials().queryParam("format", "json").get(CommonStringUtils.formatUrl(restDriver.scanUrl(scan1), "files")).then().assertThat().body("ResultSet.Result", Matchers.hasSize(3));

        // test scan creations: should be 2 scans (1 and 1-MR1)
        mainCredentials().queryParam("format", "json").get(restDriver.sessionScansUrl(session)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(2));

        final JsonPath scan1MR1JsonPath = mainCredentials().queryParam("stats", true).queryParam("format", "json").get(CommonStringUtils.formatUrl(restDriver.scanUrl(scan1MR1), "resources")).
                then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("ResultSet.Result");

        assertEquals("Should have 1 catalog (DICOM).", 1, scan1MR1JsonPath.getInt("size()"));
        assertEquals("Should have 2 files.", 2, scan1MR1JsonPath.getInt("get(0).file_count"));

        final JsonPath scan1JsonPath = mainCredentials().queryParam("stats", true).queryParam("format", "json").get(CommonStringUtils.formatUrl(restDriver.scanUrl(scan1), "resources")).
                then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("ResultSet.Result");

        assertEquals("Should have 1 catalog (DICOM).", 1, scan1JsonPath.getInt("size()"));
        assertEquals("Should have 3 files.", 3, scan1JsonPath.getInt("get(0).file_count"));
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4,
            TestData.SAMPLE_1_SCAN_5,
            TestData.SAMPLE_1_SCAN_6
    })
    public void simpleAutoarchiveMerge() {
        restDriver.interfaceFor(mainAdminUser).setSessionXmlRebuilderTimes(1, 10000);
        final Project project = new Project().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);
        final Subject subject = new Subject(project, "Sample_Patient");
        final MRSession session = new MRSession(project, subject, "Sample_ID");
        new SessionImportExtension(restDriver.interfaceFor(mainUser), session, TestData.SAMPLE_1_SCAN_4.toFile());
        restDriver.createProject(mainUser, project);
        new XnatCStore().data(TestData.SAMPLE_1_SCAN_5).sendDICOMToProject(project);
        new XnatCStore().data(TestData.SAMPLE_1_SCAN_6).sendDICOMToProject(project);
        TimeUtils.sleep(60000);
        restDriver.waitForPrearchiveEmpty(mainUser, project, 120);
        restDriver.mainInterface().waitForPipelineCompletion(session, "Merged");
        final List<Scan> allScans = restDriver.readScans(mainUser, project, subject, session);
        assertEquals(3, allScans.size());
        for (Scan scan : allScans) {
            final List<Resource> scanResources = scan.getScanResources();
            assertEquals(176, restDriver.interfaceFor(mainUser).findResource(scanResources, "DICOM").getFileCount());
        }
        restDriver.deleteProject(mainUser, project);
    }

    @Test // Test content donated by Kate at Radiologics
    @AddedIn(Xnat_1_7_7.class)
    public void testUserCacheUploadAndImportLegacy() {
        final String jsessionId = mainCredentials().get(formatRestUrl("JSESSION")).then()
                .assertThat().statusCode(200).and().extract().response().asString();

        final Object[] testObjects = setupForUserCacheUpload(RestAssured.given().sessionId(jsessionId));
        final String    listener = (String) testObjects[0];
        final Project   project  = (Project) testObjects[1];
        final Subject   subject  = (Subject) testObjects[2];
        final MRSession session  = (MRSession) testObjects[3];
        final String archiveUrl  = (String) testObjects[4];

        final long start = System.currentTimeMillis();
        boolean completed = false;
        do {
            final JsonPath json = RestAssured.given().sessionId(jsessionId)
                    .queryParam("format", "json")
                    .get(restDriver.formatRestUrl("status", listener))
                    .then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("msgs.get(0)");
            final int len = json.getInt("size()") - 1;
            if (len >= 0) {
                final String msg = json.get("get(" + len + ").msg");
                final boolean terminal = json.getBoolean("get(" + len + ").terminal");
                if (terminal) {
                    assertEquals("COMPLETED", json.get("get(" + len + ").status"));
                    assertEquals("Archive:" + archiveUrl, msg);
                    completed = true;
                    break;
                }
            }
            TimeUtils.sleep(5000);
        } while (System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(20));

        assertTrue(completed);
        restDriver.waitForAutoRun(session);
        restDriver.deleteProject(mainUser, project);
    }

    @Test // Test content donated by Kate at Radiologics
    @TestRequires(users = 1)
    @AddedIn(Xnat_1_7_7.class)
    public void testUserCacheUploadAndImport() {
        final Object[] testObjects = setupForUserCacheUpload(mainQueryBase());
        final String    listener = (String) testObjects[0];
        final Project   project  = (Project) testObjects[1];
        final Subject   subject  = (Subject) testObjects[2];
        final MRSession session  = (MRSession) testObjects[3];
        final String archiveUrl  = (String) testObjects[4];

        final long start = System.currentTimeMillis();
        boolean succeeded = false;
        String finalMsg = null;
        do {
            final JsonPath json = mainQueryBase()
                    .queryParam("format", "json")
                    .get(restDriver.formatXapiUrl("event_tracking", listener))
                    .then().assertThat().statusCode(200).and().extract().jsonPath();

            try {
                succeeded = json.getBoolean("succeeded");
            } catch (NullPointerException e) {
                // succeeded is null while processing is running, so sleep and keep polling
                TimeUtils.sleep(5000);
                continue;
            }

            // succeeded is T or F: either way, we can break out of this loop once we collect the final message
            finalMsg = json.getString("finalMessage");
            break;
        } while (System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(20));

        assertTrue(succeeded);
        assertEquals("Archive:" + archiveUrl, finalMsg);

        // Validate that other users cannot poll the progress of this event
        User genericUser = getGenericUser();
        restDriver.queryBaseFor(genericUser)
                .queryParam("format", "json")
                .get(restDriver.formatXapiUrl("event_tracking", listener))
                .then().assertThat().statusCode(404);

        restDriver.waitForAutoRun(session);
        restDriver.deleteProject(mainUser, project);
    }

    private Object[] setupForUserCacheUpload(RequestSpecification reqSpec) {
        final String listener = Long.toString(System.currentTimeMillis());

        final Project project = new Project("project" + listener).prearchiveCode(PrearchiveCode.MANUAL);
        restDriver.createProject(mainUser, project);
        final Subject   subject = new Subject(project, "subject" + listener);
        final MRSession session = new MRSession(project, subject, "session" + listener);

        final String archiveUrl = String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel());

        // Upload to user cache
        final String cacheUrl = String.format("/user/cache/resources/%s/files/%s", listener, testZip.getName());
        mainCredentials().multiPart(testZip).put(restDriver.formatRestUrl(cacheUrl)).then().assertThat().statusCode(200);

        // Upload from user cache to project
        reqSpec.contentType("multipart/form-data")
                .multiPart("src", cacheUrl)
                .multiPart("http-session-listener", listener)
                .multiPart("dest", archiveUrl)
                .post(restDriver.formatRestUrl("services", "import")).then().assertThat().statusCode(200);

        return new Object[]{listener, project, subject, session, archiveUrl};
    }

    private String newLabel() {
        return "MR" + (mrCount++);
    }

    private void setCrossModalityMergePrevention(boolean state) {
        if (XnatVersionList.testedVersionFollows(Xnat_1_7_6.class)) {
            restDriver.postToSiteConfig(mainAdminUser, Collections.singletonMap(SiteConfig.PREVENT_CROSS_MODALITY_MERGE, state));
        }
    }

}
