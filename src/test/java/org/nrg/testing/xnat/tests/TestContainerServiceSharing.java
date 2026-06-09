package org.nrg.testing.xnat.tests;

import lombok.extern.slf4j.Slf4j;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.DataAccessLevel;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Share;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.containers.CommandSummaryForContext;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.extensions.session_assessor.SessionAssessorXMLExtension;
import org.nrg.xnat.pogo.resources.*;
import org.nrg.xnat.pogo.users.CustomUserGroup;
import org.nrg.xnat.pogo.users.UserGroups;
import org.nrg.xnat.pogo.Workflow;
import org.nrg.xnat.versions.Version;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.*;

import static org.nrg.xnat.subinterfaces.ContainerServiceSubinterface.ContainerLog.STDOUT;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

import static org.nrg.testing.TestGroups.CONTAINERS;
import static org.nrg.testing.TestGroups.SHARING;
import static org.testng.AssertJUnit.*;
import org.nrg.testing.annotations.MutatesServerState;

@Slf4j
@Test(groups = {CONTAINERS, SHARING}, dataProvider = BaseXnatTest.CS_BACKENDS_DATA_PROVIDER)
@TestRequires(plugins = "containers:3.4.3")
@MutatesServerState
public class TestContainerServiceSharing extends BaseContainerTest {

    private static final String FIND_INPUT_COMMAND = "find /input -type f -o -type d | sort";
    private static final String NO_OUTPUT_WRAPPER_NAME = "debug-project-no-out";
    private static final String MAX_SESSIONS_PREF = "maxNumberOfSessionsForJobsWithSharedData";
    private static final String SESSION_LIMIT_ERROR = "more than 1 sessions are present";
    private static final LocalDate DEFAULT_SESSION_DATE = LocalDate.parse("2000-01-01");

    // Test data labels
    private static final String SUBJECT_LABEL = "S1";
    private static final String SESSION_LABEL = "MR1";
    private static final String SESSION_LABEL_2 = "MR2";
    private static final String SHARED_SUBJECT_LABEL = "SHARED_S1";
    private static final String SHARED_SESSION_LABEL = "SHARED_MR1";
    private static final String SHARED_SESSION_LABEL_2 = "SHARED_MR2";
    private static final String SHARED_ASSESSOR_LABEL = "SHARED_QC";
    private static final String DCM_FILE = "1.dcm";
    private static final String ASSESSOR_FILE = "dummy.txt";

    private Project projectA;
    private Project projectB;

    @BeforeClass
    private void setupCommands() {
        containerManagerInterface.deleteAllCommands();
        if (getPluginVersion(PluginRegistry.CS_PLUGIN_ID).lessThan(new Version("3.2"))) {
            ContainerTestUtils.setServerBackend(this, Settings.CS_PREFERRED_BACKEND, containerManagerInterface);
            containerManagerInterface.pullImage(ContainerTestUtils.DEBUG_IMG, false);
        }
        containerManagerInterface.addCommand(getDataFile("debug_command_no_output.json"));
    }

    @BeforeMethod
    private void setupProjects(Object[] backendHolder) {
        projectA = new Project().accessibility(Accessibility.PRIVATE);
        projectB = null;

        final Backend backend = (Backend) backendHolder[0];
        log.info("Setting backend {}", backend);
        ContainerTestUtils.setServerBackend(this, backend, containerManagerInterface);
    }

    @AfterMethod(alwaysRun = true)
    private void deleteProjects() {
        restDriver.deleteProjectSilently(mainAdminUser, projectA);
        if (projectB != null) {
            restDriver.deleteProjectSilently(mainAdminUser, projectB);
        }
    }

    /** Baseline: a project with no shared data should mount its own session data. */
    public void testProjectWithNoSharedData(final Backend backend) {
        mainAdminInterface().createProject(projectA);
        mainAdminInterface().addUserToProject(mainUser, projectA, UserGroups.MEMBER);
        final ImagingSession session = createSessionWithScan(projectA, SUBJECT_LABEL, SESSION_LABEL);

        String output = launchAsUser(mainInterface(), projectA, backend);
        assertNotNull("Container output should not be null", output);
        assertTrue("Output should contain /input mount point", output.contains("/input"));
        assertTrue("Output should contain session label " + SESSION_LABEL, output.contains(SESSION_LABEL));
        assertTrue("Output should contain DICOM file", output.contains(DCM_FILE));
    }

    /** A container launched at the destination project should see the shared session. */
    public void testProjectWithSharedSession(final Backend backend) {
        mainAdminInterface().createProject(projectA);
        final ImagingSession sessionA = createSessionWithScan(projectA, SUBJECT_LABEL, SESSION_LABEL);

        projectB = new Project().accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(projectB);
        mainAdminInterface().addUserToProject(mainUser, projectB, UserGroups.MEMBER);

        shareSubjectAndSession(sessionA, projectB, SHARED_SUBJECT_LABEL, SHARED_SESSION_LABEL);

        String output = launchAsUser(mainInterface(), projectB, backend);
        assertNotNull("Container output should not be null", output);
        assertTrue("Output should contain shared session label " + SHARED_SESSION_LABEL, output.contains(SHARED_SESSION_LABEL));
    }

    /** Shared data should appear under the shared label, not the original label. */
    public void testSharedSessionWithLabelChange(final Backend backend) {
        mainAdminInterface().createProject(projectA);
        mainAdminInterface().addUserToProject(mainUser, projectA, UserGroups.MEMBER);
        final ImagingSession sessionA = createSessionWithScan(projectA, SUBJECT_LABEL, SESSION_LABEL);

        projectB = new Project().accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(projectB);
        mainAdminInterface().addUserToProject(mainUser, projectB, UserGroups.MEMBER);
        shareSubjectAndSession(sessionA, projectB, SHARED_SUBJECT_LABEL, SHARED_SESSION_LABEL);

        // Launch at project B — should use shared label
        String outputB = launchAsUser(mainInterface(), projectB, backend);
        assertTrue("Project B output should use shared label " + SHARED_SESSION_LABEL,
                outputB.contains(SHARED_SESSION_LABEL));
        assertFalse("Project B output should NOT contain original label " + SESSION_LABEL + " as a directory",
                outputB.contains("/" + SESSION_LABEL + "/") || outputB.contains("/" + SESSION_LABEL + "\n"));

        // Launch at project A — should use original label
        String outputA = launchAsUser(mainInterface(), projectA, backend);
        assertTrue("Project A output should use original label " + SESSION_LABEL,
                outputA.contains(SESSION_LABEL));
    }

    /** Shared assessors should appear under the shared session using their shared labels. */
    public void testSharedSessionWithAssessor(final Backend backend) {
        mainAdminInterface().createProject(projectA);
        final ImagingSession sessionA = createSessionWithScan(projectA, SUBJECT_LABEL, SESSION_LABEL);
        final SessionAssessor assessor = createAssessor(projectA, sessionA);

        projectB = new Project().accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(projectB);
        mainAdminInterface().addUserToProject(mainUser, projectB, UserGroups.MEMBER);
        shareSubjectAndSession(sessionA, projectB, SHARED_SUBJECT_LABEL, SHARED_SESSION_LABEL);
        mainAdminInterface().shareSessionAssessor(sessionA, assessor, new Share(projectB, SHARED_ASSESSOR_LABEL));

        String output = launchAsUser(mainInterface(), projectB, backend);
        assertTrue("Output should contain ASSESSORS directory", output.contains("ASSESSORS"));
        assertTrue("Output should contain shared assessor label " + SHARED_ASSESSOR_LABEL,
                output.contains(SHARED_ASSESSOR_LABEL));
        assertFalse("Output should NOT contain original assessor label " + assessor.getLabel() + " as a directory",
                output.contains("/" + assessor.getLabel() + "/") || output.contains("/" + assessor.getLabel() + "\n"));
        assertTrue("Assessor should appear under shared session label: " + SHARED_SESSION_LABEL + "/ASSESSORS/" + SHARED_ASSESSOR_LABEL,
                output.contains(SHARED_SESSION_LABEL + "/ASSESSORS/" + SHARED_ASSESSOR_LABEL));
        assertFalse("Output should NOT contain original session label " + SESSION_LABEL + " in assessor path",
                output.contains("/" + SESSION_LABEL + "/ASSESSORS"));
        assertTrue("Output should contain assessor resource file", output.contains(ASSESSOR_FILE));
    }

    /** An assessor that exists on the home project but is NOT shared should be excluded from the mount. */
    public void testSharedSessionWithUnsharedAssessor(final Backend backend) {
        mainAdminInterface().createProject(projectA);
        final ImagingSession sessionA = createSessionWithScan(projectA, SUBJECT_LABEL, SESSION_LABEL);
        final SessionAssessor assessor = createAssessor(projectA, sessionA);

        projectB = new Project().accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(projectB);
        mainAdminInterface().addUserToProject(mainUser, projectB, UserGroups.MEMBER);
        shareSubjectAndSession(sessionA, projectB, SHARED_SUBJECT_LABEL, SHARED_SESSION_LABEL);
        // Deliberately NOT sharing the assessor

        String output = launchAsUser(mainInterface(), projectB, backend);
        assertFalse("Output should NOT contain unshared assessor label " + assessor.getLabel(),
                output.contains(assessor.getLabel()));
    }

    /** Container launch should fail when shared session count exceeds the site-wide maximum. */
    public void testMaxSessionsLimit(final Backend backend) {
        String originalValue;
        try {
            originalValue = mainAdminInterface().readSiteConfigPreference(MAX_SESSIONS_PREF);
        } catch (Exception e) {
            originalValue = null;
        }

        try {
            mainAdminInterface().postSiteConfigProperty(MAX_SESSIONS_PREF, "1");

            // Create project A with 2 sessions (session2 has no scan — only needs to exist)
            mainAdminInterface().createProject(projectA);
            final ImagingSession session1 = createSessionWithScan(projectA, SUBJECT_LABEL, SESSION_LABEL);
            final Subject subject = session1.getSubject();
            final ImagingSession session2 = new MRSession(projectA, subject, SESSION_LABEL_2).date(DEFAULT_SESSION_DATE);
            mainAdminInterface().createSubjectAssessor(session2);
            mainAdminInterface().getAccessionNumber(session2);

            // Create project B and share both sessions
            projectB = new Project().accessibility(Accessibility.PRIVATE);
            mainAdminInterface().createProject(projectB);
            mainAdminInterface().addUserToProject(mainUser, projectB, UserGroups.MEMBER);
            shareSubjectAndSession(session1, projectB, SHARED_SUBJECT_LABEL, SHARED_SESSION_LABEL);
            mainAdminInterface().shareSubjectAssessor(session2, new Share(projectB, SHARED_SESSION_LABEL_2));

            // Launch container at project B level — should fail due to max sessions limit
            int workflowId = launchContainerAtProject(mainInterface(), projectB);

            // Wait for workflow to reach a terminal state — uses startsWith matching
            // so "Failed (Command resolution)" is detected immediately rather than timing out
            mainAdminInterface().waitForWorkflowTerminal(workflowId, 60 * ContainerTest.MAX_TIMEOUTS_IN_SECONDS.get(backend));

            String status = mainAdminInterface().readWorkflowStatus(workflowId);
            assertEquals("Workflow should have failed during command resolution due to max sessions limit",
                    "Failed (Command resolution)", status);

            String details = mainAdminInterface().jsonQuery()
                    .get(mainAdminInterface().formatRestUrl("/workflows/" + workflowId))
                    .then().assertThat().statusCode(200).and().extract().jsonPath()
                    .getString("items[0].data_fields.details");
            assertNotNull("Workflow details should not be null", details);
            assertTrue("Workflow details should mention session limit exceeded, but was: " + details,
                    details.contains(SESSION_LIMIT_ERROR));
        } finally {
            if (originalValue != null) {
                mainAdminInterface().postSiteConfigProperty(MAX_SESSIONS_PREF, originalValue);
            } else {
                mainAdminInterface().postSiteConfigProperty(MAX_SESSIONS_PREF, "10000");
            }
        }
    }

    /**
     * Validates that permission filtering respects data type restrictions from custom user groups.
     * A user in a custom group with MR_SESSION READ_ONLY should see shared MR sessions but NOT
     * shared assessors for which the group grants no access.
     */
    public void testSharedDataCustomGroupMROnly(final Backend backend) {
        mainAdminInterface().createProject(projectA);
        final ImagingSession sessionA = createSessionWithScan(projectA, SUBJECT_LABEL, SESSION_LABEL);
        final SessionAssessor assessor = createAssessor(projectA, sessionA);

        // mainUser in custom group on B: can read MR sessions only
        projectB = new Project().accessibility(Accessibility.PRIVATE).addUserGroup(
                new CustomUserGroup("mronly").permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        mainAdminInterface().createProject(projectB);

        shareSubjectAndSession(sessionA, projectB, SHARED_SUBJECT_LABEL, SHARED_SESSION_LABEL);
        mainAdminInterface().shareSessionAssessor(sessionA, assessor, new Share(projectB, SHARED_ASSESSOR_LABEL));

        // Launch as mainUser (custom group with MR_SESSION READ_ONLY only)
        String output = launchAsUser(mainInterface(), projectB, backend);
        assertNotNull("Container output should not be null", output);
        assertTrue("Custom group user with MR_SESSION access should see shared MR session",
                output.contains(SHARED_SESSION_LABEL));
        assertFalse("Custom group user without QC permission should not see shared assessor",
                output.contains(SHARED_ASSESSOR_LABEL));
    }

    /**
     * Creates a subject, MR session with a DICOM scan in the given project.
     * Returns the session with accession numbers resolved.
     */
    private ImagingSession createSessionWithScan(Project project, String subjectLabel, String sessionLabel) {
        Subject subject = new Subject(project, subjectLabel).gender(Gender.MALE);
        ImagingSession session = new MRSession(project, subject, sessionLabel).date(DEFAULT_SESSION_DATE);
        Scan scan = new MRScan(session, "1").type("T1").seriesDescription("T1").quality("usable");

        final File dcmFile = getDataFile("mr_1/" + DCM_FILE);
        new ScanResource(project, subject, session, scan).folder("DICOM")
                .addResourceFile(new ResourceFile().name(dcmFile.getName())
                        .extension(new SimpleResourceFileExtension(dcmFile)));

        mainAdminInterface().createSubjectAssessor(session);
        mainAdminInterface().getAccessionNumber(subject);
        mainAdminInterface().getAccessionNumber(session);
        return session;
    }

    /**
     * Creates a ManualQC assessor on the given session with a test resource file.
     * Returns the assessor with accession number resolved.
     */
    private SessionAssessor createAssessor(Project project, ImagingSession session) {
        SessionAssessor assessor = new ManualQC(project, session.getSubject(), session)
                .extension(new SessionAssessorXMLExtension(getDataFile("test_asst_v1.xml")));
        final File dummyFile = getDataFile(ASSESSOR_FILE);
        new SessionAssessorResource(project, session.getSubject(), session, assessor, "TEST")
                .addResourceFile(new ResourceFile().name(dummyFile.getName())
                        .extension(new SimpleResourceFileExtension(dummyFile)));
        mainAdminInterface().createSessionAssessor(assessor);
        assessor.setAccessionNumber(mainAdminInterface().jsonQuery()
                .get(mainAdminInterface().assessorsUrlByAccessionNumber(session))
                .then().assertThat().statusCode(200).and().extract().jsonPath()
                .getString("ResultSet.Result.find {it.label == '" + assessor.getLabel() + "' }.ID"));
        return assessor;
    }

    /**
     * Shares a session's subject and the session itself into the destination project.
     */
    private void shareSubjectAndSession(ImagingSession session, Project destProject,
                                        String sharedSubjectLabel, String sharedSessionLabel) {
        mainAdminInterface().shareSubject(session.getPrimaryProject(), session.getSubject(),
                new Share(destProject, sharedSubjectLabel));
        mainAdminInterface().shareSubjectAssessor(session, new Share(destProject, sharedSessionLabel));
    }

    /** Admin enables all command wrappers on the given project. */
    private void enableAllWrappers(Project project) {
        for (CommandSummaryForContext w : mainAdminInterface().readAvailableCommands(DataType.PROJECT, project)) {
            mainAdminInterface().setWrapperStatusOnProject(w, project, true);
        }
    }

    /**
     * Launches a no-output container as the given user, reading stdout from container logs.
     * Uses the no-output command variant so users without write permissions can launch.
     * Admin enables all project wrappers; the user's readAvailableCommands returns only
     * wrappers they have permission to use.
     */
    private String launchAsUser(XnatInterface userInterface, Project project, Backend backend) {
        enableAllWrappers(project);

        // Select the no-output wrapper by name
        CommandSummaryForContext wrapper = userInterface.readAvailableCommands(DataType.PROJECT, project).stream()
                .filter(w -> NO_OUTPUT_WRAPPER_NAME.equals(w.getWrapperName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Wrapper " + NO_OUTPUT_WRAPPER_NAME + " not available for user"));

        Map<String, String> params = new HashMap<>();
        params.put(ContainerTestUtils.DEBUG_COMMAND_LINE_INPUT_NAME, FIND_INPUT_COMMAND);

        int workflowId = userInterface.launchContainer(project, wrapper,
                "/archive/projects/" + project.getId(), params);
        mainAdminInterface().waitForWorkflowComplete(workflowId, 60 * ContainerTest.MAX_TIMEOUTS_IN_SECONDS.get(backend));

        // Read stdout from container logs
        final Workflow workflow = mainAdminInterface().readWorkflow(workflowId);
        final String containerId = workflow.getComments();
        return containerManagerInterface.readContainerLog(containerId, STDOUT).trim();
    }

    /**
     * Launches a no-output container at project level and returns the workflow ID (does not wait).
     * Used by tests that need to inspect workflow status directly (e.g., expected failures).
     */
    private int launchContainerAtProject(XnatInterface userInterface, Project project) {
        enableAllWrappers(project);

        CommandSummaryForContext wrapper = userInterface.readAvailableCommands(DataType.PROJECT, project).stream()
                .filter(w -> NO_OUTPUT_WRAPPER_NAME.equals(w.getWrapperName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Wrapper " + NO_OUTPUT_WRAPPER_NAME + " not available for user"));

        Map<String, String> params = new HashMap<>();
        params.put(ContainerTestUtils.DEBUG_COMMAND_LINE_INPUT_NAME, FIND_INPUT_COMMAND);

        return userInterface.launchContainer(project, wrapper,
                "/archive/projects/" + project.getId(), params);
    }
}
