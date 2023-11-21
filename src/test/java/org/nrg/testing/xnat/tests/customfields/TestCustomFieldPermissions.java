package org.nrg.testing.xnat.tests.customfields;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.DataAccessLevel;
import org.nrg.xnat.pogo.*;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.scans.PETScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.users.CustomUserGroup;
import org.nrg.xnat.versions.Xnat_1_8_8;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.testng.AssertJUnit.assertEquals;

@AddedIn(Xnat_1_8_8.class)
public class TestCustomFieldPermissions extends BaseCustomFieldsRestTest {

    private static final String MR_NAME = "CUSTOM_MR";
    private static final String PET_NAME = "CUSTOM_PET";
    private static final String SCAN_ID = "1";
    private final String fieldKey = "FIELD_001";
    private final String fieldValue = "FIELD_VAL_001";
    private final Map<String, Object> fieldsMap = Collections.singletonMap(fieldKey, fieldValue);
    private final List<Project> projectsToRemove = new ArrayList<>();
    Project ownerProject;
    Project privateProject;
    Project protectedProject;
    Project publicProject;
    Project customUserGroupProject;
    Project collaboratorProject;
    Project sharingProject;

    @AfterMethod(alwaysRun = true)
    public void teardownProjects() {
        for (Project project : projectsToRemove) {
            restDriver.deleteProjectSilently(mainAdminUser, project);
        }
        projectsToRemove.clear();
    }

    @Test
    public void testCustomFieldProjectPermissions() {
        setupFieldProjects();
        final CustomFieldScope privateProjectFieldScope = new CustomFieldScope().project(privateProject);
        final CustomFieldScope protectedProjectFieldScope = new CustomFieldScope().project(protectedProject);
        final CustomFieldScope publicProjectFieldScope = new CustomFieldScope().project(publicProject);
        final CustomFieldScope collaboratorProjectFieldScope = new CustomFieldScope().project(collaboratorProject);
        final CustomFieldScope nonexistentScope = new CustomFieldScope().project("PROJECTSHOULDNTEXIST");
        final Project memberProject = createAndRegister(new Project().addMember(mainUser));
        final CustomFieldScope memberScope = new CustomFieldScope().project(memberProject);

        verifyGet403(nonexistentScope);
        verifyGet403(privateProjectFieldScope);
        verifyGet403(protectedProjectFieldScope); // See XNAT-7643
        assertEquals(fieldsMap, mainInterface().readCustomFields(publicProjectFieldScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(publicProjectFieldScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(collaboratorProjectFieldScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(memberScope));

        verifyGetIndividual403(nonexistentScope, fieldKey);
        verifyGetIndividual403(privateProjectFieldScope, fieldKey);
        verifyGetIndividual403(protectedProjectFieldScope, fieldKey); // See XNAT-7643
        assertEquals(fieldValue, mainInterface().readCustomField(publicProjectFieldScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(collaboratorProjectFieldScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(memberScope, fieldKey));

        verifyPut403(nonexistentScope);
        verifyPut403(privateProjectFieldScope);
        verifyPut403(protectedProjectFieldScope);
        verifyPut403(publicProjectFieldScope);
        verifyPut403(collaboratorProjectFieldScope);
        verifyPut403(memberScope);

        verifyDelete403(nonexistentScope, fieldKey);
        verifyDelete403(privateProjectFieldScope, fieldKey);
        verifyDelete403(protectedProjectFieldScope, fieldKey);
        verifyDelete403(publicProjectFieldScope, fieldKey);
        verifyDelete403(collaboratorProjectFieldScope, fieldKey);
        verifyDelete403(memberScope, fieldKey);
    }

    @Test
    public void testCustomFieldSubjectPermissions() {
        setupFieldProjects();
        final Subject sharingSubject = sharingProject.getSubjects().get(0);
        mainAdminInterface().shareSubject(sharingProject, sharingSubject, new Share(ownerProject));
        final CustomFieldScope nonexistentScope = new CustomFieldScope().subject("NONEXISTENTSUBJECT");
        final CustomFieldScope privateSubjectScope = new CustomFieldScope().subject(privateProject.getSubjects().get(0));
        final CustomFieldScope protectedSubjectScope = new CustomFieldScope().subject(protectedProject.getSubjects().get(0));
        final CustomFieldScope publicSubjectScope = new CustomFieldScope().subject(publicProject.getSubjects().get(0));
        final CustomFieldScope customSubjectScope = new CustomFieldScope().subject(customUserGroupProject.getSubjects().get(0));
        final CustomFieldScope collaboratorSubjectScope = new CustomFieldScope().subject(collaboratorProject.getSubjects().get(0));
        final CustomFieldScope sharedSubjectScope = new CustomFieldScope().subject(sharingSubject);

        verifyGet403(nonexistentScope);
        verifyGet403(privateSubjectScope);
        verifyGet403(protectedSubjectScope);
        assertEquals(fieldsMap, mainInterface().readCustomFields(publicSubjectScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(customSubjectScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(collaboratorSubjectScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(sharedSubjectScope));

        verifyGetIndividual403(nonexistentScope, fieldKey);
        verifyGetIndividual403(privateSubjectScope, fieldKey);
        verifyGetIndividual403(protectedSubjectScope, fieldKey);
        assertEquals(fieldValue, mainInterface().readCustomField(publicSubjectScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(customSubjectScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(collaboratorSubjectScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(sharedSubjectScope, fieldKey));

        verifyPut403(nonexistentScope);
        verifyPut403(privateSubjectScope);
        verifyPut403(protectedSubjectScope);
        verifyPut403(publicSubjectScope);
        verifyPut403(customSubjectScope);
        verifyPut403(collaboratorSubjectScope);
        verifyPut403(sharedSubjectScope);

        verifyDelete403(nonexistentScope, fieldKey);
        verifyDelete403(privateSubjectScope, fieldKey);
        verifyDelete403(protectedSubjectScope, fieldKey);
        verifyDelete403(publicSubjectScope, fieldKey);
        verifyDelete403(customSubjectScope, fieldKey);
        verifyDelete403(collaboratorSubjectScope, fieldKey);
        verifyDelete403(sharedSubjectScope, fieldKey);
    }

    @Test
    public void testCustomFieldExperimentPermissions() {
        setupFieldProjects();
        final ImagingSession sharingSession = sharingProject.getSubjects().get(0).getSessions().get(0);
        mainAdminInterface().shareSubjectAssessor(sharingSession, new Share(ownerProject, "SHARED_SESSION"));
        final CustomFieldScope nonexistentScope = new CustomFieldScope().experiment("NONEXISTENTSUBJECT");
        final CustomFieldScope privateSessionScope = new CustomFieldScope().experiment(privateProject.getSubjects().get(0).getSessions().get(0));
        final CustomFieldScope protectedSessionScope = new CustomFieldScope().experiment(protectedProject.getSubjects().get(0).getSessions().get(0));
        final CustomFieldScope publicSessionScope = new CustomFieldScope().experiment(publicProject.getSubjects().get(0).getSessions().get(0));
        final CustomFieldScope customReadSessionScope = new CustomFieldScope().experiment(customUserGroupProject.getSubjects().get(0).findSubjectAssessor(MR_NAME));
        final CustomFieldScope customHiddenSessionScope = new CustomFieldScope().experiment(customUserGroupProject.getSubjects().get(0).findSubjectAssessor(PET_NAME));
        final CustomFieldScope collaboratorSessionScope = new CustomFieldScope().experiment(collaboratorProject.getSubjects().get(0).getSessions().get(0));
        final CustomFieldScope sharedSessionScope = new CustomFieldScope().experiment(sharingSession);

        verifyGet403(nonexistentScope);
        verifyGet403(privateSessionScope);
        verifyGet403(protectedSessionScope);
        verifyGet403(customHiddenSessionScope);
        assertEquals(fieldsMap, mainInterface().readCustomFields(publicSessionScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(customReadSessionScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(collaboratorSessionScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(sharedSessionScope));

        verifyGetIndividual403(nonexistentScope, fieldKey);
        verifyGetIndividual403(privateSessionScope, fieldKey);
        verifyGetIndividual403(protectedSessionScope, fieldKey);
        verifyGetIndividual403(customHiddenSessionScope, fieldKey);
        assertEquals(fieldValue, mainInterface().readCustomField(publicSessionScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(customReadSessionScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(collaboratorSessionScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(sharedSessionScope, fieldKey));

        verifyPut403(nonexistentScope);
        verifyPut403(privateSessionScope);
        verifyPut403(protectedSessionScope);
        verifyPut403(customHiddenSessionScope);
        verifyPut403(publicSessionScope);
        verifyPut403(customReadSessionScope);
        verifyPut403(collaboratorSessionScope);
        verifyPut403(sharedSessionScope);

        verifyDelete403(nonexistentScope, fieldKey);
        verifyDelete403(privateSessionScope, fieldKey);
        verifyDelete403(protectedSessionScope, fieldKey);
        verifyDelete403(customHiddenSessionScope, fieldKey);
        verifyDelete403(publicSessionScope, fieldKey);
        verifyDelete403(customReadSessionScope, fieldKey);
        verifyDelete403(collaboratorSessionScope, fieldKey);
        verifyDelete403(sharedSessionScope, fieldKey);
    }

    @Test
    public void testCustomFieldScanPermissions() {
        setupFieldProjects();
        final ImagingSession sharingSession = sharingProject.getSubjects().get(0).getSessions().get(0);
        mainAdminInterface().shareSubjectAssessor(sharingSession, new Share(ownerProject, "SHARED_SESSION"));
        final CustomFieldScope nonexistentScope = new CustomFieldScope().experiment(sharingSession).scan("NOPE");
        final CustomFieldScope privateScanScope = new CustomFieldScope().experiment(privateProject.getSubjects().get(0).getSessions().get(0)).scan(SCAN_ID);
        final CustomFieldScope protectedScanScope = new CustomFieldScope().experiment(protectedProject.getSubjects().get(0).getSessions().get(0)).scan(SCAN_ID);
        final CustomFieldScope publicScanScope = new CustomFieldScope().experiment(publicProject.getSubjects().get(0).getSessions().get(0)).scan(SCAN_ID);
        final CustomFieldScope customReadScanScope = new CustomFieldScope().experiment(customUserGroupProject.getSubjects().get(0).findSubjectAssessor(MR_NAME)).scan(SCAN_ID);
        final CustomFieldScope customHiddenScanScope = new CustomFieldScope().experiment(customUserGroupProject.getSubjects().get(0).findSubjectAssessor(PET_NAME)).scan(SCAN_ID);
        final CustomFieldScope collaboratorScanScope = new CustomFieldScope().experiment(collaboratorProject.getSubjects().get(0).getSessions().get(0)).scan(SCAN_ID);
        final CustomFieldScope sharedScanScope = new CustomFieldScope().experiment(sharingSession).scan(SCAN_ID);

        verifyGet404(nonexistentScope);
        verifyGet403(privateScanScope);
        verifyGet403(protectedScanScope);
        verifyGet403(customHiddenScanScope);
        assertEquals(fieldsMap, mainInterface().readCustomFields(publicScanScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(customReadScanScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(collaboratorScanScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(sharedScanScope));

        verifyGetIndividual404(nonexistentScope, fieldKey);
        verifyGetIndividual403(privateScanScope, fieldKey);
        verifyGetIndividual403(protectedScanScope, fieldKey);
        verifyGetIndividual403(customHiddenScanScope, fieldKey);
        assertEquals(fieldValue, mainInterface().readCustomField(publicScanScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(customReadScanScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(collaboratorScanScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(sharedScanScope, fieldKey));

        verifyPut403(nonexistentScope);
        verifyPut403(privateScanScope);
        verifyPut403(protectedScanScope);
        verifyPut403(customHiddenScanScope);
        verifyPut403(publicScanScope);
        verifyPut403(customReadScanScope);
        verifyPut403(collaboratorScanScope);
        verifyPut403(sharedScanScope);

        verifyDelete403(nonexistentScope, fieldKey);
        verifyDelete403(privateScanScope, fieldKey);
        verifyDelete403(protectedScanScope, fieldKey);
        verifyDelete403(customHiddenScanScope, fieldKey);
        verifyDelete403(publicScanScope, fieldKey);
        verifyDelete403(customReadScanScope, fieldKey);
        verifyDelete403(collaboratorScanScope, fieldKey);
        verifyDelete403(sharedScanScope, fieldKey);
    }

    @Test
    public void testCustomFieldAssessorPermissions() {
        setupFieldProjects();
        final ImagingSession privateSession = privateProject.getSubjects().get(0).getSessions().get(0);
        final ImagingSession protectedSession = protectedProject.getSubjects().get(0).getSessions().get(0);
        final ImagingSession publicSession = publicProject.getSubjects().get(0).getSessions().get(0);
        final ImagingSession customHiddenSession = (ImagingSession) customUserGroupProject.getSubjects().get(0).findSubjectAssessor(MR_NAME);
        final ImagingSession collaboratorSession = collaboratorProject.getSubjects().get(0).getSessions().get(0);
        final ImagingSession sharingSession = sharingProject.getSubjects().get(0).getSessions().get(0);
        final SessionAssessor sharingAssessor = sharingSession.getAssessors().get(0);
        mainAdminInterface().shareSubjectAssessor(sharingSession, new Share(ownerProject, "SHARED_SESSION"));
        mainAdminInterface().shareSessionAssessor(sharingSession, sharingAssessor, new Share(ownerProject, "SHARED_QC"));

        final CustomFieldScope nonexistentScope = new CustomFieldScope().experiment(sharingSession).assessor("NOPE");
        final CustomFieldScope privateAssessorScope = new CustomFieldScope().experiment(privateSession).assessor(privateSession.getAssessors().get(0));
        final CustomFieldScope protectedAssessorScope = new CustomFieldScope().experiment(protectedSession).assessor(protectedSession.getAssessors().get(0));
        final CustomFieldScope publicAssessorScope = new CustomFieldScope().experiment(publicSession).assessor(publicSession.getAssessors().get(0));
        final CustomFieldScope customHiddenAssessorScope = new CustomFieldScope().experiment(customHiddenSession).assessor(customHiddenSession.getAssessors().get(0));
        final CustomFieldScope collaboratorAssessorScope = new CustomFieldScope().experiment(collaboratorSession).assessor(collaboratorSession.getAssessors().get(0));
        final CustomFieldScope sharedAssessorScope = new CustomFieldScope().experiment(sharingSession).assessor(sharingAssessor);

        verifyGet404(nonexistentScope);
        verifyGet403(privateAssessorScope);
        verifyGet403(protectedAssessorScope);
        verifyGet404(customHiddenAssessorScope);
        assertEquals(fieldsMap, mainInterface().readCustomFields(publicAssessorScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(collaboratorAssessorScope));
        assertEquals(fieldsMap, mainInterface().readCustomFields(sharedAssessorScope));

        verifyGetIndividual404(nonexistentScope, fieldKey);
        verifyGetIndividual403(privateAssessorScope, fieldKey);
        verifyGetIndividual403(protectedAssessorScope, fieldKey);
        verifyGetIndividual404(customHiddenAssessorScope, fieldKey);
        assertEquals(fieldValue, mainInterface().readCustomField(publicAssessorScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(collaboratorAssessorScope, fieldKey));
        assertEquals(fieldValue, mainInterface().readCustomField(sharedAssessorScope, fieldKey));

        verifyPut403(nonexistentScope);
        verifyPut403(privateAssessorScope);
        verifyPut403(protectedAssessorScope);
        verifyPut403(customHiddenAssessorScope);
        verifyPut403(publicAssessorScope);
        verifyPut403(collaboratorAssessorScope);
        verifyPut403(sharedAssessorScope);

        verifyDelete403(nonexistentScope, fieldKey);
        verifyDelete403(privateAssessorScope, fieldKey);
        verifyDelete403(protectedAssessorScope, fieldKey);
        verifyDelete403(customHiddenAssessorScope, fieldKey);
        verifyDelete403(publicAssessorScope, fieldKey);
        verifyDelete403(collaboratorAssessorScope, fieldKey);
        verifyDelete403(sharedAssessorScope, fieldKey);
    }

    @Test
    public void testCustomFieldMemberPermissions() {
        runAffirmativePermissionsTest(new Project().addMember(mainUser));
    }

    @Test
    public void testCustomFieldCustomUserGroupPermissions() {
        runAffirmativePermissionsTest(new Project().addUserGroup(
                new CustomUserGroup("customgroup").
                        permission(DataType.SUBJECT, DataAccessLevel.CREATE_AND_EDIT).
                        permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT).
                        permission(DataType.QC, DataAccessLevel.CREATE_AND_EDIT),
                Collections.singletonList(mainUser)
        ));
    }

    private void setupFieldProjects() {
        ownerProject = createAndRegister(new Project().addOwner(mainUser));
        privateProject = createAndRegister(new Project().accessibility(Accessibility.PRIVATE));
        protectedProject = createAndRegister(new Project().accessibility(Accessibility.PROTECTED));
        publicProject = createAndRegister(new Project().accessibility(Accessibility.PUBLIC));
        customUserGroupProject = createAndRegister(new Project().addUserGroup(
                new CustomUserGroup("customgroup").
                        permission(DataType.MR_SESSION, DataAccessLevel.READ_ONLY),
                Collections.singletonList(mainUser)
        ));
        collaboratorProject = createAndRegister(new Project().accessibility(Accessibility.PRIVATE).addCollaborator(mainUser));
        sharingProject = createAndRegister(new Project().accessibility(Accessibility.PRIVATE));
    }

    private Project createAndRegister(Project project) {
        final Subject subject = new Subject(project);
        final MRSession mrSession = new MRSession(project, subject, MR_NAME);
        final Scan scan = new MRScan(mrSession, SCAN_ID);
        final SessionAssessor qc = new QC(project, subject, mrSession);
        final PETSession petSession = new PETSession(project, subject, PET_NAME).addScan(new PETScan().id(SCAN_ID));

        mainAdminInterface().createProject(project);
        projectsToRemove.add(project);
        mainAdminInterface().setCustomFields(new CustomFieldScope().project(project), fieldsMap);
        mainAdminInterface().setCustomFields(new CustomFieldScope().subject(subject), fieldsMap);
        mainAdminInterface().setCustomFields(new CustomFieldScope().experiment(mrSession), fieldsMap);
        mainAdminInterface().setCustomFields(new CustomFieldScope().experiment(petSession), fieldsMap);
        mainAdminInterface().setCustomFields(new CustomFieldScope().experiment(mrSession).scan(scan), fieldsMap);
        mainAdminInterface().setCustomFields(new CustomFieldScope().experiment(mrSession).assessor(qc), fieldsMap);
        return project;
    }

    private void runAffirmativePermissionsTest(Project projectSpec) {
        final String newKey = "a-new-key";
        final String newValue = "NEW_VALUE_OVERWRITTEN";
        final Map<String, Object> expectedFinalMap = Collections.singletonMap(newKey, newValue);
        final Project memberProject = createAndRegister(projectSpec);
        final Subject subject = memberProject.getSubjects().get(0);
        final ImagingSession session = (ImagingSession) subject.findSubjectAssessor(MR_NAME);
        final CustomFieldScope subjectScope = new CustomFieldScope().subject(subject);
        final CustomFieldScope sessionScope = new CustomFieldScope().experiment(session);
        final CustomFieldScope scanScope = new CustomFieldScope().experiment(session).scan(session.getScans().get(0));
        final CustomFieldScope assessorScope = new CustomFieldScope().experiment(session).assessor(session.getAssessors().get(0));

        for (CustomFieldScope testedScope : Arrays.asList(subjectScope, sessionScope, scanScope, assessorScope)) {
            mainInterface().setCustomFields(testedScope, expectedFinalMap);
            mainInterface().deleteCustomField(testedScope, fieldKey);
            assertEquals(expectedFinalMap, mainInterface().readCustomFields(testedScope));
            assertEquals(newValue, mainInterface().readCustomField(testedScope, newKey));
        }
    }

}
