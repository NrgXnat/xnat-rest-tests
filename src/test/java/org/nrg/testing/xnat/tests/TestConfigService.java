package org.nrg.testing.xnat.tests;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.nrg.testing.CommonStringUtils;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.HardDependency;
import org.nrg.testing.annotations.SoftDependency;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.Users;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.importer.importers.GradualDicomRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.rest.Credentials;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

public class TestConfigService extends BaseXnatRestTest {

    private final List<Project> projects = new ArrayList<>();
    private final Matcher<Integer> isOk = Matchers.isOneOf(200, 201);
    private final File dummy = getDataFile("dummy.txt");
    private final String dummyContents = FileIOUtils.readFile(dummy);
    private String testConfigUrl;

    @BeforeClass
    public void initConfigUrl() {
        testConfigUrl = formatRestUrl("config/test/newPath/goes/here");
    }

    @AfterClass(alwaysRun = true)
    public void removeConfigServiceProjects() {
        for (Project project : projects) {
            restDriver.deleteProjectSilently(mainAdminUser, project);
        }
    }

    @Test
    public void testConfigServicePut() {
        /* This test does the following:
		 * Do a put and a get. make sure what you put is what you get...
		 */

        mainAdminCredentials().contentType(ContentType.TEXT).body(dummyContents).put(testConfigUrl).then().assertThat().statusCode(isOk);

        final JsonPath configResponse = getConfigJsonPath(testConfigUrl);

        assertEquals(1, configResponse.getList("").size());
        assertEquals(dummyContents, configResponse.getString("get(0).contents"));
    }

    @Test
    @HardDependency("testConfigServicePut")
    public void testConfigServiceGetContents() {
        // read contents from previous test

        assertEquals(dummyContents, mainCredentials().queryParam("contents", true).get(testConfigUrl).body().asString());
    }

    @Test
    @HardDependency("testConfigServicePut")
    @SoftDependency("testConfigServiceGetContents") // this test would modify what that test is reading, so make this one come after
    public void testConfigServiceReplace() {
        // do another put to the same URL as the first test and check for updated contents

        final String newContents = readDataFile("test_asst_v1.xml");

        mainAdminCredentials().contentType(ContentType.TEXT).body(newContents).put(testConfigUrl).then().assertThat().statusCode(isOk);

        final JsonPath configResponse = getConfigJsonPath(testConfigUrl);

        assertEquals(1, configResponse.getList("").size());
        assertEquals(newContents, configResponse.getString("get(0).contents"));
    }

    @Test
    public void testDisableAndEnable() {
		/*  This test does the following:
		 *	PUT REST/config/test/testPath  (random data)
		 *	PUT REST/config/test/testPath&status=disabled
		 *	GET REST/config/test/testPath  (assert no data)
		 *	PUT REST/config/test/testPath&status=enabled
		 *	GET REST/config/test/testPath  (assert random data is returned)
		 *
		 *	and repeat test for URLs that include a project like:
		 *
		 *	REST/projects/someproject/config/test/testPath
		*/

        // make up a unique URL
        final String toolName = UUID.randomUUID().toString();
        final String path = UUID.randomUUID().toString() + "/" + UUID.randomUUID().toString();
        final Project project = registerProject();
        final String contents = readDataFile("test_asst_v1.xml");

        mainInterface().createProject(project);

        final List<String> urlsToTest = new ArrayList<>();

        urlsToTest.add(formatRestUrl("config", toolName, path));
        urlsToTest.add(formatRestUrl("projects", project.getId(), "config", toolName, path));

        for (String url : urlsToTest) {
            final User putUser = (url.contains("/projects/") ? mainUser : mainAdminUser);

            Credentials.build(putUser).given().contentType(ContentType.TEXT).body(contents).put(url).then().assertThat().statusCode(isOk); // PUT

            final JsonPath configResponse = getConfigJsonPath(url); // GET
            assertEquals(1, configResponse.getList("").size());

            Credentials.build(putUser).queryParam("status", "disabled").put(url).then().assertThat().statusCode(200); // DISABLE

            final JsonPath responseAfterDisable = getConfigJsonPath(url); // GET
            assertEquals(1, responseAfterDisable.getList("").size());
            assertEquals("disabled", responseAfterDisable.getString("get(0).status"));

            Credentials.build(putUser).queryParam("status", "badstring").put(url).then().assertThat().statusCode(400);

            Credentials.build(putUser).queryParam("status", "enabled").put(url).then().assertThat().statusCode(200); // ENABLE

            final JsonPath responseAfterEnable = getConfigJsonPath(url); // GET
            assertEquals(1, responseAfterEnable.getList("").size());
            assertEquals("enabled", responseAfterEnable.getString("get(0).status"));
            // make sure the contents are equal to the originally uploaded config.
            assertEquals(contents, responseAfterEnable.getString("get(0).contents"));
        }
    }

    @Test
    public void testConfigServiceVersion() {
		/*  This test does the following:
		 *	REST/config/test/testPath  PUT V1
		 *	REST/config/test/testPath  PUT V2
		 *	REST/config/test/testPath  GET to retrieve the version number
		 *	REST/config/test/testPath  PUT V3
		 *
		 *	REST/config/test/testPath&version=(whatever V2's version number is)
		 *
		 *	Assert the V2 file matches the contents retrieved at last GET.
		 *
		 *	repeat with project URL's: /projects/{PROJECT_ID}/config/{TOOL_NAME}
		 */

        // make up a unique URL
        final String toolName = UUID.randomUUID().toString();
        final String path = UUID.randomUUID().toString() + "/" + UUID.randomUUID().toString();
        final Project project = registerProject();
        final String v1 = readDataFile("test_subject_v1.xml");
        final String v2 = readDataFile("test_subject_v2.xml");
        final String v3 = readDataFile("test_subject_v3.xml");

        mainInterface().createProject(project);

        final List<String> urlsToTest = new ArrayList<>();

        urlsToTest.add(formatRestUrl("config", toolName, path));
        urlsToTest.add(formatRestUrl("projects", project.getId(), "config", toolName, path));

        for (String url : urlsToTest) {
            // If this is a project URL, then the standard user should be able to put. Otherwise, the admin has to put.
            final User putUser = (url.contains("/projects/") ? mainUser : mainAdminUser);

            Credentials.build(putUser).given().contentType(ContentType.TEXT).body(v1).put(url).then().assertThat().statusCode(isOk); // PUT V1
            Credentials.build(putUser).given().contentType(ContentType.TEXT).body(v2).put(url).then().assertThat().statusCode(isOk); // PUT V2

            final JsonPath v2ConfigResponse = getConfigJsonPath(url);

            assertEquals(1, v2ConfigResponse.getList("").size());

            final String version2 = v2ConfigResponse.getString("get(0).version");

            Credentials.build(putUser).given().contentType(ContentType.TEXT).body(v3).put(url).then().assertThat().statusCode(isOk); // PUT V3

            final JsonPath laterV2ConfigResponse = mainCredentials().queryParam("format", "json").queryParam("version", version2).get(url).
                    then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("ResultSet.Result"); // GET V2

            assertEquals(1, laterV2ConfigResponse.getList("").size());
            assertEquals(v2, laterV2ConfigResponse.getString("get(0).contents"));
        }
    }

    @Test
    public void testGetToolListing() {
		/*
		 * This test does the following:
		 *
		 * GET REST/config  save result (list of tools)
		 *
		 * PUT REST/config/randomToolName/newPath  some random new configuration with a new tool name
		 *
		 * GET REST/config  save result (list of tools)
		 *
		 * assert new result = old result plus new toolName
		 *
		 * repeat with project urls: /projects/{PROJECT_ID}/config
		 *
		 */

        // make up a unique URL
        final String toolNameToAdd = UUID.randomUUID().toString();
        final String path = UUID.randomUUID().toString() + "/" + UUID.randomUUID().toString();
        final Project project = registerProject();
        final String contents = readDataFile("test_subject_v1.xml");

        mainInterface().createProject(project);

        final List<String> urlsToTest = new ArrayList<>();

        urlsToTest.add(formatRestUrl("config"));
        urlsToTest.add(formatRestUrl("projects", project.getId(), "config"));

        for (String url : urlsToTest){
            // If this is a project URL, then the standard user should be able to put. Otherwise, the admin has to put.
            final User putUser = (url.contains("/projects/") ? mainUser : mainAdminUser);
            Credentials.build(putUser).contentType(ContentType.TEXT).body(contents).
                    put(CommonStringUtils.formatUrl(url, "baselineTool", path)).then().assertThat().statusCode(isOk); // add a baseline tool

            final int baselineSize = mainCredentials().queryParam("format", "json").get(url).jsonPath().getList("ResultSet.Result").size(); // GET baseline tools

            Credentials.build(putUser).contentType(ContentType.TEXT).body(contents).
                    put(CommonStringUtils.formatUrl(url, toolNameToAdd, path)).then().assertThat().statusCode(isOk); // add new random tool

            final JsonPath updatedTools = mainCredentials().queryParam("format", "json").get(url).jsonPath().setRoot("ResultSet.Result"); // GET updated tools

            assertEquals(baselineSize + 1, updatedTools.getList("").size());
            assertNotNull(updatedTools.param("name", toolNameToAdd).get("find { it.tool == name } "));
        }
    }

    @Test
    public void testConfigServiceProjectLevelSecurityLegacy() {
        final Project project = registerProject();
        final String path = UUID.randomUUID().toString() + "/" + UUID.randomUUID().toString();
        final String urlToTest = formatRestUrl("projects", project.getId(), "config", path);
        final String contents = dummyContents;

        // create a new project as admin.
        mainAdminInterface().createProject(project);

        // put a config in that project
        mainAdminCredentials().contentType(ContentType.TEXT).body(contents).put(urlToTest).then().assertThat().statusCode(isOk);

        assertEquals(contents, mainAdminCredentials().queryParam("contents", true).get(urlToTest).then().assertThat().statusCode(200).and().extract().response().asString());

        mainCredentials().queryParam("contents", true).get(urlToTest).then().assertThat().statusCode(404); // user can't see project, so 404 instead of 403
    }

    @Test // TODO: QA-504 requires 3 users
    public void testConfigServiceProjectLevelEditSecurity() {
        final Project project = registerProject().accessibility(Accessibility.PRIVATE);
        final User member = Users.genericAccount();
        final User collaborator = Users.genericAccount();
        final User unauthorizedUser = Users.genericAccount();
        mainAdminInterface().createUser(member);
        mainAdminInterface().createUser(collaborator);
        mainAdminInterface().createUser(unauthorizedUser);
        project.addOwner(mainUser);
        project.addMember(member);
        project.addCollaborator(collaborator);
        mainAdminInterface().createProject(project);

        final String path = "tracers/tracers";
        final String tracers = "PIB FDG";
        final String tracerUrl = formatRestUrl("projects", project.getId(), "config", path);

        mainAdminCredentials().contentType(ContentType.TEXT).body(tracers).put(tracerUrl).then().assertThat().statusCode(isOk);

        TimeUtils.sleep(1000); // let cache update

        for (User user : new User[]{member, collaborator, unauthorizedUser}) {
            Credentials.build(user).contentType(ContentType.TEXT).body("junk string").put(tracerUrl).then().assertThat().statusCode(403);
        }

        final JsonPath configResponse = getConfigJsonPath(tracerUrl);

        assertEquals(1, configResponse.getList("").size());
        assertEquals(tracers, configResponse.getString("get(0).contents"));
    }

    private Project registerProject() {
        final Project project = new Project();
        projects.add(project);
        return project;
    }

    private JsonPath getConfigJsonPath(String url) {
        return mainCredentials().queryParam("format", "json").get(url).then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("ResultSet.Result");
    }

}
