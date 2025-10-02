package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.DeprecatedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.containers.Image;
import org.nrg.xnat.versions.Xnat_1_7_7;
import org.nrg.xnat.versions.Xnat_1_9_2;
import org.testng.annotations.Test;

import java.util.List;

import static org.nrg.testing.TestGroups.CONTAINERS;
import static org.testng.AssertJUnit.*;

@AddedIn(Xnat_1_7_7.class) // Pending CS-600
@Test(groups = CONTAINERS)
@TestRequires(plugins = PluginRegistry.CS_PLUGIN_ID)
public class TestContainerServiceAdmin  extends BaseContainerTest  {

    @Test
    @TestRequires(supportedContainerBackends = Backend.DOCKER)
    public void testContainerImageManagementDocker() {
        runDockerlikeManagementTest(Backend.DOCKER);
    }

    @Test
    @TestRequires(supportedContainerBackends = Backend.SWARM)
    public void testContainerImageManagementSwarm() {
        runDockerlikeManagementTest(Backend.SWARM);
    }

    @Test
    @TestRequires(supportedContainerBackends = Backend.KUBERNETES)
    public void testContainerImageManagementKubernetes() {
        setServerBackend(Backend.KUBERNETES, containerManagerInterface);
        containerManagerInterface.deleteAllCommands();
        assertDebugCommands(0);
        containerManagerInterface.addCommand(getDataFile("debug_command.json"));
        assertDebugCommands(1);
        final List<Image> imagesWithCommands = containerManagerInterface.readImages(ContainerTestUtils.IMAGES_WITH_COMMANDS_JSON_PATH);
        assertEquals(1, imagesWithCommands.size());
        containerManagerInterface.deleteCommand(imagesWithCommands.get(0).getCommands().get(0));
        assertDebugCommands(0);
    }

    @Test
    @TestRequires(supportedContainerBackends = {Backend.SWARM})
    public void testEnableSwarmMode() {
        setServerBackend(Backend.SWARM, containerManagerInterface);
        assertTrue(containerManagerInterface.readDockerServer().getSwarmMode());
    }

    @Test
    @TestRequires(supportedContainerBackends = {Backend.DOCKER})
    public void testDisableSwarmMode() {
        setServerBackend(Backend.DOCKER, containerManagerInterface);
        assertFalse(containerManagerInterface.readDockerServer().getSwarmMode());
    }

    private void runDockerlikeManagementTest(Backend backend) {
        setServerBackend(backend, containerManagerInterface);
        ContainerTestUtils.deleteAllImagesWithCommands(this, containerManagerInterface);
        assertDebugCommands(0);
        ContainerTestUtils.pullDebugImage(this, containerManagerInterface);
        assertDebugCommands(1);
        final List<Image> imagesWithCommands = containerManagerInterface.readImages(ContainerTestUtils.IMAGES_WITH_COMMANDS_JSON_PATH);
        assertEquals(1, imagesWithCommands.size());
        containerManagerInterface.deleteImage(imagesWithCommands.get(0));
        assertDebugCommands(0);
    }

    private void setServerBackend(Backend backend, XnatInterface xnatInterface) {
        ContainerTestUtils.setServerBackend(this, backend, xnatInterface);
    }

    private void assertDebugCommands(int numCommands) {
        assertEquals(numCommands, containerManagerInterface.readCommands(ContainerTestUtils.DEBUG_IMG).size());
    }

}
