package org.nrg.testing.xnat.tests;

import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.containers.Command;
import org.nrg.xnat.pogo.containers.CommandSummaryForContext;
import org.nrg.xnat.pogo.containers.Image;
import org.nrg.xnat.pogo.containers.Orchestration;
import org.nrg.xnat.pogo.containers.OrchestrationProject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.versions.Xnat_1_8_2;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.nrg.testing.TestGroups.*;

@TestRequires(plugins = {"containers", "batchLaunchPlugin"})
@AddedIn(Xnat_1_8_2.class)
@Test(groups = {CONTAINERS, ORCHESTRATION})
public class TestContainerOrchestration extends BaseXnatRestTest {
    private static final Image DEBUG_IMG = new Image("xnat", "debug-command", "latest");
    private static final String DEBUG_WRAPPER_NAME = "debug-session";
    private static final Image ALT_IMG = new Image("xnat", "generate-test-qc-assessor", "latest");
    private static final String ALT_WRAPPER_NAME = "generate-test-qc-assessor-from-session";
    private static final String IMAGES_WITH_COMMANDS_JSON_PATH = "findAll { it.commands.size() > 0 }";

    private Project project;
    private Subject subject;
    private ImagingSession session;
    private List<CommandSummaryForContext> wrapperSummaries;

    @BeforeClass
    private void setup() {
        ContainerTestUtils.setServerBackend(this, Settings.CS_PREFERRED_BACKEND);
    }

    @BeforeMethod
    private void setupTest() {
        // setup objects
        project  = testSpecificProject;
        subject  = new Subject(project, "S1").gender(Gender.MALE);
        session  = new MRSession(project, subject, "MR1").date(LocalDate.parse("2000-01-01"));

        mainInterface().createProject(project);
        TimeUtils.sleep(1000); // cache update

        // Sets accession number on subject, session, and assessor
        mainInterface().getAccessionNumber(subject);
        mainInterface().getAccessionNumber(session);

        // Add images and commands
        deleteAllImages();
        ContainerTestUtils.installFreshImageIfNecessary(this, DEBUG_IMG, Settings.CS_PREFERRED_BACKEND);
        ContainerTestUtils.installFreshImageIfNecessary(this, ALT_IMG, Settings.CS_PREFERRED_BACKEND);
        mainAdminInterface().addCommand(getDataFile("debug_command.json"));
        mainAdminInterface().addCommand(getDataFile("sample_qc_assessor.json"));

        // Enable on site
        List<CommandSummaryForContext> wrapperSummaries = mainInterface().readAvailableCommands(DataType.MR_SESSION);
        for (CommandSummaryForContext summary : wrapperSummaries) {
            mainAdminInterface().setWrapperStatusOnSite(summary.getWrapperId(), true);
        }

        // Enable on project
        enableWrappersOnProject();
    }

    @AfterMethod
    private void removeContainerServiceProjects() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @AfterMethod
    private void deleteAllImages() {
        final List<Image> imagesWithCommands = mainAdminInterface().readImages(IMAGES_WITH_COMMANDS_JSON_PATH);

        for (Image image : imagesWithCommands) {
            for (Command command : image.getCommands()) {
                mainAdminInterface().deleteCommand(command);
            }
            mainAdminInterface().deleteImage(image, true);
        }
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationSession() {
        setProjectOrchestration(setupOrchestration());
        final int workflowId = mainInterface().launchContainer(project, findSummary(DEBUG_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);
        final int nextWorkflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, session.getAccessionNumber(), findSummary(ALT_WRAPPER_NAME));
        mainInterface().waitForWorkflowComplete(nextWorkflowId, 60 * 5);
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationSessionFailure() {
        // Setup orchestration ensuring debug-session is first
        setProjectOrchestration(setupOrchestration());

        // Ensure the workflow fails
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("command", "exit 1");

        final int workflowId = mainInterface().launchContainer(project, findSummary(DEBUG_WRAPPER_NAME), session.getUri(), queryParams);
        mainInterface().waitForWorkflowFailed(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, ALT_WRAPPER_NAME);
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationSessionReverseOrder() {
        final Orchestration orchestration = setupOrchestration();
        setProjectOrchestration(orchestration);
        Collections.reverse(orchestration.getWrapperIds());
        mainAdminInterface().createOrUpdateOrchestration(orchestration);

        final int workflowId = mainInterface().launchContainer(project, findSummary(ALT_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);
        final int nextWorkflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, session.getAccessionNumber(), findSummary(DEBUG_WRAPPER_NAME));
        mainInterface().waitForWorkflowComplete(nextWorkflowId, 60 * 5);
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationDisable() {
        final Orchestration orchestration = setupOrchestration();
        setProjectOrchestration(orchestration);
        mainAdminInterface().enableOrDisableOrchestration(orchestration, false);

        assertNull(mainInterface().getProjectOrchestrationConfig(project).getSelectedOrchestrationId());

        final int workflowId = mainInterface().launchContainer(project, findSummary(DEBUG_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, ALT_WRAPPER_NAME);
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationDisableThruCommandProject() {
        setProjectOrchestration(setupOrchestration());
        mainInterface().setWrapperStatusOnProject(findSummary(DEBUG_WRAPPER_NAME), project, false);

        assertNull(mainInterface().getProjectOrchestrationConfig(project).getSelectedOrchestrationId());

        final int workflowId = mainInterface().launchContainer(project, findSummary(DEBUG_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, ALT_WRAPPER_NAME);
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationDisableThruCommandSite() {
        setProjectOrchestration(setupOrchestration());
        mainAdminInterface().setWrapperStatusOnSite(findSummary(DEBUG_WRAPPER_NAME), project, false);

        assertNull(mainInterface().getProjectOrchestrationConfig(project).getSelectedOrchestrationId());

        final int workflowId = mainInterface().launchContainer(project, findSummary(DEBUG_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, ALT_WRAPPER_NAME);
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationDelete() {
        final Orchestration orchestration = setupOrchestration();
        setProjectOrchestration(orchestration);
        mainAdminInterface().deleteOrchestration(orchestration);

        final int workflowId = mainInterface().launchContainer(project, findSummary(DEBUG_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, ALT_WRAPPER_NAME);
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationNotSetupOnProject() {
        setupOrchestration();

        final int workflowId = mainInterface().launchContainer(project, findSummary(DEBUG_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, ALT_WRAPPER_NAME);
    }

    @Test(groups = WORKFLOWS)
    public void testOrchestrationRemovedFromProject() {
        setProjectOrchestration(setupOrchestration());
        removeProjectOrchestration();

        final int workflowId = mainInterface().launchContainer(project, findSummary(DEBUG_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, ALT_WRAPPER_NAME);
    }

    @Test(groups = WORKFLOWS) // tests CS-663
    public void testOrchestrationOverwrite() {
        setProjectOrchestration(setupOrchestration());
        setProjectOrchestration(setupOrchestrationWithName("second-orchestration", ALT_WRAPPER_NAME, DEBUG_WRAPPER_NAME));
        final int workflowId = mainInterface().launchContainer(project, findSummary(ALT_WRAPPER_NAME), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);
        final int nextWorkflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, session.getAccessionNumber(), findSummary(DEBUG_WRAPPER_NAME));
        mainInterface().waitForWorkflowComplete(nextWorkflowId, 60 * 5);
    }

    @Test
    public void testProjectOrchestrationConfig() {
        final Orchestration o = setupOrchestration();
        OrchestrationProject op = mainInterface().getProjectOrchestrationConfig(project);
        assertEquals(op.getAvailableOrchestrations().size(), 1);
        assertEquals(o, op.getAvailableOrchestrations().get(0));
        assertNull(op.getSelectedOrchestrationId());

        setProjectOrchestration(o);
        op = mainInterface().getProjectOrchestrationConfig(project);
        assertEquals(op.getAvailableOrchestrations().size(), 1);
        assertEquals(o, op.getAvailableOrchestrations().get(0));
        assertEquals((Long) o.getId(), op.getSelectedOrchestrationId());

        removeProjectOrchestration();
        op = mainInterface().getProjectOrchestrationConfig(project);
        assertEquals(op.getAvailableOrchestrations().size(), 1);
        assertEquals(o, op.getAvailableOrchestrations().get(0));
        assertNull(op.getSelectedOrchestrationId());
    }

    @Test(groups = WORKFLOWS)
    public void testContainerSessionBulk() {
        // Setup another session
        final MRSession session2 = new MRSession(project, subject, "MR2").date(LocalDate.parse("2000-01-02"));
        mainInterface().createSubjectAssessor(session2);
        mainInterface().getAccessionNumber(session2);
        final List<ImagingSession> sessions = Arrays.asList(session, session2);

        setProjectOrchestration(setupOrchestration());

        mainInterface().bulkLaunchContainers(project, findSummary(DEBUG_WRAPPER_NAME),
                sessions.stream().map(SubjectAssessor::getUri).collect(Collectors.toList()));

        // Determine workflow ID, wait for complete
        for (ImagingSession ses : sessions) {
            final int workflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, ses.getAccessionNumber(), findSummary(DEBUG_WRAPPER_NAME));
            mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);
        }

        // Determine workflow ID for next command, wait for complete
        for (ImagingSession ses : sessions) {
            final int workflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, ses.getAccessionNumber(), findSummary(ALT_WRAPPER_NAME));
            mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);
        }
    }

    private void enableWrappersOnProject() {
        wrapperSummaries = mainInterface().readAvailableCommands(DataType.MR_SESSION, project);
        for (CommandSummaryForContext summary : wrapperSummaries) {
            mainInterface().setWrapperStatusOnProject(summary, project, true);
        }
    }

    private Orchestration setupOrchestration() {
        return setupOrchestration(DEBUG_WRAPPER_NAME, ALT_WRAPPER_NAME);
    }

    private Orchestration setupOrchestration(String... wrapperNames) {
        return setupOrchestrationWithName("test", wrapperNames);
    }

    private Orchestration setupOrchestrationWithName(String orchestrationName, String... wrapperNames) {
        return mainAdminInterface().createOrUpdateOrchestration(
                new Orchestration(orchestrationName,
                        Arrays.stream(wrapperNames).map(name -> findSummary(name).getWrapperId()).collect(Collectors.toList())
                )
        );
    }

    private void setProjectOrchestration(Orchestration orchestration) {
        mainInterface().setProjectOrchestration(project, orchestration);
    }

    private void removeProjectOrchestration() {
        mainInterface().removeProjectOrchestration(project);
    }

    private CommandSummaryForContext findSummary(String wrapperName) {
        return wrapperSummaries.stream().filter(summary -> wrapperName.equals(summary.getWrapperName())).findFirst().orElse(null);
    }

}
