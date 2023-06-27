package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.TestGroups;
import org.nrg.testing.TimeUtils;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.DataAccessLevel;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.enums.Handedness;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Investigator;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Share;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.scans.PETScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.users.CustomUserGroup;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.pogo.users.UserGroup;
import org.nrg.xnat.util.RandomUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.nrg.testing.TestGroups.SEARCH;

@Test(groups = SEARCH)
public class BaseSearchPermissionsTest extends BaseSearchFilterTest {

    private static boolean permsSetupDone = false;
    protected static final String MODALITY = "modality";
    protected static final String UID = "UID";
    protected static final String FIELD_STRENGTH = "fieldStrength";
    protected static User insertUser;
    protected static final Project publicProject = newProject("PUBLIC")
            .accessibility(Accessibility.PUBLIC)
            .description("Example public project for REST tests")
            .pi(
                    new Investigator()
                            .firstname("John")
                            .lastname("Scientist")
            ).investigators(
                    Arrays.asList(
                            new Investigator()
                                    .firstname("A")
                                    .lastname("A"),
                            new Investigator()
                                    .firstname("B")
                                    .lastname("B")
                    )
            ).prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
    protected static final Subject publicSubject = new Subject(publicProject);
    protected static final PETSession publicPet = new PETSession(publicProject, publicSubject);
    protected static final MRSession publicMr = new MRSession(publicProject, publicSubject);
    protected static final MRScan publicMrScan = new MRScan(publicMr, "0");
    protected static final QC publicMrQc = new QC(publicProject, publicSubject, publicMr);
    protected static final QC publicMrQc2 = new QC(publicProject, publicSubject, publicMr);

    protected static final Project protectedProject = newProject("PROT").accessibility(Accessibility.PROTECTED).prearchiveCode(PrearchiveCode.MANUAL);
    protected static final Subject protectedSubject = new Subject(protectedProject);
    protected static final MRSession protectedMr = new MRSession(protectedProject, protectedSubject);
    protected static final MRScan protectedMrScan = new MRScan(protectedMr, "0");

    protected static final Project privateProject = newProject("PRIV").accessibility(Accessibility.PRIVATE);
    protected static final Subject privateSubject = new Subject(privateProject);
    protected static final MRSession privateMr = new MRSession(privateProject, privateSubject);
    protected static final MRScan privateMrScan = new MRScan(privateMr, "0");

    protected static final Project ownerProject = newProject("OWNR");
    protected static final Subject ownerSubject = new Subject(ownerProject);
    protected static final Project memberProject = newProject("MEMB");
    protected static final Project collaboratorProject = newProject("COLLAB");
    protected static final Subject collaboratorSubject = new Subject(collaboratorProject);
    protected static final MRSession collaboratorSubjectMr = new MRSession(collaboratorProject, collaboratorSubject)
            .specificField(MODALITY, "MR")
            .specificField(FIELD_STRENGTH, "3.0");
    protected static final PETSession collaboratorSubjectPet = new PETSession(collaboratorProject, collaboratorSubject)
            .specificField(MODALITY, "PT")
            .specificField(UID, "1.2.3.4");
    protected static final Subject collaboratorSubjectNoExpts = new Subject(collaboratorProject);
    protected static final Scan collaboratorSubjectMrScan = new MRScan(collaboratorSubjectMr, "1")
            .seriesDescription(T1)
            .quality("usable");

    protected static final UserGroup customUserGroup = new CustomUserGroup("MR-only")
            .permission(DataType.SUBJECT, DataAccessLevel.READ_ONLY)
            .permission(DataType.MR_SESSION, DataAccessLevel.CREATE_AND_EDIT);
    protected static final Project customUserGroupProject = newProject("CUSTM");
    protected static final Subject customGroupSubject = new Subject(customUserGroupProject)
            .gender(Gender.MALE)
            .handedness(Handedness.AMBIDEXTROUS)
            .education(12)
            .race("NOT SPECIFIED")
            .ethnicity("NOT SPECIFIED")
            .group("Symmetric")
            .yob(2000)
            .height(70)
            .weight(175)
            .src("Internet");
    protected static final Subject customGroupSubjectDob = new Subject(customUserGroupProject)
            .dob(LocalDate.parse("1990-05-05"));
    protected static final Subject customGroupSubjectAge = new Subject(customUserGroupProject)
            .age(75);

    protected static final MRSession customGroupMrSession = new MRSession(customUserGroupProject, customGroupSubject);
    protected static final MRScan customGroupMrSessionMrScan = new MRScan(customGroupMrSession, "1");
    protected static final PETScan customGroupMrSessionPetScan = new PETScan(customGroupMrSession, "2");
    protected static final QC customGroupQc = new QC(customUserGroupProject, customGroupSubject, customGroupMrSession);
    protected static final PETSession customGroupPetSession = new PETSession(customUserGroupProject, customGroupSubject);
    protected static final MRScan customGroupPetSessionMrScan = new MRScan(customGroupPetSession, "1");
    protected static final PETScan customGroupPetSessionPetScan = new PETScan(customGroupPetSession, "2");

    protected static final List<Project> detectableProjects = Arrays.asList(publicProject, protectedProject, customUserGroupProject, ownerProject, memberProject, collaboratorProject);
    protected static final List<Project> allProjects = Arrays.asList(privateProject, publicProject, protectedProject, customUserGroupProject, ownerProject, memberProject, collaboratorProject);

    @BeforeClass(groups = TestGroups.SEARCH)
    protected void setupPermsTests() {
        if (!permsSetupDone) {
            final User otherUser = createGenericUsers(1).get(0);
            final XnatInterface xnatInterface = interfaceFor(otherUser);
            ownerProject.addOwner(mainUser);
            memberProject.addMember(mainUser);
            collaboratorProject.addCollaborator(mainUser);
            customUserGroupProject.addUserGroup(customUserGroup, Collections.singletonList(mainUser));
            for (Project project : allProjects) {
                xnatInterface.createProject(project);
            }
            interfaceFor(otherUser).shareSubject(publicProject, publicSubject, new Share(customUserGroupProject));
            interfaceFor(otherUser).shareSubject(publicProject, publicSubject, new Share(collaboratorProject));
            for (Project project : allProjects) {
                final Subject temporarySubject = new Subject(project); // make a "last workflow" in the projects we can know the order of
                xnatInterface.createSubject(temporarySubject);
                xnatInterface.deleteSubject(temporarySubject);
                TimeUtils.sleep(1000); // a method in XNAT rounds only keeps second precision and this time is important downstream
            }
            insertUser = otherUser;
            permsSetupDone = true;
        }
    }

    protected static Project newProject(String concept) {
        final String identifier = concept + "_" + RandomUtils.randomID(10);
        return registerSuiteTempProject()
                .id(identifier)
                .runningTitle(identifier + "_" + "RUN")
                .title(identifier + "_" + "FULL_TITLE");
    }

}
