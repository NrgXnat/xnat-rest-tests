package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.containers.Command;
import org.nrg.xnat.pogo.containers.Wrapper;
import org.testng.annotations.Test;

public class TestContainerServiceCommands extends BaseXnatRestTest {

    @Test
    @TestRequires(specificPluginRequirements = {
            @PluginRequirement(pluginId = PluginRegistry.CS_PLUGIN_ID, minimumSupportedVersion = "3.4.1") // see CS-944
    })
    public void testCommandPreresolutionMultipleValuesForDerivedInput() {
        final String commandAndWrapperName = "debug-fork-derived-input";
        final Project project = registerTempProject();
        final Subject subject1 = new Subject(project);
        final Subject subject2 = new Subject(project);
        mainInterface().createProject(project);

        ContainerTestUtils.setServerBackend(this, Settings.CS_PREFERRED_BACKEND);
        ContainerTestUtils.installFreshImageIfNecessary(this, ContainerTestUtils.DEBUG_IMG, Settings.CS_PREFERRED_BACKEND);
        mainAdminInterface().addCommand(getDataFile("debug_command_derived_input.json"));

        final Wrapper testedWrapper = mainAdminInterface()
                .readCommands(ContainerTestUtils.DEBUG_IMG)
                .stream()
                .filter(command -> command.getName().equals(commandAndWrapperName))
                .findFirst()
                .map(Command::getWrappers)
                .orElseThrow(RuntimeException::new)
                .stream()
                .filter(wrapper -> wrapper.getName().equals(commandAndWrapperName))
                .findFirst()
                .orElseThrow(RuntimeException::new);

        mainAdminInterface().setWrapperStatusOnSite(testedWrapper, true);
        mainAdminInterface().setWrapperStatusOnProject(testedWrapper, project, true);
        mainQueryBase()
                .queryParam("format", "json")
                .queryParam("project", project.getId())
                .get(formatXapiUrl("projects", project.getId(), "wrappers", String.valueOf(testedWrapper.getId()), "launch"))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(
                        "input-values.find { it.name = \"project\" }.values[0].children[0].values.label",
                        Matchers.containsInAnyOrder(subject1.getLabel(), subject2.getLabel())
                );
    }

}
