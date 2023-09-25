package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.containers.Image;
import org.nrg.xnat.versions.Xnat_1_7_7;
import org.testng.annotations.Test;

import java.util.List;

import static org.nrg.testing.TestGroups.CONTAINERS;
import static org.testng.AssertJUnit.*;

@TestRequires(plugins = PluginRegistry.CS_PLUGIN_ID)
@AddedIn(Xnat_1_7_7.class) // Pending CS-600
@Test(groups = CONTAINERS)
public class TestContainerServiceAdmin extends BaseXnatRestTest {

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
        setServerBackend(Backend.KUBERNETES);
        mainAdminInterface().deleteAllCommands();
        assertDebugCommands(0);
        mainAdminInterface().addCommand(getDataFile("debug_command.json"));
        assertDebugCommands(1);
        final List<Image> imagesWithCommands = mainAdminInterface().readImages(ContainerTestUtils.IMAGES_WITH_COMMANDS_JSON_PATH);
        assertEquals(1, imagesWithCommands.size());
        mainAdminInterface().deleteCommand(imagesWithCommands.get(0).getCommands().get(0));
        assertDebugCommands(0);
    }

    @Test
    @TestRequires(supportedContainerBackends = {Backend.SWARM})
    public void testEnableSwarmMode() {
        setServerBackend(Backend.SWARM);
        assertTrue(mainInterface().readDockerServer().getSwarmMode());
    }

    @Test
    @TestRequires(supportedContainerBackends = {Backend.DOCKER})
    public void testDisableSwarmMode() {
        setServerBackend(Backend.DOCKER);
        assertFalse(mainInterface().readDockerServer().getSwarmMode());
    }

    private void runDockerlikeManagementTest(Backend backend) {
        setServerBackend(backend);
        ContainerTestUtils.deleteAllImagesWithCommands(this);
        assertDebugCommands(0);
        ContainerTestUtils.pullDebugImage(this);
        assertDebugCommands(1);
        final List<Image> imagesWithCommands = mainAdminInterface().readImages(ContainerTestUtils.IMAGES_WITH_COMMANDS_JSON_PATH);
        assertEquals(1, imagesWithCommands.size());
        mainAdminInterface().deleteImage(imagesWithCommands.get(0));
        assertDebugCommands(0);
    }

    private void setServerBackend(Backend backend) {
        ContainerTestUtils.setServerBackend(this, backend);
    }

    private void assertDebugCommands(int numCommands) {
        assertEquals(numCommands, mainAdminInterface().readCommands(ContainerTestUtils.DEBUG_IMG).size());
    }

}
