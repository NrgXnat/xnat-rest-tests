package org.nrg.testing.xnat.tests;

import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.ImportException;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.EventTrackingData;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.pogo.users.UserGroups;
import org.nrg.xnat.rest.NotFoundException;
import org.nrg.xnat.versions.Xnat_1_11_0;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.Assert.assertNotEquals;
import static org.testng.AssertJUnit.*;

/**
 * Imports run in the background: an upload in the user cache is deleted once its import has run, and
 * {@code async=true} stores the file sent with the request, queues its import and answers 202 with the
 * tracking key.
 */
@Test(groups = IMPORTER)
@AddedIn(Xnat_1_11_0.class)
public class TestBackgroundImport extends BaseXnatRestTest {

    private final Project project = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final File testZip = getDataFile("mr_1.zip");
    private final File otherStudyZip = getDataFile("scan1.zip");
    private final String subjectFromTestZip = "SPP_0x220790";
    private final String sessionFromTestZip = "SPP_0x220790_MR2";
    private final String nonDicomFilename = "non_dicom_text_file.dcm";
    private File zipContainingNonDicom;

    @BeforeClass(groups = IMPORTER)
    private void setupBackgroundImportEnvironment() throws IOException {
        mainInterface().createProject(project);
        mainAdminInterface().disableSiteAnonScript();

        final Path stagingDir = Files.createTempDirectory(Paths.get(Settings.TEMP_SUBDIR), "background_import");
        zipContainingNonDicom = stagingDir.resolve("mr_1_with_txt.zip").toFile();
        FileUtils.copyFile(testZip, zipContainingNonDicom);
        final File textFile = stagingDir.resolve(nonDicomFilename).toFile();
        FileUtils.write(textFile, "I'm actually a text file!", StandardCharsets.UTF_8);
        new ZipFile(zipContainingNonDicom).addFile(textFile, new ZipParameters());
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

        mainInterface().callImporter(new DicomZipRequest().project(project)
                .param("src", cachedFile)
                .param("http-session-listener", key)
                .param("action", "commit"));
        // The tracking record exists as soon as the import is queued, before the import reports anything
        mainInterface().readEventTrackingData(key);

        assertThat(mainInterface().waitForTrackedEventSuccessful(key).getFinalMessage(),
                Matchers.startsWith("Prearchive:/prearchive/projects/" + project.getId() + "/"));
        assertCachedFileGone(cachedFile);
    }

    @Test
    public void testCachedUploadDeletedAfterImportWithinRequest() {
        final String cachedFile = uploadToUserCache(testZip);

        final String sessionUri = mainInterface().callImporter(new DicomZipRequest().project(project).param("src", cachedFile));

        assertThat(sessionUri, Matchers.containsString("/prearchive/projects/" + project.getId() + "/"));
        assertCachedFileGone(cachedFile);
    }

    @Test
    public void testCachedUploadDeletedAfterFailedImport() {
        final String cachedFile = uploadToUserCache(zipContainingNonDicom);

        try {
            mainInterface().callImporter(new DicomZipRequest().project(project).ignoreUnparsable(false).param("src", cachedFile));
            fail("An import of a zip holding a file that isn't DICOM should have failed");
        } catch (ImportException e) {
            assertEquals(400, e.getStatusCode());
        }

        assertCachedFileGone(cachedFile);
    }

    @Test
    public void testCachedUploadKeptWhenRequestRejected() {
        final String cachedFile = uploadToUserCache(testZip);

        // A file in the request as well as src is rejected before the import runs, so the cached file can be sent again
        try {
            mainInterface().callImporter(new DicomZipRequest().project(project).file(testZip).param("src", cachedFile));
            fail("An import with both src and a file should have been rejected");
        } catch (ImportException e) {
            assertEquals(400, e.getStatusCode());
        }

        mainQueryBase().get(formatRestUrl(cachedFile)).then().assertThat().statusCode(200);
        mainQueryBase().delete(formatRestUrl(cachedFile)).then().assertThat().statusCode(200);
    }

    @Test
    public void testAsyncImportWithFileInBody() {
        final Response response = importInBody(testZip, params());

        assertEquals(202, response.statusCode());
        final String key = response.asString().trim();
        assertTrue(response.getHeader("Location").endsWith("/xapi/event_tracking/" + key));
        mainInterface().readEventTrackingData(key);

        // Without action=commit the import ends once its files are in the prearchive
        assertThat(mainInterface().waitForTrackedEventSuccessful(key).getFinalMessage(),
                Matchers.startsWith("Prearchive:/prearchive/projects/" + project.getId() + "/"));
    }

    @Test
    public void testAsyncImportMultipartWithOwnKey() {
        final String key = newKey();
        final Map<String, Object> params = params();
        params.put("http-session-listener", key);

        final Response response = mainQueryBase().multiPart(testZip).queryParams(params).queryParam("async", true)
                .post(formatRestUrl("services/import"));

        assertEquals(202, response.statusCode());
        assertEquals(key, response.asString().trim());
        assertTrue(response.getHeader("Location").endsWith("/xapi/event_tracking/" + key));
        assertThat(mainInterface().waitForTrackedEventSuccessful(key).getFinalMessage(), Matchers.startsWith("Prearchive:"));
    }

    @Test
    public void testAsyncImportArchives() {
        final Map<String, Object> params = params();
        params.put("action", "commit");
        params.put("auto-archive", true);

        final Response response = importInBody(testZip, params);

        assertEquals(202, response.statusCode());
        assertEquals(
                String.format("Archive:/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subjectFromTestZip, sessionFromTestZip),
                mainInterface().waitForTrackedEventSuccessful(response.asString().trim()).getFinalMessage()
        );
        mainInterface().jsonQuery().get(formatRestUrl("projects", project.getId(), "experiments")).then().assertThat()
                .body("ResultSet.Result.label", Matchers.hasItem(sessionFromTestZip));
    }

    @Test
    public void testAsyncImportFromUserCache() {
        final String cachedFile = uploadToUserCache(testZip);
        final Map<String, Object> params = params();
        params.put("src", cachedFile);

        // The importer reads parameters from the query string or a multipart form, so send an empty form rather than a form-urlencoded body
        final Response response = mainQueryBase().multiPart(new MultiPartSpecBuilder("").build()).queryParams(params).queryParam("async", true)
                .post(formatRestUrl("services/import"));

        assertEquals(202, response.statusCode());
        assertThat(mainInterface().waitForTrackedEventSuccessful(response.asString().trim()).getFinalMessage(), Matchers.startsWith("Prearchive:"));
        assertCachedFileGone(cachedFile);
    }

    @Test
    public void testAsyncImportFailureIsReported() {
        final Map<String, Object> params = params();
        params.put("Ignore-Unparsable", false);

        final Response response = importInBody(zipContainingNonDicom, params);

        assertEquals(202, response.statusCode());
        final EventTrackingData tracking = waitForTrackedEventEnd(mainInterface(), response.asString().trim());
        assertFalse(tracking.getSucceeded());
        assertThat(tracking.getFinalMessage(), Matchers.containsString("unable to read DICOM object " + nonDicomFilename));
    }

    @Test
    @TestRequires(users = 1)
    public void testTrackingKeyBelongsToItsUser() {
        final User otherUser = getGenericUser();
        final XnatInterface otherInterface = interfaceFor(otherUser);
        mainAdminInterface().addUserToProject(otherUser, project, UserGroups.MEMBER);
        final String key = newKey();
        final Map<String, Object> params = params();
        params.put("http-session-listener", key);

        assertEquals(202, importInBody(testZip, params).statusCode());
        final String mainMessage = mainInterface().waitForTrackedEventSuccessful(key).getFinalMessage();

        // Another user can't read the key
        try {
            otherInterface.readEventTrackingData(key);
            fail("Another user should not be able to read the tracking of this import");
        } catch (NotFoundException ignored) {
        }

        // ... and can use the same key for an import of their own
        final Response otherResponse = otherInterface.queryBase().contentType("application/zip").body(otherStudyZip)
                .queryParams(params).queryParam("async", true).queryParam("inbody", true).post(formatRestUrl("services/import"));
        assertEquals(202, otherResponse.statusCode());
        assertEquals(key, otherResponse.asString().trim());
        final String otherMessage = otherInterface.waitForTrackedEventSuccessful(key).getFinalMessage();

        assertThat(otherMessage, Matchers.startsWith("Prearchive:"));
        assertNotEquals(otherMessage, mainMessage);
        assertEquals(mainMessage, mainInterface().readEventTrackingData(key).getFinalMessage());
    }

    private Map<String, Object> params() {
        final Map<String, Object> params = new HashMap<>();
        params.put("import-handler", "DICOM-zip");
        params.put("project", project.getId());
        return params;
    }

    private Response importInBody(File zip, Map<String, Object> params) {
        final RequestSpecification request = mainQueryBase().contentType("application/zip").body(zip);
        return request.queryParams(params).queryParam("async", true).queryParam("inbody", true).post(formatRestUrl("services/import"));
    }

    private String uploadToUserCache(File zip) {
        final String cachedFile = String.format("/user/cache/resources/%s/files/%s", newKey(), zip.getName());
        mainQueryBase().contentType("application/zip").body(zip).queryParam("inbody", true).put(formatRestUrl(cachedFile))
                .then().assertThat().statusCode(200);
        return cachedFile;
    }

    private void assertCachedFileGone(String cachedFile) {
        mainQueryBase().get(formatRestUrl(cachedFile)).then().assertThat().statusCode(404);
    }

    private String newKey() {
        return UUID.randomUUID().toString();
    }

    private EventTrackingData waitForTrackedEventEnd(XnatInterface xnatInterface, String key) {
        final long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        while (System.currentTimeMillis() < deadline) {
            final EventTrackingData tracking = xnatInterface.readEventTrackingData(key);
            if (tracking.getSucceeded() != null) {
                return tracking;
            }
            TimeUtils.sleep(2000);
        }
        fail("The import tracked as " + key + " did not end within 5 minutes");
        return null;
    }

}
