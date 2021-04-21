package org.nrg.testing.xnat.tests;

import org.nrg.framework.constants.Scope;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.containers.CommandSummaryForContext;
import org.nrg.xnat.pogo.containers.Image;
import org.nrg.xnat.pogo.containers.Orchestration;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.versions.Xnat_1_8_2;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@TestRequires(plugins = {"containers", "batchLaunchPlugin"})
@AddedIn(Xnat_1_8_2.class)
public class TestContainerOrchestration extends BaseXnatRestTest {
    private static final Image DEBUG_IMG = new Image("xnat", "debug-command", "latest");
    private static final Image ALT_IMG = new Image("xnat", "generate-test-qc-assessor", "latest");
    private static final String IMAGES_WITH_COMMANDS_JSON_PATH = "findAll { it.commands.size() > 0 }";

    private Project project;
    private Subject subject;
    private ImagingSession session;
    private List<CommandSummaryForContext> wrapperSummaries;

    @BeforeMethod
    public void setupTest() {
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
        mainAdminInterface().pullImage(DEBUG_IMG);
        mainAdminInterface().pullImage(ALT_IMG);

        // Enable on site
        List<CommandSummaryForContext> wrapperSummaries = mainInterface().readAvailableCommands(DataType.MR_SESSION);
        for (CommandSummaryForContext summary : wrapperSummaries) {
            mainAdminInterface().setWrapperStatusOnSite(summary.getWrapperId(), true);
        }

        // Enable on project
        enableWrappersOnProject();
    }

    @AfterMethod
    public void removeContainerServiceProjects() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @AfterMethod
    public void deleteAllImages() {
        final List<Image> imagesWithCommands = mainAdminInterface().readImages(IMAGES_WITH_COMMANDS_JSON_PATH);

        for (Image image : imagesWithCommands) {
            mainAdminInterface().deleteImage(image, true);
        }
    }

    @Test
    public void testOrchestrationSession() {
        setupOrchestration();
        final int workflowId = mainInterface().launchContainer(project, wrapperSummaries.get(0), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);
        final int nextWorkflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, session.getAccessionNumber(),
                wrapperSummaries.get(1));
        mainInterface().waitForWorkflowComplete(nextWorkflowId, 60 * 5);
    }

    @Test
    public void testOrchestrationSessionFailure() throws Exception {
        // Setup orchestration ensuring debug-session is first
        List<Long> wrapperIds = new ArrayList<>();
        wrapperIds.add(wrapperSummaries.stream().filter(s -> s.getWrapperName().equals("debug-session")).findFirst()
                .orElseThrow(Exception::new).getWrapperId());
        wrapperIds.addAll(wrapperSummaries.stream().map(CommandSummaryForContext::getWrapperId)
                .filter(wrapperId -> !wrapperIds.contains(wrapperId)).collect(Collectors.toList()));
        Orchestration orchestration = new Orchestration("test", Scope.Project.name(), project.getId(), wrapperIds);
        mainAdminInterface().createOrUpdateOrchestration(orchestration);

        // Ensure the workflow fails
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("command", "exit 1");

        final int workflowId = mainInterface().launchContainer(project, wrapperSummaries.get(0), session.getUri(), queryParams);
        mainInterface().waitForWorkflowFailed(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, wrapperSummaries.get(1).getWrapperName());
    }

    @Test
    public void testOrchestrationSessionReverseOrder() {
        Orchestration orchestration = setupOrchestration();
        Collections.reverse(orchestration.getWrapperIds());
        mainAdminInterface().createOrUpdateOrchestration(orchestration);

        final int workflowId = mainInterface().launchContainer(project, wrapperSummaries.get(wrapperSummaries.size()-1),
                session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);
        final int nextWorkflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, session.getAccessionNumber(),
                wrapperSummaries.get(0));
        mainInterface().waitForWorkflowComplete(nextWorkflowId, 60 * 5);
    }

    @Test
    public void testOrchestrationDisable() {
        Orchestration orchestration = setupOrchestration();
        mainAdminInterface().disableOrchestration(orchestration);

        final int workflowId = mainInterface().launchContainer(project, wrapperSummaries.get(0), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, wrapperSummaries.get(1).getWrapperName());
    }

    @Test
    public void testOrchestrationDelete() {
        Orchestration orchestration = setupOrchestration();
        mainAdminInterface().deleteOrchestration(orchestration);

        final int workflowId = mainInterface().launchContainer(project, wrapperSummaries.get(0), session.getUri());
        mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);

        // verify next command doesn't run
        mainInterface().verifyNoWorkflow(session, wrapperSummaries.get(1).getWrapperName());
    }

    @Test
    public void testOrchestrationFind() {
        Orchestration o = setupOrchestration();
        Orchestration o2 = mainAdminInterface().findOrchestration(project);
        assertEquals(o, o2);
    }

    @Test
    public void testContainerSessionBulk() {
        // Setup another session
        final MRSession session2 = new MRSession(project, subject, "MR2").date(LocalDate.parse("2000-01-02"));
        mainInterface().createSubjectAssessor(session2);
        mainInterface().getAccessionNumber(session2);
        final List<ImagingSession> sessions = Arrays.asList(session, session2);

        setupOrchestration();

        mainInterface().bulkLaunchContainers(project, wrapperSummaries.get(0),
                sessions.stream().map(SubjectAssessor::getUri).collect(Collectors.toList()));

        // Determine workflow ID, wait for complete
        for (ImagingSession ses : sessions) {
            final int workflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, ses.getAccessionNumber(), wrapperSummaries.get(0));
            mainInterface().waitForWorkflowComplete(workflowId, 60 * 5);
        }

        // Determine workflow ID for next command, wait for complete
        for (ImagingSession ses : sessions) {
            final int workflowId = mainInterface().determineWorkflowId(DataType.MR_SESSION, ses.getAccessionNumber(), wrapperSummaries.get(1));
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
        List<Long> wrapperIds = wrapperSummaries.stream().map(CommandSummaryForContext::getWrapperId)
                .collect(Collectors.toList());
        Orchestration orchestration = new Orchestration("test", Scope.Project.name(), project.getId(), wrapperIds);
        return mainAdminInterface().createOrUpdateOrchestration(orchestration);
    }
}
