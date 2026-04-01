package org.nrg.testing.xnat.tests.dicomwebproxy;

import io.restassured.response.Response;
import org.nrg.testing.util.RandomHelper;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
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
                .body("siteWideEnabled", equalTo(true))
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
}
