package org.nrg.testing.xnat.tests;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.containers.Command;
import org.nrg.xnat.pogo.containers.CommandSummaryForContext;
import org.nrg.xnat.pogo.containers.DockerServer;
import org.nrg.xnat.pogo.containers.Wrapper;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.SubjectAssessorResource;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.reporters.Files;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.nrg.testing.TestGroups.CONTAINERS;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;

@TestRequires(plugins = CONTAINERS, supportedContainerBackends = Backend.KUBERNETES)
@AddedIn(Xnat_1_8_5.class)
@Test(groups = CONTAINERS)
public class TestContainerServiceKubernetes extends BaseXnatRestTest {
    private static final Logger LOGGER = Logger.getLogger(TestContainerServiceKubernetes.class);

    private static final String DEBUG_INPUT_MOUNT_PATH = "/input";
    private static final String TEST_RESOURCE_FILE_NAME = "dummy.txt";
    private static final String INPUT_RESOURCE_LABEL = RandomStringUtils.randomAlphabetic(5);

    private DockerServer preExistingServerSettings;
    private File testFile;
    private Command debug;
    private Project project;
    private String inputAccessionNumber;
    private Resource dummyOutputResource;

    @BeforeClass
    public void classSetup() {
        LOGGER.info("Class setup");

        preExistingServerSettings = mainAdminInterface().readDockerServer();
        setServerToKubernetesMode();

        // Delete all images + commands
        ContainerTestUtils.deleteAllImagesWithCommands(this);

        ContainerTestUtils.installFreshImageIfNecessary(this, ContainerTestUtils.DEBUG_IMG, Backend.KUBERNETES);
        mainAdminInterface().addCommand(getDataFile("debug_command.json"));

        debug = mainInterface().readCommands(ContainerTestUtils.DEBUG_IMG).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find debug command"));

        // Set up model objects
        project = registerTempProject();
        final Subject subject  = new Subject(project, "S1");
        final MRSession session = new MRSession(project, subject, "session");

        // Create session resource containing test resource file
        testFile = getDataFile(TEST_RESOURCE_FILE_NAME);
        final Resource resource = new SubjectAssessorResource(project, subject, session, INPUT_RESOURCE_LABEL).addResourceFile(
                new ResourceFile()
                        .name(testFile.getName())
                        .extension(new SimpleResourceFileExtension(testFile))
        );
        // Store this for later. This is basically a method parameter used for searching for resources.
        dummyOutputResource = new SubjectAssessorResource().project(project).subject(subject).subjectAssessor(session);

        // Create project + objects underneath
        mainInterface().createProject(project);

        // Enable debug command wrappers on project
        enableCommandWrappersOnProject(debug, project);

        // Find accession number
        inputAccessionNumber = mainInterface().getAccessionNumber(session);

        // Ensure input resources were created
        final Resource inputResource = mainInterface().findResource(session.getResources(), INPUT_RESOURCE_LABEL);
        assertNotNull("Cannot find input resource", inputResource);
        assertEquals("Input resource does not have file", 1, inputResource.getFileCount());

    }

    @AfterClass
    public void classCleanup() {
        LOGGER.info("Class cleanup");

        // Delete all images + commands
        ContainerTestUtils.deleteAllImagesWithCommands(this);

        // Revert server settings
        mainAdminInterface().updateDockerServer(preExistingServerSettings);
    }

    private void setServerToKubernetesMode() {
        ContainerTestUtils.setServerBackend(this, Backend.KUBERNETES);
    }

    private void enableCommandWrappersOnProject(final Command command, final Project project) {
        command.getWrappers().stream()
                .map(Wrapper::getId)
                .forEach(wrapperId -> mainInterface().setWrapperStatusOnProject(wrapperId, project, true));
    }

    @Test
    public void testSetServerToKubernetesMode() {
        final DockerServer dockerServer = mainInterface().readDockerServer();
        assertEquals(Backend.KUBERNETES, dockerServer.getBackend());
        assertFalse(dockerServer.getSwarmMode());
    }

    @Test
    public void launchContainerAndCheckOutputs() throws Exception {
        // Find command summary
        final DataType dataType = DataType.MR_SESSION;
        final CommandSummaryForContext debugSummary = mainInterface().readAvailableCommands(dataType, project).stream()
                .filter(commandSummary -> commandSummary.getCommandId() == debug.getId())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find available debug command wrapper on " + dataType));

        // Prepare input args
        final String outputFileName = RandomStringUtils.randomAlphabetic(3) + ".txt";
        final Path mountedResourceFile = Paths.get(DEBUG_INPUT_MOUNT_PATH, "RESOURCES", INPUT_RESOURCE_LABEL, TEST_RESOURCE_FILE_NAME);
        final Map<String, String> otherInputs = Stream.of(
                Pair.of(ContainerTestUtils.DEBUG_COMMAND_LINE_INPUT_NAME,
                        "cat " + mountedResourceFile),
                Pair.of(ContainerTestUtils.DEBUG_OUTPUT_FILE_INPUT_NAME, outputFileName)
        ).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // Launch debug
        final int workflowId = mainInterface().launchContainer(project, debugSummary, inputAccessionNumber, otherInputs);

        // Wait for complete
        mainInterface().waitForWorkflowComplete(workflowId, 60);

        // Read output resource contents
        final List<Resource> resources = mainInterface().readResources(dummyOutputResource);
        final Resource outputResource = mainInterface().findResource(resources, ContainerTestUtils.DEBUG_OUTPUT_RESOURCE_NAME);
        assertNotNull(outputResource);
        assertEquals(1, outputResource.getFileCount());
        final ResourceFile outputResourceFile = outputResource.getResourceFiles().get(0);
        final String outputFileContent = mainInterface().readResourceFile(outputResource, outputResourceFile);

        // Test file contents == resource file contents
        assertEquals(Files.readFile(testFile).trim(), outputFileContent.trim());
    }
}
