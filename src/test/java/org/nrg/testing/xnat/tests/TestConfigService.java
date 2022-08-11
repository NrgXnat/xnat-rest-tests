package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.HardDependency;
import org.nrg.testing.annotations.SoftDependency;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.ConfigServiceObject;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_8_3;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

import static org.nrg.testing.TestGroups.CONFIG_SERVICE;
import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.AssertJUnit.*;

@Test(groups = CONFIG_SERVICE)
public class TestConfigService extends BaseXnatRestTest {

    private final List<Project> projects = new ArrayList<>();
    private final File dummy = getDataFile("dummy.txt");
    private final String dummyContents = FileIOUtils.readFile(dummy);
    private final String TEST_TOOL = "test";
    private final String TEST_PATH = "newPath/goes/here";

    @AfterClass(alwaysRun = true)
    private void removeConfigServiceProjects() {
        for (Project project : projects) {
            restDriver.deleteProjectSilently(mainAdminUser, project);
        }
    }

    @Test
    public void testConfigServicePut() {
        /* This test does the following:
		 * Do a put and a get. make sure what you put is what you get...
		 */

        mainAdminInterface().putConfig(TEST_TOOL, TEST_PATH, dummyContents);
        assertEquals(dummyContents, mainInterface().readConfigToolPath(TEST_TOOL, TEST_PATH).getContents());
    }

    @Test
    @HardDependency("testConfigServicePut")
    public void testConfigServiceGetContents() {
        // read contents from previous test

        assertEquals(dummyContents, mainInterface().readConfigToolPathContents(TEST_TOOL, TEST_PATH));
    }

    @Test
    @HardDependency("testConfigServicePut")
    @SoftDependency("testConfigServiceGetContents") // this test would modify what that test is reading, so make this one come after
    public void testConfigServiceReplace() {
        // do another put to the same URL as the first test and check for updated contents

        final String newContents = readDataFile("test_asst_v1.xml");

        mainAdminInterface().putConfig(TEST_TOOL, TEST_PATH, newContents);
        assertEquals(newContents, mainInterface().readConfigToolPath(TEST_TOOL, TEST_PATH).getContents());
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
        final String path = UUID.randomUUID() + "/" + UUID.randomUUID();
        final Project project = registerProject();
        final String contents = readDataFile("test_asst_v1.xml");

        mainInterface().createProject(project);

        for (Project proj : Arrays.asList(null, project)) {
            final XnatInterface auth = interfaceFor(proj == null ? mainAdminUser : mainUser);

            auth.putConfig(proj, toolName, path, contents);
            auth.readConfigToolPath(proj, toolName, path);
            auth.setConfigStatus(proj, toolName, path, ConfigServiceObject.Status.DISABLED);
            assertEquals(ConfigServiceObject.Status.DISABLED, auth.readConfigToolPath(proj, toolName, path).getStatus());
            auth.queryBase().queryParam("status", "badstring").put(auth.configServiceUrl(proj, toolName, path)).then().assertThat().statusCode(400);
            auth.setConfigStatus(proj, toolName, path, ConfigServiceObject.Status.ENABLED);
            final ConfigServiceObject finalObjectState = auth.readConfigToolPath(proj, toolName, path);
            assertEquals(ConfigServiceObject.Status.ENABLED, finalObjectState.getStatus());
            assertEquals(contents, finalObjectState.getContents());
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
        final String path = UUID.randomUUID() + "/" + UUID.randomUUID();
        final Project project = registerProject();
        final String v1 = readDataFile("test_subject_v1.xml");
        final String v2 = readDataFile("test_subject_v2.xml");
        final String v3 = readDataFile("test_subject_v3.xml");

        mainInterface().createProject(project);

        for (Project proj : Arrays.asList(null, project)) {
            // If this is a project URL, then the standard user should be able to put. Otherwise, the admin has to put.
            final XnatInterface auth = interfaceFor(proj == null ? mainAdminUser : mainUser);

            auth.putConfig(proj, toolName, path, v1);
            auth.putConfig(proj, toolName, path, v2);
            final ConfigServiceObject initialV2Retrieve = auth.readConfigToolPath(proj, toolName, path);
            assertEquals(v2, initialV2Retrieve.getContents());
            assertEquals(2, initialV2Retrieve.getVersion());
            auth.putConfig(proj, toolName, path, v3);
            final ConfigServiceObject historicalV2Retrieve = auth.readConfigToolPath(proj, toolName, path, 2);
            assertEquals(v2, historicalV2Retrieve.getContents());
            assertEquals(2, historicalV2Retrieve.getVersion());
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
        final String path = UUID.randomUUID() + "/" + UUID.randomUUID();
        final Project project = registerProject();
        final String contents = readDataFile("test_subject_v1.xml");

        mainInterface().createProject(project);

        for (Project proj : Arrays.asList(null, project)) {
            // If this is a project URL, then the standard user should be able to put. Otherwise, the admin has to put.
            final XnatInterface auth = interfaceFor(proj == null ? mainAdminUser : mainUser);

            auth.putConfig(proj, "baselineTool", path, contents); // add a baseline tool
            final int baselineSize = auth.listConfigTools(proj).size(); // GET baseline tools

            auth.putConfig(proj, toolNameToAdd, path, contents); // add new random tool
            final List<String> updatedTools = auth.listConfigTools(proj);
            assertEquals(baselineSize + 1, updatedTools.size());
            assertTrue(updatedTools.contains(toolNameToAdd));
        }
    }

    @Test(groups = PERMISSIONS)
    public void testConfigServiceProjectLevelSecurityLegacy() {
        final Project project = registerProject();
        final String tool = UUID.randomUUID().toString();
        final String path = UUID.randomUUID().toString();
        final String contents = dummyContents;

        mainAdminInterface().createProject(project);
        mainAdminInterface().putConfig(project, tool, path, contents);
        assertEquals(contents, mainAdminInterface().readConfigToolPathContents(project, tool, path));
        mainQueryBase().queryParam("contents", true).get(mainInterface().configServiceUrl(project, tool, path)).
                then().assertThat().statusCode(404); // user can't see project, so 404 instead of 403
    }

    @Test(groups = PERMISSIONS)
    @TestRequires(users = 3)
    public void testConfigServiceProjectLevelEditSecurity() {
        final Project project = registerProject().accessibility(Accessibility.PRIVATE);
        final User member = getGenericUser();
        final User collaborator = getGenericUser();
        final User unauthorizedUser = getGenericUser();
        project.addOwner(mainUser);
        project.addMember(member);
        project.addCollaborator(collaborator);
        mainAdminInterface().createProject(project);

        final String tool = "tracers";
        final String path = "tracers";
        final String tracers = "PIB FDG";

        mainAdminInterface().putConfig(project, tool, path, tracers);

        TimeUtils.sleep(1000); // let cache update

        for (User user : Arrays.asList(member, collaborator, unauthorizedUser)) {
            interfaceFor(user).queryBase().contentType(ContentType.TEXT).body("junk string").put(mainInterface().configServiceUrl(project, tool, path)).
                    then().assertThat().statusCode(403);
        }

        assertEquals(tracers, mainInterface().readConfigToolPath(project, tool, path).getContents());
    }

    @Test // Verify XNAT-6870
    @AddedIn(Xnat_1_8_3.class)
    public void testConfigServiceCreateWithStatus() {
        final String tool = RandomHelper.randomID(10);
        final String path = RandomHelper.randomID(10);
        final ConfigServiceObject configServiceObject = new ConfigServiceObject();
        configServiceObject.setContents("ABC123");
        configServiceObject.setStatus(ConfigServiceObject.Status.ENABLED);
        mainAdminInterface().putConfig(tool, path, configServiceObject);
        assertEquals(configServiceObject.getContents(), mainAdminInterface().readConfigToolPath(tool, path).getContents());
    }

    private Project registerProject() {
        final Project project = new Project();
        projects.add(project);
        return project;
    }

}
