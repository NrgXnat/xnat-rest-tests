package org.nrg.testing.xnat.tests;

import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.testing.xnat.processing.files.resources.GenericResource;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.containers.CommandSummaryForContext;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.Project;

import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public class ContainerTest {
    public static final String OUTPUT_CONTENT = "hello world";
    public static final String OUTPUT_FILENAME = "out.txt";
    public static final Map<String, String> BASE_DEBUG_LAUNCH_PARAMS = makeContainerLaunchReqBody();
    public static final Map<Backend, Integer> MAX_TIMEOUTS_IN_SECONDS = makeMaxTimeouts();


    String uri;
    Map<String, String> urisAndIds;

    ContainerTest uri(String uri) {
        this.uri = uri;
        return this;
    }

    ContainerTest urisAndIds(Map<String, String> urisAndIds) {
        this.urisAndIds = urisAndIds;
        return this;
    }

    public void run(XnatInterface userInterface, Project project, DataType dataType, Backend backend) {
        final CommandSummaryForContext wrapper = userInterface.readAvailableCommands(dataType, project).get(0);
        userInterface.setWrapperStatusOnProject(wrapper, project, true);
        if (urisAndIds != null) {
            userInterface.bulkLaunchContainers(project, wrapper, urisAndIds.keySet(), BASE_DEBUG_LAUNCH_PARAMS);
            // Determine workflow ID, wait for complete, verify outputs
            for (Map.Entry<String, String> uriAndId : urisAndIds.entrySet()) {
                final int workflowId = userInterface.determineWorkflowId(dataType, uriAndId.getValue(), wrapper);
                waitForWorkflowComplete(userInterface,workflowId, backend);
                verifyOutputs(userInterface, uriAndId.getKey());
            }
        } else {
            final int workflowId = userInterface.launchContainer(project, wrapper, uri, BASE_DEBUG_LAUNCH_PARAMS);
            waitForWorkflowComplete(userInterface, workflowId, backend);
            verifyOutputs(userInterface,uri);
        }
    }

    public void waitForWorkflowComplete(XnatInterface userInterface, int workflowId, Backend backend) {
        userInterface.waitForWorkflowComplete(workflowId, 60 * MAX_TIMEOUTS_IN_SECONDS.get(backend));
    }

    public void verifyOutputs(XnatInterface userInterface, String uri) {
        final Resource resource = new GenericResource("/data" + uri).folder(ContainerTestUtils.DEBUG_OUTPUT_RESOURCE_NAME);
        assertEquals(1, userInterface.readResourceFiles(resource).size());
        final ResourceFile resourceFile = resource.getResourceFiles().get(0);
        assertEquals(OUTPUT_FILENAME, resourceFile.getName());
        assertEquals(
                OUTPUT_CONTENT,
                userInterface.readResourceFile(resource, resourceFile).trim()
        );
    }

    private static Map<String, String> makeContainerLaunchReqBody() {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(ContainerTestUtils.DEBUG_COMMAND_LINE_INPUT_NAME, "echo " + ContainerTest.OUTPUT_CONTENT);
        queryParams.put(ContainerTestUtils.DEBUG_OUTPUT_FILE_INPUT_NAME, ContainerTest.OUTPUT_FILENAME);
        return queryParams;
    }

    private static Map<Backend, Integer> makeMaxTimeouts() {
        final Map<Backend, Integer> timeouts = new HashMap<>();
        timeouts.put(Backend.DOCKER, 5);
        timeouts.put(Backend.SWARM, Settings.CS_SWARM_TIMEOUT);
        timeouts.put(Backend.KUBERNETES, 10); // TODO: eh?
        return timeouts;
    }

}
