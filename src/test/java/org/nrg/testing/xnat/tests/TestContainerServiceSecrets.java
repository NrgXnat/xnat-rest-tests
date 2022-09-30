package org.nrg.testing.xnat.tests;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.RandomStringGenerator;
import org.hamcrest.Matchers;
import org.hamcrest.collection.IsMapContaining;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Workflow;
import org.nrg.xnat.pogo.containers.Container;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.restassured.http.ContentType.ANY;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.nrg.testing.TestGroups.CONTAINERS;
import static org.nrg.xnat.subinterfaces.ContainerServiceSubinterface.ContainerLog.STDOUT;
import static org.testng.Assert.assertEquals;

@TestRequires(plugins = "containers:3.3")
@Test(groups = {CONTAINERS})
public class TestContainerServiceSecrets extends BaseXnatRestTest {

    private static final RandomStringGenerator UPPERCASE_GENERATOR = new RandomStringGenerator.Builder().withinRange(new char[] {'A', 'Z'}).build();
    public static final String NEWLINE = "\\R";
    public static final String SCRIPT_OUTPUT_LINE0 = "<b>BEGIN PROCESSING UPLOADED FILES</b>";
    public static final String SCRIPT_OUTPUT_LINE1 = "<br><b>SCRIPT EXECUTION RESULTS</b>";
    public static final String SCRIPT_OUTPUT_LINE2 = "<br><b>SCRIPT STDOUT</b><br>";
    public static final String SCRIPT_OUTPUT_LINEN = "<br><b>PROCESSING COMPLETE";

    private String scriptId = null;
    private String triggerId = null;

    @BeforeClass
    public void setupClass() {
        // Clean up all commands from previous tests
        mainAdminInterface().deleteAllCommands();
    }

    @AfterClass
    public void cleanUpClass() {
        // Clean up all commands
        mainAdminInterface().deleteAllCommands();

        // Clean up script trigger and script
        if (StringUtils.isNotBlank(triggerId)) {
            mainAdminInterface()
                    .queryBase()
                    .delete(formatRestUrl("automation", "triggers", triggerId));
        }
        if (StringUtils.isNotBlank(scriptId)) {
            mainAdminInterface()
                    .queryBase()
                    .delete(formatRestUrl("automation", "scripts", scriptId));
        }
    }

    private String readXnatSystemProperty(String propertyName) {
        // TODO one day, make these script rest calls into a scripts subinterface

        // Create a script to read given system property
        scriptId = RandomStringUtils.randomAlphabetic(5);
        final String description = "Read system property to stdout";
        final String scriptContent =
                "from java.lang import System; print(System.getProperty('" + propertyName + "'))";
        final String scriptCreationJson = "{" +
                "\"scriptLabel\": \"" + description + "\", " +
                "\"content\": \"" + scriptContent + "\", " +
                "\"language\": \"python\", " +
                "\"version\": \"2.7\", " +
                "\"srcEventClass\": \"org.nrg.xnat.event.entities.ScriptLaunchRequestEvent\"" +
                "}";
        mainAdminInterface()
                .queryBase()
                .body(scriptCreationJson)
                .contentType(JSON)
                .put(formatRestUrl("automation", "scripts", scriptId))
                .then()
                .assertThat()
                .statusCode(200);

        // Create a site-wide event handler to run the script on request
        final String eventName = RandomStringUtils.randomAlphabetic(5);
        final String eventHandlerJson = "{" +
                "\"import\": false," +
                "\"eventHandlers\": [{" +
                    "\"eventClass\": \"org.nrg.xnat.event.entities.ScriptLaunchRequestEvent\"," +
                    "\"event\": \"" + eventName + "\"," +
                    "\"scriptId\": \"" + scriptId + "\"," +
                    "\"description\": \"" + description + "\"," +
                    "\"filters\": {}" +
                "}]" +
                "}";
        triggerId = mainAdminInterface()
                .queryBase()
                .body(eventHandlerJson)
                .contentType(JSON)
                .put(formatRestUrl("automation", "handlers"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .asString();

        // Run the script
        final String scriptOutput = mainAdminInterface()
                .queryBase()
                .queryParams(Stream.of(
                            Pair.of("import-handler", "automation"),
                            Pair.of("project", "site"), // I don't think the value matters here, but we need something
                            Pair.of("process", "true"),
                            Pair.of("eventHandler", triggerId),
                            Pair.of("escapeHtml", "false"),
                            Pair.of("extract", "false"),
                            Pair.of("sendmail", "false")
                        ).collect(Collectors.toMap(Pair::getLeft, Pair::getRight))
                )
                .contentType(ANY)
                .post(formatRestUrl("services", "import"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .asString();

        // Extract the script's stdout content from the formatted response
        final String[] lines = scriptOutput.split(NEWLINE);
        // First and last lines are boilerplate
        assertEquals(lines[0], SCRIPT_OUTPUT_LINE0);
        assertEquals(lines[lines.length - 1], SCRIPT_OUTPUT_LINEN);
        // Next lines will be different if there is content or not
        assertEquals(lines[1], SCRIPT_OUTPUT_LINE1);
        assertEquals(lines[2], SCRIPT_OUTPUT_LINE2);

        // Remaining lines are content
        final int firstContentLine = 3;
        final int lastContentLine = lines.length - 2;
        final String content = (firstContentLine != lastContentLine ?
                String.join("\n", Arrays.copyOfRange(lines, firstContentLine, lastContentLine)) :
                lines[firstContentLine])
                .replaceAll("<br>", "\n")
                .trim();
        assertThat(content, is(not(emptyString())));
        return content;
    }

    public void testCreateContainerWithEnvSecret() {
        // get system property value
        final String systemPropertyName = "java.version";
        final String expectedSystemPropertyValue = readXnatSystemProperty(systemPropertyName);

        // Define command
        final String secretEnvName = UPPERCASE_GENERATOR.generate(3, 7);
        final String name = RandomStringUtils.randomAlphabetic(5);
        final String command = "{" +
                "\"image\": \"busybox:latest\", " +
                "\"name\": \"" + name + "\", " +
                "\"command-line\": \"/bin/sh -c 'echo $" + secretEnvName + "'\", " +
                "\"xnat\": [{" +
                    "\"name\": \"" + name + "\", " +
                    "\"contexts\": [\"site\"]" +
                "}], " +
                "\"secrets\": [{" +
                    "\"source\": {\"type\": \"system-property\", \"identifier\": \"" + systemPropertyName + "\"}, " +
                    "\"destination\": {\"type\": \"environment-variable\", \"identifier\": \"" + secretEnvName + "\"}" +
                "}]}";
        // Create command
        final int commandId = mainAdminInterface().addCommand(command);

        // Launch container
        final String rootElement = "site";
        final int workflowId = mainQueryBase()
                .contentType(JSON)
                .body("{\"" + rootElement + "\": \"" + rootElement + "\"}")  // BS non-useful param
                .post(formatXapiUrl("commands", String.valueOf(commandId), "wrappers", name, "root", rootElement, "launch"))
                .then()
                .assertThat()
                .statusCode(200)
                .body("status", Matchers.equalTo("success"))
                .extract()
                .jsonPath()
                .getInt("workflow-id");

        // Wait for container to finish successfully
        mainAdminInterface().waitForWorkflowComplete(workflowId);

        // Container id from workflow comments
        final Workflow workflow = mainAdminInterface().readWorkflow(workflowId);
        final String containerId = workflow.getComments();

        // Get container logs
        final String containerLog = mainAdminInterface().readContainerLog(containerId, STDOUT);
        final String systemPropertyValue = containerLog.trim();

        // Assert
        assertEquals(systemPropertyValue, expectedSystemPropertyValue);

        // Check that the secret is not reported in the container object's environment variables
        final Container container = mainInterface().getContainer(containerId);
        assertThat(container.getEnvironmentVariables(),
                not(either(IsMapContaining.hasKey(secretEnvName))
                        .or(IsMapContaining.hasValue(systemPropertyValue))));
    }
}
