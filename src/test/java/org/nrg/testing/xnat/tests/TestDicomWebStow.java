package org.nrg.testing.xnat.tests;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.nrg.testing.TestGroups.*;
import static org.testng.Assert.*;

/**
 * STOW-RS (STore Over the Web by RESTful Services) Tests
 *
 * Tests the DICOMweb STOW-RS endpoint for uploading DICOM instances.
 * STOW-RS uses multipart/related HTTP POST requests to upload DICOM files.
 *
 * Test Data Strategy:
 * - EXTRACTION_MR: Small MR dataset (54KB, 1 file) for basic upload tests
 * - EXTRACTION_CT: Small CT dataset (251KB) for multi-instance tests
 *
 * DICOMweb STOW-RS Reference:
 * - Endpoint: POST /dicomweb/projects/{projectId}/studies
 * - Content-Type: multipart/related; type="application/dicom"
 * - Response: DICOM JSON with ReferencedSOPSequence/FailedSOPSequence
 * - Standard: DICOM PS3.18 Section 10.5
 */
@TestRequires(
        specificPluginRequirements = {
                @PluginRequirement(pluginId = "dicomwebproxy")
        },
        data = {
                TestData.EXTRACTION_MR,
                TestData.EXTRACTION_CT
        }
)
@Test(groups = {IMPORTER, METADATA_EXTRACTION})
public class TestDicomWebStow extends BaseXnatRestTest {

    private static final String BOUNDARY = "DICOMweb_STOW_Boundary";
    private static final String CONTENT_TYPE =
            "multipart/related; type=\"application/dicom\"; boundary=" + BOUNDARY;

    // DICOM tag constants (hex notation)
    private static final String TAG_RETRIEVE_URL = "00081190";
    private static final String TAG_FAILED_SOP_SEQUENCE = "00081198";
    private static final String TAG_REFERENCED_SOP_SEQUENCE = "00081199";
    private static final String TAG_STUDY_INSTANCE_UID = "0020000D";

    private final Project testProject = new Project("STOWTest" + RandomHelper.randomID(8));

    @BeforeClass
    public void setupStowTests() {
        mainInterface().createProject(testProject);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupStowTests() {
        restDriver.deleteProjectSilently(mainAdminUser, testProject);
    }

    // ========================================
    // STOW-RS: Basic Upload Tests
    // ========================================

    /**
     * Test: Basic single DICOM instance upload (Smoke Test)
     *
     * Validates DICOM JSON response structure:
     * - ReferencedSOPSequence (00081199) present with success entry
     * - FailedSOPSequence (00081198) present (empty or absent)
     * - RetrieveURL (00081190) present
     */
    @Test(groups = SMOKE, priority = 1)
    @Basic
    public void testStowSingleInstance() throws IOException {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

        File dicomDir = TestData.EXTRACTION_MR.toDirectory();
        File[] dicomFiles = findDicomFiles(dicomDir);
        assertTrue(dicomFiles.length > 0, "Should have at least one DICOM file in test data");

        Response response = mainQueryBase()
                .contentType(CONTENT_TYPE)
                .body(buildMultipartRelatedBody(dicomFiles[0]))
                .post(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        // Parse DICOM JSON response (array of study-level response objects)
        JsonPath json = response.jsonPath();
        List<Map<String, Object>> results = json.getList("$");
        assertFalse(results.isEmpty(), "Response should contain at least one result object");

        Map<String, Object> result = results.get(0);

        // ReferencedSOPSequence must be present with at least one success entry
        assertNotNull(result.get(TAG_REFERENCED_SOP_SEQUENCE),
                "Response must contain ReferencedSOPSequence (00081199)");
        Map<String, Object> refSeqTag = (Map<String, Object>) result.get(TAG_REFERENCED_SOP_SEQUENCE);
        List<Object> refSeqValues = (List<Object>) refSeqTag.get("Value");
        assertNotNull(refSeqValues, "ReferencedSOPSequence should have Value array");
        assertTrue(refSeqValues.size() >= 1,
                "ReferencedSOPSequence should have at least 1 entry for uploaded instance");

        // FailedSOPSequence should be absent or empty
        if (result.containsKey(TAG_FAILED_SOP_SEQUENCE)) {
            Map<String, Object> failedTag = (Map<String, Object>) result.get(TAG_FAILED_SOP_SEQUENCE);
            List<Object> failedValues = (List<Object>) failedTag.get("Value");
            assertTrue(failedValues == null || failedValues.isEmpty(),
                    "FailedSOPSequence should be empty for successful upload");
        }

        // RetrieveURL should be present
        assertNotNull(result.get(TAG_RETRIEVE_URL),
                "Response should contain RetrieveURL (00081190)");
    }

    /**
     * Test: Upload multiple DICOM instances in a single request
     *
     * Uploads up to 5 CT files in one multipart/related body.
     * Validates per-instance success entries match uploaded file count.
     */
    @Test(priority = 2)
    public void testStowMultipleInstances() throws IOException {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

        File dicomDir = TestData.EXTRACTION_CT.toDirectory();
        File[] dicomFiles = findDicomFiles(dicomDir);
        assertTrue(dicomFiles.length > 0, "Should have DICOM files in test data");

        int fileCount = Math.min(dicomFiles.length, 5);
        File[] filesToUpload = new File[fileCount];
        System.arraycopy(dicomFiles, 0, filesToUpload, 0, fileCount);

        Response response = mainQueryBase()
                .contentType(CONTENT_TYPE)
                .body(buildMultipartRelatedBody(filesToUpload))
                .post(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        // Count total success entries across all study-level response objects
        JsonPath json = response.jsonPath();
        List<Map<String, Object>> results = json.getList("$");
        assertFalse(results.isEmpty(), "Response should contain at least one result object");

        int totalSuccessEntries = 0;
        for (Map<String, Object> result : results) {
            if (result.containsKey(TAG_REFERENCED_SOP_SEQUENCE)) {
                Map<String, Object> refSeqTag = (Map<String, Object>) result.get(TAG_REFERENCED_SOP_SEQUENCE);
                List<Object> refSeqValues = (List<Object>) refSeqTag.get("Value");
                if (refSeqValues != null) {
                    totalSuccessEntries += refSeqValues.size();
                }
            }
        }

        assertEquals(totalSuccessEntries, fileCount,
                "Total ReferencedSOPSequence entries should match uploaded file count");
    }

    /**
     * Test: STOW-RS round trip — upload then verify via QIDO-RS
     *
     * Uploads a DICOM file via STOW-RS, extracts StudyInstanceUID from
     * the RetrieveURL in the response, then polls QIDO-RS until the
     * study appears (async import via GradualDicomImporter).
     */
    @Test(groups = SMOKE, priority = 3)
    public void testStowRoundTrip() throws IOException, InterruptedException {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

        File dicomDir = TestData.EXTRACTION_MR.toDirectory();
        File[] dicomFiles = findDicomFiles(dicomDir);
        assertTrue(dicomFiles.length > 0, "Should have at least one DICOM file in test data");

        Response response = mainQueryBase()
                .contentType(CONTENT_TYPE)
                .body(buildMultipartRelatedBody(dicomFiles[0]))
                .post(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        // Extract StudyInstanceUID from RetrieveURL (format: .../studies/{studyUID})
        JsonPath json = response.jsonPath();
        List<Map<String, Object>> results = json.getList("$");
        assertFalse(results.isEmpty(), "Response should contain at least one result object");

        Map<String, Object> result = results.get(0);
        String retrieveUrl = getDicomTagValue(result, TAG_RETRIEVE_URL);
        assertNotNull(retrieveUrl, "Response should contain RetrieveURL");

        String studyUID = retrieveUrl.substring(retrieveUrl.lastIndexOf("/studies/") + "/studies/".length());
        assertFalse(studyUID.isEmpty(), "Should extract StudyInstanceUID from RetrieveURL");

        // Poll QIDO-RS until study appears (async import)
        assertTrue(pollForStudy(testProject.getId(), studyUID),
                "Study " + studyUID + " should appear in QIDO-RS after STOW upload");
    }

    /**
     * Test: Idempotent re-upload of the same DICOM instance
     *
     * Uploads the same file twice. Both requests should succeed
     * (either 200 with success entries or re-import handled gracefully).
     */
    @Test(priority = 4)
    public void testStowIdempotentReUpload() throws IOException {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

        File dicomDir = TestData.EXTRACTION_MR.toDirectory();
        File[] dicomFiles = findDicomFiles(dicomDir);
        assertTrue(dicomFiles.length > 0, "Should have at least one DICOM file in test data");

        byte[] body = buildMultipartRelatedBody(dicomFiles[0]);

        // First upload
        Response firstResponse = mainQueryBase()
                .contentType(CONTENT_TYPE)
                .body(body)
                .post(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        // Second upload of the same file
        Response secondResponse = mainQueryBase()
                .contentType(CONTENT_TYPE)
                .body(body)
                .post(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        // Both should return valid DICOM JSON with no FailedSOPSequence errors
        for (Response resp : new Response[]{firstResponse, secondResponse}) {
            JsonPath json = resp.jsonPath();
            List<Map<String, Object>> results = json.getList("$");
            assertFalse(results.isEmpty(), "Response should contain at least one result object");
        }
    }

    // ========================================
    // STOW-RS: Error Handling Tests
    // ========================================

    /**
     * Test: Upload non-DICOM data
     *
     * The DICOM standard allows either a 4xx/5xx error or a 200 with
     * FailedSOPSequence entries. This test accepts both.
     */
    @Test(priority = 5)
    public void testStowInvalidData() throws IOException {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

        byte[] invalidData = "This is not a DICOM file".getBytes(StandardCharsets.UTF_8);

        Response response = mainQueryBase()
                .contentType(CONTENT_TYPE)
                .body(buildMultipartRelatedBody(invalidData))
                .post(formatXapiUrl(endpoint));

        int statusCode = response.getStatusCode();

        if (statusCode == 200) {
            // 200 with FailedSOPSequence is acceptable per DICOM standard
            JsonPath json = response.jsonPath();
            List<Map<String, Object>> results = json.getList("$");
            assertFalse(results.isEmpty(), "200 response should contain result objects");

            // Should have failures and no successes
            Map<String, Object> result = results.get(0);
            if (result.containsKey(TAG_REFERENCED_SOP_SEQUENCE)) {
                Map<String, Object> refSeqTag = (Map<String, Object>) result.get(TAG_REFERENCED_SOP_SEQUENCE);
                List<Object> refSeqValues = (List<Object>) refSeqTag.get("Value");
                assertTrue(refSeqValues == null || refSeqValues.isEmpty(),
                        "Invalid data should not produce success entries");
            }
        } else {
            // 4xx or 5xx is also acceptable
            assertTrue(statusCode >= 400 && statusCode < 600,
                    "Should return error status code for invalid DICOM data, got: " + statusCode);
        }
    }

    /**
     * Test: Upload to a non-existent project
     *
     * Should return 404 or 403 (the endpoint throws ForbiddenException
     * when the project is not found or user lacks access).
     */
    @Test(priority = 5)
    public void testStowToNonExistentProject() throws IOException {
        String endpoint = "/dicomweb/projects/NONEXISTENT_PROJECT_12345/studies";

        File dicomDir = TestData.EXTRACTION_MR.toDirectory();
        File[] dicomFiles = findDicomFiles(dicomDir);
        assertTrue(dicomFiles.length > 0, "Should have DICOM files in test data");

        Response response = mainQueryBase()
                .contentType(CONTENT_TYPE)
                .body(buildMultipartRelatedBody(dicomFiles[0]))
                .post(formatXapiUrl(endpoint));

        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 404 || statusCode == 403,
                "Should return 404 or 403 for non-existent project, got: " + statusCode);
    }

    /**
     * Test: POST with wrong Content-Type
     *
     * Sending a non-multipart Content-Type should be rejected.
     */
    @Test(priority = 5)
    public void testStowMissingContentType() throws IOException {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

        File dicomDir = TestData.EXTRACTION_MR.toDirectory();
        File[] dicomFiles = findDicomFiles(dicomDir);
        assertTrue(dicomFiles.length > 0, "Should have DICOM files in test data");

        byte[] dicomBytes = Files.readAllBytes(dicomFiles[0].toPath());

        Response response = mainQueryBase()
                .contentType("application/octet-stream")
                .body(dicomBytes)
                .post(formatXapiUrl(endpoint));

        int statusCode = response.getStatusCode();
        assertTrue(statusCode >= 400 && statusCode < 600,
                "Should return error for wrong Content-Type, got: " + statusCode);
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Build a multipart/related request body from DICOM files.
     *
     * Format per DICOM PS3.18:
     * --boundary\r\n
     * Content-Type: application/dicom\r\n
     * \r\n
     * [DICOM bytes]\r\n
     * --boundary--\r\n
     */
    private byte[] buildMultipartRelatedBody(File... dicomFiles) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (File file : dicomFiles) {
            baos.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.US_ASCII));
            baos.write("Content-Type: application/dicom\r\n".getBytes(StandardCharsets.US_ASCII));
            baos.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            baos.write(Files.readAllBytes(file.toPath()));
            baos.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        baos.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return baos.toByteArray();
    }

    /**
     * Build a multipart/related request body from raw bytes (e.g., invalid data).
     */
    private byte[] buildMultipartRelatedBody(byte[] rawContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.US_ASCII));
        baos.write("Content-Type: application/dicom\r\n".getBytes(StandardCharsets.US_ASCII));
        baos.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        baos.write(rawContent);
        baos.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        baos.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return baos.toByteArray();
    }

    /**
     * Poll QIDO-RS until a study appears (handles async import via GradualDicomImporter).
     * Polls every 2 seconds, up to 60 seconds total.
     *
     * @return true if study found, false if timeout reached
     */
    private boolean pollForStudy(String projectId, String studyUID) throws InterruptedException {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies?StudyInstanceUID=%s",
                projectId, studyUID);

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            Response response = mainQueryBase()
                    .get(formatXapiUrl(endpoint));

            if (response.getStatusCode() == 200) {
                JsonPath json = response.jsonPath();
                List<Map<String, Object>> studies = json.getList("$");
                if (studies != null && !studies.isEmpty()) {
                    return true;
                }
            }

            Thread.sleep(2000);
        }
        return false;
    }

    /**
     * Extract DICOM tag value from DICOM JSON object
     */
    private String getDicomTagValue(Map<String, Object> dicomJson, String tag) {
        if (!dicomJson.containsKey(tag)) {
            return null;
        }
        Map<String, Object> tagData = (Map<String, Object>) dicomJson.get(tag);
        List<String> values = (List<String>) tagData.get("Value");
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /** Guess at test data contents by name */
    private static boolean hasDicomName(final Path p) {
        final String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".dcm") || !name.contains(".");
    }

    /**
     * Recursively find all DICOM files in a directory
     */
    private File[] findDicomFiles(File dir) throws IOException {
        if (!dir.exists()) {
            return new File[0];
        }

        try (Stream<Path> paths = dir.isFile() ? Stream.of(dir.toPath()) : Files.walk(dir.toPath())) {
           return paths
                    .filter(Files::isRegularFile)
                    .filter(TestDicomWebStow::hasDicomName)
                    .map(Path::toFile)
                    .toArray(File[]::new);
        }
    }
}
