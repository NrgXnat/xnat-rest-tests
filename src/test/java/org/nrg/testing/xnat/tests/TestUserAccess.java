package org.nrg.testing.xnat.tests;

import com.google.common.collect.Sets;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.nrg.testing.TestNgUtils;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.ProjectResource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.rest.Credentials;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Set;

import static org.nrg.testing.TestGroups.*;
import static org.testng.AssertJUnit.*;

public class TestUserAccess extends BaseXnatRestTest {

    @Test(groups = {PERMISSIONS, AUTHENTICATION, SMOKE})
    @Basic
    public void testValidAccess() {
        final Response response = Settings.mainCredentials().expect().statusCode(200).given().queryParam("format", "xml").get(formatRestUrl("projects"));
        TestNgUtils.assertNonempty(response.asString());
    }

    @Test(groups = {PERMISSIONS, AUTHENTICATION, SMOKE})
    @Basic
    public void testInvalidAccess() {
        restDriver.invalidCredentials().expect().statusCode(401).given().queryParam("format", "xml").get(formatRestUrl("projects"));
    }

    @Test(groups = {PERMISSIONS, AUTHENTICATION, RESOURCES})
    @TestRequires(users = 1)
    public void testNonExpiringUserRestCalls() {
        final User nonExpiringUser = getGenericUser();
        mainAdminInterface().assignUserToRoles(nonExpiringUser, "non_expiring");
        interfaceFor(nonExpiringUser).createProject(
                new Project().addResource(
                        new ProjectResource().folder("test-resource").addResourceFile(
                                new ResourceFile().extension(new SimpleResourceFileExtension(getDataFile("louie.jpg")))
                        )
                )
        );
    }

    @Test(groups = {PERMISSIONS, AUTHENTICATION, SMOKE})
    @TestRequires(users = 1)
    @Basic
    public void testUserSessionInvalidation() {
        final User testUser = getGenericUser();
        final Project project = new Project().accessibility(Accessibility.PRIVATE);
        interfaceFor(testUser).createProject(project);
        final String singleLoginJsession = restDriver.interfaceFor(testUser).jsessionId();

        invalidateJsessions(testUser, Collections.singleton(singleLoginJsession));
        assertJsessionInvalidated(project, singleLoginJsession);

        final Set<String> additionalJsessions = Sets.newHashSet(generateJsession(testUser), generateJsession(testUser));
        invalidateJsessions(testUser, additionalJsessions);
        for (String jsession : additionalJsessions) {
            assertJsessionInvalidated(project, jsession);
        }
    }

    private void invalidateJsessions(User testUser, Set<String> expectedJsessions) {
        assertEquals(expectedJsessions, restDriver.interfaceFor(mainAdminUser).queryBase().delete(formatXapiUrl("users/active", testUser.getUsername())).then().assertThat().statusCode(200).and().extract().response().as(Set.class));
    }

    private void assertJsessionInvalidated(Project project, String jsession) {
        final Response invalidatedJsessionResponse = RestAssured.given().sessionId(jsession).get(formatRestUrl("projects", project.getId()));
        switch (invalidatedJsessionResponse.statusCode()) {
            case 200:
                assertTrue(invalidatedJsessionResponse.asString().contains("<!-- BEGIN xnat-templates/screens/Login.vm -->")); // redirected to login page
                break;
            case 401:
                assertFalse(invalidatedJsessionResponse.asString().contains(project.getId()));
                break;
            default:
                fail("Improper status code for REST call with invalidated JSESSION: " + invalidatedJsessionResponse.statusCode());
        }
    }

    private String generateJsession(User user) {
        final Response response = Credentials.build(user).get(formatRestUrl("auth"));
        if (response.statusCode() != 200) fail("Login attempt failed.");
        return response.getSessionId();
    }

}
