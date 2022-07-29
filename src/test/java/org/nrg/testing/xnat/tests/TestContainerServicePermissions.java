package org.nrg.testing.xnat.tests;

import com.google.common.collect.ImmutableMap;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.DataAccessLevel;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Share;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.containers.*;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.resources.ScanResource;
import org.nrg.xnat.pogo.resources.SessionAssessorResource;
import org.nrg.xnat.pogo.users.CustomUserGroup;
import org.nrg.xnat.rest.ForbiddenException;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.*;
import static org.testng.AssertJUnit.*;

@TestRequires(plugins = "containers:3.2")
@AddedIn(Xnat_1_8_5.class)
@Test(groups = {CONTAINERS, PERMISSIONS})
public class TestContainerServicePermissions extends BaseXnatRestTest {

    private static final String RESOURCE_NAME = "NEEDSOMETHING";
    private static final DataType PROJECT_ASSET = new DataType().xsiType("sets:definition");
    private static final String DATASETS_PLUGIN = "datasetsPlugin";
    private static final Image DEBUG_IMG = new Image("xnat", "debug-command", "latest");
    private static final Image QC_IMG = new Image("xnat", "generate-test-qc-assessor", "latest");
    private static final List<Image> TEST_IMAGES = Arrays.asList(DEBUG_IMG, QC_IMG);
    private static final Map<String, String> BASE_DEBUG_LAUNCH_PARAMS = makeContainerLaunchReqBody();

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
    private Wrapper standardProjectDebug;
    private Wrapper projectDebugNoOutput;
    private Wrapper standardSubjectDebug;
    private Wrapper subjectDebugNoOutput;
    private Wrapper standardProjectAssetDebug;
    private Wrapper projectAssetDebugNoOutput;
    private Wrapper standardSessionDebug;
    private Wrapper sessionDebugNoOutput;
    private Wrapper sessionDebugCreateScan;
    private Wrapper standardScanDebug;
    private Wrapper scanDebugNoOutput;
    private Wrapper standardSessionAssessorDebug;
    private Wrapper sessionAssessorDebugNoOutput;
    private Wrapper qcImageCreateUnspecifiedAssessor;
    private Wrapper qcImageCreateSpecifiedAssessor;

    @BeforeClass
    public void setupCompute() {
        // TODO: once grxnat DockerServer supports "backend", also need to set that to docker
        final DockerServer dockerServer = mainAdminInterface().readDockerServer();
        dockerServer.setSwarmMode(false);
        mainAdminInterface().updateDockerServer(dockerServer);

        final List<Image> installedImages = mainAdminInterface().readImages();

        for (Image image : installedImages) {
            for (Image testImage : TEST_IMAGES) {
                if (testImage.getUser().equals(image.getUser()) && testImage.getName().equals(image.getName())) {
                    mainAdminInterface().deleteImage(image);
                }
            }
        }

        assertEquals(
                1,
                mainAdminInterface().pullImage(DEBUG_IMG).readCommands(DEBUG_IMG).size()
        );
        mainAdminInterface().pullImage(QC_IMG, false);

        mainAdminInterface().addCommand(getDataFile("debug_command_no_output.json"));
        mainAdminInterface().addCommand(getDataFile("debug_command_create_child.json"));
        mainAdminInterface().addCommand(getDataFile("sample_qc_assessor.json"));
        for (Image image : TEST_IMAGES) {
            for (Command command : mainAdminInterface().readCommands(image)) {
                for (Wrapper wrapper : command.getWrappers()) {
                    mainAdminInterface().setWrapperStatusOnSite(wrapper, true);
                }
            }
        }

        standardProjectDebug = lookupWrapper("debug-project");
        projectDebugNoOutput = lookupWrapper("debug-project-no-out");
        standardSubjectDebug = lookupWrapper("debug-subject");
        subjectDebugNoOutput = lookupWrapper("debug-subject-no-out");
        standardProjectAssetDebug = lookupWrapper("debug-project-asset");
        projectAssetDebugNoOutput = lookupWrapper("debug-project-asset-no-out");
        standardSessionDebug = lookupWrapper("debug-session");
        sessionDebugNoOutput = lookupWrapper("debug-session-no-out");
        sessionDebugCreateScan = lookupWrapper("debug-session-create-child-scan");
        standardScanDebug = lookupWrapper("debug-scan");
        scanDebugNoOutput = lookupWrapper("debug-scan-no-out");
        standardSessionAssessorDebug = lookupWrapper("debug-assessor");
        sessionAssessorDebugNoOutput = lookupWrapper("debug-assessor-no-out");
        qcImageCreateUnspecifiedAssessor = lookupWrapper("generate-test-qc-assessor-from-session-missing-xsitype");
        qcImageCreateSpecifiedAssessor = lookupWrapper("generate-test-qc-assessor-from-session");

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
        for (Image image : TEST_IMAGES) {
            mainAdminInterface().deleteImage(image);
        }
    }

    public void testCommandsAvailableProjectContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.PROJECT)
                .expectedWrappers(standardProjectDebug, projectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableProjectContextMember() {
        new AvailableCommandsTest(memberProject, DataType.PROJECT)
                .expectedWrappers(projectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableProjectContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.PROJECT)
                .expectedWrappers(projectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableProjectContextCustom() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.PROJECT)
                .expectedWrappers(projectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableProjectContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.PROJECT)
                .expectedWrappers(projectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableProjectContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.PROJECT)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableProjectContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.PROJECT)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableSubjectContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.SUBJECT)
                .expectedWrappers(standardSubjectDebug, subjectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSubjectContextMember() {
        new AvailableCommandsTest(memberProject, DataType.SUBJECT)
                .expectedWrappers(standardSubjectDebug, subjectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSubjectContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.SUBJECT)
                .expectedWrappers(subjectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSubjectContextCustomRead() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.SUBJECT)
                .expectedWrappers(subjectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSubjectContextCustomWrite() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.SUBJECT)
                .expectedWrappers(standardSubjectDebug, subjectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSubjectContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.SUBJECT)
                .expectedWrappers(subjectDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSubjectContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.SUBJECT)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableSubjectContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.SUBJECT)
                .expectedException(ForbiddenException.class)
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextOwner() {
        new AvailableCommandsTest(ownerProject, PROJECT_ASSET)
                .expectedWrappers(standardProjectAssetDebug, projectAssetDebugNoOutput)
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextMember() {
        new AvailableCommandsTest(memberProject, PROJECT_ASSET)
                .expectedWrappers(standardProjectAssetDebug, projectAssetDebugNoOutput)
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, PROJECT_ASSET)
                .expectedWrappers(projectAssetDebugNoOutput)
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextCustomMinimum() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, PROJECT_ASSET)
                .expectedWrappers()
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextCustomRead() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(PROJECT_ASSET, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, PROJECT_ASSET)
                .expectedWrappers(projectAssetDebugNoOutput)
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextCustomWrite() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(PROJECT_ASSET, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, PROJECT_ASSET)
                .expectedWrappers(standardProjectAssetDebug, projectAssetDebugNoOutput)
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextPublic() {
        new AvailableCommandsTest(publicProject, PROJECT_ASSET)
                .expectedWrappers(projectAssetDebugNoOutput)
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextProtected() {
        new AvailableCommandsTest(protectedProject, PROJECT_ASSET)
                .expectedException(ForbiddenException.class)
                .run();
    }

    @TestRequires(plugins = DATASETS_PLUGIN)
    public void testCommandsAvailableProjectAssetContextPrivate() {
        new AvailableCommandsTest(privateProject, PROJECT_ASSET)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableSessionContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.MR_SESSION)
                .expectedWrappers(standardSessionDebug, sessionDebugNoOutput, sessionDebugCreateScan, qcImageCreateSpecifiedAssessor, qcImageCreateUnspecifiedAssessor)
                .run();
    }

    public void testCommandsAvailableSessionContextMember() {
        new AvailableCommandsTest(memberProject, DataType.MR_SESSION)
                .expectedWrappers(standardSessionDebug, sessionDebugNoOutput, sessionDebugCreateScan, qcImageCreateSpecifiedAssessor, qcImageCreateUnspecifiedAssessor)
                .run();
    }

    public void testCommandsAvailableSessionContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.MR_SESSION)
                .expectedWrappers(sessionDebugNoOutput, qcImageCreateUnspecifiedAssessor)
                .run();
    }

    public void testCommandsAvailableSessionContextCustomMinimum() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.MR_SESSION)
                .expectedWrappers()
                .run();
    }

    public void testCommandsAvailableSessionContextCustomRead() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION)
                .expectedWrappers(sessionDebugNoOutput, qcImageCreateUnspecifiedAssessor)
                .run();
    }

    public void testCommandsAvailableSessionContextCustomWrite() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION)
                .expectedWrappers(standardSessionDebug, sessionDebugNoOutput, sessionDebugCreateScan, qcImageCreateUnspecifiedAssessor)
                .run();
    }

    public void testCommandsAvailableSessionContextCustomWriteAndWriteAssessor() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MANUAL_QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION)
                .expectedWrappers(standardSessionDebug, sessionDebugNoOutput, sessionDebugCreateScan, qcImageCreateSpecifiedAssessor, qcImageCreateUnspecifiedAssessor)
                .run();
    }

    public void testCommandsAvailableSessionContextCustomWriteAssessorOnly() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY)
                        .permission(DataType.MANUAL_QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION)
                .expectedWrappers(sessionDebugNoOutput, qcImageCreateUnspecifiedAssessor, qcImageCreateSpecifiedAssessor)
                .run();
    }

    public void testCommandsAvailableSessionContextCustomNoReadSessionWriteAssessor() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.PET_SESSION, DataAccessLevel.READ_ONLY)
                        .permission(DataType.MANUAL_QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.MR_SESSION)
                .expectedWrappers()
                .run();
    }

    public void testCommandsAvailableSessionContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.MR_SESSION)
                .expectedWrappers(sessionDebugNoOutput, qcImageCreateUnspecifiedAssessor)
                .run();
    }

    public void testCommandsAvailableSessionContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.MR_SESSION)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableSessionContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.MR_SESSION)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableScanContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.MR_SCAN)
                .expectedWrappers(standardScanDebug, scanDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableScanContextMember() {
        new AvailableCommandsTest(memberProject, DataType.MR_SCAN)
                .expectedWrappers(standardScanDebug, scanDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableScanContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.MR_SCAN)
                .expectedWrappers(scanDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableScanContextCustomMinimum() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.MR_SCAN)
                .expectedWrappers()
                .run();
    }

    public void testCommandsAvailableScanContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.MR_SCAN)
                .expectedWrappers(scanDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableScanContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.MR_SCAN)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableScanContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.MR_SCAN)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextOwner() {
        new AvailableCommandsTest(ownerProject, DataType.QC)
                .expectedWrappers(standardSessionAssessorDebug, sessionAssessorDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextMember() {
        new AvailableCommandsTest(memberProject, DataType.QC)
                .expectedWrappers(standardSessionAssessorDebug, sessionAssessorDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextCollaborator() {
        new AvailableCommandsTest(collaboratorProject, DataType.QC)
                .expectedWrappers(sessionAssessorDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextCustomMinimum() {
        new AvailableCommandsTest(customUserGroupMinPermsProject, DataType.QC)
                .expectedWrappers()
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextCustomRead() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY)
                        .permission(DataType.QC, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.QC)
                .expectedWrappers(sessionAssessorDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextCustomWrite() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup").
                        permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY).
                        permission(DataType.QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new AvailableCommandsTest(customProject, DataType.QC)
                .expectedWrappers(standardSessionAssessorDebug, sessionAssessorDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextPublic() {
        new AvailableCommandsTest(publicProject, DataType.QC)
                .expectedWrappers(sessionAssessorDebugNoOutput)
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextProtected() {
        new AvailableCommandsTest(protectedProject, DataType.QC)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testCommandsAvailableSessionAssessorContextPrivate() {
        new AvailableCommandsTest(privateProject, DataType.QC)
                .expectedException(ForbiddenException.class)
                .run();
    }

    public void testLaunchProjectOwner() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardProjectDebug)
                .projectInput(ownerProject)
                .run();
    }

    public void testLaunchProjectMember() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardProjectDebug)
                .projectInput(memberProject)
                .run();
    }

    public void testLaunchProjectReadOnlyMember() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, projectDebugNoOutput)
                .projectInput(memberProject)
                .run();
    }

    public void testLaunchProjectCollaborator() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardProjectDebug)
                .projectInput(collaboratorProject)
                .run();
    }

    public void testLaunchProjectReadOnlyCollaborator() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, projectDebugNoOutput)
                .projectInput(collaboratorProject)
                .run();
    }

    public void testLaunchProjectCustomMinimum() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardProjectDebug)
                .projectInput(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchProjectReadOnlyCustomMinimum() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, projectDebugNoOutput)
                .projectInput(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchProjectPublic() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardProjectDebug)
                .projectInput(publicProject)
                .run();
    }

    public void testLaunchProjectReadOnlyPublic() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, projectDebugNoOutput)
                .projectInput(publicProject)
                .run();
    }

    public void testLaunchProjectReadOnlyProtected() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, projectDebugNoOutput)
                .projectInput(protectedProject)
                .run();
    }

    public void testLaunchProjectReadOnlyPrivate() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, projectDebugNoOutput)
                .projectInput(privateProject)
                .run();
    }

    public void testLaunchSubjectOwner() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardSubjectDebug)
                .arbitrarySubjectInputFrom(ownerProject)
                .run();
    }

    public void testLaunchSubjectMember() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardSubjectDebug)
                .arbitrarySubjectInputFrom(memberProject)
                .run();
    }

    public void testLaunchSubjectCollaborator() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardSubjectDebug)
                .arbitrarySubjectInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSubjectReadOnlyCollaborator() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, subjectDebugNoOutput)
                .arbitrarySubjectInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSubjectCustomMinimum() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardSubjectDebug)
                .arbitrarySubjectInputFrom(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchSubjectReadOnlyCustomMinimum() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, subjectDebugNoOutput)
                .arbitrarySubjectInputFrom(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchSubjectCustomEditSubject() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardSubjectDebug)
                .arbitrarySubjectInputFrom(customProject)
                .run();
    }

    public void testLaunchSubjectPrivate() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, subjectDebugNoOutput)
                .arbitrarySubjectInputFrom(privateProject)
                .run();
    }

    public void testLaunchSubjectProtected() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, subjectDebugNoOutput)
                .arbitrarySubjectInputFrom(protectedProject)
                .run();
    }

    public void testLaunchSubjectPublic() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardSubjectDebug)
                .arbitrarySubjectInputFrom(publicProject)
                .run();
    }

    public void testLaunchSubjectReadOnlyPublic() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, subjectDebugNoOutput)
                .arbitrarySubjectInputFrom(publicProject)
                .run();
    }

    public void testLaunchSubjectSharedNoEditOnSource() {
        final Project sourceProject = new Project();
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(ownerProject));
        initProject(sourceProject);
        new LaunchTest(
                ExpectedLaunchResult.RESOLUTION_FAILED, // sure we can edit subjects in ownerProject, but not this subject!
                standardSubjectDebug,
                ownerProject,
                sharedSubject.getUri()
        ).run();
    }

    public void testLaunchSubjectSharedReadyOnlyNoEditOnSource() {
        final Project sourceProject = new Project();
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(ownerProject));
        initProject(sourceProject);
        new LaunchTest(
                ExpectedLaunchResult.SUCCESS,
                subjectDebugNoOutput,
                ownerProject,
                sharedSubject.getUri()
        ).run();
    }

    public void testLaunchSubjectSharedWithEditOnSource() {
        final Project sourceProject = new Project().addMember(mainUser);
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(ownerProject));
        initProject(sourceProject);
        new LaunchTest(
                ExpectedLaunchResult.SUCCESS,
                standardSubjectDebug,
                ownerProject,
                sharedSubject.getUri()
        ).run();
    }

    public void testLaunchSubjectSharedWithEditOnSourceButNotDestination() {
        final Project sourceProject = new Project().addMember(mainUser);
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(collaboratorProject));
        initProject(sourceProject);
        new LaunchTest(
                ExpectedLaunchResult.SUCCESS, // surprising, but I don't see it as a problem
                standardSubjectDebug,
                collaboratorProject,
                sharedSubject.getUri()
        ).run();
    }

    public void testLaunchSessionOwner() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardSessionDebug)
                .arbitrarySessionInputFrom(ownerProject)
                .run();
    }

    public void testLaunchSessionCreateScanOwner() {
        new LaunchTest(ExpectedLaunchResult.UPLOAD_FAILED, sessionDebugCreateScan)
                .arbitrarySessionInputFrom(ownerProject)
                .run();
    }

    public void testLaunchSessionCreateAssessorOwner() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, qcImageCreateUnspecifiedAssessor)
                .arbitrarySessionInputFrom(ownerProject)
                .run();
    }

    public void testLaunchSessionMember() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardSessionDebug)
                .arbitrarySessionInputFrom(memberProject)
                .run();
    }

    public void testLaunchSessionCreateScanMember() {
        new LaunchTest(ExpectedLaunchResult.UPLOAD_FAILED, sessionDebugCreateScan)
                .arbitrarySessionInputFrom(memberProject)
                .run();
    }

    public void testLaunchSessionCreateAssessorMember() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, qcImageCreateUnspecifiedAssessor)
                .arbitrarySessionInputFrom(memberProject)
                .run();
    }

    public void testLaunchSessionCollaborator() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardSessionDebug)
                .arbitrarySessionInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSessionReadOnlyCollaborator() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, sessionDebugNoOutput)
                .arbitrarySessionInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSessionCreateScanCollaborator() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, sessionDebugCreateScan)
                .arbitrarySessionInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSessionCreateSpecifiedAssessorCollaborator() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, qcImageCreateSpecifiedAssessor)
                .arbitrarySessionInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSessionCreateUnspecifiedAssessorCollaborator() {
        new LaunchTest(ExpectedLaunchResult.UPLOAD_FAILED, qcImageCreateUnspecifiedAssessor)
                .arbitrarySessionInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSessionCustomReadOnlyMinimum() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, sessionDebugNoOutput)
                .arbitrarySessionInputFrom(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchSessionCustomCreateScanMinimum() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, sessionDebugCreateScan)
                .arbitrarySessionInputFrom(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchSessionCustomCreateSpecifiedAssessorMinimum() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, qcImageCreateSpecifiedAssessor)
                .arbitrarySessionInputFrom(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchSessionCustomMultipleCommandsReadMR() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);

        new LaunchTest(ImmutableMap.of(
                sessionDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                standardSessionDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                sessionDebugCreateScan, ExpectedLaunchResult.RESOLUTION_FAILED,
                qcImageCreateSpecifiedAssessor, ExpectedLaunchResult.RESOLUTION_FAILED,
                qcImageCreateUnspecifiedAssessor, ExpectedLaunchResult.UPLOAD_FAILED
        ))
                .arbitrarySessionInputFrom(customProject)
                .run();
    }

    public void testLaunchSessionCustomMultipleCommandsEditMR() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);

        new LaunchTest(ImmutableMap.of(
                sessionDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                standardSessionDebug, ExpectedLaunchResult.SUCCESS,
                sessionDebugCreateScan, ExpectedLaunchResult.UPLOAD_FAILED,
                qcImageCreateSpecifiedAssessor, ExpectedLaunchResult.RESOLUTION_FAILED,
                qcImageCreateUnspecifiedAssessor, ExpectedLaunchResult.UPLOAD_FAILED
        ))
                .arbitrarySessionInputFrom(customProject)
                .run();
    }

    public void testLaunchSessionCustomMultipleCommandsReadMREditManualQC() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY)
                        .permission(DataType.MANUAL_QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);

        new LaunchTest(ImmutableMap.of(
                sessionDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                standardSessionDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                sessionDebugCreateScan, ExpectedLaunchResult.RESOLUTION_FAILED,
                qcImageCreateSpecifiedAssessor, ExpectedLaunchResult.SUCCESS,
                qcImageCreateUnspecifiedAssessor, ExpectedLaunchResult.SUCCESS
        ))
                .arbitrarySessionInputFrom(customProject)
                .run();
    }

    public void testLaunchSessionCustomMultipleCommandsReadMREditWrongQC() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY)
                        .permission(DataType.QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);

        new LaunchTest(ImmutableMap.of(
                sessionDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                standardSessionDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                sessionDebugCreateScan, ExpectedLaunchResult.RESOLUTION_FAILED,
                qcImageCreateSpecifiedAssessor, ExpectedLaunchResult.RESOLUTION_FAILED,
                qcImageCreateUnspecifiedAssessor, ExpectedLaunchResult.UPLOAD_FAILED
        ))
                .arbitrarySessionInputFrom(customProject)
                .run();
    }

    public void testLaunchSessionPrivate() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, sessionDebugNoOutput)
                .arbitrarySessionInputFrom(privateProject)
                .run();
    }

    public void testLaunchSessionProtected() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, sessionDebugNoOutput)
                .arbitrarySessionInputFrom(protectedProject)
                .run();
    }

    public void testLaunchSessionPublic() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardSessionDebug)
                .arbitrarySessionInputFrom(publicProject)
                .run();
    }

    public void testLaunchSessionReadOnlyPublic() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, sessionDebugNoOutput)
                .arbitrarySessionInputFrom(publicProject)
                .run();
    }

    public void testLaunchSessionCreateScanPublic() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, sessionDebugCreateScan)
                .arbitrarySessionInputFrom(publicProject)
                .run();
    }

    public void testLaunchSessionCreateUnspecifiedAssessorPublic() {
        new LaunchTest(ExpectedLaunchResult.UPLOAD_FAILED, qcImageCreateUnspecifiedAssessor)
                .arbitrarySessionInputFrom(publicProject)
                .run();
    }

    public void testLaunchSessionCreateSpecifiedAssessorPublic() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, qcImageCreateSpecifiedAssessor)
                .arbitrarySessionInputFrom(publicProject)
                .run();
    }

    public void testLaunchSessionSharedNoEditOnSource() {
        final Project sourceProject = new Project();
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(memberProject));
        final MRSession sharedSession = new MRSession(sourceProject, sharedSubject).addShare(new Share(memberProject));
        scanWithResource(sharedSession);
        initProject(sourceProject);

        new LaunchTest(ImmutableMap.of(
                standardSessionDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                sessionDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                sessionDebugCreateScan, ExpectedLaunchResult.RESOLUTION_FAILED,
                qcImageCreateUnspecifiedAssessor, ExpectedLaunchResult.SUCCESS,
                qcImageCreateSpecifiedAssessor, ExpectedLaunchResult.SUCCESS
        ), memberProject, generateSharedSessionUri(sharedSession)).run();
    }

    public void testLaunchSessionSharedNoEditOnSourceNoEditOnDest() {
        final Project sourceProject = new Project();
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(collaboratorProject));
        final MRSession sharedSession = new MRSession(sourceProject, sharedSubject).addShare(new Share(collaboratorProject));
        scanWithResource(sharedSession);
        initProject(sourceProject);

        new LaunchTest(ImmutableMap.of(
                standardSessionDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                sessionDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                sessionDebugCreateScan, ExpectedLaunchResult.RESOLUTION_FAILED,
                qcImageCreateUnspecifiedAssessor, ExpectedLaunchResult.UPLOAD_FAILED,
                qcImageCreateSpecifiedAssessor, ExpectedLaunchResult.RESOLUTION_FAILED
        ), collaboratorProject, generateSharedSessionUri(sharedSession)).run();
    }

    public void testLaunchScanOwner() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardScanDebug)
                .arbitraryScanInputFrom(ownerProject)
                .run();
    }

    public void testLaunchScanMember() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardScanDebug)
                .arbitraryScanInputFrom(memberProject)
                .run();
    }

    public void testLaunchScanCollaborator() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardScanDebug)
                .arbitraryScanInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchScanReadOnlyCollaborator() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, scanDebugNoOutput)
                .arbitraryScanInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchScanCustomReadOnlyMinimum() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, scanDebugNoOutput)
                .arbitraryScanInputFrom(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchScanCustomMultipleCommandsReadMR() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);

        new LaunchTest(ImmutableMap.of(
                scanDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                standardScanDebug, ExpectedLaunchResult.RESOLUTION_FAILED
        ))
                .arbitraryScanInputFrom(customProject)
                .run();
    }

    public void testLaunchScanCustomMultipleCommandsEditMR() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);

        new LaunchTest(ImmutableMap.of(
                scanDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                standardScanDebug, ExpectedLaunchResult.SUCCESS
        ))
                .arbitraryScanInputFrom(customProject)
                .run();
    }

    public void testLaunchScanPrivate() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, scanDebugNoOutput)
                .arbitraryScanInputFrom(privateProject)
                .run();
    }

    public void testLaunchScanProtected() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, scanDebugNoOutput)
                .arbitraryScanInputFrom(protectedProject)
                .run();
    }

    public void testLaunchScanPublic() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardScanDebug)
                .arbitraryScanInputFrom(publicProject)
                .run();
    }

    public void testLaunchScanReadOnlyPublic() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, scanDebugNoOutput)
                .arbitraryScanInputFrom(publicProject)
                .run();
    }

    public void testLaunchScanSharedNoEditOnSource() {
        final Project sourceProject = new Project();
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(ownerProject));
        final MRSession sharedSession = new MRSession(sourceProject, sharedSubject)
                .addShare(new Share(ownerProject));
        final MRScan sharedScan = scanWithResource(sharedSession);
        initProject(sourceProject);

        new LaunchTest(ImmutableMap.of(
                standardScanDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                scanDebugNoOutput, ExpectedLaunchResult.SUCCESS
        ), ownerProject, generateSharedScanUri(sharedScan)).run();
    }

    public void testLaunchScanSharedNoEditOnSourceNoEditOnDest() {
        final Project sourceProject = new Project();
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(collaboratorProject));
        final MRSession sharedSession = new MRSession(sourceProject, sharedSubject)
                .addShare(new Share(collaboratorProject));
        final MRScan sharedScan = scanWithResource(sharedSession);
        initProject(sourceProject);

        new LaunchTest(ImmutableMap.of(
                standardScanDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                scanDebugNoOutput, ExpectedLaunchResult.SUCCESS
        ), ownerProject, sharedScan.getUri()).run();
    }

    public void testLaunchSessionAssessorOwner() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardSessionAssessorDebug)
                .arbitrarySessionAssessorInputFrom(ownerProject)
                .run();
    }

    public void testLaunchSessionAssessorMember() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, standardSessionAssessorDebug)
                .arbitrarySessionAssessorInputFrom(memberProject)
                .run();
    }

    public void testLaunchSessionAssessorCollaborator() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardSessionAssessorDebug)
                .arbitrarySessionAssessorInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSessionAssessorReadOnlyCollaborator() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, sessionAssessorDebugNoOutput)
                .arbitrarySessionAssessorInputFrom(collaboratorProject)
                .run();
    }

    public void testLaunchSessionAssessorCustomReadOnlyMinimum() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, sessionAssessorDebugNoOutput)
                .arbitrarySessionAssessorInputFrom(customUserGroupMinPermsProject)
                .run();
    }

    public void testLaunchSessionAssessorCustomMultipleCommandsReadQCs() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY)
                        .permission(DataType.MANUAL_QC, DataAccessLevel.READ_ONLY)
                        .permission(DataType.QC, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);

        new LaunchTest(ImmutableMap.of(
                sessionAssessorDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                standardSessionAssessorDebug, ExpectedLaunchResult.RESOLUTION_FAILED
        ))
                .arbitrarySessionAssessorInputFrom(customProject)
                .run();
    }

    public void testLaunchSessionAssessorCustomMultipleCommandsEditQCs() {
        final Project customProject = new Project().addUserGroup(
                new CustomUserGroup("customgroup")
                        .permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.MANUAL_QC, DataAccessLevel.CREATE_AND_EDIT)
                        .permission(DataType.QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        );
        initProject(customProject);

        new LaunchTest(ImmutableMap.of(
                sessionAssessorDebugNoOutput, ExpectedLaunchResult.SUCCESS,
                standardSessionAssessorDebug, ExpectedLaunchResult.SUCCESS
        ))
                .arbitrarySessionAssessorInputFrom(customProject)
                .run();
    }

    public void testLaunchSessionAssessorPrivate() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, sessionAssessorDebugNoOutput)
                .arbitrarySessionAssessorInputFrom(privateProject)
                .run();
    }

    public void testLaunchSessionAssessorProtected() {
        new LaunchTest(ExpectedLaunchResult.FORBIDDEN, sessionAssessorDebugNoOutput)
                .arbitrarySessionAssessorInputFrom(protectedProject)
                .run();
    }

    public void testLaunchSessionAssessorPublic() {
        new LaunchTest(ExpectedLaunchResult.RESOLUTION_FAILED, standardSessionAssessorDebug)
                .arbitrarySessionAssessorInputFrom(publicProject)
                .run();
    }

    public void testLaunchSessionAssessorReadOnlyPublic() {
        new LaunchTest(ExpectedLaunchResult.SUCCESS, sessionAssessorDebugNoOutput)
                .arbitrarySessionAssessorInputFrom(publicProject)
                .run();
    }

    public void testLaunchSessionAssessorSharedNoEditOnSource() {
        final Project sourceProject = new Project();
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(ownerProject));
        final MRSession sharedSession = new MRSession(sourceProject, sharedSubject)
                .addShare(new Share(ownerProject));
        final QC sharedAssessor = qcWithResource(sourceProject, sharedSubject, sharedSession)
                .addShare(new Share(ownerProject));
        initProject(sourceProject);

        new LaunchTest(ImmutableMap.of(
                standardSessionAssessorDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                sessionAssessorDebugNoOutput, ExpectedLaunchResult.SUCCESS
        ), ownerProject, generateSharedAssessorUri(sharedAssessor)).run();
    }

    public void testLaunchSessionAssessorSharedNoEditOnSourceNoEditOnDest() {
        final Project sourceProject = new Project();
        final Subject sharedSubject = new Subject(sourceProject).addShare(new Share(collaboratorProject));
        final MRSession sharedSession = new MRSession(sourceProject, sharedSubject)
                .addShare(new Share(collaboratorProject));
        final QC sharedAssessor = qcWithResource(sourceProject, sharedSubject, sharedSession)
                .addShare(new Share(collaboratorProject));
        initProject(sourceProject);

        new LaunchTest(ImmutableMap.of(
                standardSessionAssessorDebug, ExpectedLaunchResult.RESOLUTION_FAILED,
                sessionAssessorDebugNoOutput, ExpectedLaunchResult.SUCCESS
        ), collaboratorProject, generateSharedAssessorUri(sharedAssessor)).run();
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

        AvailableCommandsTest expectedWrappers(Wrapper... wrappers) {
            expectedWrappers = Arrays.stream(wrappers)
                    .map(Wrapper::getId)
                    .collect(Collectors.toSet());
            return this;
        }

        AvailableCommandsTest expectedException(Class<? extends Exception> exceptionClass) {
            expectedException = exceptionClass;
            return this;
        }

        void run() {
            Set<Integer> actualWrappers;
            try {
                actualWrappers = mainInterface().readAvailableCommands(dataType, project)
                        .stream()
                        .filter(CommandSummaryForContext::isEnabled)
                        .map(wrapper -> (int) wrapper.getWrapperId())
                        .collect(Collectors.toSet());
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

    private class LaunchTest {
        private final Map<Wrapper, ExpectedLaunchResult> expectedLaunchResultMap;
        private Project project;
        private String uri;

        LaunchTest(Map<Wrapper, ExpectedLaunchResult> expectedLaunchResultMap, Project project, String uri) {
            this.expectedLaunchResultMap = expectedLaunchResultMap;
            this.project = project;
            this.uri = uri;
        }

        LaunchTest(Map<Wrapper, ExpectedLaunchResult> expectedLaunchResultMap) {
            this(expectedLaunchResultMap, null, null);
        }

        LaunchTest(ExpectedLaunchResult expectedLaunchResult, Wrapper wrapper, Project project, String uri) {
            this(Collections.singletonMap(wrapper, expectedLaunchResult), project, uri);
        }

        LaunchTest(ExpectedLaunchResult expectedLaunchResult, Wrapper wrapper) {
            this(expectedLaunchResult, wrapper, null, null);
        }

        LaunchTest projectInput(Project project) {
            this.project = project;
            uri = project.getUri();
            return this;
        }

        LaunchTest arbitrarySubjectInputFrom(Project project) {
            this.project = project;
            uri = project.getSubjects().get(0).getUri();
            return this;
        }

        LaunchTest arbitrarySessionInputFrom(Project project) {
            this.project = project;
            uri = project.getSubjects().get(0).getSessions().get(0).getUri();
            return this;
        }

        LaunchTest arbitraryScanInputFrom(Project project) {
            this.project = project;
            uri = project.getSubjects().get(0).getSessions().get(0).getScans().get(0).getUri();
            return this;
        }

        LaunchTest arbitrarySessionAssessorInputFrom(Project project) {
            this.project = project;
            final Subject subject = project.getSubjects().get(0);
            final ImagingSession session = subject.getSessions().get(0);
            final SessionAssessor assessor = session.getAssessors()
                    .stream()
                    .filter(candidate -> candidate.getDataType().equals(DataType.QC)) // the QC we generated ourselves will have accession number cached & resource created
                    .findFirst()
                    .get();
            uri = assessor.getUri();
            return this;
        }

        void run() {
            for (Map.Entry<Wrapper, ExpectedLaunchResult> testEntry : expectedLaunchResultMap.entrySet()) {
                final Class<? extends Exception> expectedException = testEntry.getValue().expectedException;
                int workflowId;
                try {
                    workflowId = mainInterface().launchContainer(project, testEntry.getKey(), uri, BASE_DEBUG_LAUNCH_PARAMS);
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
                mainAdminInterface().waitForWorkflowTerminal(workflowId);
                assertEquals(testEntry.getValue().expectedWorkflowStatus, mainAdminInterface().readWorkflowStatus(workflowId));
            }
        }
    }

    private enum ExpectedLaunchResult {
        SUCCESS(null, "Complete"),
        RESOLUTION_FAILED(null, "Failed (Command resolution)"),
        UPLOAD_FAILED(null, "Failed (Upload)"),
        FORBIDDEN(ForbiddenException.class, null);

        ExpectedLaunchResult(Class<? extends Exception> expectedException, String expectedWorkflowStatus) {
            this.expectedException = expectedException;
            this.expectedWorkflowStatus = expectedWorkflowStatus;
        }

        private final Class<? extends Exception> expectedException;
        private final String expectedWorkflowStatus;
    }

    private void initProject(Project project) {
        final Subject subject = new Subject(project);
        final MRSession session = new MRSession(project, subject);
        scanWithResource(session);
        qcWithResource(project, subject, session);

        mainAdminInterface().createProject(project);
        projects.add(project);
        for (Image image : TEST_IMAGES) {
            for (Command command : mainAdminInterface().readCommands(image)) {
                for (Wrapper wrapper : command.getWrappers()) {
                    mainAdminInterface().setWrapperStatusOnProject(wrapper, project, true);
                }
            }
        }
    }

    private Wrapper lookupWrapper(String wrapperName) {
        for (Image image : TEST_IMAGES) {
            for (Command command : mainAdminInterface().readCommands(image)) {
                for (Wrapper wrapper : command.getWrappers()) {
                    if (wrapperName.equals(wrapper.getName())) {
                        return wrapper;
                    }
                }
            }
        }
        fail("Missing wrapper: " + wrapperName);
        return null;
    }

    private String generateSharedSessionUri(ImagingSession session) {
        return "/archive/experiments/" + session.getAccessionNumber();
    }

    private String generateSharedScanUri(Scan scan) {
        return String.format("%s/scans/%s", generateSharedSessionUri(scan.getSession()), scan.getId());
    }

    private String generateSharedAssessorUri(SessionAssessor assessor) {
        return String.format("%s/assessors/%s", generateSharedSessionUri(assessor.getParentSession()), assessor.getAccessionNumber());
    }

    private QC qcWithResource(Project project, Subject subject, ImagingSession session) {
        final QC qc = new QC(project, subject, session);
        new SessionAssessorResource(project, subject, session, qc, RESOURCE_NAME);
        return qc;
    }

    private MRScan scanWithResource(ImagingSession session) {
        final MRScan scan = new MRScan(session, "1");
        new ScanResource(session.getPrimaryProject(), session.getSubject(), session, scan, RESOURCE_NAME);
        return scan;
    }

    private static Map<String, String> makeContainerLaunchReqBody() {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("command", "echo hello world");
        queryParams.put("output-file", "out.txt");
        return queryParams;
    }

}
