package org.nrg.testing.xnat.tests;

import lombok.extern.slf4j.Slf4j;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.containers.DockerServer;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_9_2;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static io.restassured.http.ContentType.JSON;
import static org.nrg.testing.TestGroups.CONTAINERS;
import static org.nrg.testing.TestGroups.PERMISSIONS;

@Slf4j
@TestRequires(users = 2, specificPluginRequirements = {
        @PluginRequirement(pluginId = PluginRegistry.CS_PLUGIN_ID, minimumSupportedVersion = "3.7.0") // see CS-944
})
@AddedIn(Xnat_1_9_2.class)
@Test(groups = {CONTAINERS, PERMISSIONS})
public class TestContainerVisibility  extends BaseXnatRestTest {


    private XnatInterface containerManagerInterface;
    private Project project;

    @BeforeClass
    public void setup() {
        final User containerManagerUser = getGenericUser();
        mainAdminInterface().assignUserToRoles(containerManagerUser, "ContainerManager", "Privileged");
        containerManagerInterface = interfaceFor(containerManagerUser);
        project = new Project();
        mainInterface().createProject(project);
    }

    @AfterClass(alwaysRun = true)
    public void deleteProject() {
        mainAdminInterface().deleteProject(project);
    }

    @Test
    public void testDefaultVisibility() {
        // Define command
        final String command = "{" +
                "\"image\": \"busybox:latest\", " +
                "\"name\": \"testVisibility\", " +
                "\"command-line\": \"/bin/sh -c 'echo hello'\", " +
                "\"xnat\": [{" +
                "\"name\": \"testVisibility\", " +
                "\"contexts\": [\"xnat:projectData\"]" +
                "}]" +
                "}";
        // Create command
        final int commandId = containerManagerInterface.addCommand(command);
        final String commandUri = formatXapiUrl("commands", String.valueOf(commandId));

        //Get the available commands, the default visibility should be Public
        containerManagerInterface.queryBase()
                .get(commandUri)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("visibility")
                .equals("public");

        containerManagerInterface.deleteCommand(commandId);
    }

    @Test
    public void testPrivateVisibility() {
        // Define command
        final String command = "{" +
                "\"image\": \"busybox:latest\", " +
                "\"name\": \"testPrivateVisibility\", " +
                "\"command-line\": \"/bin/sh -c 'echo hello'\", " +
                "\"xnat\": [{" +
                "\"name\": \"testPrivateVisibility\", " +
                "\"contexts\": [\"xnat:projectData\"]" +
                "}]," +
                "\"visibility\": \"private\"" +
                "}";
        // Create command
        final int commandId = containerManagerInterface.addCommand(command);
        final String commandUri = formatXapiUrl("commands", String.valueOf(commandId));
        //Get the available commands, the default visibility should be Public
        containerManagerInterface.queryBase()
                .get(commandUri)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("visibility")
                .equals("private");
        //Get list of commands available for a project
        final String commandsForProject = formatXapiUrl("projects", project.getId(), "commands","available?xsiType=xnat:projectData");
        mainQueryBase()
                .get(commandsForProject)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .asString()
                .equals("");
        containerManagerInterface.deleteCommand(commandId);
    }

    @Test
    public void testProtectedVisibility() {
        // Define command
        final String command = "\"image\": \"busybox:latest\", " +
                "\"name\": \"testProtectedVisibility\", " +
                "\"command-line\": \"/bin/sh -c 'echo hello'\", " +
                "\"xnat\": [{" +
                "\"name\": \"testProtectedVisibility\", " +
                "\"contexts\": [\"xnat:projectData\"]" +
                "}]," +
                "\"visibility\": \"protected\"";

        // Create command
        final int commandId = containerManagerInterface.addCommand("{" + command + "}");
        final String commandUri = formatXapiUrl("commands", String.valueOf(commandId));
        //Get the available commands, the default visibility should be Public
        containerManagerInterface.queryBase()
                .get(commandUri)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("visibility")
                .equals("protected");
        //Get list of commands available for a project
        final String commandsForProject = formatXapiUrl("projects", project.getId(), "commands","available?xsiType=xnat:projectData");
        mainQueryBase()
                .get(commandsForProject)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .asString()
                .equals("{" + "\"id\": \"" + commandId +"\", " +  command + "}");
        containerManagerInterface.deleteCommand(commandId);
    }

    @Test
    public void testBadVisibility() {
        // Define command
        final String command = "\"image\": \"busybox:latest\", " +
                "\"name\": \"testBadVisibility\", " +
                "\"command-line\": \"/bin/sh -c 'echo hello'\", " +
                "\"xnat\": [{" +
                "\"name\": \"testBadVisibility\", " +
                "\"contexts\": [\"xnat:projectData\"]" +
                "}]," +
                "\"visibility\": \"bad_value\"";

        // Attempt to create a bad command
        containerManagerInterface.queryBase()
                .body("{" + command + "}")
                .contentType(JSON)
                .post(formatXapiUrl("commands"))
                .then()
                .assertThat()
                .statusCode(400)
                .extract()
                .response()
                .asString()
                .startsWith("Invalid command");
    }

    @Test
    public void testVisibilityModification() {
        // Define command
        final String command = "\"image\": \"busybox:latest\", " +
                "\"name\": \"testStartPrivateVisibility\", " +
                "\"command-line\": \"/bin/sh -c 'echo hello'\", " +
                "\"xnat\": [{" +
                "\"name\": \"testStartPrivateVisibility\", " +
                "\"contexts\": [\"xnat:projectData\"]" +
                "}],";

        // Create command
        final int commandId = containerManagerInterface.addCommand("{" + command +  "\"visibility\": \"private\" }");
        final String commandUri = formatXapiUrl("commands", String.valueOf(commandId));
        //Get the available commands, the default visibility should be Public
        containerManagerInterface.queryBase()
                .get(commandUri)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("visibility")
                .equals("private");

        //Modify the visibility to public
        containerManagerInterface.queryBase()
                .post(formatXapiUrl("command", String.valueOf(commandId), "visibility","public"))
                .then()
                .assertThat()
                .statusCode(200);
        final String commandsForProject = formatXapiUrl("projects", project.getId(), "commands","available?xsiType=xnat:projectData");
        //Get list of commands available for a project
        mainQueryBase()
                .get(commandsForProject)
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response()
                .asString()
                .equals("{" + "\"id\": \"" + commandId +"\", " +  command +  "\"visibility\": \"public\"}");

        containerManagerInterface.deleteCommand(commandId);
    }

    @Test
    public void testVisibilityModificationWithBadValue() {
        // Define command
        final String command = "\"image\": \"busybox:latest\", " +
                "\"name\": \"testStartPrivateVisibility\", " +
                "\"command-line\": \"/bin/sh -c 'echo hello'\", " +
                "\"xnat\": [{" +
                "\"name\": \"testStartPrivateVisibility\", " +
                "\"contexts\": [\"xnat:projectData\"]" +
                "}],";

        // Create command
        final int commandId = containerManagerInterface.addCommand("{" + command + "\"visibility\": \"private\" }");
        containerManagerInterface.queryBase()
                .post(formatXapiUrl("command", String.valueOf(commandId), "visibility", "BAD_VALUE"))
                .then()
                .assertThat()
                .statusCode(400);
        containerManagerInterface.deleteCommand(commandId);
    }

    @Test
    public void testSiteAdminHasNoPermissions() {
        final String command = "{" +
                "\"image\": \"busybox:latest\", " +
                "\"name\": \"testVisibility\", " +
                "\"command-line\": \"/bin/sh -c 'echo hello'\", " +
                "\"xnat\": [{" +
                "\"name\": \"testVisibility\", " +
                "\"contexts\": [\"xnat:projectData\"]" +
                "}]" +
                "}";
        // Create command
        mainAdminInterface()
                .queryBase().contentType(JSON)
                .body(command)
                .post(formatXapiUrl("/commands"))
                .then().assertThat().statusCode(401);

    }

}
