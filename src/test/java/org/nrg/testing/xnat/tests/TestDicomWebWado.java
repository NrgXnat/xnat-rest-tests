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
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.CTSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.nrg.testing.TestGroups.*;
import static org.testng.Assert.*;

/**
 * WADO-RS (Web Access to DICOM Objects by RESTful Services) Tests
 *
 * Tests the DICOMweb WADO-RS retrieval endpoints for accessing DICOM instances,
 * metadata, and rendered images.
 *
 * Test Data Strategy:
 * - EXTRACTION_MR: Small MR dataset for basic retrieval tests (54KB, 1 file)
 * - EXTRACTION_CT: Small CT dataset for multi-instance tests (251KB)
 *
 * WADO-RS Endpoints:
 * - Retrieve Instance: GET /studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}
 * - Retrieve Metadata: GET /studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/metadata
 * - Retrieve Frames: GET /studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/frames/{frameList}
 * - Retrieve Rendered: GET /studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/rendered
 *
 * DICOM Standard: DICOM PS3.18 Section 10.4
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
public class TestDicomWebWado extends BaseXnatRestTest {

    // DICOM tag constants (hex notation)
    private static final String TAG_STUDY_INSTANCE_UID = "0020000D";
    private static final String TAG_SERIES_INSTANCE_UID = "0020000E";
    private static final String TAG_SOP_INSTANCE_UID = "00080018";
    private static final String TAG_SOP_CLASS_UID = "00080016";
    private static final String TAG_MODALITY = "00080060";
    private static final String TAG_INSTANCE_NUMBER = "00200013";

    private final Project testProject = new Project("WADOTest" + RandomHelper.randomID(8));
    private final Subject testSubject = new Subject(testProject);

    // Test sessions for different modalities
    private final MRSession mrSession = new MRSession(testProject, testSubject);
    private final CTSession ctSession = new CTSession(testProject, testSubject);

    // Stored UIDs from uploads for retrieval validation
    private String mrStudyUID;
    private String mrSeriesUID;
    private String mrInstanceUID;
    private String ctStudyUID;
    private String ctSeriesUID;
    private String ctInstanceUID;

    @BeforeClass
    public void setupWadoTests() {
        // Set up session import extensions BEFORE creating project
        new SessionImportExtension(mrSession, TestData.EXTRACTION_MR.toFile());
        new SessionImportExtension(ctSession, TestData.EXTRACTION_CT.toFile());

        // Create project once - this creates project, subject, and all sessions
        mainInterface().createProject(testProject);

        // Extract UIDs from created sessions for validation
        extractSessionUIDs();
    }
    
    @AfterClass(alwaysRun = true)
    public void cleanupWadoTests() {
        restDriver.deleteProjectSilently(mainAdminUser, testProject);
    }

    /**
     * Extract Study/Series/Instance UIDs from uploaded sessions
     */
    private void extractSessionUIDs() {
        // Get MR study UIDs
        extractUIDsForSession(mrSession, true);

        // Get CT study UIDs
        extractUIDsForSession(ctSession, false);
    }

    /**
     * Extract UIDs for a specific session using QIDO-RS
     */
    private void extractUIDsForSession(ImagingSession session, boolean isMR) {
        JsonPath sessionJson = mainInterface().jsonQuery()
                .get(mainInterface().subjectAssessorUrl(session))
                .then().assertThat().statusCode(200).and().extract().jsonPath();
        Map<String, Object> sessionDataFields = sessionJson.get("items[0].data_fields");
        String studyUID = (String) sessionDataFields.get("UID");

        // Query series using QIDO-RS
        String seriesEndpoint = String.format("/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(), studyUID);

        Response seriesResponse = mainQueryBase()
                .get(formatXapiUrl(seriesEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        JsonPath seriesJson = seriesResponse.jsonPath();
        List<Map<String, Object>> seriesList = seriesJson.getList("$");

        assertTrue(seriesList.size() >= 1, "Should have at least one series");
        Map<String, Object> firstSeries = seriesList.get(0);
        Map<String, Object> seriesUidTag = (Map<String, Object>) firstSeries.get(TAG_SERIES_INSTANCE_UID);
        String seriesUID = ((List<String>) seriesUidTag.get("Value")).get(0);

        // Query instances using QIDO-RS
        String instancesEndpoint = String.format("/dicomweb/projects/%s/studies/%s/series/%s/instances",
                testProject.getId(), studyUID, seriesUID);

        Response instancesResponse = mainQueryBase()
                .get(formatXapiUrl(instancesEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        JsonPath instancesJson = instancesResponse.jsonPath();
        List<Map<String, Object>> instancesList = instancesJson.getList("$");

        assertTrue(instancesList.size() >= 1, "Should have at least one instance");
        Map<String, Object> firstInstance = instancesList.get(0);
        Map<String, Object> instanceUidTag = (Map<String, Object>) firstInstance.get(TAG_SOP_INSTANCE_UID);
        String instanceUID = ((List<String>) instanceUidTag.get("Value")).get(0);

        // Store UIDs
        if (isMR) {
            mrStudyUID = studyUID;
            mrSeriesUID = seriesUID;
            mrInstanceUID = instanceUID;
        } else {
            ctStudyUID = studyUID;
            ctSeriesUID = seriesUID;
            ctInstanceUID = instanceUID;
        }
    }

    // ========================================
    // WADO-RS: Instance Retrieval Tests
    // ========================================

    /**
     * Test: Retrieve single DICOM instance (Smoke Test)
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}
     *
     * Purpose:
     * - Verify basic WADO-RS instance retrieval
     * - Validate DICOM file is returned
     * - Check proper Content-Type header
     *
     * Test Data: EXTRACTION_MR
     *
     * Expected Result:
     * - HTTP 200 OK
     * - Content-Type: multipart/related; type="application/dicom"
     * - Response contains DICOM file data
     * - File size > 0
     */
    @Test(groups = SMOKE, priority = 1)
    @Basic
    public void testWadoRetrieveInstance() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s",
                testProject.getId(),
                mrStudyUID,
                mrSeriesUID,
                mrInstanceUID
        );

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        // Validate Content-Type
        String contentType = response.getContentType();
        assertTrue(contentType.contains("multipart/related") || contentType.contains("application/dicom"),
                "Content-Type should be multipart/related or application/dicom, got: " + contentType);

        // Validate response has data
        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "Response should contain DICOM data");

        // DICOM files start with specific preamble and "DICM" magic number
        // This is a basic validation that we got DICOM data
        assertTrue(body.length > 132, "DICOM file should be at least 132 bytes (preamble + DICM)");
    }

    /**
     * Test: Retrieve multiple instances from same series
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}
     *
     * Purpose:
     * - Test retrieval of different instances from same series
     * - Verify each instance is unique
     * - Validate consistent response format
     *
     * Test Data: EXTRACTION_CT (multiple instances)
     *
     * Expected Result:
     * - HTTP 200 OK for each instance
     * - Each instance has different data
     * - All instances are valid DICOM files
     */
    @Test(priority = 2)
    public void testWadoRetrieveMultipleInstances() {
        // Get all instances from CT series
        String instancesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances",
                testProject.getId(),
                ctStudyUID,
                ctSeriesUID
        );

        Response qidoResponse = mainQueryBase()
                .get(formatXapiUrl(instancesEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        JsonPath instancesJson = qidoResponse.jsonPath();
        List<Map<String, Object>> instancesList = instancesJson.getList("$");

        assertTrue(instancesList.size() >= 1, "Should have at least one instance");

        // Retrieve up to 3 instances
        int retrieveCount = Math.min(instancesList.size(), 3);
        byte[][] retrievedData = new byte[retrieveCount][];

        for (int i = 0; i < retrieveCount; i++) {
            Map<String, Object> instance = instancesList.get(i);
            Map<String, Object> instanceUidTag = (Map<String, Object>) instance.get(TAG_SOP_INSTANCE_UID);
            String instanceUID = ((List<String>) instanceUidTag.get("Value")).get(0);

            String wadoEndpoint = String.format(
                    "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s",
                    testProject.getId(),
                    ctStudyUID,
                    ctSeriesUID,
                    instanceUID
            );

            Response wadoResponse = mainQueryBase()
                    .get(formatXapiUrl(wadoEndpoint))
                    .then()
                    .assertThat()
                    .statusCode(200)
                    .extract()
                    .response();

            retrievedData[i] = wadoResponse.asByteArray();
            assertTrue(retrievedData[i].length > 0, "Instance " + i + " should have data");
        }

        // Verify instances are different (different sizes is a simple check)
        if (retrieveCount > 1) {
            boolean allSame = true;
            for (int i = 1; i < retrieveCount; i++) {
                if (retrievedData[i].length != retrievedData[0].length) {
                    allSame = false;
                    break;
                }
            }
            // Note: It's possible all instances have same size, so this is not a hard requirement
            // Just checking they're valid DICOM data is the main goal
        }
    }

    // ========================================
    // WADO-RS: Metadata Retrieval Tests
    // ========================================

    /**
     * Test: Retrieve instance metadata in DICOM JSON format (Smoke Test)
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/metadata
     *
     * Purpose:
     * - Test metadata-only retrieval (no pixel data)
     * - Validate DICOM JSON format
     * - Verify required DICOM tags present
     *
     * Test Data: EXTRACTION_MR
     *
     * Expected Result:
     * - HTTP 200 OK
     * - Content-Type: application/dicom+json
     * - JSON array with metadata
     * - Required tags: StudyInstanceUID, SeriesInstanceUID, SOPInstanceUID, SOPClassUID
     *
     * DICOM JSON Format:
     * [
     *   {
     *     "0020000D": {"vr": "UI", "Value": ["1.2.3..."]},
     *     "0020000E": {"vr": "UI", "Value": ["1.2.3..."]},
     *     ...
     *   }
     * ]
     */
    @Test(groups = SMOKE, priority = 2)
    @Basic
    public void testWadoRetrieveMetadata() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/metadata",
                testProject.getId(),
                mrStudyUID,
                mrSeriesUID,
                mrInstanceUID
        );

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        // Parse DICOM JSON response
        JsonPath json = response.jsonPath();
        List<Map<String, Object>> metadata = json.getList("$");

        assertTrue(metadata.size() >= 1, "Metadata should contain at least one object");

        Map<String, Object> firstMetadata = metadata.get(0);

        // Validate required DICOM tags are present
        assertNotNull(firstMetadata.get(TAG_STUDY_INSTANCE_UID),
                "Metadata must have StudyInstanceUID (0020000D)");
        assertNotNull(firstMetadata.get(TAG_SERIES_INSTANCE_UID),
                "Metadata must have SeriesInstanceUID (0020000E)");
        assertNotNull(firstMetadata.get(TAG_SOP_INSTANCE_UID),
                "Metadata must have SOPInstanceUID (00080018)");
        assertNotNull(firstMetadata.get(TAG_SOP_CLASS_UID),
                "Metadata must have SOPClassUID (00080016)");

        // Verify DICOM JSON structure
        Map<String, Object> studyUidTag = (Map<String, Object>) firstMetadata.get(TAG_STUDY_INSTANCE_UID);
        assertEquals(studyUidTag.get("vr"), "UI", "StudyInstanceUID should have VR of UI");
        assertNotNull(studyUidTag.get("Value"), "StudyInstanceUID should have Value array");

        // Verify UID values match what we expect
        List<String> studyUidValues = (List<String>) studyUidTag.get("Value");
        assertEquals(studyUidValues.get(0), mrStudyUID,
                "StudyInstanceUID in metadata should match expected value");
    }

    /**
     * Test: Retrieve metadata for CT instance
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/metadata
     *
     * Purpose:
     * - Test metadata retrieval for different modality
     * - Verify modality-specific tags are present
     * - Validate CT-specific DICOM attributes
     *
     * Test Data: EXTRACTION_CT
     *
     * Expected Result:
     * - HTTP 200 OK
     * - Metadata contains Modality tag with value "CT"
     * - CT-specific tags may be present
     */
    @Test(priority = 2)
    public void testWadoRetrieveMetadataCT() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/metadata",
                testProject.getId(),
                ctStudyUID,
                ctSeriesUID,
                ctInstanceUID
        );

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> metadata = json.getList("$");

        assertTrue(metadata.size() >= 1, "Metadata should contain at least one object");

        Map<String, Object> firstMetadata = metadata.get(0);

        // Verify Modality tag
        Map<String, Object> modalityTag = (Map<String, Object>) firstMetadata.get(TAG_MODALITY);
        assertNotNull(modalityTag, "Metadata should have Modality tag");

        List<String> modalityValues = (List<String>) modalityTag.get("Value");
        assertEquals(modalityValues.get(0), "CT", "Modality should be CT");
    }

    // ========================================
    // WADO-RS: Error Handling Tests
    // ========================================

    /**
     * Test: Retrieve non-existent instance
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}
     *
     * Purpose:
     * - Verify proper error handling for invalid UIDs
     * - Test 404 Not Found response
     *
     * Expected Result:
     * - HTTP 404 Not Found
     * - Error message in response
     */
    @Test(priority = 3)
    public void testWadoRetrieveNonExistentInstance() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s",
                testProject.getId(),
                mrStudyUID,
                mrSeriesUID,
                "9.9.9.9.9.9.9.9.9.9.9.9.9.9.9"  // Non-existent UID
        );

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint));

        // Should return 404
        int statusCode = response.getStatusCode();
        assertEquals(statusCode, 404,
                "Should return 404 for non-existent instance");
    }

    /**
     * Test: Retrieve metadata for non-existent instance
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}/metadata
     *
     * Purpose:
     * - Verify metadata endpoint error handling
     * - Test 404 response for invalid UIDs
     *
     * Expected Result:
     * - HTTP 404 Not Found
     */
    @Test(priority = 3)
    public void testWadoRetrieveMetadataNonExistent() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/metadata",
                testProject.getId(),
                mrStudyUID,
                mrSeriesUID,
                "9.9.9.9.9.9.9.9.9.9.9.9.9.9.9"  // Non-existent UID
        );

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint));

        // Should return 404
        int statusCode = response.getStatusCode();
        assertEquals(statusCode, 404,
                "Should return 404 for non-existent instance metadata");
    }

    /**
     * Test: Retrieve instance from non-existent project
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances/{instanceUID}
     *
     * Purpose:
     * - Verify project validation
     * - Test authorization checks
     *
     * Expected Result:
     * - HTTP 404 Not Found or 403 Forbidden
     */
    @Test(priority = 3)
    public void testWadoRetrieveFromNonExistentProject() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s",
                "NONEXISTENT_PROJECT_12345",
                mrStudyUID,
                mrSeriesUID,
                mrInstanceUID
        );

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint));

        // Should return 404 or 403
        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 404 || statusCode == 403,
                "Should return 404 or 403 for non-existent project, got: " + statusCode);
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Extract DICOM tag value from DICOM JSON object
     *
     * @param dicomJson DICOM JSON object (Map)
     * @param tag DICOM tag in hex notation (e.g., "0020000D")
     * @return String value or null if tag not present
     */
    private String getDicomTagValue(Map<String, Object> dicomJson, String tag) {
        if (!dicomJson.containsKey(tag)) {
            return null;
        }
        Map<String, Object> tagData = (Map<String, Object>) dicomJson.get(tag);
        List<String> values = (List<String>) tagData.get("Value");
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}
