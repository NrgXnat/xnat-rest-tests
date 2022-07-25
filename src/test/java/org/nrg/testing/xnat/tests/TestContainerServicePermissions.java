package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.DataAccessLevel;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.containers.*;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.users.CustomUserGroup;
import org.nrg.xnat.rest.ForbiddenException;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.*;
import static org.testng.AssertJUnit.*;

@TestRequires(plugins = "containers:3.1.2") // TODO: update to 3.2 once we start actually building the jar like that
@AddedIn(Xnat_1_8_5.class)
@Test(groups = {CONTAINERS, PERMISSIONS})
public class TestContainerServicePermissions extends BaseXnatRestTest {

    private static final DataType PROJECT_ASSET = new DataType().xsiType("sets:definition");
    private static final String DATASETS_PLUGIN = "datasetsPlugin";
    private static final Image DEBUG_IMG = new Image("xnat", "debug-command","latest");

    private final List<Project> projects = new ArrayList<>();
    private final Project ownerProject = new Project().addOwner(mainUser);
    private final Project memberProject = new Project().addMember(mainUser);
    private final Project collaboratorProject = new Project().addCollaborator(mainUser);
    private final Project customUserGroupMinPermsProject = new Project().addUserGroup(
            new CustomUserGroup("customgroup"),
            Collections.singletonList(mainUser)
    );
    private final Project privateProject = new Project().accessibility(Accessibility.PRIVATE);
    private final Project protectedProject = new Project().accessibility(Accessibility.PROTECTED);
    private final Project publicProject = new Project().accessibility(Accessibility.PUBLIC);
    private int standardProjectDebug;
    private int projectDebugNoOutput;
    private int standardSubjectDebug;
    private int subjectDebugNoOutput;
    private int standardProjectAssetDebug;
    private int projectAssetDebugNoOutput;
    private int standardSessionDebug;
    private int sessionDebugNoOutput;
    private int sessionDebugCreateScan;
    private int sessionDebugCreateUnspecifiedAssessor;
    private int sessionDebugCreateSpecifiedAssessor;
    private int standardScanDebug;
    private int scanDebugNoOutput;
    private int standardSessionAssessorDebug;
    private int sessionAssessorDebugNoOutput;

    @BeforeClass
    public void setupCompute() {
        // TODO: once grxnat DockerServer supports "backend", also need to set that to docker
        final DockerServer dockerServer = mainAdminInterface().readDockerServer();
        dockerServer.setSwarmMode(false);
        mainAdminInterface().updateDockerServer(dockerServer);

        final List<Image> images = mainAdminInterface().readImages();

        for (Image image : images) {
            if (DEBUG_IMG.getUser().equals(image.getUser()) && DEBUG_IMG.getName().equals(image.getName())) {
                mainAdminInterface().deleteImage(image);
            }
        }

        assertEquals(
                1,
                mainAdminInterface().pullImage(DEBUG_IMG).readCommands(DEBUG_IMG).size()
        );
        mainAdminInterface().addCommand(getDataFile("debug_command_no_output.json"));
        mainAdminInterface().addCommand(getDataFile("debug_command_create_child.json"));
        for (Command command : mainAdminInterface().readCommands(DEBUG_IMG)) {
            for (Wrapper wrapper : command.getWrappers()) {
                mainAdminInterface().setWrapperStatusOnSite(wrapper, true);
            }
        }

        standardProjectDebug = lookupWrapperId("debug-project");
        projectDebugNoOutput = lookupWrapperId("debug-project-no-out");
        standardSubjectDebug = lookupWrapperId("debug-subject");
        subjectDebugNoOutput = lookupWrapperId("debug-subject-no-out");
        standardProjectAssetDebug = lookupWrapperId("debug-project-asset");
        projectAssetDebugNoOutput = lookupWrapperId("debug-project-asset-no-out");
        standardSessionDebug = lookupWrapperId("debug-session");
        sessionDebugNoOutput = lookupWrapperId("debug-session-no-out");
        sessionDebugCreateScan = lookupWrapperId("debug-session-create-child-scan");
        sessionDebugCreateUnspecifiedAssessor = lookupWrapperId("debug-session-create-assessor-missing-xsitype");
        sessionDebugCreateSpecifiedAssessor = lookupWrapperId("debug-session-create-assessor");
        standardScanDebug = lookupWrapperId("debug-scan");
        scanDebugNoOutput = lookupWrapperId("debug-scan-no-out");
        standardSessionAssessorDebug = lookupWrapperId("debug-assessor");
        sessionAssessorDebugNoOutput = lookupWrapperId("debug-assessor-no-out");

        initProject(ownerProject);
        initProject(memberProject);
        initProject(collaboratorProject);
        initProject(customUserGroupMinPermsProject);
        initProject(privateProject);
        initProject(protectedProject);
        initProject(publicProject);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupProjects() {
        for (Project project : projects) {
            restDriver.deleteProjectSilently(mainAdminUser, project);
        }
        mainAdminInterface().deleteImage(DEBUG_IMG);
    }

    public void testCommandsAvailableProjectContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.PROJECT).
                expectedWrappers(standardProjectDebug, projectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableProjectContextMember() {
        new AvailableCommandsTest(memberProject, DataType.PROJECT).
                expectedWrappers(projectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableProjectContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.PROJECT).
                expectedWrappers(projectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableProjectContextCustom() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.PROJECT).
                expectedWrappers(projectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableProjectContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.PROJECT).
                expectedWrappers(projectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableProjectContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.PROJECT).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableProjectContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.PROJECT).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableSubjectContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.SUBJECT).
                expectedWrappers(standardSubjectDebug, subjectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSubjectContextMember() {
        new AvailableCommandsTest(memberProject, DataType.SUBJECT).
                expectedWrappers(standardSubjectDebug, subjectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSubjectContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.SUBJECT).
                expectedWrappers(subjectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSubjectContextCustomRead() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.SUBJECT).
                expectedWrappers(subjectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSubjectContextCustomWrite() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.SUBJECT).
                expectedWrappers(standardSubjectDebug, subjectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSubjectContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.SUBJECT).
                expectedWrappers(subjectDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSubjectContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.SUBJECT).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableSubjectContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.SUBJECT).
                expectedException(ForbiddenException.class).
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextOwner() {
        new AvailableCommandsTest(ownerProject, PROJECT_ASSET).
                expectedWrappers(standardProjectAssetDebug, projectAssetDebugNoOutput).
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextMember() {
        new AvailableCommandsTest(memberProject, PROJECT_ASSET).
                expectedWrappers(standardProjectAssetDebug, projectAssetDebugNoOutput).
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, PROJECT_ASSET).
                expectedWrappers(projectAssetDebugNoOutput).
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextCustomMinimum() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, PROJECT_ASSET).
                expectedWrappers().
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextCustomRead() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(PROJECT_ASSET, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, PROJECT_ASSET).
                expectedWrappers(projectAssetDebugNoOutput).
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextCustomWrite() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(PROJECT_ASSET, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, PROJECT_ASSET).
                expectedWrappers(standardProjectAssetDebug, projectAssetDebugNoOutput).
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextPublic() {
        new AvailableCommandsTest(publicProject, PROJECT_ASSET).
                expectedWrappers(projectAssetDebugNoOutput).
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextProtected() {
        new AvailableCommandsTest(protectedProject, PROJECT_ASSET).
                expectedException(ForbiddenException.class).
                run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextPrivate() {
        new AvailableCommandsTest(privateProject, PROJECT_ASSET).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableSessionContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.MR_SESSION).
                expectedWrappers(standardSessionDebug, sessionDebugNoOutput, sessionDebugCreateScan, sessionDebugCreateSpecifiedAssessor, sessionDebugCreateUnspecifiedAssessor).
                run();
    }

    public void testCommandsAvailableSessionContextMember() {
        new AvailableCommandsTest(memberProject, DataType.MR_SESSION).
                expectedWrappers(standardSessionDebug, sessionDebugNoOutput, sessionDebugCreateScan, sessionDebugCreateSpecifiedAssessor, sessionDebugCreateUnspecifiedAssessor).
                run();
    }

    public void testCommandsAvailableSessionContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.MR_SESSION).
                expectedWrappers(sessionDebugNoOutput, sessionDebugCreateUnspecifiedAssessor).
                run();
    }

    public void testCommandsAvailableSessionContextCustomMinimum() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.MR_SESSION).
                expectedWrappers().
                run();
    }

    public void testCommandsAvailableSessionContextCustomRead() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION).
                expectedWrappers(sessionDebugNoOutput, sessionDebugCreateUnspecifiedAssessor).
                run();
    }

    public void testCommandsAvailableSessionContextCustomWrite() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION).
                expectedWrappers(standardSessionDebug, sessionDebugNoOutput, sessionDebugCreateScan, sessionDebugCreateUnspecifiedAssessor).
                run();
    }

    public void testCommandsAvailableSessionContextCustomWriteAndWriteAssessor() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").
                        permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT).
                        permission(DataType.MANUAL_QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION).
                expectedWrappers(standardSessionDebug, sessionDebugNoOutput, sessionDebugCreateScan, sessionDebugCreateSpecifiedAssessor, sessionDebugCreateUnspecifiedAssessor).
                run();
    }

    public void testCommandsAvailableSessionContextCustomWriteAssessorOnly() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").
                        permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY).
                        permission(DataType.MANUAL_QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION).
                expectedWrappers(sessionDebugNoOutput, sessionDebugCreateUnspecifiedAssessor, sessionDebugCreateSpecifiedAssessor).
                run();
    }

    public void testCommandsAvailableSessionContextCustomNoReadSessionWriteAssessor() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").
                        permission(DataType.PET_SESSION, DataAccessLevel.READ_ONLY).
                        permission(DataType.MANUAL_QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION).
                expectedWrappers().
                run();
    }

    public void testCommandsAvailableSessionContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.MR_SESSION).
                expectedWrappers(sessionDebugNoOutput, sessionDebugCreateUnspecifiedAssessor).
                run();
    }

    public void testCommandsAvailableSessionContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.MR_SESSION).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableSessionContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.MR_SESSION).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableScanContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.MR_SCAN).
                expectedWrappers(standardScanDebug, scanDebugNoOutput).
                run();
    }

    public void testCommandsAvailableScanContextMember() {
        new AvailableCommandsTest(memberProject, DataType.MR_SCAN).
                expectedWrappers(standardScanDebug, scanDebugNoOutput).
                run();
    }

    public void testCommandsAvailableScanContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.MR_SCAN).
                expectedWrappers(scanDebugNoOutput).
                run();
    }

    public void testCommandsAvailableScanContextCustomMinimum() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.MR_SCAN).
                expectedWrappers().
                run();
    }

    public void testCommandsAvailableScanContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.MR_SCAN).
                expectedWrappers(scanDebugNoOutput).
                run();
    }

    public void testCommandsAvailableScanContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.MR_SCAN).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableScanContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.MR_SCAN).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableSessionAssessorContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.QC).
                expectedWrappers(standardSessionAssessorDebug, sessionAssessorDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSessionAssessorContextMember() {
        new AvailableCommandsTest(memberProject, DataType.QC).
                expectedWrappers(standardSessionAssessorDebug, sessionAssessorDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSessionAssessorContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.QC).
                expectedWrappers(sessionAssessorDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSessionAssessorContextCustomMinimum() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.QC).
                expectedWrappers().
                run();
    }

    public void testCommandsAvailableSessionAssessorContextCustomRead() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").
                        permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY).
                        permission(DataType.QC, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.QC).
                expectedWrappers(sessionAssessorDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSessionAssessorContextCustomWrite() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").
                        permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY).
                        permission(DataType.QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.QC).
                expectedWrappers(standardSessionAssessorDebug, sessionAssessorDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSessionAssessorContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.QC).
                expectedWrappers(sessionAssessorDebugNoOutput).
                run();
    }

    public void testCommandsAvailableSessionAssessorContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.QC).
                expectedException(ForbiddenException.class).
                run();
    }

    public void testCommandsAvailableSessionAssessorContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.QC).
                expectedException(ForbiddenException.class).
                run();
    }

    private void initProject(Project project) {
        // Add a session (could be any type) to every project
        new MRSession(project, new Subject(project));
        mainAdminInterface().createProject(project);
        projects.add(project);
        for (Command command : mainAdminInterface().readCommands(DEBUG_IMG)) {
            for (Wrapper wrapper : command.getWrappers()) {
                mainAdminInterface().setWrapperStatusOnProject(wrapper, project, true);
            }
        }
    }
    
    private int lookupWrapperId(String wrapperName) {
        for (Command command : mainAdminInterface().readCommands(DEBUG_IMG)) {
            for (Wrapper wrapper : command.getWrappers()) {
                if (wrapperName.equals(wrapper.getName())) {
                    return wrapper.getId();
                }
            }
        }
        fail("Missing wrapper: " + wrapperName);
        return -1;
    }

    private class AvailableCommandsTest {
        private Class<? extends Exception> expectedException;
        private Set<Integer> expectedWrappers;
        private final Project project;
        private final DataType dataType;

        AvailableCommandsTest(Project project, DataType dataType) {
            this.project = project;
            this.dataType = dataType;
        }

        AvailableCommandsTest expectedWrappers(int... wrappers) {
            expectedWrappers = Arrays.stream(wrappers).boxed().collect(Collectors.toSet());
            return this;
        }

        AvailableCommandsTest expectedException(Class<? extends Exception> exceptionClass) {
            expectedException = exceptionClass;
            return this;
        }

        void run() {
            Set<Integer> actualWrappers;
            try {
                actualWrappers = mainInterface().readAvailableCommands(dataType, project).
                        stream().
                        filter(CommandSummaryForContext::isEnabled).
                        map(wrapper -> (int) wrapper.getWrapperId()).
                        collect(Collectors.toSet());
            } catch (Throwable t) {
                if (expectedException != null) {
                    assertEquals(expectedException, t.getClass());
                    return;
                }
                throw t;
            }
            if (expectedException != null) {
                fail("Exception should have already been thrown.");
            }
            assertEquals(expectedWrappers, actualWrappers);
        }
    }

}
