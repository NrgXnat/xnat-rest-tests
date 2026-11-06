package org.nrg.testing.xnat.tests;

import io.restassured.response.Response;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.ImportException;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.pogo.EventTrackingData;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.rest.SerializationUtils;
import org.nrg.xnat.versions.Xnat_1_11_0;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.*;

/**
 * Imports run in the background: an upload in the user cache is deleted once its import has run, and
 * {@code async=true} stores the file sent with the request, queues its import and answers with the tracking key.
 */
@Test(groups = IMPORTER)
@AddedIn(Xnat_1_11_0.class)
public class TestBackgroundImport extends BaseXnatRestTest {

    // These imports finish in about a second, so poll more often than the 5 seconds grxnat defaults to
    private static final int POLL_MILLIS = 250;
    private static final int MAX_WAIT_SECONDS = (int) TimeUnit.MINUTES.toSeconds(5);
    private static final String NO_FILES_FOUND = "No files were found in the upload";

    private final Project project = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final File testZip = getDataFile("mr_1.zip");
    private final String subjectFromTestZip = "SPP_0x220790";
    private final String sessionFromTestZip = "SPP_0x220790_MR2";
    private File notAZip; // fails in the import, once the importer has read it

    @BeforeClass(groups = IMPORTER)
    private void setupBackgroundImportEnvironment() throws IOException {
        mainInterface().createProject(project);
        mainAdminInterface().disableSiteAnonScript();
        notAZip = Files.write(Files.createTempFile(Paths.get(Settings.TEMP_SUBDIR), "not_a_zip", ".zip"),
                              "I'm actually a text file!".getBytes(StandardCharsets.UTF_8)).toFile();
    }

    @BeforeMethod(groups = IMPORTER, alwaysRun = true)
    private void clearProject() {
        restDriver.drainIngestPipeline(mainUser, project);
        mainInterface().deleteAllProjectData(project);
    }

    @AfterClass(groups = IMPORTER, alwaysRun = true)
    private void tearDownBackgroundImportTests() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test
    public void testCachedUploadDeletedAfterBackgroundImport() {
        final String cachedFile = uploadToUserCache(testZip);
        final String key = newKey();

        mainInterface().sendImportRequest(request().src(cachedFile).httpSessionListener(key).param("action", "commit"))
                .then().assertThat().statusCode(200)
                .body(Matchers.equalTo("Submitted for prearchival"))
                .header("Location", trackingUrl(key));
        // The tracking record exists as soon as the import is queued, before the import reports anything
        mainInterface().readEventTrackingData(key);

        assertThat(waitForSuccess(key), Matchers.startsWith("Prearchive:/prearchive/projects/" + project.getId() + "/"));
        assertCachedFileGone(cachedFile);
    }

    @Test
    public void testCachedUploadDeletedAfterImportWithinRequest() {
        final String cachedFile = uploadToUserCache(testZip);

        assertThat(mainInterface().callImporter(request().src(cachedFile)), Matchers.containsString("/prearchive/projects/" + project.getId() + "/"));
        assertCachedFileGone(cachedFile);
    }

    @Test
    public void testCachedUploadDeletedAfterFailedImport() {
        final String cachedFile = uploadToUserCache(notAZip);

        final ImportException failure = expectThrows(ImportException.class, () -> mainInterface().callImporter(request().src(cachedFile)));
        assertEquals(400, failure.getStatusCode());
        assertThat(failure.getMessage(), Matchers.containsString(NO_FILES_FOUND));
        assertCachedFileGone(cachedFile);
    }

    @Test
    public void testCachedUploadKeptWhenRequestRejected() {
        final String cachedFile = uploadToUserCache(testZip);

        // A file in the request as well as src is rejected before the import runs, so the cached file can be sent again
        assertEquals(400, expectThrows(ImportException.class, () -> mainInterface().callImporter(request().file(testZip).src(cachedFile))).getStatusCode());
        mainQueryBase().get(formatRestUrl(cachedFile)).then().assertThat().statusCode(200);
        mainQueryBase().delete(formatRestUrl(cachedFile)).then().assertThat().statusCode(200);
    }

    @Test
    public void testMissingCachedUploadLeavesNoFolder() {
        final String cachedFile = cachedFilePath(testZip);

        assertEquals(404, expectThrows(ImportException.class, () -> mainInterface().callImporter(request().src(cachedFile))).getStatusCode());
        // Looking the file up creates its folder, which the import removes again. While the folder is there, a GET of
        // the file answers with the folder's listing.
        assertCachedFileGone(cachedFile);
    }

    @Test
    public void testAsyncImportWithFileInBody() {
        // As the Compressed Uploader sends it: the file is the body, and the path names it
        final Response response = mainQueryBase().contentType("application/zip").body(testZip)
                .queryParams(SerializationUtils.serializeToMap(request().param("async", true).param("inbody", true)))
                .post(formatRestUrl("services/import", testZip.getName()));

        final String key = response.then().assertThat().statusCode(200).extract().asString().trim();
        response.then().assertThat().header("Location", trackingUrl(key));
        mainInterface().readEventTrackingData(key);

        // Without action=commit the import ends once its files are in the prearchive
        assertThat(waitForSuccess(key), Matchers.startsWith("Prearchive:/prearchive/projects/" + project.getId() + "/"));
        assertThat(mainInterface().readEventTrackingData(key).getPayload(), Matchers.containsString("Importing file (" + testZip.getName()));
    }

    @Test
    public void testAsyncMultipartImportWithOwnKeyArchives() {
        final String key = newKey();

        assertEquals(key, mainInterface().callImporter(request().file(testZip).httpSessionListener(key).param("async", true)
                .param("action", "commit").param("auto-archive", true)));
        assertEquals(
                String.format("Archive:/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subjectFromTestZip, sessionFromTestZip),
                waitForSuccess(key)
        );
        mainInterface().jsonQuery().get(formatRestUrl("projects", project.getId(), "experiments")).then().assertThat()
                .body("ResultSet.Result.label", Matchers.hasItem(sessionFromTestZip));
    }

    @Test
    public void testAsyncImportFailureIsReported() {
        final String key = mainInterface().callImporter(request().file(notAZip).param("async", true));

        final EventTrackingData tracking = mainInterface().waitForTrackedEventEnd(key, MAX_WAIT_SECONDS, POLL_MILLIS);
        assertFalse(tracking.getSucceeded());
        assertThat(tracking.getFinalMessage(), Matchers.containsString(NO_FILES_FOUND));
    }

    private DicomZipRequest request() {
        return new DicomZipRequest().project(project);
    }

    private String uploadToUserCache(File file) {
        final String cachedFile = cachedFilePath(file);
        mainQueryBase().contentType("application/zip").body(file).queryParam("inbody", true).put(formatRestUrl(cachedFile))
                .then().assertThat().statusCode(200);
        return cachedFile;
    }

    private String cachedFilePath(File file) {
        return String.format("/user/cache/resources/%s/files/%s", newKey(), file.getName());
    }

    private void assertCachedFileGone(String cachedFile) {
        mainQueryBase().get(formatRestUrl(cachedFile)).then().assertThat().statusCode(404);
    }

    private String newKey() {
        return UUID.randomUUID().toString();
    }

    private Matcher<String> trackingUrl(String key) {
        return Matchers.endsWith("/xapi/event_tracking/" + key);
    }

    private String waitForSuccess(String key) {
        return mainInterface().waitForTrackedEventSuccessful(key, MAX_WAIT_SECONDS, POLL_MILLIS).getFinalMessage();
    }

}
