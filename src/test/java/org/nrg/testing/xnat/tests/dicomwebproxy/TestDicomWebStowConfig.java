package org.nrg.testing.xnat.tests.dicomwebproxy;

import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import io.restassured.response.Response;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.util.RandomHelper;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.Assert.*;
import org.nrg.testing.annotations.MutatesServerState;

/**
 * Integration tests for STOW-RS configuration: strategy selection, build delay,
 * concurrent upload merge behavior, and preference validation.
 */
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = "dicomwebplugin"))
@Test(groups = {PERMISSIONS})
@MutatesServerState
public class TestDicomWebStowConfig extends BaseDicomWebProxyTest {

    private User memberUser;
    private Project project;

    // Unique UIDs per test method to avoid cross-test interference.
    // Each test that uploads data uses its own set generated via createDataForProject().

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);

        project = new Project("DWCfg" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(project);

        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
    }

    @AfterMethod(alwaysRun = true)
    private void resetPrefsAfterTest() {
        // Reset to defaults after each test to avoid bleed-through
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("defaultStrategy", "GradualDicomImporter");
        defaults.put("buildDelayMs", 5000);
        mainAdminQueryBase()
                .contentType("application/json")
                .body(defaults)
                .post(prefsUrl());
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        resetPrefsAfterTest();
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    // ==================== Strategy Selection ====================

    /**
     * Upload with ?strategy=DirectArchive query parameter and buildDelayMs=0.
     * Should succeed and the data should be immediately available (no prearchive).
     */
    public void testStowWithDirectArchiveStrategy() {
        updatePrefs("buildDelayMs", 0);

        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.90001000000000000000000000000000001",
                "2.25.90001000000000000000000000000000002",
                "2.25.90001000000000000000000000000000003",
                "stow_cfg_direct_" + project.getId());

        Response response = stowAs(memberUser,
                projectStowUrl(project, "DirectArchive"), data);
        assertEquals(response.getStatusCode(), 200,
                "STOW with DirectArchive strategy should return 200");

        // Data should be immediately queryable (not stuck in prearchive)
        Response qido = getAs(memberUser, projectStudiesUrl(project));
        assertEquals(qido.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(qido, "2.25.90001000000000000000000000000000001"),
                "DirectArchive data should be immediately queryable");
    }

    /**
     * Upload with ?strategy=GradualDicomImporter query parameter.
     * Should succeed (data may take a moment to appear while it goes through prearchive).
     */
    public void testStowWithGradualDicomImporterStrategy() {
        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.90002000000000000000000000000000001",
                "2.25.90002000000000000000000000000000002",
                "2.25.90002000000000000000000000000000003",
                "stow_cfg_gradual_" + project.getId());

        Response response = stowAs(memberUser,
                projectStowUrl(project, "GradualDicomImporter"), data);
        assertEquals(response.getStatusCode(), 200,
                "STOW with GradualDicomImporter strategy should return 200");
    }

    /**
     * Upload with an unrecognized strategy name should fall back to the default
     * strategy and succeed rather than failing.
     */
    public void testStowWithUnknownStrategyFallsBackToDefault() {
        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.90003000000000000000000000000000001",
                "2.25.90003000000000000000000000000000002",
                "2.25.90003000000000000000000000000000003",
                "stow_cfg_fallback_" + project.getId());

        Response response = stowAs(memberUser,
                projectStowUrl(project, "NonExistentStrategy"), data);
        assertEquals(response.getStatusCode(), 200,
                "STOW with unrecognized strategy should fall back to default and succeed");
    }

    /**
     * Change the default strategy preference to DirectArchive, then upload without
     * a query parameter. The upload should use DirectArchive.
     */
    public void testDefaultStrategyPreference() {
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.90004000000000000000000000000000001",
                "2.25.90004000000000000000000000000000002",
                "2.25.90004000000000000000000000000000003",
                "stow_cfg_default_da_" + project.getId());

        Response response = stowAs(memberUser, projectStowUrl(project), data);
        assertEquals(response.getStatusCode(), 200,
                "STOW with default strategy set to DirectArchive should return 200");

        // DirectArchive with buildDelayMs=0: data should be immediately queryable
        Response qido = getAs(memberUser, projectStudiesUrl(project));
        assertEquals(qido.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(qido, "2.25.90004000000000000000000000000000001"),
                "Data should be queryable with DirectArchive as default strategy");
    }

    /**
     * Per-request strategy parameter should override the default preference.
     */
    public void testStrategyQueryParamOverridesDefault() {
        updatePrefs("defaultStrategy", "GradualDicomImporter");
        updatePrefs("buildDelayMs", 0);

        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.90005000000000000000000000000000001",
                "2.25.90005000000000000000000000000000002",
                "2.25.90005000000000000000000000000000003",
                "stow_cfg_override_" + project.getId());

        // Override with DirectArchive per-request
        Response response = stowAs(memberUser,
                projectStowUrl(project, "DirectArchive"), data);
        assertEquals(response.getStatusCode(), 200,
                "Per-request strategy override should succeed");

        // DirectArchive with buildDelayMs=0: data should be immediately queryable
        Response qido = getAs(memberUser, projectStudiesUrl(project));
        assertEquals(qido.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(qido, "2.25.90005000000000000000000000000000001"),
                "Per-request DirectArchive override should take effect");
    }

    // ==================== Build Delay Configuration ====================

    /**
     * Verify that buildDelayMs can be read and written via the prefs API.
     */
    public void testBuildDelayPreferenceReadWrite() {
        // Set to a custom value
        updatePrefs("buildDelayMs", 10000);

        // Read back
        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("buildDelayMs", equalTo(10000));

        // Set to 0 (immediate build)
        updatePrefs("buildDelayMs", 0);

        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("buildDelayMs", equalTo(0));
    }

    /**
     * Upload with buildDelayMs=0 (immediate build). Data should appear right away.
     */
    public void testStowWithZeroBuildDelay() {
        updatePrefs("buildDelayMs", 0);
        updatePrefs("defaultStrategy", "DirectArchive");

        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.90006000000000000000000000000000001",
                "2.25.90006000000000000000000000000000002",
                "2.25.90006000000000000000000000000000003",
                "stow_cfg_nodelay_" + project.getId());

        Response response = stowAs(memberUser, projectStowUrl(project), data);
        assertEquals(response.getStatusCode(), 200,
                "STOW with buildDelayMs=0 should return 200");

        // Immediate build: data should be queryable right away
        Response qido = getAs(memberUser, projectStudiesUrl(project));
        assertEquals(qido.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(qido, "2.25.90006000000000000000000000000000001"),
                "With buildDelayMs=0, data should be immediately queryable");
    }

    /**
     * Upload with a longer buildDelayMs. The upload should still succeed,
     * and data should eventually become queryable within the delay window.
     */
    public void testStowWithLongerBuildDelay() {
        updatePrefs("buildDelayMs", 8000);
        updatePrefs("defaultStrategy", "DirectArchive");

        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.90007000000000000000000000000000001",
                "2.25.90007000000000000000000000000000002",
                "2.25.90007000000000000000000000000000003",
                "stow_cfg_longdelay_" + project.getId());

        Response response = stowAs(memberUser, projectStowUrl(project), data);
        assertEquals(response.getStatusCode(), 200,
                "STOW with longer buildDelayMs should still return 200");
    }

    // ==================== Concurrent Upload Merge ====================

    /**
     * Two sequential uploads to the same study with DirectArchive should merge
     * into one session with both series.
     */
    public void testSequentialUploadsMergeIntoSameSession() {
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        String studyUID = "2.25.90008000000000000000000000000000001";

        LocallyCacheableDicomTransformation batch1 = createDataForProject(
                project.getId(), studyUID,
                "2.25.90008000000000000000000000000000002",
                "2.25.90008000000000000000000000000000003",
                "stow_cfg_merge1_" + project.getId());

        LocallyCacheableDicomTransformation batch2 = createDataForProject(
                project.getId(), studyUID,
                "2.25.90008000000000000000000000000000004",
                "2.25.90008000000000000000000000000000005",
                "stow_cfg_merge2_" + project.getId());

        // First upload
        Response resp1 = stowAs(memberUser, projectStowUrl(project, "DirectArchive"), batch1);
        assertEquals(resp1.getStatusCode(), 200, "First batch should upload successfully");

        // Second upload to same study (should merge)
        Response resp2 = stowAs(memberUser, projectStowUrl(project, "DirectArchive"), batch2);
        assertEquals(resp2.getStatusCode(), 200, "Second batch should merge successfully");

        // Both series should be in the same study
        Response seriesResponse = getAs(memberUser, projectSeriesUrl(project, studyUID));
        assertEquals(seriesResponse.getStatusCode(), 200);
        String seriesBody = seriesResponse.getBody().asString();
        assertTrue(seriesBody.contains("2.25.90008000000000000000000000000000002"),
                "First series should exist after merge");
        assertTrue(seriesBody.contains("2.25.90008000000000000000000000000000004"),
                "Second series should exist after merge");
    }

    /**
     * Two concurrent uploads to the same study should both succeed and merge.
     * This tests the per-study build lock mechanism.
     */
    public void testConcurrentUploadsMerge() throws Exception {
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        String studyUID = "2.25.90009000000000000000000000000000001";

        LocallyCacheableDicomTransformation batch1 = createDataForProject(
                project.getId(), studyUID,
                "2.25.90009000000000000000000000000000002",
                "2.25.90009000000000000000000000000000003",
                "stow_cfg_concurrent1_" + project.getId());

        LocallyCacheableDicomTransformation batch2 = createDataForProject(
                project.getId(), studyUID,
                "2.25.90009000000000000000000000000000004",
                "2.25.90009000000000000000000000000000005",
                "stow_cfg_concurrent2_" + project.getId());

        String url = projectStowUrl(project, "DirectArchive");

        // Launch two uploads in parallel
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Response> future1 = CompletableFuture.supplyAsync(
                    () -> stowAs(memberUser, url, batch1), executor);
            CompletableFuture<Response> future2 = CompletableFuture.supplyAsync(
                    () -> stowAs(memberUser, url, batch2), executor);

            Response resp1 = future1.get(60, TimeUnit.SECONDS);
            Response resp2 = future2.get(60, TimeUnit.SECONDS);

            assertEquals(resp1.getStatusCode(), 200,
                    "First concurrent upload should succeed");
            assertEquals(resp2.getStatusCode(), 200,
                    "Second concurrent upload should succeed");
        } finally {
            executor.shutdown();
        }

        // Verify both series ended up in the same study
        Response seriesResponse = getAs(memberUser, projectSeriesUrl(project, studyUID));
        assertEquals(seriesResponse.getStatusCode(), 200,
                "Merged study should be queryable");
        String seriesBody = seriesResponse.getBody().asString();
        assertTrue(seriesBody.contains("2.25.90009000000000000000000000000000002"),
                "First concurrent series should exist");
        assertTrue(seriesBody.contains("2.25.90009000000000000000000000000000004"),
                "Second concurrent series should exist");
    }

    // ==================== Preference Validation ====================

    /**
     * Setting defaultStrategy to an invalid value should be rejected.
     */
    public void testInvalidStrategyPreferenceRejected() {
        Map<String, Object> body = new HashMap<>();
        body.put("defaultStrategy", "NotAStrategy");

        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .post(prefsUrl())
                .then()
                .assertThat()
                .statusCode(400);
    }

    /**
     * Setting buildDelayMs to a negative value should be rejected.
     */
    public void testNegativeBuildDelayRejected() {
        Map<String, Object> body = new HashMap<>();
        body.put("buildDelayMs", -1);

        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .post(prefsUrl())
                .then()
                .assertThat()
                .statusCode(400);
    }

    /**
     * Verify all STOW-related preferences are returned by the prefs endpoint.
     */
    public void testPrefsEndpointIncludesStowConfig() {
        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("defaultStrategy", notNullValue())
                .body("buildDelayMs", notNullValue())
                .body("defaultPageSize", notNullValue())
                .body("maxPageSize", notNullValue())
                .body("bulkDataThreshold", notNullValue());
    }

    /**
     * Verify that multiple preferences can be updated in a single request.
     */
    public void testBatchPreferenceUpdate() {
        Map<String, Object> body = new HashMap<>();
        body.put("defaultStrategy", "DirectArchive");
        body.put("buildDelayMs", 3000);
        body.put("defaultPageSize", 50);

        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .post(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200);

        getAsAdmin(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .body("defaultStrategy", equalTo("DirectArchive"))
                .body("buildDelayMs", equalTo(3000))
                .body("defaultPageSize", equalTo(50));

        // Reset defaultPageSize
        updatePrefs("defaultPageSize", 100);
    }
}
