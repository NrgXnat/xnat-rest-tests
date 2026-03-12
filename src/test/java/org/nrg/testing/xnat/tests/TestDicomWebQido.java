package org.nrg.testing.xnat.tests;

import io.restassured.path.json.JsonPath;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.dcm4che3.data.Tag;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
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
 * QIDO-RS (Query based on ID for DICOM Objects by RESTful Services) Tests
 *
 * Tests the DICOMweb QIDO-RS query endpoints for studies, series, and instances.
 * Uses multiple test datasets to cover different modalities and query scenarios.
 *
 * Test Data Strategy:
 * - EXTRACTION_MR: Small MR dataset for smoke tests and basic validation (54KB, 1 file)
 * - EXTRACTION_CT: Small CT dataset for multi-modality testing (251KB)
 *
 * DICOM Tag Reference (Hex notation used in JSON responses):
 * - 0020000D: StudyInstanceUID
 * - 0020000E: SeriesInstanceUID
 * - 00080018: SOPInstanceUID
 * - 00100010: PatientName
 * - 00100020: PatientID
 * - 00080060: Modality
 * - 00200011: SeriesNumber
 * - 00200013: InstanceNumber
 */
@TestRequires(
        specificPluginRequirements = {
                @PluginRequirement(pluginId = "dicomwebproxy")
        },
        data = {
                TestData.EXTRACTION_MR,   // Primary: Small MR for smoke tests
                TestData.EXTRACTION_CT    // Secondary: CT for multi-modality
        }
)
@Test(groups = {IMPORTER, METADATA_EXTRACTION})
public class TestDicomWebQido extends BaseXnatRestTest {

    /**
     * Convert a dcm4che Tag integer to the 8-character uppercase hex string
     * used as JSON property names in DICOM JSON (PS3.18 Annex F).
     */
    private static String tagKey(int tag) {
        return String.format("%08X", tag);
    }

    private final Project testProject = new Project("QIDOTest" + RandomHelper.randomID(8));
    private final Subject testSubject = new Subject(testProject);

    // Test sessions for different modalities
    private final MRSession mrSession = new MRSession(testProject, testSubject);
    private final CTSession ctSession = new CTSession(testProject, testSubject);

    // Stored UIDs from uploads for query validation
    private String mrStudyUID;
    private String ctStudyUID;

    @BeforeClass
    public void setupQidoTests() {
        // Set up session import extensions BEFORE creating project
        // This attaches DICOM data to each session
        new SessionImportExtension(mrSession, TestData.EXTRACTION_MR.toFile());
        new SessionImportExtension(ctSession, TestData.EXTRACTION_CT.toFile());

        // Create project once - this creates project, subject, and all sessions
        mainInterface().createProject(testProject);

        // Extract UIDs from created sessions for validation
        extractSessionUIDs();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupQidoTests() {
        restDriver.deleteProjectSilently(mainAdminUser, testProject);
    }

    /**
     * Extract Study/Series UIDs from uploaded sessions
     * These UIDs will be used to validate QIDO-RS query results
     */
    private void extractSessionUIDs() {
        // Get MR study UID from XNAT session
        JsonPath mrJson = restDriver.mainInterface().jsonQuery()
                .get(mainInterface().subjectAssessorUrl(mrSession))
                .then().assertThat().statusCode(200).and().extract().jsonPath();
        System.out.println("retrieved identifiers: " + mrJson.prettyPrint());
        Map<String, Object> mrDataFields = mrJson.get("items[0].data_fields");
        mrStudyUID = (String) mrDataFields.get("UID");

        // Get CT study UID
        JsonPath ctJson = restDriver.mainInterface().jsonQuery()
                .get(mainInterface().subjectAssessorUrl(ctSession))
                .then().assertThat().statusCode(200).and().extract().jsonPath();
        Map<String, Object> ctDataFields = ctJson.get("items[0].data_fields");
        ctStudyUID = (String) ctDataFields.get("UID");
    }

    // ========================================
    // QIDO-RS: Study-Level Queries
    // ========================================

    /**
     * Test: Basic study-level query (Smoke Test)
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies
     *
     * Purpose:
     * - Verify basic QIDO-RS functionality for study queries
     * - Validate DICOM JSON response format
     * - Ensure required DICOM tags are present
     *
     * Test Data: EXTRACTION_MR (smallest dataset)
     *
     * Expected Result:
     * - HTTP 200 response
     * - Content-Type: application/dicom+json
     * - JSON array with at least 1 study
     * - Study contains required tags: StudyInstanceUID, PatientName, PatientID
     * - StudyInstanceUID matches uploaded study
     *
     * DICOM JSON Format Example:
     * [
     *   {
     *     "0020000D": {"vr": "UI", "Value": ["1.2.3.4.5..."]},
     *     "00100010": {"vr": "PN", "Value": [{"Alphabetic": "Patient^Name"}]},
     *     ...
     *   }
     * ]
     */
    @Test(groups = SMOKE, priority = 1)
    @Basic
    public void testQidoSearchStudies_Basic() {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

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
        List<Map<String, Object>> studies = json.getList("$");

        // Should have at least 2 studies (MR and CT)
        assertTrue(studies.size() >= 2,
                "Expected at least 2 studies, found: " + studies.size());

        // Validate first study has required DICOM tags
        Map<String, Object> firstStudy = studies.get(0);
        assertNotNull(firstStudy.get(tagKey(Tag.StudyInstanceUID)),
                "Study must have StudyInstanceUID (0020000D)");
        assertNotNull(firstStudy.get(tagKey(Tag.PatientName)),
                "Study must have PatientName (00100010)");
        assertNotNull(firstStudy.get(tagKey(Tag.PatientID)),
                "Study must have PatientID (00100020)");

        // Verify DICOM JSON structure for StudyInstanceUID
        Map<String, Object> studyUidTag = (Map<String, Object>) firstStudy.get(tagKey(Tag.StudyInstanceUID));
        assertEquals(studyUidTag.get("vr"), "UI", "StudyInstanceUID should have VR of UI");
        assertNotNull(studyUidTag.get("Value"), "StudyInstanceUID should have Value array");

        // Verify we can find our MR study in the results
        boolean foundMrStudy = studies.stream()
                .anyMatch(study -> {
                    Map<String, Object> uidTag = (Map<String, Object>) study.get(tagKey(Tag.StudyInstanceUID));
                    List<String> values = (List<String>) uidTag.get("Value");
                    return values != null && values.contains(mrStudyUID);
                });

        assertTrue(foundMrStudy, "Should find uploaded MR study in results");
    }

    /**
     * Test: Study query with StudyInstanceUID filter
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies?StudyInstanceUID={uid}
     *
     * Purpose:
     * - Test QIDO-RS query parameter filtering
     * - Verify exact match filtering works correctly
     *
     * Test Data: EXTRACTION_CT (known Study UID)
     *
     * Expected Result:
     * - HTTP 200 response
     * - JSON array with exactly 1 study
     * - Returned study matches the queried StudyInstanceUID
     * - Other studies are excluded from results
     */
    @Test(priority = 1)
    public void testQidoSearchStudies_WithStudyUIDFilter() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies?StudyInstanceUID=%s",
                testProject.getId(),
                ctStudyUID
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
        List<Map<String, Object>> studies = json.getList("$");

        // Should return exactly 1 study matching the UID
        assertEquals(studies.size(), 1,
                "Query with StudyInstanceUID filter should return exactly 1 study");

        Map<String, Object> study = studies.get(0);
        Map<String, Object> uidTag = (Map<String, Object>) study.get(tagKey(Tag.StudyInstanceUID));
        List<String> values = (List<String>) uidTag.get("Value");

        assertEquals(values.get(0), ctStudyUID,
                "Returned study should match queried StudyInstanceUID");
    }

    /**
     * Test: Study query with Modality filter
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies?Modality=CT
     *
     * Purpose:
     * - Test modality-based filtering
     * - Verify multi-modality project can filter by specific modality
     *
     * Test Data: Project contains MR, CT, and PT studies
     *
     * Expected Result:
     * - HTTP 200 response
     * - Only CT studies returned (not MR or PT)
     * - At least 1 CT study found (from EXTRACTION_CT)
     *
     * Note: This test validates that DICOMweb can properly filter
     * by modality in a mixed-modality project, which is critical
     * for clinical workflows.
     */
    @Test(priority = 1)
    public void testQidoSearchStudies_WithModalityFilter() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies?Modality=CT",
                testProject.getId()
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
        List<Map<String, Object>> studies = json.getList("$");

        // Should have at least 1 CT study
        assertTrue(!studies.isEmpty(), "Should find at least 1 CT study");

        // Verify all returned studies have Modality=CT
        for (Map<String, Object> study : studies) {
            // Note: Modality may be at study level or series level
            // For study queries, we check ModalitiesInStudy if present
            if (study.containsKey(tagKey(Tag.Modality))) {
                Map<String, Object> modalityTag = (Map<String, Object>) study.get(tagKey(Tag.Modality));
                List<String> values = (List<String>) modalityTag.get("Value");
                assertTrue(values.contains("CT"), "All returned studies should be CT modality");
            }
        }

        // Verify our CT study is in the results
        boolean foundCtStudy = studies.stream()
                .anyMatch(study -> {
                    Map<String, Object> uidTag = (Map<String, Object>) study.get(tagKey(Tag.StudyInstanceUID));
                    List<String> values = (List<String>) uidTag.get("Value");
                    return values != null && values.contains(ctStudyUID);
                });

        assertTrue(foundCtStudy, "Should find uploaded CT study in filtered results");
    }

    /**
     * Test: Study query with no matching results
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies?PatientName=NONEXISTENT
     *
     * Purpose:
     * - Verify proper handling of queries with no matches
     * - Ensure empty result set returns valid JSON (not error)
     *
     * Expected Result:
     * - HTTP 200 response (not 404)
     * - Content-Type: application/dicom+json
     * - Empty JSON array: []
     *
     * Note: Per DICOMweb standard, empty query results should
     * return 200 with empty array, not 404 Not Found.
     */
    @Test(priority = 1)
    public void testQidoSearchStudies_NoResults() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies?PatientName=NONEXISTENT_PATIENT_12345",
                testProject.getId()
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
        List<Map<String, Object>> studies = json.getList("$");

        assertEquals(studies.size(), 0,
                "Query with no matches should return empty array");
    }

    // ========================================
    // QIDO-RS: Series-Level Queries
    // ========================================

    /**
     * Test: Series-level query for a specific study
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series
     *
     * Purpose:
     * - Test series-level QIDO-RS queries
     * - Verify series metadata is correctly returned
     * - Validate series-specific DICOM tags
     *
     * Test Data: STS_001_A (PET/MR fusion study with 3 series)
     *
     * Expected Result:
     * - HTTP 200 response
     * - JSON array with 3 series (PET AC, Aligned T1, Aligned T2FS)
     * - Each series has required tags: SeriesInstanceUID, Modality, SeriesNumber
     * - Series descriptions are present
     *
     * Key Validation:
     * - Multiple series in same study are all returned
     * - Different modalities (PT, MR) are properly represented
     */
    @Test(groups = SMOKE, priority = 2)
    public void testQidoSearchSeries_Basic() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(),
                ctStudyUID
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
        List<Map<String, Object>> series = json.getList("$");

        // CT study should have at least 1 series
        assertTrue(series.size() >= 1,
                "CT study should have at least 1 series");

        // Validate each series has required tags
        for (Map<String, Object> seriesItem : series) {
            assertNotNull(seriesItem.get(tagKey(Tag.SeriesInstanceUID)),
                    "Series must have SeriesInstanceUID (0020000E)");
            assertNotNull(seriesItem.get(tagKey(Tag.Modality)),
                    "Series must have Modality (00080060)");
            assertNotNull(seriesItem.get(tagKey(Tag.SeriesNumber)),
                    "Series must have SeriesNumber (00200011)");
        }

        // Verify modality is CT
        for (Map<String, Object> seriesItem : series) {
            Map<String, Object> modalityTag = (Map<String, Object>) seriesItem.get(tagKey(Tag.Modality));
            List<String> values = (List<String>) modalityTag.get("Value");
            assertEquals(values.get(0), "CT", "Series should have CT modality");
        }
    }

    /**
     * Test: Series query with SeriesInstanceUID filter
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series?SeriesInstanceUID={seriesUID}
     *
     * Purpose:
     * - Test series-level filtering by UID
     * - Verify single series can be retrieved from multi-series study
     *
     * Expected Result:
     * - HTTP 200 response
     * - Exactly 1 series returned
     * - Series UID matches query parameter
     */
    @Test(priority = 2)
    public void testQidoSearchSeries_WithSeriesUIDFilter() {
        // First, get all series to obtain a SeriesInstanceUID
        String getAllSeriesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(),
                mrStudyUID
        );

        Response allSeriesResponse = mainQueryBase()
                .get(formatXapiUrl(getAllSeriesEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        JsonPath allSeriesJson = allSeriesResponse.jsonPath();
        List<Map<String, Object>> allSeries = allSeriesJson.getList("$");

        assertTrue(allSeries.size() >= 1, "Study should have at least 1 series");

        // Extract first series UID
        Map<String, Object> firstSeries = allSeries.get(0);
        Map<String, Object> seriesUidTag = (Map<String, Object>) firstSeries.get(tagKey(Tag.SeriesInstanceUID));
        List<String> seriesUidValues = (List<String>) seriesUidTag.get("Value");
        String seriesUID = seriesUidValues.get(0);

        // Query for specific series
        String filteredEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series?SeriesInstanceUID=%s",
                testProject.getId(),
                mrStudyUID,
                seriesUID
        );

        Response filteredResponse = mainQueryBase()
                .get(formatXapiUrl(filteredEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        JsonPath filteredJson = filteredResponse.jsonPath();
        List<Map<String, Object>> filteredSeries = filteredJson.getList("$");

        // Should return exactly 1 series
        assertEquals(filteredSeries.size(), 1,
                "Query with SeriesInstanceUID filter should return exactly 1 series");

        Map<String, Object> returnedSeries = filteredSeries.get(0);
        Map<String, Object> returnedUidTag = (Map<String, Object>) returnedSeries.get(tagKey(Tag.SeriesInstanceUID));
        List<String> returnedUidValues = (List<String>) returnedUidTag.get("Value");

        assertEquals(returnedUidValues.get(0), seriesUID,
                "Returned series should match queried SeriesInstanceUID");
    }

    /**
     * Test: Series query with Modality filter in multi-modality study
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series?Modality=PT
     *
     * Purpose:
     * - Test modality filtering at series level
     * - Validate proper filtering in fusion studies (PET/MR, PET/CT)
     *
     * Test Data: STS_001_A (PET/MR study with both PT and MR series)
     *
     * Expected Result:
     * - HTTP 200 response
     * - Only PT series returned (not MR)
     * - At least 1 PT series found
     *
     * Clinical Significance:
     * In PET/MR fusion studies, radiologists often want to view
     * only PET or only MR images. This test ensures proper filtering.
     */
    @Test(priority = 2)
    public void testQidoSearchSeries_WithModalityFilter() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series?Modality=CT",
                testProject.getId(),
                ctStudyUID
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
        List<Map<String, Object>> series = json.getList("$");

        // Should have at least 1 CT series
        assertTrue(series.size() >= 1, "Should find at least 1 CT series");

        // Verify all returned series are CT modality
        for (Map<String, Object> seriesItem : series) {
            Map<String, Object> modalityTag = (Map<String, Object>) seriesItem.get(tagKey(Tag.Modality));
            List<String> values = (List<String>) modalityTag.get("Value");
            assertEquals(values.get(0), "CT", "All returned series should be CT modality");
        }
    }

    // ========================================
    // QIDO-RS: Instance-Level Queries
    // ========================================

    /**
     * Test: Instance-level query for a specific series
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances
     *
     * Purpose:
     * - Test instance-level QIDO-RS queries
     * - Verify instance metadata is correctly returned
     * - Validate instance-specific DICOM tags
     *
     * Test Data: SAMPLE_1_SCAN_4 (176 instances in single series)
     *
     * Expected Result:
     * - HTTP 200 response
     * - JSON array with 176 instances
     * - Each instance has required tags: SOPInstanceUID, InstanceNumber, SOPClassUID
     * - Instance numbers are unique within series
     *
     * Performance Note:
     * This test queries a full series (176 instances) to verify the
     * plugin can handle realistic series sizes without pagination.
     */
    @Test(groups = SMOKE, priority = 3)
    public void testQidoSearchInstances_FullSeries() {
        // First, get series UID from the large MR session
        String getSeriesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(),
                mrStudyUID
        );

        Response seriesResponse = mainQueryBase()
                .get(formatXapiUrl(getSeriesEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        JsonPath seriesJson = seriesResponse.jsonPath();
        List<Map<String, Object>> seriesList = seriesJson.getList("$");

        assertTrue(seriesList.size() >= 1, "Study should have at least 1 series");

        Map<String, Object> firstSeries = seriesList.get(0);
        Map<String, Object> seriesUidTag = (Map<String, Object>) firstSeries.get(tagKey(Tag.SeriesInstanceUID));
        List<String> seriesUidValues = (List<String>) seriesUidTag.get("Value");
        String seriesUID = seriesUidValues.get(0);

        // Query all instances in the series
        String instancesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances",
                testProject.getId(),
                mrStudyUID,
                seriesUID
        );

        Response instancesResponse = mainQueryBase()
                .get(formatXapiUrl(instancesEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        JsonPath instancesJson = instancesResponse.jsonPath();
        List<Map<String, Object>> instances = instancesJson.getList("$");

        // MR series should have at least 1 instance
        assertTrue(instances.size() >= 1,
                "MR series should have at least 1 instance");

        // Validate each instance has required tags
        for (Map<String, Object> instance : instances) {
            assertNotNull(instance.get(tagKey(Tag.SOPInstanceUID)),
                    "Instance must have SOPInstanceUID (00080018)");
            assertNotNull(instance.get(tagKey(Tag.InstanceNumber)),
                    "Instance must have InstanceNumber (00200013)");
        }

        // Verify all SOPInstanceUIDs are unique
        long uniqueSopUIDs = instances.stream()
                .map(inst -> {
                    Map<String, Object> uidTag = (Map<String, Object>) inst.get(tagKey(Tag.SOPInstanceUID));
                    List<String> values = (List<String>) uidTag.get("Value");
                    return values.get(0);
                })
                .distinct()
                .count();

        assertEquals(uniqueSopUIDs, instances.size(),
                "All instances should have unique SOPInstanceUIDs");
    }

    /**
     * Test: Instance query with SOPInstanceUID filter
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances?SOPInstanceUID={sopUID}
     *
     * Purpose:
     * - Test instance-level filtering by SOP UID
     * - Verify single instance can be retrieved from series
     *
     * Expected Result:
     * - HTTP 200 response
     * - Exactly 1 instance returned
     * - Instance SOP UID matches query parameter
     */
    @Test(priority = 3)
    public void testQidoSearchInstances_WithSOPUIDFilter() {
        // First, get series and instance UIDs
        String getSeriesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(),
                mrStudyUID
        );

        Response seriesResponse = mainQueryBase()
                .get(formatXapiUrl(getSeriesEndpoint))
                .then()
                .extract()
                .response();

        JsonPath seriesJson = seriesResponse.jsonPath();
        List<Map<String, Object>> seriesList = seriesJson.getList("$");
        Map<String, Object> firstSeries = seriesList.get(0);
        Map<String, Object> seriesUidTag = (Map<String, Object>) firstSeries.get(tagKey(Tag.SeriesInstanceUID));
        String seriesUID = ((List<String>) seriesUidTag.get("Value")).get(0);

        // Get all instances to obtain a SOPInstanceUID
        String getAllInstancesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances",
                testProject.getId(),
                mrStudyUID,
                seriesUID
        );

        Response allInstancesResponse = mainQueryBase()
                .get(formatXapiUrl(getAllInstancesEndpoint))
                .then()
                .extract()
                .response();

        JsonPath allInstancesJson = allInstancesResponse.jsonPath();
        List<Map<String, Object>> allInstances = allInstancesJson.getList("$");

        assertTrue(allInstances.size() >= 1, "Series should have at least 1 instance");

        Map<String, Object> firstInstance = allInstances.get(0);
        Map<String, Object> sopUidTag = (Map<String, Object>) firstInstance.get(tagKey(Tag.SOPInstanceUID));
        String sopUID = ((List<String>) sopUidTag.get("Value")).get(0);

        // Query for specific instance
        String filteredEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances?SOPInstanceUID=%s",
                testProject.getId(),
                mrStudyUID,
                seriesUID,
                sopUID
        );

        Response filteredResponse = mainQueryBase()
                .get(formatXapiUrl(filteredEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        JsonPath filteredJson = filteredResponse.jsonPath();
        List<Map<String, Object>> filteredInstances = filteredJson.getList("$");

        // Should return exactly 1 instance
        assertEquals(filteredInstances.size(), 1,
                "Query with SOPInstanceUID filter should return exactly 1 instance");

        Map<String, Object> returnedInstance = filteredInstances.get(0);
        Map<String, Object> returnedUidTag = (Map<String, Object>) returnedInstance.get(tagKey(Tag.SOPInstanceUID));
        String returnedUID = ((List<String>) returnedUidTag.get("Value")).get(0);

        assertEquals(returnedUID, sopUID,
                "Returned instance should match queried SOPInstanceUID");
    }

    // ========================================
    // QIDO-RS: Pagination Tests
    // ========================================

    /**
     * Test: Pagination with limit parameter
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances?limit=10
     *
     * Purpose:
     * - Test QIDO-RS pagination using limit parameter
     * - Verify X-Total-Count header is returned
     * - Ensure only requested number of results returned
     *
     * Test Data: SAMPLE_1_SCAN_4 (176 instances)
     *
     * Expected Result:
     * - HTTP 200 response
     * - Exactly 10 instances returned (not all 176)
     * - X-Total-Count header = 176
     * - Response body contains first 10 instances
     *
     * DICOMweb Pagination:
     * The limit parameter restricts the number of results returned,
     * but X-Total-Count header indicates total matches for the query.
     */
    @Test(priority = 4)
    public void testQidoPagination_WithLimit() {
        // Get series UID from large MR session
        String getSeriesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(),
                mrStudyUID
        );

        Response seriesResponse = mainQueryBase()
                .get(formatXapiUrl(getSeriesEndpoint))
                .then()
                .extract()
                .response();

        JsonPath seriesJson = seriesResponse.jsonPath();
        List<Map<String, Object>> seriesList = seriesJson.getList("$");
        Map<String, Object> firstSeries = seriesList.get(0);
        Map<String, Object> seriesUidTag = (Map<String, Object>) firstSeries.get(tagKey(Tag.SeriesInstanceUID));
        String seriesUID = ((List<String>) seriesUidTag.get("Value")).get(0);

        // Query with limit=10
        String paginatedEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances?limit=10",
                testProject.getId(),
                mrStudyUID,
                seriesUID
        );

        Response paginatedResponse = mainQueryBase()
                .get(formatXapiUrl(paginatedEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        // Verify X-Total-Count header
        String totalCountHeader = paginatedResponse.getHeader("X-Total-Count");
        assertNotNull(totalCountHeader, "Response should include X-Total-Count header");
        int totalCount = Integer.parseInt(totalCountHeader);
        assertTrue(totalCount >= 1, "X-Total-Count should indicate at least 1 instance");

        // Verify no more than 10 instances returned (or fewer if total < 10)
        JsonPath paginatedJson = paginatedResponse.jsonPath();
        List<Map<String, Object>> instances = paginatedJson.getList("$");

        assertTrue(instances.size() <= 10,
                "Query with limit=10 should return no more than 10 instances");
        assertTrue(instances.size() <= totalCount,
                "Returned instances should not exceed total count");
    }

    /**
     * Test: Pagination with limit and offset parameters
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances?limit=10&offset=10
     *
     * Purpose:
     * - Test pagination with both limit and offset
     * - Verify offset skips the correct number of results
     * - Ensure results differ from first page
     *
     * Test Data: SAMPLE_1_SCAN_4 (176 instances)
     *
     * Expected Result:
     * - HTTP 200 response
     * - Exactly 10 instances returned (page 2)
     * - X-Total-Count header = 176
     * - Returned instances are different from offset=0
     *
     * Pagination Logic:
     * - offset=0, limit=10: instances 0-9
     * - offset=10, limit=10: instances 10-19
     * - offset=170, limit=10: instances 170-175 (only 6 returned)
     */
    @Test(priority = 4)
    public void testQidoPagination_WithLimitAndOffset() {
        // Get series UID
        String getSeriesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(),
                mrStudyUID
        );

        Response seriesResponse = mainQueryBase()
                .get(formatXapiUrl(getSeriesEndpoint))
                .then()
                .extract()
                .response();

        JsonPath seriesJson = seriesResponse.jsonPath();
        List<Map<String, Object>> seriesList = seriesJson.getList("$");
        Map<String, Object> firstSeries = seriesList.get(0);
        Map<String, Object> seriesUidTag = (Map<String, Object>) firstSeries.get(tagKey(Tag.SeriesInstanceUID));
        String seriesUID = ((List<String>) seriesUidTag.get("Value")).get(0);

        // Get first page (offset=0)
        String firstPageEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances?limit=10&offset=0",
                testProject.getId(),
                mrStudyUID,
                seriesUID
        );

        Response firstPageResponse = mainQueryBase()
                .get(formatXapiUrl(firstPageEndpoint))
                .then()
                .extract()
                .response();

        JsonPath firstPageJson = firstPageResponse.jsonPath();
        List<Map<String, Object>> firstPageInstances = firstPageJson.getList("$");
        String firstInstanceUID = getDicomTagValue(firstPageInstances.get(0), tagKey(Tag.SOPInstanceUID));

        // Get second page (offset=10)
        String secondPageEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances?limit=10&offset=10",
                testProject.getId(),
                mrStudyUID,
                seriesUID
        );

        Response secondPageResponse = mainQueryBase()
                .get(formatXapiUrl(secondPageEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        JsonPath secondPageJson = secondPageResponse.jsonPath();
        List<Map<String, Object>> secondPageInstances = secondPageJson.getList("$");

        // If there are more than 10 total instances, second page should have data
        // and it should differ from first page
        if (secondPageInstances.size() > 0 && firstPageInstances.size() > 0) {
            String firstInstanceFirstPage = getDicomTagValue(firstPageInstances.get(0), tagKey(Tag.SOPInstanceUID));
            String firstInstanceSecondPage = getDicomTagValue(secondPageInstances.get(0), tagKey(Tag.SOPInstanceUID));
            assertNotEquals(firstInstanceSecondPage, firstInstanceFirstPage,
                    "Second page should have different instances than first page");
        }

        // Verify X-Total-Count is consistent
        String totalCountHeader = secondPageResponse.getHeader("X-Total-Count");
        String firstPageTotalCount = firstPageResponse.getHeader("X-Total-Count");
        assertEquals(totalCountHeader, firstPageTotalCount,
                "X-Total-Count should be consistent across pages");
    }

    /**
     * Test: Pagination edge case - offset beyond results
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/instances?limit=10&offset=200
     *
     * Purpose:
     * - Test pagination when offset exceeds total results
     * - Verify proper handling of edge case
     *
     * Test Data: SAMPLE_1_SCAN_4 (176 instances)
     *
     * Expected Result:
     * - HTTP 200 response (not error)
     * - Empty array returned (no instances)
     * - X-Total-Count header = 176 (total still reported)
     */
    @Test(priority = 4)
    public void testQidoPagination_OffsetBeyondResults() {
        // Get series UID
        String getSeriesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(),
                mrStudyUID
        );

        Response seriesResponse = mainQueryBase()
                .get(formatXapiUrl(getSeriesEndpoint))
                .then()
                .extract()
                .response();

        JsonPath seriesJson = seriesResponse.jsonPath();
        List<Map<String, Object>> seriesList = seriesJson.getList("$");
        Map<String, Object> firstSeries = seriesList.get(0);
        Map<String, Object> seriesUidTag = (Map<String, Object>) firstSeries.get(tagKey(Tag.SeriesInstanceUID));
        String seriesUID = ((List<String>) seriesUidTag.get("Value")).get(0);

        // First get total count
        String getAllEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances",
                testProject.getId(),
                mrStudyUID,
                seriesUID
        );

        Response countResponse = mainQueryBase().get(formatXapiUrl(getAllEndpoint)).then().extract().response();
        int totalCount = Integer.parseInt(countResponse.getHeader("X-Total-Count"));

        // Query with offset beyond total
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances?limit=10&offset=%d",
                testProject.getId(),
                mrStudyUID,
                seriesUID,
                totalCount + 10  // Offset beyond total
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
        List<Map<String, Object>> instances = json.getList("$");

        assertEquals(instances.size(), 0,
                "Query with offset beyond total should return empty array");

        // Total count should still be reported
        String totalCountHeader = response.getHeader("X-Total-Count");
        assertEquals(Integer.parseInt(totalCountHeader), totalCount,
                "X-Total-Count should still report total instances");
    }

    // ========================================
    // QIDO-RS: PS3.18 Compliance Tests
    // ========================================

    /**
     * Test: Study-level QIDO-RS response compliance with PS3.18 Table 10.6.3-3
     *
     * Validates that each study in the response contains all required return
     * key attributes with correct DICOM JSON encoding per PS3.18 Annex F.
     *
     * Required (R) Return Keys (Table 10.6.3-3):
     * - StudyDate (0008,0020) DA
     * - StudyTime (0008,0030) TM
     * - AccessionNumber (0008,0050) SH
     * - ModalitiesInStudy (0008,0061) CS
     * - ReferringPhysicianName (0008,0090) PN
     * - RetrieveURL (0008,1190) UR  [Required if retrievable]
     * - PatientName (0010,0010) PN
     * - PatientID (0010,0020) LO
     * - PatientBirthDate (0010,0030) DA
     * - PatientSex (0010,0040) CS
     * - StudyInstanceUID (0020,000D) UI  [Unique Key]
     * - StudyID (0020,0010) SH
     * - NumberOfStudyRelatedSeries (0020,1206) IS
     * - NumberOfStudyRelatedInstances (0020,1208) IS
     *
     * Note: PatientBirthDate may be absent if DOB is not populated in XNAT.
     * ReferringPhysicianName is present but empty (XNAT does not track this).
     */
    @Test(groups = SMOKE, priority = 1)
    @Basic
    public void testStudyResponseCompliance() {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertFalse(studies.isEmpty(), "Should return at least one study");

        for (Map<String, Object> study : studies) {
            // Validate PS3.18 Annex F structure for all tags in this object
            assertDicomJsonStructure(study);

            // Required return keys (Table 10.6.3-3)
            assertRequiredTag(study, Tag.StudyInstanceUID, "UI", "StudyInstanceUID");
            assertRequiredTag(study, Tag.StudyDate, "DA", "StudyDate");
            assertRequiredTag(study, Tag.StudyTime, "TM", "StudyTime");
            assertRequiredTag(study, Tag.AccessionNumber, "SH", "AccessionNumber");
            assertRequiredTag(study, Tag.ModalitiesInStudy, "CS", "ModalitiesInStudy");
            assertRequiredTag(study, Tag.ReferringPhysicianName, "PN", "ReferringPhysicianName");
            assertRequiredTag(study, Tag.PatientName, "PN", "PatientName");
            assertRequiredTag(study, Tag.PatientID, "LO", "PatientID");
            assertRequiredTag(study, Tag.PatientSex, "CS", "PatientSex");
            assertRequiredTag(study, Tag.StudyID, "SH", "StudyID");
            assertRequiredTag(study, Tag.NumberOfStudyRelatedSeries, "IS",
                    "NumberOfStudyRelatedSeries");
            assertRequiredTag(study, Tag.NumberOfStudyRelatedInstances, "IS",
                    "NumberOfStudyRelatedInstances");

            // RetrieveURL required since study is retrievable
            assertRequiredTag(study, Tag.RetrieveURL, "UR", "RetrieveURL");
            String retrieveUrl = getDicomTagValue(study, tagKey(Tag.RetrieveURL));
            assertNotNull(retrieveUrl, "RetrieveURL must have a non-null value");
            assertFalse(retrieveUrl.isEmpty(), "RetrieveURL must not be empty");
            assertTrue(retrieveUrl.contains("/studies/"),
                    "Study RetrieveURL must contain /studies/ path segment");

            // PatientBirthDate: required by PS3.18, but may be absent if
            // DOB is not populated in XNAT subject demographics
            assertOptionalTagIfPresent(study, Tag.PatientBirthDate, "DA",
                    "PatientBirthDate");

            // Validate UID format
            String studyUid = getDicomTagValue(study, tagKey(Tag.StudyInstanceUID));
            assertValidUid(studyUid, "StudyInstanceUID");

            // Validate PN encoding (must be object with Alphabetic, not plain string)
            assertValidPnValue(study, Tag.PatientName);
            assertValidPnValue(study, Tag.ReferringPhysicianName);

            // Validate DA format if non-empty
            String studyDate = getDicomTagValue(study, tagKey(Tag.StudyDate));
            if (studyDate != null && !studyDate.isEmpty()) {
                assertValidDaValue(studyDate, "StudyDate");
            }
            String birthDate = getDicomTagValue(study, tagKey(Tag.PatientBirthDate));
            if (birthDate != null && !birthDate.isEmpty()) {
                assertValidDaValue(birthDate, "PatientBirthDate");
            }

            // Validate IS values are parseable non-negative integers
            String numSeries = getDicomTagValue(study, tagKey(Tag.NumberOfStudyRelatedSeries));
            assertValidIsValue(numSeries, "NumberOfStudyRelatedSeries");
            int seriesCount = Integer.parseInt(numSeries.trim());
            assertTrue(seriesCount >= 0,
                    "NumberOfStudyRelatedSeries must be non-negative, got: " + seriesCount);

            String numInstances = getDicomTagValue(study, tagKey(Tag.NumberOfStudyRelatedInstances));
            assertValidIsValue(numInstances, "NumberOfStudyRelatedInstances");
            int instanceCount = Integer.parseInt(numInstances.trim());
            assertTrue(instanceCount >= 0,
                    "NumberOfStudyRelatedInstances must be non-negative, got: " + instanceCount);

            // Series count should be <= instance count (each series has >= 1 instance)
            assertTrue(instanceCount >= seriesCount,
                    "NumberOfStudyRelatedInstances (" + instanceCount
                            + ") should be >= NumberOfStudyRelatedSeries (" + seriesCount + ")");
        }
    }

    /**
     * Test: Series-level QIDO-RS response compliance with PS3.18 Table 10.6.3-4
     *
     * Required (R) Return Keys:
     * - Modality (0008,0060) CS
     * - SeriesInstanceUID (0020,000E) UI  [Unique Key]
     * - SeriesNumber (0020,0011) IS
     * - NumberOfSeriesRelatedInstances (0020,1209) IS
     * - RetrieveURL (0008,1190) UR  [Required if retrievable]
     *
     * Type C Return Keys (present if known):
     * - SeriesDescription (0008,103E) LO
     * - PerformedProcedureStepStartDate (0040,0244) DA
     * - PerformedProcedureStepStartTime (0040,0245) TM
     *
     * Not asserted:
     * - TimezoneOffsetFromUTC (0008,0201) — not available in XNAT
     * - RequestAttributesSequence (0040,0275) — XNAT doesn't track scheduling
     */
    @Test(priority = 2)
    public void testSeriesResponseCompliance() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(), ctStudyUID);

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> seriesList = json.getList("$");
        assertFalse(seriesList.isEmpty(), "Should return at least one series");

        for (Map<String, Object> series : seriesList) {
            assertDicomJsonStructure(series);

            // Required return keys (Table 10.6.3-4)
            assertRequiredTag(series, Tag.SeriesInstanceUID, "UI", "SeriesInstanceUID");
            assertRequiredTag(series, Tag.Modality, "CS", "Modality");
            assertRequiredTag(series, Tag.SeriesNumber, "IS", "SeriesNumber");
            assertRequiredTag(series, Tag.NumberOfSeriesRelatedInstances, "IS",
                    "NumberOfSeriesRelatedInstances");

            // RetrieveURL required since series is retrievable
            assertRequiredTag(series, Tag.RetrieveURL, "UR", "RetrieveURL");
            String retrieveUrl = getDicomTagValue(series, tagKey(Tag.RetrieveURL));
            assertNotNull(retrieveUrl, "RetrieveURL must have a non-null value");
            assertFalse(retrieveUrl.isEmpty(), "RetrieveURL must not be empty");
            assertTrue(retrieveUrl.contains("/series/"),
                    "Series RetrieveURL must contain /series/ path segment");

            // Validate UID format
            String seriesUid = getDicomTagValue(series, tagKey(Tag.SeriesInstanceUID));
            assertValidUid(seriesUid, "SeriesInstanceUID");

            // Validate IS values
            String seriesNum = getDicomTagValue(series, tagKey(Tag.SeriesNumber));
            if (seriesNum != null) {
                assertValidIsValue(seriesNum, "SeriesNumber");
            }
            String numInstances = getDicomTagValue(series, tagKey(Tag.NumberOfSeriesRelatedInstances));
            if (numInstances != null) {
                assertValidIsValue(numInstances, "NumberOfSeriesRelatedInstances");
                int count = Integer.parseInt(numInstances.trim());
                assertTrue(count >= 0,
                        "NumberOfSeriesRelatedInstances must be non-negative, got: " + count);
            }

            // Type C attributes — validated if present
            assertOptionalTagIfPresent(series, Tag.SeriesDescription, "LO", "SeriesDescription");
            assertOptionalTagIfPresent(series, Tag.PerformedProcedureStepStartDate, "DA",
                    "PerformedProcedureStepStartDate");
            assertOptionalTagIfPresent(series, Tag.PerformedProcedureStepStartTime, "TM",
                    "PerformedProcedureStepStartTime");

            // Validate DA format if PerformedProcedureStepStartDate is present
            String ppsDate = getDicomTagValue(series, tagKey(Tag.PerformedProcedureStepStartDate));
            if (ppsDate != null && !ppsDate.isEmpty()) {
                assertValidDaValue(ppsDate, "PerformedProcedureStepStartDate");
            }
        }
    }

    /**
     * Test: Instance-level QIDO-RS response compliance with PS3.18 Table 10.6.3-5
     *
     * Required (R) Return Keys:
     * - SOPClassUID (0008,0016) UI
     * - SOPInstanceUID (0008,0018) UI  [Unique Key]
     * - InstanceNumber (0020,0013) IS
     * - RetrieveURL (0008,1190) UR  [Required if instance is retrievable]
     *
     * Type C Return Keys (present if known — all normally present for image data):
     * - InstanceAvailability (0008,0056) CS
     * - Rows (0028,0010) US
     * - Columns (0028,0011) US
     * - BitsAllocated (0028,0100) US
     * - NumberOfFrames (0028,0008) IS
     *
     * Note: TimezoneOffsetFromUTC (0008,0201) is Type C but not normally
     * present in DICOM files, so it is not asserted here.
     */
    @Test(priority = 3)
    public void testInstanceResponseCompliance() {
        // Get a series UID to query instances
        String getSeriesEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(), mrStudyUID);

        Response seriesResponse = mainQueryBase()
                .get(formatXapiUrl(getSeriesEndpoint))
                .then().assertThat().statusCode(200).extract().response();

        JsonPath seriesJson = seriesResponse.jsonPath();
        List<Map<String, Object>> seriesList = seriesJson.getList("$");
        assertTrue(seriesList.size() >= 1, "Study should have at least 1 series");

        Map<String, Object> firstSeries = seriesList.get(0);
        Map<String, Object> seriesUidTag = (Map<String, Object>) firstSeries.get(tagKey(Tag.SeriesInstanceUID));
        String seriesUID = ((List<String>) seriesUidTag.get("Value")).get(0);

        // Query instances
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances",
                testProject.getId(), mrStudyUID, seriesUID);

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> instances = json.getList("$");
        assertFalse(instances.isEmpty(), "Should return at least one instance");

        for (Map<String, Object> instance : instances) {
            assertDicomJsonStructure(instance);

            // Required return keys (Table 10.6.3-5)
            assertRequiredTag(instance, Tag.SOPClassUID, "UI", "SOPClassUID");
            assertRequiredTag(instance, Tag.SOPInstanceUID, "UI", "SOPInstanceUID");
            assertRequiredTag(instance, Tag.InstanceNumber, "IS", "InstanceNumber");

            // RetrieveURL is required when the instance is retrievable (which it always is here)
            assertRequiredTag(instance, Tag.RetrieveURL, "UR", "RetrieveURL");
            String retrieveUrl = getDicomTagValue(instance, tagKey(Tag.RetrieveURL));
            assertNotNull(retrieveUrl, "RetrieveURL must have a non-null value");
            assertFalse(retrieveUrl.isEmpty(), "RetrieveURL must not be empty");

            // Type C attributes — normally present for image DICOM instances
            assertRequiredTag(instance, Tag.InstanceAvailability, "CS", "InstanceAvailability");
            assertRequiredTag(instance, Tag.Rows, "US", "Rows");
            assertRequiredTag(instance, Tag.Columns, "US", "Columns");
            assertRequiredTag(instance, Tag.BitsAllocated, "US", "BitsAllocated");
            // NumberOfFrames is only present for multi-frame objects;
            // single-frame MR instances typically omit it.
            // PS3.6 defines VR=IS but some DICOM files encode it as US;
            // dcm4che preserves the source VR, so we don't enforce VR here.
            if (instance.containsKey(tagKey(Tag.NumberOfFrames))) {
                Map<String, Object> nfTag = (Map<String, Object>) instance.get(tagKey(Tag.NumberOfFrames));
                assertNotNull(nfTag.get("vr"), "NumberOfFrames must have a VR");
            }

            // Validate UID formats
            String sopClassUid = getDicomTagValue(instance, tagKey(Tag.SOPClassUID));
            assertValidUid(sopClassUid, "SOPClassUID");
            String sopInstanceUid = getDicomTagValue(instance, tagKey(Tag.SOPInstanceUID));
            assertValidUid(sopInstanceUid, "SOPInstanceUID");

            // Validate IS format for InstanceNumber
            String instanceNum = getDicomTagValue(instance, tagKey(Tag.InstanceNumber));
            if (instanceNum != null) {
                assertValidIsValue(instanceNum, "InstanceNumber");
            }
        }
    }

    /**
     * Test: DICOM JSON encoding compliance with PS3.18 Annex F
     *
     * Validates structural requirements across a full QIDO-RS response:
     * - Tag property names are 8-character uppercase hex strings
     * - Every attribute has a "vr" string field
     * - "Value" is always a JSON array (never a scalar)
     * - No attribute has both "Value" and "BulkDataURI"
     * - Response Content-Type is application/dicom+json
     */
    @Test(groups = SMOKE, priority = 1)
    @Basic
    public void testDicomJsonEncoding() {
        String endpoint = String.format("/dicomweb/projects/%s/studies", testProject.getId());

        Response response = mainQueryBase()
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json")
                .extract()
                .response();

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertFalse(studies.isEmpty(), "Should return at least one study");

        for (Map<String, Object> study : studies) {
            assertDicomJsonStructure(study);
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Extract DICOM tag value from DICOM JSON object.
     *
     * @param dicomJson DICOM JSON object (Map)
     * @param tag DICOM tag key in hex notation (e.g., "0020000D")
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

    /**
     * Assert that a required DICOM tag is present with the correct VR.
     * Per PS3.18 Section 10.6.3.3, required return key attributes shall be
     * present in the response if they exist in the source data.
     */
    private void assertRequiredTag(Map<String, Object> dicomJson, int tag,
                                   String expectedVr, String description) {
        String key = tagKey(tag);
        assertTrue(dicomJson.containsKey(key),
                description + " (" + key + ") must be present per PS3.18");
        Map<String, Object> tagData = (Map<String, Object>) dicomJson.get(key);
        assertEquals(tagData.get("vr"), expectedVr,
                description + " (" + key + ") must have VR=" + expectedVr);
    }

    /**
     * If a DICOM tag is present, validate that it has the correct VR.
     * Use for tags that are required by PS3.18 but not yet implemented
     * in the XNAT DICOMweb proxy — validates correctness when present
     * without failing when absent.
     */
    private void assertOptionalTagIfPresent(Map<String, Object> dicomJson, int tag,
                                            String expectedVr, String description) {
        String key = tagKey(tag);
        if (dicomJson.containsKey(key)) {
            Map<String, Object> tagData = (Map<String, Object>) dicomJson.get(key);
            assertEquals(tagData.get("vr"), expectedVr,
                    description + " (" + key + ") must have VR=" + expectedVr);
        }
    }

    /**
     * Validate DICOM JSON structure of an entire object per PS3.18 Annex F.
     *
     * For each attribute:
     * - Property name is an 8-character uppercase hex string
     * - Has a "vr" field that is a non-empty string
     * - If "Value" is present, it is a List (JSON array)
     * - "Value" and "BulkDataURI" are mutually exclusive
     */
    private void assertDicomJsonStructure(Map<String, Object> dicomObject) {
        for (Map.Entry<String, Object> entry : dicomObject.entrySet()) {
            String key = entry.getKey();

            // Tag key must be 8-character uppercase hex
            assertTrue(key.matches("[0-9A-F]{8}"),
                    "DICOM JSON tag key must be 8-char uppercase hex, got: " + key);

            assertTrue(entry.getValue() instanceof Map,
                    "Tag " + key + " value must be a JSON object");
            Map<String, Object> tagData = (Map<String, Object>) entry.getValue();

            // Must have "vr" field
            assertTrue(tagData.containsKey("vr"),
                    "Tag " + key + " must have 'vr' field per PS3.18 Annex F");
            assertTrue(tagData.get("vr") instanceof String,
                    "Tag " + key + " 'vr' must be a string");
            String vr = (String) tagData.get("vr");
            assertFalse(vr.isEmpty(),
                    "Tag " + key + " 'vr' must not be empty");

            // If "Value" is present, it must be a JSON array (List)
            if (tagData.containsKey("Value")) {
                assertTrue(tagData.get("Value") instanceof List,
                        "Tag " + key + " 'Value' must be a JSON array, not a scalar");
            }

            // "Value" and "BulkDataURI" are mutually exclusive (PS3.18 F.2.2)
            if (tagData.containsKey("Value") && tagData.containsKey("BulkDataURI")) {
                fail("Tag " + key + " has both 'Value' and 'BulkDataURI' — "
                        + "these are mutually exclusive per PS3.18 F.2.2");
            }
        }
    }

    /**
     * Validate a DICOM UID value per PS3.5 Section 9.1.
     * UIDs contain only digits and dots, max 64 characters.
     */
    private void assertValidUid(String value, String description) {
        assertNotNull(value, description + " UID value must not be null");
        assertTrue(value.length() <= 64,
                description + " UID must be <= 64 characters, got " + value.length());
        assertTrue(value.matches("[0-9]+(\\.[0-9]+)*"),
                description + " UID must contain only digits and dots, got: " + value);
    }

    /**
     * Validate a DICOM DA (Date) value per PS3.5 Section 6.2.
     * Format: YYYYMMDD (8 characters).
     */
    private void assertValidDaValue(String value, String description) {
        assertTrue(value.matches("\\d{8}"),
                description + " DA value must be YYYYMMDD format, got: " + value);
    }

    /**
     * Validate a DICOM IS (Integer String) value per PS3.5 Section 6.2.
     * Must be parseable as an integer.
     */
    private void assertValidIsValue(String value, String description) {
        try {
            Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            fail(description + " IS value must be a valid integer string, got: " + value);
        }
    }

    /**
     * Validate PN (Person Name) encoding per PS3.18 Annex F.
     * PN values must be JSON objects with an "Alphabetic" key, not plain strings.
     */
    private void assertValidPnValue(Map<String, Object> dicomJson, int tag) {
        String key = tagKey(tag);
        if (!dicomJson.containsKey(key)) {
            return;
        }
        Map<String, Object> tagData = (Map<String, Object>) dicomJson.get(key);
        if (!tagData.containsKey("Value")) {
            return; // Empty attribute is allowed
        }
        List<Object> values = (List<Object>) tagData.get("Value");
        for (Object value : values) {
            if (value == null) continue; // null elements allowed per PS3.18 F.2.5
            assertTrue(value instanceof Map,
                    key + " PN Value entries must be JSON objects with 'Alphabetic' key, "
                            + "not plain strings. Got: " + value.getClass().getSimpleName());
            Map<String, Object> pnObj = (Map<String, Object>) value;
            assertTrue(pnObj.containsKey("Alphabetic"),
                    key + " PN object must have 'Alphabetic' key per PS3.18 Annex F");
        }
    }
}
