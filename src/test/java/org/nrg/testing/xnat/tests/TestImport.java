package org.nrg.testing.xnat.tests;

import io.restassured.path.json.JsonPath;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.hamcrest.Matchers;
import org.nrg.testing.CommonStringUtils;
import org.nrg.testing.TestNgUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.versions.*;
import org.nrg.xnat.enums.MergeBehavior;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.ImportException;
import org.nrg.xnat.importer.importers.DefaultImporterRequest;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.importer.importers.SessionImporterRequest;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.ScanResource;
import org.nrg.xnat.pogo.resources.SubjectAssessorResource;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.rest.NotFoundException;
import org.nrg.xnat.versions.*;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.*;
import static org.testng.Assert.assertNotEquals;
import static org.testng.AssertJUnit.*;

@Test(groups = IMPORTER)
public class TestImport extends BaseXnatRestTest {

    private final Project project = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final File testZip = getDataFile("mr_1.zip");
    @SuppressWarnings("FieldCanBeLocal")
    private final String subjectFromTestZip = "SPP_0x220790";
    private final String sessionFromTestZip = "SPP_0x220790_MR2";
    private final File scan1First11Files = getDataFile("scan1_first_11_files.zip");
    private final File scan1Last5Files = getDataFile("scan1_last_5_files.zip"); // disjoint in files from scan1First11Files
    private final File scan1Last6Files = getDataFile("scan1_last_6_files.zip"); // contains 1 DICOM file in common with scan1First11Files
    private final File scan1 = getDataFile("scan1.zip");
    private final File scan2 = getDataFile("scan2.zip"); // same study instance UID as scan1
    private final File scan2DiffUID = getDataFile("scan2_diffUID.zip"); // same as scan2, but with different study instance UID
    private final File zipContainingNonDicom = getDataFile("mr_1_with_txt.zip");
    private final String nonDicomFilename = "non_dicom_text_file.dcm";
    private int mrCount = 0;
    private int conflictStatus = 409;
    private static final Logger LOGGER = Logger.getLogger(TestImport.class);

    @BeforeClass(groups = IMPORTER)
    private void setupImportEnvironment() {
        mainInterface().createProject(project);
        mainAdminInterface().disableSiteAnonScript();
        if ("DICOM-zip".equals(mainAdminInterface().readSiteConfig().getUiDefaultCompressedUploaderImporter())) {
            conflictStatus = 400;
        }
    }

    @BeforeClass(groups = IMPORTER)
    private void setupZip() throws IOException {
        if (zipContainingNonDicom.exists()) {
            //noinspection ResultOfMethodCallIgnored
            zipContainingNonDicom.delete();
        }
        if (!zipContainingNonDicom.exists()) {
            FileUtils.copyFile(testZip, zipContainingNonDicom);
            final Path tempStagingDir = Files.createTempDirectory(Paths.get(Settings.TEMP_SUBDIR), "mr_1_nondcm");
            final File tempFile = tempStagingDir.resolve(nonDicomFilename).toFile();
            FileUtils.write(tempStagingDir.resolve(nonDicomFilename).toFile(), "I'm actually a text file!", StandardCharsets.UTF_8);
            final ZipFile zipFile = new ZipFile(zipContainingNonDicom);
            zipFile.addFile(tempFile, new ZipParameters());
        }
    }

    @BeforeMethod(groups = IMPORTER, alwaysRun = true) // clear out prearchive/archive for each test
    @AfterClass(groups = IMPORTER, alwaysRun = true) // ... and then clear them out when we're all done
    private void clearArchives() {
        try {
            restDriver.clearPrearchiveSessions(mainUser, project);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
        try {
            mainInterface().deleteAllProjectData(project);
            TimeUtils.sleep(1000);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
    }

    @AfterClass(groups = IMPORTER, alwaysRun = true)
    private void tearDownImportTests() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testDataProject() {
        // services/import?dest=/archive/projects/{PROJECT} POST

        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        dest(project.getUri()).
                        file(testZip)
        );

        final Subject subject = new Subject(project, subjectFromTestZip);
        final ImagingSession session = new MRSession(project, subject, sessionFromTestZip);

        mainQueryBase().get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        mainInterface().deleteSubjectAssessor(session);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testDataSubject() {
        // services/import?dest=/archive/projects/{PROJECT}/subjects/SUBJECT POST

        final Subject subject = new Subject(project, "SUBJ1");

        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        dest(subject.getUri()).
                        file(testZip)
        );

        final ImagingSession session = new MRSession(project, subject, sessionFromTestZip);

        mainQueryBase().get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        mainInterface().deleteSubjectAssessor(session);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testDataProjectExperimentNew() {
        // services/import?dest=/archive/projects/{PROJECT}/subjects/SUBJECT/experiments/EXPERIMENT POST

        final Subject subject = new Subject(project, "SUBJ2");
        final ImagingSession session = new MRSession(project, subject, newLabel());

        mainInterface().callImporter(
                defaultRequestFor(session).file(testZip)
        );

        mainQueryBase().get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        mainInterface().deleteSubjectAssessor(session);

    }

    @Test
    public void testDataProjectExperimentOldRes() {
        // services/import?dest=/archive/projects/{PROJECT}/subjects/SUBJECT/experiments/EXPERIMENT POST

        final Subject subject = new Subject(project, "SUBJ_0002");
        final MRSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));
        final Resource sessionResource = new SubjectAssessorResource(project, subject, session, "TEST");
        final ResourceFile resourceFile = new ResourceFile().extension(new SimpleResourceFileExtension(testZip));
        sessionResource.addResourceFile(new ResourceFile().extension(new SimpleResourceFileExtension(testZip)));

        // create subject & session with custom date and misc resource
        mainInterface().createSubject(subject);

        // upload scan
        mainInterface().callImporter(
                defaultRequestFor(session).overwrite(MergeBehavior.APPEND).file(testZip)
        );

        // test scan upload
        mainInterface().jsonQuery().get(mainInterface().sessionScansUrl(session)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        // test session date, should *not* be overwritten
        assertEquals(
                DateTimeFormatter.ISO_DATE.format(session.getDate()),
                mainInterface().jsonQuery().get(CommonStringUtils.formatUrl(mainInterface().subjectUrl(subject), "experiments")).
                        jsonPath().getString(String.format("ResultSet.Result.find { it.label == '%s' }.date", session.getLabel()))
        );

        restDriver.validateUpload(mainUser, mainInterface().resourceFileUrl(sessionResource, resourceFile), testZip);
        mainInterface().deleteSubjectAssessor(session);
    }

    /**
     * Tests merging MR scans into an existing PET session... this should fail
     */
    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataProjectExperimentNewExptXSIOverwriteFDeleteF() {
        setCrossModalityMergePrevention(false);

        final Subject subject = new Subject(project, "SUBJ3");
        final ImagingSession session = new PETSession(project, subject).date(LocalDate.parse("2000-01-01"));

        mainInterface().createSubject(subject);

        try {
            mainInterface().callImporter(defaultRequestFor(session).file(testZip));
            failOnImproperSuccess();
        } catch (ImportException importException) {
            assertEquals(conflictStatus, importException.getStatusCode());
        }

        mainInterface().deleteSubjectAssessor(session);
    }

    /**
     * Tests merging MR scans into an existing PET session with overwrite=delete... this should fail unconditionally prior to 1.7.7 and conditionally afterward
     */
    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataProjectExperimentNewExptXSIOverwriteTDeleteF() {
        setCrossModalityMergePrevention(true);

        final Subject subject = new Subject(project, "SUBJ_03");
        final ImagingSession session = new PETSession(project, subject).date(LocalDate.parse("2000-01-01"));
        final SessionImporterRequest overwriteRequest = defaultRequestFor(session).overwrite(MergeBehavior.DELETE).file(testZip);

        mainInterface().createSubject(subject);

        try {
            mainInterface().callImporter(overwriteRequest);
            failOnImproperSuccess();
        } catch (ImportException importException) {
            assertEquals(conflictStatus, importException.getStatusCode());
        }

        if (XnatTestingVersionManager.testedVersionFollows(Xnat_1_7_6.class)) {
            setCrossModalityMergePrevention(false);
            mainInterface().callImporter(overwriteRequest);

            final List<SubjectAssessor> subjectAssessors = mainInterface().readSubjectAssessors(project, subject);
            assertEquals(1, subjectAssessors.size());

            final ImagingSession retrievedSessionRepresentation = (ImagingSession) subjectAssessors.get(0);
            assertEquals(DataType.PET_SESSION, retrievedSessionRepresentation.getDataType());

            assertEquals(DataType.MR_SCAN.getXsiType(), retrievedSessionRepresentation.findScan("1").getXsiType());
        }

        mainInterface().deleteSubjectAssessor(session);
    }


    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataDuplicateScanFilesOverwriteFDeleteF() {
        final Subject subject = new Subject(project, "SUBJ5");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));
        final SessionImporterRequest repeatedRequest = defaultRequestFor(session).file(testZip);

        mainInterface().callImporter(repeatedRequest);
        try {
            mainInterface().callImporter(repeatedRequest);
            failOnImproperSuccess();
        } catch (ImportException importException) {
            assertEquals(conflictStatus, importException.getStatusCode());
        }

        mainInterface().deleteSubjectAssessor(session);
    }

    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataDuplicateScanFilesOverwriteTDeleteF() {
        final Subject subject = new Subject(project, "SUBJ_5");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));
        final SessionImporterRequest request = defaultRequestFor(session).file(testZip);

        mainInterface().callImporter(request);
        try {
            mainInterface().callImporter(request.overwrite(MergeBehavior.APPEND));
            failOnImproperSuccess();
        } catch (ImportException importException) {
            assertEquals(conflictStatus, importException.getStatusCode());
        }

        mainInterface().deleteSubjectAssessor(session);
    }

    /**
     * This used to fail.  But now it should succeed.  Reimporting the same files is OK.
     */
    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataDuplicateScanFilesOverwriteTDeleteFWithFileFlag() {
        final Subject subject = new Subject(project, "SUBJ_05");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));
        final SessionImporterRequest importerRequest = defaultRequestFor(session).file(testZip);
        mainInterface().callImporter(importerRequest);
        mainInterface().callImporter(importerRequest.overwrite(MergeBehavior.APPEND).overwriteFiles());

        mainInterface().deleteSubjectAssessor(session);
    }

    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataDuplicateScanFilesOverwriteTDeleteT() {
        final Subject subject = new Subject(project, "SUBJ_005");
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));
        final SessionImporterRequest importerRequest = defaultRequestFor(session).file(testZip);
        mainInterface().callImporter(importerRequest);
        mainInterface().callImporter(importerRequest.overwrite(MergeBehavior.DELETE));

        mainInterface().deleteSubjectAssessor(session);
    }

    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataScan2partsFilesOverwriteTDeleteF() {
        final Subject subject = new Subject(project, "SUBJ5");
        final ImagingSession session = new MRSession(project, subject, newLabel());
        final Scan scan = new MRScan(session, "1");
        final SessionImporterRequest importerRequest = defaultRequestFor(session);
        mainInterface().callImporter(importerRequest.file(scan1First11Files));
        mainInterface().callImporter(importerRequest.overwrite(MergeBehavior.APPEND).file(scan1Last5Files));

        mainInterface().
                jsonQuery().
                queryParam("all", true).
                get(CommonStringUtils.formatUrl(mainInterface().scanUrl(scan), "files")).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(16)); // full scan1
    }

    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataScan2partsOverlapFilesOverwriteTDeleteT() {
        final Subject subject = new Subject(project, "SUBJ5");
        final ImagingSession session = new MRSession(project, subject, newLabel());
        final Scan scan = new MRScan(session, "1");
        final SessionImporterRequest importerRequest = defaultRequestFor(session);
        mainInterface().callImporter(importerRequest.file(scan1First11Files));
        mainInterface().callImporter(importerRequest.overwrite(MergeBehavior.DELETE).file(scan1Last6Files));

        mainInterface().
                jsonQuery().
                queryParam("all", true).
                get(CommonStringUtils.formatUrl(mainInterface().scanUrl(scan), "files")).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(16)); // full scan1
    }

    @Test(groups = {PREARCHIVE, MERGE})
    public void testDataScan2partsOverlapFilesOverwriteTDeleteF() {
        final Subject subject = new Subject(project, "SUBJ5");
        final ImagingSession session = new MRSession(project, subject, newLabel());
        final SessionImporterRequest importerRequest = defaultRequestFor(session);
        mainInterface().callImporter(importerRequest.file(scan1First11Files));
        try {
            mainInterface().callImporter(importerRequest.file(scan1Last6Files).overwrite(MergeBehavior.APPEND));
            failOnImproperSuccess(); // fails with overwrite=append and duplicate files
        } catch (ImportException importException) {
            assertEquals(conflictStatus, importException.getStatusCode());
        }
    }

    @Test(groups = {PREARCHIVE, MERGE})
    public void testPrearcMergeNewScan() {
        final String timestamp = "20000101_100001";
        final String session = newLabel();
        final SessionImporterRequest importerRequest = new DefaultImporterRequest().
                triggerPipelines(false).
                overwrite(MergeBehavior.APPEND).
                destPrearchive(project, timestamp, session);
        mainInterface().callImporter(importerRequest.file(scan1));
        mainInterface().callImporter(importerRequest.file(scan2));

        for (int i = 1; i <= 2; i++) {
            restDriver.validateUpload(mainUser, formatRestUrl("prearchive/projects", project.getId(), timestamp, session, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm"), getDataFile(String.format("scan%d/000000.dcm", i)));
        }
    }

    /**
     * Tests uploading a series to the prearchive and then merging a different series with a different Study Instance UID
     * into it. This breaks some critical assumptions about a session containing a single DICOM study, but is permitted
     * by SI.
     */
    @Test(groups = {PREARCHIVE, MERGE})
    public void testPrearcMergeDiffUidSi() {
        testPrearcDodgyUidMerge(
                () -> {
                    final String timestamp = "20000101_100001";
                    final String session = newLabel();
                    final SessionImporterRequest importerRequest = new SessionImporterRequest()
                            .triggerPipelines(false)
                            .overwrite(MergeBehavior.APPEND)
                            .destPrearchive(project, timestamp, session);
                    mainInterface().callImporter(importerRequest.file(scan1));
                    mainInterface().callImporter(importerRequest.file(scan2DiffUID));
                }, true
        );
    }

    /**
     * Tests uploading a series to the prearchive and then merging a different series with a different Study Instance UID
     * into it. This breaks some critical assumptions about a session containing a single DICOM study, and is not permitted
     * by DICOM-zip.
     */
    @Test(groups = {PREARCHIVE, MERGE})
    public void testPrearcMergeDiffUidDicomZip() {
        testPrearcDodgyUidMerge(
                () -> {
                    final String timestamp = "20000101_100001";
                    final String session = newLabel();
                    final DicomZipRequest importerRequest = new DicomZipRequest()
                            .overwrite(MergeBehavior.APPEND)
                            .destPrearchive(project, timestamp, session);
                    mainInterface().callImporter(importerRequest.file(scan1));

                    try {
                        mainInterface().callImporter(importerRequest.file(scan2DiffUID));
                        failOnImproperSuccess();
                    } catch (ImportException importException) {
                        assertEquals(400, importException.getStatusCode());
                    }
                }, false
        );
    }

    /**
     * This mimics the behavior of the upload assistant on a non-autoarchive project.
     */
    @Test(groups = PREARCHIVE)
    public void testPrearcGradualImport() {
        final List<String> responseUris = new ArrayList<>();
        final DicomZipRequest importerRequest = new DicomZipRequest().destPrearchive(project);
        responseUris.add(mainInterface().callImporter(importerRequest.file(scan1)));
        responseUris.add(mainInterface().callImporter(importerRequest.file(scan2)));

        final String responseUri = responseUris.get(0);

        assertEquals(responseUri, responseUris.get(1));

        assertEquals(responseUri, mainQueryBase().queryParam("action", "commit").post(formatXnatUrl(responseUris.get(0))).asString());

        for (int i = 1; i < 3; i++) {
            TestNgUtils.assertBinaryFilesEqual(
                    getDataFile(String.format("scan%d/000000.dcm", i)),
                    restDriver.saveBinaryResponseToFile(mainQueryBase().get(formatXnatUrl(responseUri, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm")))
            );
        }
    }

    /**
     * This mimics a session sent from DICOM Browser into Unassigned, moved to a valid project, and archived.
     */
    @Test(groups = {SMOKE, PREARCHIVE})
    @Basic
    public void testUnassignedManualArchiveGradualImport() {
        final List<String> sessionUris = new ArrayList<>();
        final DicomZipRequest importerRequest = new DicomZipRequest().destPrearchive();
        sessionUris.add(mainInterface().callImporter(importerRequest.file(scan1)));
        sessionUris.add(mainInterface().callImporter(importerRequest.file(scan2)));

        final String unassignedUrl = sessionUris.get(0);

        assertEquals(unassignedUrl, sessionUris.get(1));

        mainQueryBase().queryParam("action", "build").post(formatXnatUrl(unassignedUrl)).then().assertThat().statusCode(200);

        mainQueryBase().queryParam("action", "move").queryParam("newProject", project.getId()).post(formatXnatUrl(unassignedUrl)).then().assertThat().statusCode(301);

        final String newSessionUrl = unassignedUrl.replace("Unassigned", project.getId());

        final String archiveUrl = mainQueryBase().queryParam("action", "commit").queryParam("AA", true).post(formatXnatUrl(newSessionUrl)).
                then().assertThat().statusCode(301).and().extract().response().asString().trim();

        assertNotEquals(unassignedUrl, archiveUrl);
        assertTrue(archiveUrl.startsWith("/data/archive"));

        mainInterface().waitForAutoRun(
                mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0).project(project)
        );

        for (int i = 1; i < 3; i++) {
            TestNgUtils.assertBinaryFilesEqual(
                    getDataFile(String.format("scan%d/000000.dcm", i)),
                    restDriver.saveBinaryResponseToFile(mainQueryBase().get(formatXnatUrl(archiveUrl, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm")))
            );
        }
    }

    /**
     * This mimics the behavior of the upload assistant on an auto archive project.
     */
    @Test(groups = PREARCHIVE)
    public void testManualArchiveGradualImport() {
        final List<String> sessionUris = new ArrayList<>();
        final DicomZipRequest importerRequest = new DicomZipRequest().destPrearchive(project);
        sessionUris.add(mainInterface().callImporter(importerRequest.file(scan1)));
        sessionUris.add(mainInterface().callImporter(importerRequest.file(scan2)));

        final String sessionUrl = sessionUris.get(0);

        assertEquals(sessionUrl, sessionUris.get(1));

        final String archiveUrl = mainQueryBase().queryParam("action", "commit").queryParam("AA", true).post(formatXnatUrl(sessionUrl)).
                then().assertThat().statusCode(301).and().extract().response().asString().trim();

        assertNotEquals(sessionUrl, archiveUrl);
        assertTrue(archiveUrl.startsWith("/data/archive"));

        for (int i = 1; i < 3; i++) {
            TestNgUtils.assertBinaryFilesEqual(
                    getDataFile(String.format("scan%d/000000.dcm", i)),
                    restDriver.saveBinaryResponseToFile(mainQueryBase().get(formatXnatUrl(archiveUrl, "scans", Integer.toString(i), "resources/DICOM/files/000000.dcm")))
            );
        }
    }

    @Test
    public void testDataProjectExperimentNewScan() {
        final Subject subject = new Subject(project, "SUBJ_0009");
        mainInterface().createSubject(subject);
        final ImagingSession session = new MRSession(project, subject, newLabel()).date(LocalDate.parse("2000-01-01"));
        final SessionImporterRequest importerRequest = defaultRequestFor(session).overwrite(MergeBehavior.APPEND);
        mainInterface().callImporter(importerRequest.file(scan1));

        final Scan expectedScan = new MRScan(session, "1");
        final Resource expectedScanResource = new ScanResource(project, subject, session, expectedScan).folder("DICOM");

        final int scan1FileCount = mainInterface().jsonQuery().get(mainInterface().resourceFilesUrl(expectedScanResource)).jsonPath().getInt("ResultSet.Result.size()");

        mainInterface().callImporter(importerRequest.file(scan2));

        mainInterface().jsonQuery().get(mainInterface().sessionScansUrl(session)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(2));

        mainInterface().jsonQuery().get(mainInterface().resourceFilesUrl(expectedScanResource)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(scan1FileCount));
    }

    @Test
    public void testNonPathBasedImport() {
        final String transactionId = "s" + Calendar.getInstance().getTimeInMillis();

        mainInterface().regenerateUserSession(); // make sure we have a good JSESSIONID

        mainInterface().callImporter(
                new DefaultImporterRequest()
                        .overwrite(MergeBehavior.APPEND)
                        .httpSessionListener(transactionId)
                        .triggerPipelines(false)
                        .project(project)
                        .subject("SUBJ_0010")
                        .session(newLabel())
                        .param("action", "commit")
                        .file(testZip)
        );

        mainInterface().jsonQuery().get(formatRestUrl("status", transactionId)).then().
                assertThat().body("msgs.get(0).last().status", Matchers.equalTo("COMPLETED"));
    }

    @Test(groups = {PREARCHIVE, MERGE})
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
        /*
           Technically this is going to be a DICOM-zip request if that's what the site default is set to. The test framework hasn't been updated
           to fully handle new expectations, but we need to ignore unparseable files within the zip in the DICOM-zip case.
           That behavior is implicit in SI for which this test was originally written.
         */
        final SessionImporterRequest requestBase = defaultRequestFor(session).param("Ignore-Unparsable", true);

        // upload frame 1, should create scan 1
        mainInterface().callImporter(
                requestBase.file(getDataFile("scan_mod/000001.dcm.zip"))
        );

        // confirm that the data went where we expected
        mainQueryBase().get(mainInterface().scanUrl(scan1)).then().assertThat().statusCode(200);
        mainInterface().jsonQuery().get(CommonStringUtils.formatUrl(mainInterface().scanUrl(scan1), "files")).then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        // upload frame 2, should create scan 1-MR1
        waitForBackup();
        mainInterface().callImporter(
                requestBase.overwrite(MergeBehavior.DELETE).file(getDataFile("scan_mod/000002.dcm.zip"))
        );

        // confirm that the data went where we expected
        mainQueryBase().get(mainInterface().scanUrl(scan1MR1)).then().assertThat().statusCode(200);
        mainInterface().jsonQuery().get(CommonStringUtils.formatUrl(mainInterface().scanUrl(scan1MR1), "files")).then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        // upload frame 3, should be added to scan 1-MR1
        waitForBackup();
        mainInterface().callImporter(requestBase.overwrite(MergeBehavior.DELETE).file(getDataFile("scan_mod/000003.dcm.zip")));

        // confirm that the data went where we expected
        mainQueryBase().get(mainInterface().scanUrl(scan1MR1)).then().assertThat().statusCode(200);
        mainInterface().jsonQuery().get(CommonStringUtils.formatUrl(mainInterface().scanUrl(scan1MR1), "files")).then().assertThat().body("ResultSet.Result", Matchers.hasSize(2));

        // upload frame 4 & 5, should be added to scan 1
        waitForBackup();
        mainInterface().callImporter(
                requestBase.overwrite(MergeBehavior.DELETE).file(getDataFile("scan_mod/000004_000005.zip"))
        );

        // confirm that the data went where we expected
        mainQueryBase().get(mainInterface().scanUrl(scan1)).then().assertThat().statusCode(200);
        mainInterface().jsonQuery().get(CommonStringUtils.formatUrl(mainInterface().scanUrl(scan1), "files")).then().assertThat().body("ResultSet.Result", Matchers.hasSize(3));

        // test scan creations: should be 2 scans (1 and 1-MR1)
        mainInterface().jsonQuery().get(mainInterface().sessionScansUrl(session)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(2));

        final JsonPath scan1MR1JsonPath = mainInterface().jsonQuery().queryParam("stats", true).get(CommonStringUtils.formatUrl(mainInterface().scanUrl(scan1MR1), "resources")).
                then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("ResultSet.Result");

        assertEquals("Should have 1 catalog (DICOM).", 1, scan1MR1JsonPath.getInt("size()"));
        assertEquals("Should have 2 files.", 2, scan1MR1JsonPath.getInt("get(0).file_count"));

        final JsonPath scan1JsonPath = mainInterface().jsonQuery().queryParam("stats", true).get(CommonStringUtils.formatUrl(mainInterface().scanUrl(scan1), "resources")).
                then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("ResultSet.Result");

        assertEquals("Should have 1 catalog (DICOM).", 1, scan1JsonPath.getInt("size()"));
        assertEquals("Should have 3 files.", 3, scan1JsonPath.getInt("get(0).file_count"));
    }

    @Test(groups = {PREARCHIVE, MERGE})
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4,
            TestData.SAMPLE_1_SCAN_5,
            TestData.SAMPLE_1_SCAN_6
    }, dicomScp = true)
    public void simpleAutoarchiveMerge() {
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 10000);
        final Project project = new Project().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);
        final Subject subject = new Subject(project, "Sample_Patient");
        final MRSession session = new MRSession(project, subject, "Sample_ID");
        new SessionImportExtension(session, TestData.SAMPLE_1_SCAN_4.toFile());
        mainInterface().createProject(project);
        new XnatCStore().data(TestData.SAMPLE_1_SCAN_5).sendDICOMToProject(project);
        new XnatCStore().data(TestData.SAMPLE_1_SCAN_6).sendDICOMToProject(project);
        TimeUtils.sleep(60000);
        restDriver.waitForPrearchiveEmpty(mainUser, project, 120);
        mainInterface().waitForPipelineCompletion(session, "Merged");
        final List<Scan> allScans = mainInterface().readScans(project, subject, session);
        assertEquals(3, allScans.size());
        for (Scan scan : allScans) {
            final List<Resource> scanResources = scan.getScanResources();
            assertEquals(176, mainInterface().findResource(scanResources, "DICOM").getFileCount());
        }
        mainInterface().deleteProject(project);
    }

    @Test // Test content donated by Kate at Radiologics
    @AddedIn(Xnat_1_7_7.class)
    public void testUserCacheUploadAndImportLegacy() {
        mainInterface().regenerateUserSession();

        final UserCacheTestObject testObject = setupForUserCacheUpload(mainInterface());

        final long start = System.currentTimeMillis();
        boolean completed = false;
        do {
            final JsonPath json = mainInterface()
                    .jsonQuery()
                    .get(formatRestUrl("status", testObject.listener))
                    .then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("msgs.get(0)");
            final int len = json.getInt("size()") - 1;
            if (len >= 0) {
                final String msg = json.get("get(" + len + ").msg");
                final boolean terminal = json.getBoolean("get(" + len + ").terminal");
                if (terminal) {
                    assertEquals("COMPLETED", json.get("get(" + len + ").status"));
                    assertEquals("Archive:" + testObject.session.getUri(), msg);
                    completed = true;
                    break;
                }
            }
            TimeUtils.sleep(5000);
        } while (System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(20));

        assertTrue(completed);
        mainInterface().waitForAutoRun(testObject.session);
        mainInterface().deleteProject(testObject.session.getPrimaryProject());
    }

    @Test // Test content donated by Kate at Radiologics
    @TestRequires(users = 1)
    @AddedIn(Xnat_1_7_7.class)
    public void testUserCacheUploadAndImport() {
        mainInterface().regenerateUserSession();

        final UserCacheTestObject testObjects = setupForUserCacheUpload(mainInterface());

        assertEquals(
                "Archive:" + testObjects.session.getUri(),
                mainInterface().waitForTrackedEventSuccessful(testObjects.listener).getFinalMessage()
        );

        // Validate that other users cannot poll the progress of this event
        try {
            interfaceFor(getGenericUser()).readEventTrackingData(testObjects.listener);
            fail("Method call should have failed");
        } catch (NotFoundException ignored) {}

        mainInterface().waitForAutoRun(testObjects.session);
        mainInterface().deleteProject(testObjects.session.getPrimaryProject());
    }

    @Test(groups = PREARCHIVE)
    public void testUploadIgnoreUnparsableMissingParam() {
        runUnparsableTest(false, false);
    }

    @Test(groups = PREARCHIVE)
    @AddedIn(Xnat_1_8_3.class)
    public void testUploadIgnoreUnparsableFalse() {
        runUnparsableTest(true, false);
    }

    @Test(groups = PREARCHIVE)
    @AddedIn(Xnat_1_8_3.class)
    public void testUploadIgnoreUnparsableTrue() {
        runUnparsableTest(true, true);
    }

    private void testPrearcDodgyUidMerge(Runnable importWorkflow, boolean acceptableForImporter) {
        final String fileToCheck = "000000.dcm";
        importWorkflow.run();
        final SessionData sessionData = mainInterface().expectSinglePrearchiveResultForProject(project);
        final List<Scan> prearcScans = mainInterface()
                .readScansForPrearchiveSession(sessionData)
                .stream()
                .sorted(Comparator.comparing(Scan::getId))
                .collect(Collectors.toList());

        assertEquals(acceptableForImporter ? 2 : 1, prearcScans.size());

        for (int i = 0; i < 2; i++) {
            if (i == 0 || acceptableForImporter) {
                restDriver.validateUpload(
                        mainUser,
                        mainInterface().resourceFileUrl(prearcScans.get(i).getScanResources().get(0), new ResourceFile().name(fileToCheck)),
                        getDataFile(Paths.get((i == 0) ? "scan1" : "scan2_diffUID", fileToCheck))
                );
            }
        }
    }

    private UserCacheTestObject setupForUserCacheUpload(XnatInterface xnatInterface) {
        final String listener = Long.toString(System.currentTimeMillis());

        final Project project = new Project("project" + listener).prearchiveCode(PrearchiveCode.MANUAL);
        mainInterface().createProject(project);
        final Subject   subject = new Subject(project, "subject" + listener);
        final MRSession session = new MRSession(project, subject, "session" + listener);

        // Upload to user cache
        final String cacheUrl = String.format("/user/cache/resources/%s/files/%s", listener, testZip.getName());
        mainQueryBase().multiPart(testZip).put(formatRestUrl(cacheUrl)).then().assertThat().statusCode(200);

        // Upload from user cache to project
        xnatInterface.callImporter(
                new DefaultImporterRequest().
                        src(cacheUrl).
                        httpSessionListener(listener).
                        dest(session.getUri())
        );

        return new UserCacheTestObject(listener, session);
    }

    private String newLabel() {
        return "MR" + (mrCount++);
    }

    private void setCrossModalityMergePrevention(boolean state) {
        if (XnatTestingVersionManager.testedVersionFollows(Xnat_1_7_6.class)) {
            mainAdminInterface().setCrossModalityMergePreventionFlag(state);
        }
    }

    private void waitForBackup() { // see https://issues.xnat.org/browse/XNAT-6424
        if (XnatTestingVersionManager.testedVersionPrecedes(Xnat_1_8_0.class)) {
            TimeUtils.sleep(1000); // wait for 1s so cache backup dir is distinct
        }
    }

    private void runUnparsableTest(boolean includeParam, boolean paramValue) {
        final DicomZipRequest importerRequest = new DicomZipRequest().project(project).file(zipContainingNonDicom);
        if (includeParam) {
            importerRequest.ignoreUnparsable(paramValue);
        }
        if (paramValue) {
            final String prearcUriFragment = mainInterface().callImporter(importerRequest);
            mainQueryBase().queryParam("action", "commit").queryParam("AA", true).post(formatXnatUrl(prearcUriFragment)).then().assertThat().statusCode(301);

            final MRSession session = mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0).project(project);
            mainInterface().waitForAutoRun(session);
            assertEquals(6, mainInterface().findResource(session.getScans().get(0).getScanResources(), "DICOM").getFileCount());
        } else {
            try {
                mainInterface().callImporter(importerRequest);
                failOnImproperSuccess();
            } catch (ImportException importException) {
                assertEquals(400, importException.getStatusCode());
                assertTrue(importException.getMessage().contains("unable to read DICOM object " + nonDicomFilename));
            }
        }
    }

    private void failOnImproperSuccess() {
        fail("Attempted import invocation should have failed");
    }

    private SessionImporterRequest defaultRequestFor(ImagingSession session) {
        return new DefaultImporterRequest().
                triggerPipelines(false).
                dest(session.getUri());
    }

    private static class UserCacheTestObject {
        String listener;
        ImagingSession session;

        UserCacheTestObject(String listener, ImagingSession session) {
            this.listener = listener;
            this.session = session;
        }
    }

}
