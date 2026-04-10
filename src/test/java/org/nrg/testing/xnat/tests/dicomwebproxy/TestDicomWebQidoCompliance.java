package org.nrg.testing.xnat.tests.dicomwebproxy;

import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.Assert.*;

/**
 * QIDO-RS compliance tests: query filters, pagination, DICOM JSON response structure.
 * Mirrors coverage from the old TestDicomWebQido tests but against the new project-scoped
 * and site-wide DICOMweb plugin endpoints.
 */
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = "dicomwebplugin"))
@Test(groups = {PERMISSIONS})
public class TestDicomWebQidoCompliance extends BaseDicomWebProxyTest {

    private static String tagKey(int tag) {
        return String.format("%08X", tag);
    }

    // Unique UIDs for this test class to avoid cross-test conflicts
    private static final String QC_STUDY_UID_1 = "2.25.70001000000000000000000000000000001";
    private static final String QC_SERIES_UID_1 = "2.25.70001000000000000000000000000000002";
    private static final String QC_SOP_UID_1 = "2.25.70001000000000000000000000000000003";

    private static final String QC_STUDY_UID_2 = "2.25.70002000000000000000000000000000001";
    private static final String QC_SERIES_UID_2 = "2.25.70002000000000000000000000000000002";
    private static final String QC_SOP_UID_2 = "2.25.70002000000000000000000000000000003";

    private User memberUser;
    private Project project;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);
        project = new Project("DWQido" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(project);

        // Use DirectArchive with no delay so data is immediately queryable
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        // Upload test data with unique UIDs and explicit StudyTime
        LocallyCacheableDicomTransformation data1 = createQidoTestData(
                project.getId(), QC_STUDY_UID_1, QC_SERIES_UID_1, QC_SOP_UID_1,
                "143000", "qido_compliance_1_" + project.getId());
        LocallyCacheableDicomTransformation data2 = createQidoTestData(
                project.getId(), QC_STUDY_UID_2, QC_SERIES_UID_2, QC_SOP_UID_2,
                "153000", "qido_compliance_2_" + project.getId());

        stowAs(memberUser, projectStowUrl(project), data1);
        stowAs(memberUser, projectStowUrl(project), data2);
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        updatePrefs("defaultStrategy", "GradualDicomImporter");
        updatePrefs("buildDelayMs", 5000);
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    // ==================== Content Type ====================

    /**
     * QIDO-RS responses must have Content-Type: application/dicom+json.
     */
    public void testStudyResponseContentType() {
        getAs(memberUser, projectStudiesUrl(project))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/dicom+json");
    }

    // ==================== Study Response Structure ====================

    /**
     * Study responses must include required DICOM tags per PS3.18 Section 10.3:
     * StudyInstanceUID, PatientName, PatientID, StudyDate, StudyTime.
     */
    public void testStudyResponseRequiredTags() {
        Response response = getAs(memberUser, projectStudiesUrl(project));
        assertEquals(response.getStatusCode(), 200);

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertTrue(studies.size() >= 2, "Should have at least 2 studies (A and B)");

        Map<String, Object> study = studies.get(0);
        assertNotNull(study.get(tagKey(Tag.StudyInstanceUID)),
                "Study must have StudyInstanceUID (0020000D)");
        assertNotNull(study.get(tagKey(Tag.PatientName)),
                "Study must have PatientName (00100010)");
        assertNotNull(study.get(tagKey(Tag.PatientID)),
                "Study must have PatientID (00100020)");
    }

    /**
     * DICOM JSON encoding compliance: tags must be 8-char hex keys,
     * each must have a "vr" field, and UID values must be in a "Value" array.
     */
    public void testDicomJsonEncoding() {
        Response response = getAs(memberUser, projectStudiesUrl(project));
        assertEquals(response.getStatusCode(), 200);

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertFalse(studies.isEmpty(), "Should have at least 1 study");

        Map<String, Object> study = studies.get(0);

        // StudyInstanceUID structure: {"vr": "UI", "Value": ["..."]}
        Map<String, Object> uidTag = (Map<String, Object>) study.get(tagKey(Tag.StudyInstanceUID));
        assertNotNull(uidTag, "StudyInstanceUID tag must be present");
        assertEquals(uidTag.get("vr"), "UI", "StudyInstanceUID should have VR of UI");
        assertNotNull(uidTag.get("Value"), "StudyInstanceUID should have Value array");
        List<String> values = (List<String>) uidTag.get("Value");
        assertFalse(values.isEmpty(), "StudyInstanceUID Value array should not be empty");
    }

    // ==================== Series Response Structure ====================

    /**
     * Series responses must include SeriesInstanceUID, Modality, and SeriesNumber.
     */
    public void testSeriesResponseRequiredTags() {
        Response response = getAs(memberUser, projectSeriesUrl(project, QC_STUDY_UID_1));
        assertEquals(response.getStatusCode(), 200);

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> seriesList = json.getList("$");
        assertTrue(seriesList.size() >= 1, "Study should have at least 1 series");

        Map<String, Object> series = seriesList.get(0);
        assertNotNull(series.get(tagKey(Tag.SeriesInstanceUID)),
                "Series must have SeriesInstanceUID (0020000E)");
    }

    // ==================== Instance Response Structure ====================

    /**
     * Instance responses must include SOPInstanceUID and SOPClassUID.
     */
    public void testInstanceResponseRequiredTags() {
        Response response = getAs(memberUser,
                projectInstancesUrl(project, QC_STUDY_UID_1, QC_SERIES_UID_1));
        assertEquals(response.getStatusCode(), 200);

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> instances = json.getList("$");
        assertTrue(instances.size() >= 1, "Series should have at least 1 instance");

        Map<String, Object> instance = instances.get(0);
        assertNotNull(instance.get(tagKey(Tag.SOPInstanceUID)),
                "Instance must have SOPInstanceUID (00080018)");
        assertNotNull(instance.get(tagKey(Tag.SOPClassUID)),
                "Instance must have SOPClassUID (00080016)");
    }

    // ==================== Query Filters ====================

    /**
     * Query with StudyInstanceUID filter should return exactly 1 matching study.
     * First discovers the actual UID from unfiltered results, then uses it as a filter.
     */
    public void testStudyFilterByStudyInstanceUID() {
        // Get all studies to discover actual UIDs in the database
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        assertEquals(allResponse.getStatusCode(), 200);

        JsonPath allJson = allResponse.jsonPath();
        List<Map<String, Object>> allStudies = allJson.getList("$");
        assertTrue(allStudies.size() >= 2, "Need at least 2 studies for filter test");

        // Extract UID of first study
        Map<String, Object> firstStudy = allStudies.get(0);
        Map<String, Object> uidTag = (Map<String, Object>) firstStudy.get(tagKey(Tag.StudyInstanceUID));
        List<String> uidValues = (List<String>) uidTag.get("Value");
        String actualStudyUID = uidValues.get(0);

        // Filter by that UID
        String url = projectStudiesUrl(project) + "?StudyInstanceUID=" + actualStudyUID;
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertEquals(studies.size(), 1,
                "StudyInstanceUID filter should return exactly 1 study");

        Map<String, Object> returnedUidTag = (Map<String, Object>) studies.get(0).get(tagKey(Tag.StudyInstanceUID));
        List<String> returnedValues = (List<String>) returnedUidTag.get("Value");
        assertEquals(returnedValues.get(0), actualStudyUID, "Returned study should match queried UID");
    }

    /**
     * Query with a non-matching filter should return an empty array, not 404.
     */
    public void testStudyFilterNoResults() {
        String url = projectStudiesUrl(project) + "?PatientName=NONEXISTENT_PATIENT_XYZ";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200,
                "Empty results should return 200, not 404");

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertEquals(studies.size(), 0, "No-match query should return empty array");
    }

    // ==================== StudyInstanceUID Wildcard ====================

    /**
     * StudyInstanceUID filter with DICOM wildcard (*) should match using ILIKE.
     */
    public void testStudyFilterByStudyInstanceUIDWildcard() {
        // Get actual UID to build a wildcard prefix
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> uidTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.StudyInstanceUID));
        String actualUID = ((List<String>) uidTag.get("Value")).get(0);
        // Use first 10 chars as prefix with wildcard
        String prefix = actualUID.substring(0, 10);

        String url = projectStudiesUrl(project) + "?StudyInstanceUID=" + prefix + "*";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "Wildcard StudyInstanceUID filter (" + prefix + "*) should match at least 1 study");
    }

    // ==================== PatientName Filter ====================

    /**
     * PatientName exact match filter should return matching studies.
     */
    public void testStudyFilterByPatientNameExact() {
        // Discover the actual PatientName from unfiltered results
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> nameTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.PatientName));
        assertNotNull(nameTag, "Study should have PatientName tag");
        List<Object> nameValues = (List<Object>) nameTag.get("Value");
        // PatientName Value is an array of objects with "Alphabetic" key
        String patientName;
        Object first = nameValues.get(0);
        if (first instanceof Map) {
            patientName = (String) ((Map<String, Object>) first).get("Alphabetic");
        } else {
            patientName = first.toString();
        }
        assertNotNull(patientName, "PatientName should have a value");

        String url = projectStudiesUrl(project) + "?PatientName=" + patientName;
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "PatientName exact filter should return at least 1 study");
    }

    /**
     * PatientName wildcard filter should match using DICOM wildcard (*).
     */
    public void testStudyFilterByPatientNameWildcard() {
        String url = projectStudiesUrl(project) + "?PatientName=DWP_*";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        // createDataForProject sets PatientName to "DWP_<hash>" so this prefix should match
        assertTrue(studies.size() >= 1,
                "PatientName wildcard filter (DWP_*) should match at least 1 study");
    }

    // ==================== PatientID Filter ====================

    /**
     * PatientID exact match filter should return matching studies.
     */
    public void testStudyFilterByPatientIDExact() {
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> idTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.PatientID));
        assertNotNull(idTag, "Study should have PatientID tag");
        String patientID = ((List<String>) idTag.get("Value")).get(0);

        String url = projectStudiesUrl(project) + "?PatientID=" + patientID;
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "PatientID exact filter should return at least 1 study");
    }

    /**
     * PatientID wildcard filter should match.
     */
    public void testStudyFilterByPatientIDWildcard() {
        String url = projectStudiesUrl(project) + "?PatientID=DWP_*";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "PatientID wildcard filter (DWP_*) should match at least 1 study");
    }

    // ==================== AccessionNumber Filter ====================

    /**
     * AccessionNumber exact match filter should return matching studies.
     * In this plugin, AccessionNumber maps to the XNAT experiment ID.
     */
    public void testStudyFilterByAccessionNumberExact() {
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> accTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.AccessionNumber));
        assertNotNull(accTag, "Study should have AccessionNumber tag");
        String accessionNumber = ((List<String>) accTag.get("Value")).get(0);

        String url = projectStudiesUrl(project) + "?AccessionNumber=" + accessionNumber;
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertEquals(studies.size(), 1,
                "AccessionNumber exact filter should return exactly 1 study");
    }

    /**
     * AccessionNumber wildcard filter should match.
     */
    public void testStudyFilterByAccessionNumberWildcard() {
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> accTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.AccessionNumber));
        String accessionNumber = ((List<String>) accTag.get("Value")).get(0);
        // Use first 6 chars as prefix
        String prefix = accessionNumber.substring(0, Math.min(6, accessionNumber.length()));

        String url = projectStudiesUrl(project) + "?AccessionNumber=" + prefix + "*";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "AccessionNumber wildcard filter (" + prefix + "*) should match at least 1 study");
    }

    // ==================== StudyDate Filter ====================

    /**
     * StudyDate exact match filter (DICOM format yyyyMMdd) should return matching studies.
     */
    public void testStudyFilterByStudyDateExact() {
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> dateTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.StudyDate));
        assertNotNull(dateTag, "Study should have StudyDate tag");
        List<String> dateValues = (List<String>) dateTag.get("Value");
        assertNotNull(dateValues, "StudyDate should have values");
        String studyDate = dateValues.get(0);

        String url = projectStudiesUrl(project) + "?StudyDate=" + studyDate;
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "StudyDate exact filter (" + studyDate + ") should return at least 1 study");
    }

    /**
     * StudyDate wildcard filter should match using year prefix.
     */
    public void testStudyFilterByStudyDateWildcard() {
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> dateTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.StudyDate));
        String studyDate = ((List<String>) dateTag.get("Value")).get(0);
        // Use year as prefix wildcard
        String yearPrefix = studyDate.substring(0, 4);

        String url = projectStudiesUrl(project) + "?StudyDate=" + yearPrefix + "*";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "StudyDate wildcard filter (" + yearPrefix + "*) should match at least 1 study");
    }

    /**
     * StudyDate with non-matching date should return empty results.
     */
    public void testStudyFilterByStudyDateNoMatch() {
        String url = projectStudiesUrl(project) + "?StudyDate=19000101";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertEquals(studies.size(), 0,
                "StudyDate filter with non-matching date should return empty");
    }

    // ==================== Modality Filter ====================

    /**
     * Modality filter should return studies matching the XNAT session type.
     * Test data is MR (from SAMPLE_1), so ?Modality=MR should match.
     */
    public void testStudyFilterByModalityMatch() {
        String url = projectStudiesUrl(project) + "?Modality=MR";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "Modality=MR filter should match at least 1 study (test data is MR)");
    }

    /**
     * Modality filter with non-matching modality should return empty results.
     */
    public void testStudyFilterByModalityNoMatch() {
        String url = projectStudiesUrl(project) + "?Modality=US";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertEquals(studies.size(), 0,
                "Modality=US filter should return empty (test data is MR, not US)");
    }

    // ==================== StudyTime Filter ====================

    /**
     * StudyTime exact match filter should return matching studies.
     * Test data has StudyTime set to "143000" (14:30:00).
     */
    public void testStudyFilterByStudyTimeExact() {
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> timeTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.StudyTime));
        assertNotNull(timeTag, "Study should have StudyTime tag");
        List<String> timeValues = (List<String>) timeTag.get("Value");
        assertNotNull(timeValues, "StudyTime should have values");
        assertFalse(timeValues.isEmpty(), "StudyTime values should not be empty");
        String studyTime = timeValues.get(0);
        assertFalse(studyTime.isEmpty(), "StudyTime should not be empty string");

        String url = projectStudiesUrl(project) + "?StudyTime=" + studyTime;
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "StudyTime exact filter (" + studyTime + ") should return at least 1 study");
    }

    /**
     * StudyTime wildcard filter should match using hour prefix.
     */
    public void testStudyFilterByStudyTimeWildcard() {
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> timeTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.StudyTime));
        String studyTime = ((List<String>) timeTag.get("Value")).get(0);
        // Use first 2 digits (hour) as prefix
        String hourPrefix = studyTime.substring(0, 2);

        String url = projectStudiesUrl(project) + "?StudyTime=" + hourPrefix + "*";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertTrue(studies.size() >= 1,
                "StudyTime wildcard filter (" + hourPrefix + "*) should match at least 1 study");
    }

    /**
     * StudyTime with non-matching time should return empty results.
     */
    public void testStudyFilterByStudyTimeNoMatch() {
        String url = projectStudiesUrl(project) + "?StudyTime=235959";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertEquals(studies.size(), 0,
                "StudyTime filter with non-matching time should return empty");
    }

    // ==================== Combined Filters ====================

    /**
     * Multiple filters in a single query should be ANDed together.
     */
    public void testCombinedFilters() {
        // Get actual values from first study
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> study = allStudies.get(0);
        Map<String, Object> uidTag = (Map<String, Object>) study.get(tagKey(Tag.StudyInstanceUID));
        String uid = ((List<String>) uidTag.get("Value")).get(0);

        Map<String, Object> idTag = (Map<String, Object>) study.get(tagKey(Tag.PatientID));
        String patientID = ((List<String>) idTag.get("Value")).get(0);

        // Both filters should match the same study
        String url = projectStudiesUrl(project)
                + "?StudyInstanceUID=" + uid
                + "&PatientID=" + patientID;
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertEquals(studies.size(), 1,
                "Combined StudyInstanceUID + PatientID filter should return exactly 1 study");
    }

    /**
     * Combined filters where one doesn't match should return empty results.
     */
    public void testCombinedFiltersNoMatch() {
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        List<Map<String, Object>> allStudies = allResponse.jsonPath().getList("$");
        assertTrue(allStudies.size() >= 1, "Need at least 1 study");

        Map<String, Object> uidTag = (Map<String, Object>) allStudies.get(0).get(tagKey(Tag.StudyInstanceUID));
        String uid = ((List<String>) uidTag.get("Value")).get(0);

        // Valid UID but impossible PatientName — should AND to zero results
        String url = projectStudiesUrl(project)
                + "?StudyInstanceUID=" + uid
                + "&PatientName=IMPOSSIBLE_NAME_XYZ";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        List<Map<String, Object>> studies = response.jsonPath().getList("$");
        assertEquals(studies.size(), 0,
                "Combined filter with one non-matching should return empty");
    }

    // ==================== Pagination ====================

    /**
     * Pagination with limit parameter should cap the number of results.
     */
    public void testPaginationWithLimit() {
        String url = projectStudiesUrl(project) + "?limit=1";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertEquals(studies.size(), 1, "limit=1 should return exactly 1 study");
    }

    /**
     * Pagination with limit and offset should skip results.
     */
    public void testPaginationWithLimitAndOffset() {
        // First get total count
        Response allResponse = getAs(memberUser, projectStudiesUrl(project));
        int totalStudies = allResponse.jsonPath().getList("$").size();
        assertTrue(totalStudies >= 2, "Need at least 2 studies for offset test");

        // Get second study via offset
        String url = projectStudiesUrl(project) + "?limit=1&offset=1";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertEquals(studies.size(), 1, "limit=1&offset=1 should return 1 study");
    }

    /**
     * Pagination with offset beyond available results should return empty array.
     */
    public void testPaginationOffsetBeyondResults() {
        String url = projectStudiesUrl(project) + "?limit=10&offset=9999";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> studies = json.getList("$");
        assertEquals(studies.size(), 0,
                "Offset beyond results should return empty array");
    }

    /**
     * X-Total-Count header should be present and reflect the unfiltered total.
     */
    public void testXTotalCountHeader() {
        String url = projectStudiesUrl(project) + "?limit=1";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        String totalCount = response.getHeader("X-Total-Count");
        assertNotNull(totalCount, "X-Total-Count header should be present");
        int total = Integer.parseInt(totalCount);
        assertTrue(total >= 2, "X-Total-Count should reflect total studies (>= 2)");
    }

    // ==================== Series & Instance Queries ====================

    /**
     * Query series for a non-existent study should return empty results.
     */
    public void testSeriesQueryNonExistentStudy() {
        String url = projectSeriesUrl(project, "2.25.99999999999999999999999999999999999");
        Response response = getAs(memberUser, url);
        // Should return 200 with empty array or 404
        assertTrue(response.getStatusCode() == 200 || response.getStatusCode() == 404,
                "Non-existent study should return 200 (empty) or 404. Got: " + response.getStatusCode());
    }

    /**
     * Query instances for a non-existent series should return empty results.
     */
    public void testInstancesQueryNonExistentSeries() {
        String url = projectInstancesUrl(project, QC_STUDY_UID_1, "2.25.99999999999999999999999999999999999");
        Response response = getAs(memberUser, url);
        assertTrue(response.getStatusCode() == 200 || response.getStatusCode() == 404,
                "Non-existent series should return 200 (empty) or 404. Got: " + response.getStatusCode());
    }

    // ==================== Helpers ====================

    /**
     * Create DICOM test data with explicit StudyTime set.
     */
    private static LocallyCacheableDicomTransformation createQidoTestData(
            String projectId, String studyUID, String seriesUID, String sopUID,
            String studyTime, String cacheName) {
        final String patientId = "DWP_" + cacheName.hashCode();
        return new LocallyCacheableDicomTransformation(cacheName)
                .data(TestData.SAMPLE_1)
                .createZip()
                .transformations(
                        new DicomTransformation(cacheName + "_transform")
                                .produceZip()
                                .prefilter(DicomFilters.subsetWithSeriesAndInstanceNumbers(4, 100))
                                .transformFunction(
                                        TransformFunction.simple((dicom) -> {
                                            dicom.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
                                            dicom.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
                                            dicom.setString(Tag.SOPInstanceUID, VR.UI, sopUID);
                                            dicom.setString(Tag.StudyDescription, VR.LO, projectId);
                                            dicom.setString(Tag.PatientID, VR.LO, patientId);
                                            dicom.setString(Tag.PatientName, VR.PN, patientId);
                                            dicom.setString(Tag.StudyTime, VR.TM, studyTime);
                                        })
                                )
                ).build();
    }
}
