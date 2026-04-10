package org.nrg.testing.xnat.tests.dicomwebproxy;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.Assert.*;

@Test(groups = {PERMISSIONS})
public class TestDicomWebPrefsApi extends BaseDicomWebProxyTest {

    private User nonAdminUser;
    private Project testProject;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        nonAdminUser = createGenericUsers(1).get(0);
        testProject = new Project("DWPrefs" + RandomHelper.randomID(6)).accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(testProject);
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        resetSiteWideDefaults();
        restDriver.deleteProjectSilently(mainAdminUser, testProject);
    }

    public void testAdminCanReadPrefs() {
        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("siteWideEnabled", notNullValue())
                .body("filterMode", notNullValue())
                .body("projectList", notNullValue());
    }

    public void testAdminCanUpdatePrefs() {
        Map<String, Object> body = new HashMap<>();
        body.put("siteWideEnabled", true);
        body.put("filterMode", "whitelist");
        body.put("projectList", "PROJ1,PROJ2");

        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .post(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200);

        // Verify changes persisted
        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("siteWideEnabled", equalTo("true"))
                .body("filterMode", equalTo("whitelist"))
                .body("projectList", equalTo("PROJ1,PROJ2"));

        // Reset
        resetSiteWideDefaults();
    }

    public void testNonAdminCantReadPrefs() {
        getAs(nonAdminUser, prefsUrl())
                .then()
                .assertThat()
                .statusCode(403);
    }

    public void testNonAdminCantUpdatePrefs() {
        Map<String, Object> body = new HashMap<>();
        body.put("siteWideEnabled", true);

        restDriver.queryBaseFor(nonAdminUser)
                .contentType("application/json")
                .body(body)
                .post(prefsUrl())
                .then()
                .assertThat()
                .statusCode(403);
    }

    public void testInvalidFilterModeRejected() {
        Map<String, Object> body = new HashMap<>();
        body.put("filterMode", "invalid");

        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .post(prefsUrl())
                .then()
                .assertThat()
                .statusCode(400);
    }

    public void testAdminCanReadProjectConfig() {
        getAsAdmin(projectConfigUrl(testProject))
                .then()
                .assertThat()
                .statusCode(200)
                .body("excludeFromSiteWide", notNullValue());
    }

    public void testAdminCanUpdateProjectConfig() {
        Map<String, Object> body = new HashMap<>();
        body.put("excludeFromSiteWide", true);

        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .put(projectConfigUrl(testProject))
                .then()
                .assertThat()
                .statusCode(200);

        // Verify
        getAsAdmin(projectConfigUrl(testProject))
                .then()
                .assertThat()
                .statusCode(200)
                .body("excludeFromSiteWide", equalTo(true));

        // Reset
        body.put("excludeFromSiteWide", false);
        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .put(projectConfigUrl(testProject));
    }

    public void testNonAdminCantUpdateProjectConfig() {
        Map<String, Object> body = new HashMap<>();
        body.put("excludeFromSiteWide", true);

        restDriver.queryBaseFor(nonAdminUser)
                .contentType("application/json")
                .body(body)
                .put(projectConfigUrl(testProject))
                .then()
                .assertThat()
                .statusCode(403);
    }

    // ==================== All Preferences Read ====================

    /**
     * GET /prefs should return ALL exposed preference fields.
     */
    public void testPrefsResponseContainsAllFields() {
        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("defaultPageSize", notNullValue())
                .body("maxPageSize", notNullValue())
                .body("bulkDataThreshold", notNullValue())
                .body("defaultStrategy", notNullValue())
                .body("buildDelayMs", notNullValue())
                .body("siteWideEnabled", notNullValue())
                .body("filterMode", notNullValue())
                .body("projectList", notNullValue());
    }

    // ==================== defaultPageSize ====================

    /**
     * defaultPageSize can be updated and read back.
     */
    public void testDefaultPageSizeReadWrite() {
        updatePrefs("defaultPageSize", 25);

        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("defaultPageSize", equalTo(25));

        // Reset
        updatePrefs("defaultPageSize", 100);
    }

    /**
     * defaultPageSize affects QIDO-RS pagination when no limit is specified.
     */
    public void testDefaultPageSizeAffectsQido() {
        // Set a small default page size
        updatePrefs("defaultPageSize", 1);

        // Use DirectArchive with no delay for immediate data
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        // Upload 2 studies with unique UIDs
        Project qidoProject = new Project("DWPfPg" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(qidoProject);

        try {
            LocallyCacheableDicomTransformation data1 = createDataForProject(
                    qidoProject.getId(),
                    "2.25.60001000000000000000000000000000001",
                    "2.25.60001000000000000000000000000000002",
                    "2.25.60001000000000000000000000000000003",
                    "prefs_pagesize_1_" + qidoProject.getId());
            LocallyCacheableDicomTransformation data2 = createDataForProject(
                    qidoProject.getId(),
                    "2.25.60002000000000000000000000000000001",
                    "2.25.60002000000000000000000000000000002",
                    "2.25.60002000000000000000000000000000003",
                    "prefs_pagesize_2_" + qidoProject.getId());

            stowAsAdmin(projectStowUrl(qidoProject), data1);
            stowAsAdmin(projectStowUrl(qidoProject), data2);

            // Query without explicit limit — should be capped by defaultPageSize=1
            Response response = getAsAdmin(projectStudiesUrl(qidoProject));
            assertEquals(response.getStatusCode(), 200);

            JsonPath json = response.jsonPath();
            List<Map<String, Object>> studies = json.getList("$");
            assertEquals(studies.size(), 1,
                    "defaultPageSize=1 should cap results to 1 study when no limit specified");

            // X-Total-Count should still reflect the full count
            String totalCount = response.getHeader("X-Total-Count");
            assertNotNull(totalCount, "X-Total-Count header should be present");
            assertTrue(Integer.parseInt(totalCount) >= 2,
                    "X-Total-Count should reflect total (>= 2), not the capped page size");
        } finally {
            restDriver.deleteProjectSilently(mainAdminUser, qidoProject);
            updatePrefs("defaultPageSize", 100);
            updatePrefs("defaultStrategy", "GradualDicomImporter");
            updatePrefs("buildDelayMs", 5000);
            mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        }
    }

    // ==================== maxPageSize ====================

    /**
     * maxPageSize can be updated and read back.
     */
    public void testMaxPageSizeReadWrite() {
        updatePrefs("maxPageSize", 500);

        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("maxPageSize", equalTo(500));

        // Reset
        updatePrefs("maxPageSize", 1000);
    }

    /**
     * maxPageSize should cap explicit limit values that exceed it.
     */
    public void testMaxPageSizeCapsLimit() {
        updatePrefs("maxPageSize", 1);

        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        Project capProject = new Project("DWPfMx" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(capProject);

        try {
            LocallyCacheableDicomTransformation data1 = createDataForProject(
                    capProject.getId(),
                    "2.25.60003000000000000000000000000000001",
                    "2.25.60003000000000000000000000000000002",
                    "2.25.60003000000000000000000000000000003",
                    "prefs_maxpage_1_" + capProject.getId());
            LocallyCacheableDicomTransformation data2 = createDataForProject(
                    capProject.getId(),
                    "2.25.60004000000000000000000000000000001",
                    "2.25.60004000000000000000000000000000002",
                    "2.25.60004000000000000000000000000000003",
                    "prefs_maxpage_2_" + capProject.getId());

            stowAsAdmin(projectStowUrl(capProject), data1);
            stowAsAdmin(projectStowUrl(capProject), data2);

            // Request limit=100 but maxPageSize=1 should cap it
            String url = projectStudiesUrl(capProject) + "?limit=100";
            Response response = getAsAdmin(url);
            assertEquals(response.getStatusCode(), 200);

            List<Map<String, Object>> studies = response.jsonPath().getList("$");
            assertEquals(studies.size(), 1,
                    "maxPageSize=1 should cap limit=100 to 1 result");
        } finally {
            restDriver.deleteProjectSilently(mainAdminUser, capProject);
            updatePrefs("maxPageSize", 1000);
            updatePrefs("defaultStrategy", "GradualDicomImporter");
            updatePrefs("buildDelayMs", 5000);
            mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        }
    }

    // ==================== bulkDataThreshold ====================

    /**
     * bulkDataThreshold can be updated and read back.
     */
    public void testBulkDataThresholdReadWrite() {
        updatePrefs("bulkDataThreshold", 4096);

        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("bulkDataThreshold", equalTo(4096));

        // Reset
        updatePrefs("bulkDataThreshold", 1024);
    }

    // ==================== baseUrl ====================

    /**
     * baseUrl can be updated and affects RetrieveURL in QIDO responses.
     */
    public void testBaseUrlReadWrite() {
        updatePrefs("baseUrl", "https://custom.example.com/xnat");

        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("baseUrl", equalTo("https://custom.example.com/xnat"));

        // Reset to empty (use site URL)
        updatePrefs("baseUrl", "");
    }

    /**
     * baseUrl affects the RetrieveURL in QIDO study responses.
     */
    public void testBaseUrlAffectsRetrieveUrl() {
        String customBase = "https://custom.dicomweb.example.com";

        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);
        updatePrefs("baseUrl", customBase);

        Project urlProject = new Project("DWPfUrl" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(urlProject);

        try {
            LocallyCacheableDicomTransformation data = createDataForProject(
                    urlProject.getId(),
                    "2.25.60005000000000000000000000000000001",
                    "2.25.60005000000000000000000000000000002",
                    "2.25.60005000000000000000000000000000003",
                    "prefs_baseurl_" + urlProject.getId());

            stowAsAdmin(projectStowUrl(urlProject), data);

            Response response = getAsAdmin(projectStudiesUrl(urlProject));
            assertEquals(response.getStatusCode(), 200);

            String body = response.getBody().asString();
            assertTrue(body.contains(customBase),
                    "RetrieveURL should use custom baseUrl. Response: " +
                    body.substring(0, Math.min(300, body.length())));
        } finally {
            restDriver.deleteProjectSilently(mainAdminUser, urlProject);
            updatePrefs("baseUrl", "");
            updatePrefs("defaultStrategy", "GradualDicomImporter");
            updatePrefs("buildDelayMs", 5000);
            mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        }
    }
}
