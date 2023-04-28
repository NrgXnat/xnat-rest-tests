package org.nrg.testing.xnat.tests;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.runners.Parameterized;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.conf.XNATProperties;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.xnat.pogo.Workflow;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.containers.ContainerLogPollResponse;
import org.nrg.xnat.subinterfaces.ContainerServiceSubinterface;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.http.ContentType.JSON;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.nrg.testing.TestGroups.CONTAINERS;

@Slf4j
@TestRequires(plugins = "containers")
@Test(groups = CONTAINERS, dataProvider = "backend")
public class TestContainerLogs extends BaseXnatRestTest {

    public static final String LOG_SUFFIX = ".log";

    private void deleteCommands() {
        // Clean up all commands
        mainAdminInterface().deleteAllCommands();
    }

    private String createCommandWhichRunsScriptAndReturnLaunchUri(final String commandAndWrapperName,
                                                                  final String rootElement,
                                                                  final String script) {
        // Define command
        final String command = "{" +
                "\"image\": \"busybox:latest\", " +
                "\"name\": \"" + commandAndWrapperName + "\", " +
                "\"command-line\": \"/bin/sh -c '" + script + "'\", " +
                "\"xnat\": [{" +
                    "\"name\": \"" + commandAndWrapperName + "\", " +
                    "\"contexts\": [\"" + rootElement + "\"]" +
                "}]" +
                "}";
        // Create command
        final int commandId = mainAdminInterface().addCommand(command);

        // Construct launch URI
        return formatXapiUrl("commands", String.valueOf(commandId), "wrappers", commandAndWrapperName, "root", rootElement, "launch");
    }

    @BeforeClass
    private void setup() {
        deleteCommands();
    }

    @BeforeMethod
    private void before(Object[] backendHolder) {
        if (backendHolder != null && backendHolder.length > 0) {
            final Backend backend = (Backend) backendHolder[0];
            try {
                log.info("Setting backend {}", backend);
                ContainerTestUtils.setServerBackend(this, backend);
            } catch (Throwable e) {
                log.error("Couldn't set backend");
                throw new SkipException("Couldn't set backend " + backend.name());
            }

            if (!mainAdminInterface().readDockerServer().getPing()) {
                log.error("Setting backend failed. Could not ping.");
                throw new SkipException("Setting backend failed. Could not ping.");
            }
        }
    }

    @AfterClass
    private void cleanup() {
        deleteCommands();
    }

    @DataProvider
    public static Object[][] backend() {
        final List<Backend[]> backends = new ArrayList<>();
        backends.add(new Backend[] {Backend.DOCKER});
        if (Settings.getBooleanProperty(XNATProperties.CS_SWARM_CAN_ENABLE, false)) {
            backends.add(new Backend[] {Backend.SWARM});
        }
        if (Settings.getBooleanProperty("cs.k8s.canEnable", false)) {
            backends.add(new Backend[] {Backend.KUBERNETES});
        }
        return backends.toArray(new Object[backends.size()][1] );
    }

    public void testAfterContainerFinishesFetchLogs(final Backend backend) throws IOException {
        testAfterContainerFinishesFetchLogs(backend != Backend.KUBERNETES);
    }
    private void testAfterContainerFinishesFetchLogs(final boolean expectSplitStdoutStderr) throws IOException {
        final String name = RandomStringUtils.randomAlphabetic(5);
        final String rootElement = "site";

        final String stdout = "Hello world " + RandomStringUtils.randomAlphabetic(10);
        final String stderr = "A different kind of log " + RandomStringUtils.randomAlphabetic(10);

        final String expectedStdoutMessage = stdout + (expectSplitStdoutStderr ? "" : "\n" + stderr) + "\n\n";
        final String expectedStderrMessage = expectSplitStdoutStderr ? stderr + "\n\n" : "";

        final String script = "echo " + stdout + "; sleep 1; echo " + stderr + " >&2";

        // Create command
        final String launchUri = createCommandWhichRunsScriptAndReturnLaunchUri(name, rootElement, script);

        // Launch container
        final int workflowId = mainQueryBase()
                .contentType(JSON)
                .body("{\"" + rootElement + "\": \"" + rootElement + "\"}")  // BS non-useful param
                .post(launchUri)
                .then()
                .assertThat()
                .statusCode(200)
                .body("status", Matchers.equalTo("success"))
                .extract()
                .jsonPath()
                .getInt("workflow-id");

        // Wait for container to finish successfully
        mainAdminInterface().waitForWorkflowComplete(workflowId, 20);

        // Container id from workflow comments
        final Workflow workflow = mainAdminInterface().readWorkflow(workflowId);
        final String containerId = workflow.getComments();

        // Ensure container is finalized and it has a log file of the type we're looking for
        await().atMost(2, TimeUnit.SECONDS)
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .until(() -> mainInterface().getContainer(containerId)
                        .getLogPaths()
                        .size() == (expectSplitStdoutStderr ? 2 : 1)
                );

        // Use the "logSince" API to get stdout
        final ContainerLogPollResponse actualStdoutResponse = mainInterface().pollContainerLog(containerId, ContainerServiceSubinterface.ContainerLog.STDOUT);

        // Assert
        assertThat(actualStdoutResponse.getFromFile(), is(true));
        assertThat(actualStdoutResponse.getTimestamp(), is(ContainerLogPollResponse.LOG_COMPLETE_TIMESTAMP));
        assertThat(actualStdoutResponse.getContent(), is(expectedStdoutMessage));

        // Use the "logSince" API to get stdout
        final ContainerLogPollResponse actualStderrResponse = mainInterface().pollContainerLog(containerId, ContainerServiceSubinterface.ContainerLog.STDERR);

        // Assert
        assertThat(actualStderrResponse.getFromFile(), is(expectSplitStdoutStderr));
        assertThat(actualStderrResponse.getTimestamp(), is(ContainerLogPollResponse.LOG_COMPLETE_TIMESTAMP));
        assertThat(actualStderrResponse.getContent(), is(expectedStderrMessage));

        // Pull the zip from the API
        try (final ZipInputStream zipStream = mainInterface().getContainerLogZip(containerId)) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                final String logFileName = entry.getName();
                assertThat(logFileName, endsWith(LOG_SUFFIX));
                final String logName = logFileName.replace(LOG_SUFFIX, "");
                final String expectedEntryContents = StringUtils.equals(logName.toLowerCase(), ContainerServiceSubinterface.ContainerLog.STDOUT.name().toLowerCase()) ?
                        expectedStdoutMessage : expectedStderrMessage;

                final ByteArrayOutputStream entryContentsStream = new ByteArrayOutputStream();
                IOUtils.copy(zipStream, entryContentsStream);
                final String entryContents = entryContentsStream.toString();
                assertThat(entryContents, is(expectedEntryContents));
            }
        }
    }

    @TestRequires(specificPluginRequirements = {
            @PluginRequirement(pluginId = "containers", minimumSupportedVersion = "3.3.2")
    })
    public void testLogsFromLiveContainer(final Backend ignored) {
        runTestLogsFromLiveContainer(false);
    }

    // CS versions prior to 3.3.2 added an extra newline at the beginning of the log messages when polling
    @TestRequires(specificPluginRequirements = {
            @PluginRequirement(pluginId = "containers", maximumSupportedVersion = "3.3.2")
    })
    public void testLogsFromLiveContainer_pre332(final Backend ignored) {
        runTestLogsFromLiveContainer(true);
    }

    private void runTestLogsFromLiveContainer(final boolean addInitialNewline) {
        final ContainerServiceSubinterface.ContainerLog logType = ContainerServiceSubinterface.ContainerLog.STDOUT;
        final String name = RandomStringUtils.randomAlphabetic(5);
        final String rootElement = "site";

        // Produce a log message, then wait, then produce another
        final String initialLogMessage = "Starting logs " + RandomStringUtils.randomAlphabetic(10);
        final String finalLogMessage = "We did it fam " + RandomStringUtils.randomAlphabetic(10);
        final int logMessageDelaySeconds = 10;
        final String script = "echo " + initialLogMessage + "; sleep " + logMessageDelaySeconds + "; echo " + finalLogMessage;

        final String expectedInitialLogMessage = (addInitialNewline ? "\n" : "") + initialLogMessage + "\n";
        final String expectedFinalLogMessage = (addInitialNewline ? "\n" : "") + finalLogMessage + "\n";

        // Create command
        final String launchUri = createCommandWhichRunsScriptAndReturnLaunchUri(name, rootElement, script);

        // Launch container
        final int workflowId = mainQueryBase()
                .contentType(JSON)
                .body("{\"" + rootElement + "\": \"" + rootElement + "\"}")  // BS non-useful param
                .post(launchUri)
                .then()
                .assertThat()
                .statusCode(200)
                .body("status", Matchers.equalTo("success"))
                .extract()
                .jsonPath()
                .getInt("workflow-id");

        // Container id from workflow comments
        final String[] containerIdHolder = {null};
        await().atMost(logMessageDelaySeconds, TimeUnit.SECONDS)
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    containerIdHolder[0] = mainAdminInterface().readWorkflow(workflowId).getComments();
                    return StringUtils.isNotBlank(containerIdHolder[0]);
                });
        final String containerId = containerIdHolder[0];

        // Use the "logSince" API
        final ContainerLogPollResponse[] initialLogResponseHolder = {null};
        await().atMost(logMessageDelaySeconds, TimeUnit.SECONDS)
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    initialLogResponseHolder[0] = mainInterface().pollContainerLog(containerId, logType);
                    return StringUtils.isNotBlank(initialLogResponseHolder[0].getContent());
                });
        final ContainerLogPollResponse initialLogResponse = initialLogResponseHolder[0];

        // Assert
        final long since = initialLogResponse.getTimestamp();
        assertThat(initialLogResponse.getFromFile(), is(false));
        assertThat(since, is(not(ContainerLogPollResponse.LOG_COMPLETE_TIMESTAMP)));
        assertThat(initialLogResponse.getContent(), is(expectedInitialLogMessage));

        // Use the "logSince" API again, this time only getting logs newer than what we had before
        final ContainerLogPollResponse[] finalLogResponseHolder = {null};
        await().atMost(3*logMessageDelaySeconds, TimeUnit.SECONDS)
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    finalLogResponseHolder[0] = mainInterface().pollContainerLog(containerId, logType, since);
                    return StringUtils.isNotBlank(finalLogResponseHolder[0].getContent());
                });
        final ContainerLogPollResponse finalLogResponse = finalLogResponseHolder[0];

        // Assert
        assertThat(finalLogResponse.getContent(), is(expectedFinalLogMessage));
    }

    @TestRequires(specificPluginRequirements = {
            @PluginRequirement(pluginId = "containers", minimumSupportedVersion = "3.3.2")
    })
    public void testGetNothingWhenContainerLogsNothing(final Backend ignored) {
        final ContainerServiceSubinterface.ContainerLog logType = ContainerServiceSubinterface.ContainerLog.STDOUT;
        final String name = RandomStringUtils.randomAlphabetic(5);
        final String rootElement = "site";

        // Exit successfully without putting anything into stdout or stderr
        final String script = "exit 0";

        // Create command
        final String launchUri = createCommandWhichRunsScriptAndReturnLaunchUri(name, rootElement, script);

        // Launch container
        final int workflowId = mainQueryBase()
                .contentType(JSON)
                .body("{\"" + rootElement + "\": \"" + rootElement + "\"}")  // BS non-useful param
                .post(launchUri)
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

        // Use the "logSince" API
        final ContainerLogPollResponse logResponse = mainInterface().pollContainerLog(containerId, logType);

        // Assert
        assertThat(logResponse.getFromFile(), is(false));
        assertThat(logResponse.getTimestamp(), is(ContainerLogPollResponse.LOG_COMPLETE_TIMESTAMP));
        assertThat(logResponse.getContent(), is(emptyOrNullString()));
    }
}
