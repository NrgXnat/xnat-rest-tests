package org.nrg.testing.xnat.containers;

import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.containers.Command;
import org.nrg.xnat.pogo.containers.DockerServer;
import org.nrg.xnat.pogo.containers.Image;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_8_0;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

public class ContainerTestUtils {
    public static final Image DEBUG_IMG = new Image("xnat", "debug-command",
            XnatTestingVersionManager.testedVersionPrecedes(Xnat_1_8_0.class) ? "1.5" : "latest");
    public static final String IMAGES_WITH_COMMANDS_JSON_PATH = "findAll { it.commands.size() > 0 }";
    public static final String DEBUG_COMMAND_LINE_INPUT_NAME = "command";
    public static final String DEBUG_OUTPUT_FILE_INPUT_NAME = "output-file";
    public static final String DEBUG_OUTPUT_RESOURCE_NAME = "DEBUG_OUTPUT";

    public static void setServerBackend(BaseXnatRestTest testClassInstance, Backend backend, XnatInterface xnatInterface) {
        final DockerServer dockerServer = xnatInterface.readDockerServer();
        dockerServer.setBackend(backend);
        if (backend == Backend.SWARM && !Settings.swarmConstraints().isEmpty()) {
            dockerServer.setSwarmConstraints(Settings.swarmConstraints());
        } else {
            dockerServer.setSwarmConstraints(Collections.emptyList());
        }
        xnatInterface.updateDockerServer(dockerServer);
    }

    public static void pullDebugImage(BaseXnatRestTest testClassInstance, XnatInterface xnatInterface) {
        xnatInterface.pullImage(DEBUG_IMG);
    }

    public static void deleteAllImagesWithCommands(BaseXnatRestTest test, XnatInterface xnatInterface) {
        xnatInterface.readImages(IMAGES_WITH_COMMANDS_JSON_PATH)
                .stream()
                .filter(Objects::nonNull)
                .forEach(xnatInterface::deleteImage);
    }

    public static void installFreshImageIfNecessary(BaseXnatRestTest test, Image testImage, Backend backend, XnatInterface xnatInterface) {
        for (Command command : xnatInterface.readCommands(testImage)) {
            xnatInterface.deleteCommand(command);
        }
        if (backend == Backend.DOCKER) {
            xnatInterface.pullImage(testImage, false); // add commands explicitly for both docker and k8s in next step
        }
    }


}
