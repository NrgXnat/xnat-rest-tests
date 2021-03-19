package org.nrg.testing.xnat.tests;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.nrg.testing.TestNgUtils;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.Users;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.SiteDataRole;
import org.nrg.xnat.pogo.*;
import org.nrg.xnat.pogo.experiments.Experiment;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.extensions.session_assessor.ShareSafeSessionAssessorExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.ShareSafeSubjectAssessorExtension;
import org.nrg.xnat.pogo.users.CustomUserGroup;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.pogo.users.UserGroups;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static org.nrg.xnat.enums.DataAccessLevel.*;
import static org.nrg.xnat.pogo.DataType.MR_SESSION;
import static org.nrg.xnat.pogo.DataType.QC;

public class BasePermissionComparisonTest extends BaseXnatRestTest {

    private final User allDataAdmin = Users.genericAccount().dataRole(SiteDataRole.ALL_DATA_ADMIN);
    private final User allDataAccess = Users.genericAccount().dataRole(SiteDataRole.ALL_DATA_ACCESS);
    private final User owner = Users.genericAccount();
    private final User member = Users.genericAccount();
    private final User collaborator = Users.genericAccount();
    private final User readSubjects = Users.genericAccount();
    private final User manageSubjects = Users.genericAccount();
    private final User readMR = Users.genericAccount();
    private final User editMR = Users.genericAccount();
    private final User manageMR = Users.genericAccount();
    private final User readQC = Users.genericAccount(); // and read MR
    private final User manageQC = Users.genericAccount(); // and read MR
    private final User guest = User.GUEST;
    private final List<User> allUsers = Arrays.asList(allDataAdmin, allDataAccess, owner, member, collaborator, readSubjects, manageSubjects, readMR, editMR, manageMR, readQC, manageQC, mainUser, guest, mainAdminUser);
    private final List<User> fullPermissions = Arrays.asList(allDataAdmin, mainAdminUser);
    private final List<User> readAll = Arrays.asList(allDataAdmin, allDataAccess, mainAdminUser);
    private final List<User> noPermissions = Arrays.asList(guest, mainUser);
    private final Project privateProject = generateTestProject("PRIVATE", Accessibility.PRIVATE, true);
    private final Project protectedProject = generateTestProject("PROTECTED", Accessibility.PROTECTED, true);
    private final Project publicProject = generateTestProject("PUBLIC", Accessibility.PUBLIC, true);
    private final Project outsideProject = generateTestProject("OUTSIDE", Accessibility.PRIVATE, false);
    private final Project nonexistent = new Project();
    private final Subject nonexistentSubject = new Subject(nonexistent);
    private final List<Project> allProjects = Arrays.asList(privateProject, protectedProject, publicProject, outsideProject);
    private final List<Project> mainProjects = Arrays.asList(privateProject, protectedProject, publicProject);
    private final Matcher<Integer> genericErrorMatcher = Matchers.isOneOf(401, 403, 404);
    private final boolean canDistinguishErrorConditions;
    private final String uriFragment;

    BasePermissionComparisonTest(boolean canDistinguishErrorConditions, String uriFragment) {
        this.canDistinguishErrorConditions = canDistinguishErrorConditions;
        this.uriFragment = uriFragment;
    }

    @BeforeClass
    public void createTestProjects() {
        TestNgUtils.assumeFalse(mainAdminUser.getUsername().equals(mainUser.getUsername()), "Main admin user and main user accounts must be distinct.");
        for (User user : Arrays.asList(allDataAdmin, allDataAccess, owner, member, collaborator, readSubjects, manageSubjects, readMR, editMR, manageMR, readQC, manageQC)) {
            mainAdminInterface().createUser(user);
        }
        for (Project project : allProjects) {
            mainAdminInterface().createProject(project);
        }
        for (Project project : mainProjects) {
            mainAdminInterface().createSubject(project, generateFullyIntertwinedSubject(project, outsideProject));
            mainAdminInterface().createSubject(outsideProject, generateFullyIntertwinedSubject(outsideProject, project));
        }
        for (Project project : allProjects) {
            for (Subject subject : project.getSubjects()) {
                mainAdminInterface().getAccessionNumber(subject);
            }
            mainAdminInterface().removeUserFromGroups(mainAdminUser, String.format("%s_%s", project.getId(), UserGroups.OWNER.groupIdSuffix()));
        }
    }

    private Project generateTestProject(String idPrefix, Accessibility accessibility, boolean addUsers) {
        final Project project = new Project(idPrefix + RandomHelper.randomID(8)).accessibility(accessibility);
        if (addUsers) {
            project.addOwner(owner);
            project.addMember(member);
            project.addCollaborator(collaborator);
            project.addUserGroup(new CustomUserGroup("ReadSubjects"), Collections.singletonList(readSubjects));
            project.addUserGroup(new CustomUserGroup("ManageSubjects").permission(DataType.SUBJECT, ALL), Collections.singletonList(manageSubjects));
            project.addUserGroup(new CustomUserGroup("ReadMR").permission(MR_SESSION, READ_ONLY), Collections.singletonList(readMR));
            project.addUserGroup(new CustomUserGroup("EditMR").permission(MR_SESSION, CREATE_AND_EDIT), Collections.singletonList(editMR));
            project.addUserGroup(new CustomUserGroup("ManageMR").permission(MR_SESSION, ALL), Collections.singletonList(manageMR));
            project.addUserGroup(new CustomUserGroup("ReadQC").permission(MR_SESSION, READ_ONLY).permission(QC, READ_ONLY), Collections.singletonList(readQC));
            project.addUserGroup(new CustomUserGroup("ManageQC").permission(MR_SESSION, READ_ONLY).permission(QC, ALL), Collections.singletonList(manageQC));
        }
        final Subject subject = new Subject(project);
        final ImagingSession mrSession = new MRSession(project, subject);
        final ImagingSession petSession = new PETSession(project, subject);
        final SessionAssessor mrQc = new QC(project, subject, mrSession);
        final SessionAssessor petQc = new QC(project, subject, petSession);
        return project;
    }

    private Subject generateFullyIntertwinedSubject(Project source, Project destination) {
        final Subject originalSubject = new Subject(source);
        share(originalSubject, destination);
        final ImagingSession insideMR1 = new MRSession(source, originalSubject);
        share(insideMR1, destination);
        final ImagingSession insideMR2 = new MRSession(source, originalSubject);
        final SessionAssessor insideMR1QC1 = new QC(source, originalSubject, insideMR1);
        share(insideMR1QC1, destination);
        final SessionAssessor insideMR1QC2 = new QC(source, originalSubject, insideMR1);
        final SessionAssessor insideMR2QC1 = new QC(source, originalSubject, insideMR2); // now done with all data that doesn't involve intertwining ownership.

        final ImagingSession outsideMR1 = shareSafeMR(destination, originalSubject);
        share(outsideMR1, source);
        final ImagingSession outsideMR2 = shareSafeMR(destination, originalSubject);
        final SessionAssessor outsideMR1QC1 = shareSafeQC(destination, originalSubject, outsideMR1);
        share(outsideMR1QC1, source);
        final SessionAssessor outsideMR1QC2 = shareSafeQC(destination, originalSubject, outsideMR1);
        final SessionAssessor outsideMR2QC1 = shareSafeQC(destination, originalSubject, outsideMR2); // now done with one level of intertwining
        final SessionAssessor outsideMR1InsideQC1 = shareSafeQC(source, originalSubject, outsideMR1);
        share(outsideMR1InsideQC1, destination);
        final SessionAssessor outsideMR1InsideQC2 = shareSafeQC(source, originalSubject, outsideMR1);

        return originalSubject;
    }

    private void share(Subject subject, Project destination) {
        subject.addShare(new Share(destination, RandomHelper.randomID(15)));
    }

    private void share(Experiment experiment, Project destination) {
        experiment.addShare(new Share(destination, RandomHelper.randomID(15)));
    }

    private MRSession shareSafeMR(Project project, Subject subject) {
        final MRSession session = new MRSession(project, subject);
        new ShareSafeSubjectAssessorExtension(session);
        return session;
    }

    private QC shareSafeQC(Project project, Subject subject, ImagingSession session) {
        final QC qc = new QC(project, subject, session);
        new ShareSafeSessionAssessorExtension(qc);
        return qc;
    }

    protected enum Operation {
        READ, EDIT, DELETE;

        public String urlString() {
            return name().toLowerCase();
        }
    }

    private class TestUrl {
        private String project;
        private String subject;
        private String experiment;

        private TestUrl() {}

        private TestUrl(String project, String subject, String experiment) {
            this.project = project;
            this.subject = subject;
            this.experiment = experiment;
        }

        public TestUrl project(String project) {
            this.project = project;
            return this;
        }

        public TestUrl subject(String subject) {
            this.subject = subject;
            return this;
        }

        TestUrl experiment(String experiment) {
            this.experiment = experiment;
            return this;
        }

        String expectedResponse() {
            final List<String> specified = new ArrayList<>();
            if (project != null) specified.add(project);
            if (subject != null) specified.add(subject);
            if (experiment != null) specified.add(experiment);
            return StringUtils.join(specified, ":");
        }

        String url(Operation operation) {
            final List<String> urlComponents = new ArrayList<>();
            urlComponents.add("/test");
            urlComponents.add(uriFragment);
            if (project != null) {
                urlComponents.add("/projects/");
                urlComponents.add(project);
            }
            if (subject != null) {
                urlComponents.add("/subjects/");
                urlComponents.add(subject);
            }
            if (experiment != null) {
                urlComponents.add("/experiments/");
                urlComponents.add(experiment);
            }
            urlComponents.add(operation.urlString());
            return formatXapiUrl(urlComponents.toArray(new String[0]));
        }
    }

    protected abstract class RestrictTest {
        boolean openXnat = false;
        boolean includeProject = false;
        boolean includeSubject = false;
        Operation operation;

        RestrictTest openXnat(boolean open) {
            openXnat = open;
            return this;
        }

        RestrictTest operation(Operation operation) {
            this.operation = operation;
            return this;
        }

        RequestSpecification queryBase(User user) {
            return (user == guest) ? RestAssured.given() : restDriver.queryBaseFor(user);
        }

        void performAllowedCall(User user, TestUrl testUrl) {
            queryBase(user).get(testUrl.url(operation)).then().assertThat().statusCode(200).and().body(Matchers.equalTo(testUrl.expectedResponse()));
        }

        void performDisallowedCall(User user, TestUrl testUrl) {
            performDisallowedCall(user, testUrl, false);
        }

        void performDisallowedCall(User user, TestUrl testUrl, boolean overrideFor404) {
            final Matcher<Integer> matcher;
            if (canDistinguishErrorConditions) {
                int expectedStatusCode;
                if (overrideFor404) {
                    expectedStatusCode = 404;
                } else if (user.equals(guest)) {
                    expectedStatusCode = 401;
                } else {
                    expectedStatusCode = 403;
                }
                matcher = Matchers.equalTo(expectedStatusCode);
            } else {
                matcher = genericErrorMatcher;
            }
            queryBase(user).get(testUrl.url(operation)).then().assertThat().statusCode(matcher).and().body(Matchers.not(Matchers.equalTo(testUrl.expectedResponse())));
        }

        void performNonexistentCall(User user, TestUrl testUrl) {
            performDisallowedCall(user, testUrl, readAll.contains(user));
        }

        public abstract void run();
    }

    protected class ProjectTest extends RestrictTest {
        @Override
        public void run() {
            for (Project project : mainProjects) {
                for (User user : allUsers) {
                    final TestUrl testUrl = new TestUrl().project(project.getId());
                    if (!openXnat && user.equals(guest)) {
                        performDisallowedCall(user, testUrl);
                    } else if (fullPermissions.contains(user)) {
                        performAllowedCall(user, testUrl);
                    } else if (operation.equals(Operation.READ)) { // private/public/protected distinction only matters for this operation
                        if (project.equals(publicProject) || !noPermissions.contains(user)) {
                            performAllowedCall(user, testUrl);
                        } else {
                            performDisallowedCall(user, testUrl);
                        }
                    } else if (owner.equals(user)) {
                        performAllowedCall(user, testUrl);
                    } else {
                        performDisallowedCall(user, testUrl);
                    }
                }
            }
            for (User user : allUsers) {
                performNonexistentCall(user, new TestUrl().project(nonexistent.getId()));
            }
        }
    }

    protected class SubjectTest extends ProjectTest {
        Map<Subject, Map<Project, String>> subjectLabelsMap = new HashMap<>();

        public RestrictTest project(boolean project) {
            includeProject = project;
            return this;
        }

        Map<Project, String> generateProjectLabelMap(Shareable shareable) {
            final Map<Project, String> labelMap = new HashMap<>();
            labelMap.put(shareable.getPrimaryProject(), shareable.getLabel());
            for (Share share : shareable.getShares()) {
                labelMap.put(new Project(share.getDestinationProject()), share.getDestinationLabel());
            }
            return labelMap;
        }

        boolean canDoToSubjectsIn(User user, Project project) {
            if (!openXnat && user.equals(guest)) {
                return false;
            } else if (fullPermissions.contains(user)) {
                return true;
            } else if (operation == Operation.READ) {
                if (project.equals(publicProject) || user.equals(allDataAccess)) {
                    return true;
                } else {
                    return !noPermissions.contains(user) && !project.equals(outsideProject);
                }
            } else {
                if (project.equals(outsideProject)) {
                    return false; // only users in the fullPermissions list can edit/delete these
                } else {
                    final List<User> permittedUsers = new ArrayList<>(Arrays.asList(owner, manageSubjects)); // users who are fine for both remaining operations
                    if (operation == Operation.EDIT) {
                        permittedUsers.add(member);
                    }
                    return permittedUsers.contains(user);
                }
            }
        }

        protected boolean existsIn(Project project, Subject subject) {
            return subjectLabelsMap.get(subject).containsKey(project);
        }

        private boolean canDoIn(User user, Project project, Subject subject) {
            if (!existsIn(project, subject)) { // if the subject doesn't even exist in the project, just stop
                return false;
            } else if (project.equals(subject.getProject())) {
                return canDoToSubjectsIn(user, project); // subject not in a shared context
            } else if (operation == Operation.EDIT) {
                return canDoToSubjectsIn(user, project) && canDoToSubjectsIn(user, subject.getPrimaryProject()); // You need to be able to edit the subject itself [in primary project] in addition to being able to edit subjects in the context of $project
            } else {
                return canDoToSubjectsIn(user, project); // delete case is handled here, since "delete" means "unshare" when specifying a shared project context
            }
        }

        private boolean canDo(User user, Subject subject) {
            if (operation == Operation.READ) {
                for (Project project : subjectLabelsMap.get(subject).keySet()) {
                    if (canDoToSubjectsIn(user, project)) {
                        return true;
                    }
                }
                return false;
            } else {
                return canDoToSubjectsIn(user, subject.getPrimaryProject());
            }
        }

        void populateSubjectLabelMap() {
            for (Project project : allProjects) {
                for (Subject subject : project.getSubjects()) {
                    if (!subjectLabelsMap.containsKey(subject)) {
                        subjectLabelsMap.put(subject, generateProjectLabelMap(subject));
                    }
                }
            }
        }

        @Override
        public void run() {
            populateSubjectLabelMap();
            for (Subject subject : subjectLabelsMap.keySet()) {
                final Map<Project, String> subjectMap = subjectLabelsMap.get(subject);
                for (User user : allUsers) {
                    if (includeProject) {
                        performNonexistentCall(user, new TestUrl().project(nonexistent.getId()).subject(subject.getAccessionNumber()));
                        for (Map.Entry<Project, String> labelEntry : subjectMap.entrySet()) {
                            performNonexistentCall(user, new TestUrl().project(nonexistent.getId()).subject(labelEntry.getValue()));
                            for (Project specifiedProject : allProjects) {
                                final boolean existsInSpecifiedProject = existsIn(specifiedProject, subject);
                                final boolean permitted = canDoIn(user, specifiedProject, subject);
                                final TestUrl idVariant = new TestUrl().project(specifiedProject.getId()).subject(subject.getAccessionNumber());
                                if (permitted) {
                                    performAllowedCall(user, idVariant);
                                } else if (existsInSpecifiedProject) {
                                    performDisallowedCall(user, idVariant);
                                } else {
                                    performNonexistentCall(user, idVariant);
                                }
                                final TestUrl labelVariant = new TestUrl().project(specifiedProject.getId()).subject(labelEntry.getValue());
                                if (!existsInSpecifiedProject || !specifiedProject.equals(labelEntry.getKey())) { // the subject has to exist in specifiedProject AND we have to be using the right label, otherwise the URL won't be valid
                                    performNonexistentCall(user, labelVariant);
                                } else if (permitted) {
                                    performAllowedCall(user, labelVariant);
                                } else {
                                    performDisallowedCall(user, labelVariant);
                                }
                            }
                        }
                    } else {
                        for (String label : subjectMap.values()) { // Subject label without @Project is insufficient
                            performNonexistentCall(user, new TestUrl().subject(label));
                        }
                        final TestUrl testUrl = new TestUrl().subject(subject.getAccessionNumber());
                        if (canDo(user, subject)) {
                            performAllowedCall(user, testUrl);
                        } else {
                            performDisallowedCall(user, testUrl);
                        }
                    }
                }
            }
        }
    }

    protected abstract class ExperimentTest extends SubjectTest {
        Map<Experiment, Map<Project, String>> experimentLabelsMap = new HashMap<>();

        public ExperimentTest subject(boolean subject) {
            includeSubject = subject;
            return this;
        }

        private boolean existsIn(Project project, Experiment experiment) {
            return experimentLabelsMap.get(experiment).containsKey(project);
        }

        private boolean canDoToExperimentsIn(User user, Project project, DataType dataType) {
            if (!openXnat && user.equals(guest)) {
                return false;
            } else if (fullPermissions.contains(user)) {
                return true;
            } else if (operation == Operation.READ) {
                if (project.equals(publicProject) || user.equals(allDataAccess)) {
                    return true;
                } else if (noPermissions.contains(user) || project.equals(outsideProject)) {
                    return false;
                } else if (Arrays.asList(owner, member, collaborator).contains(user))  {
                    return true;
                } else { // just custom user groups left
                    final List<User> allowedUsers = new ArrayList<>();
                    if (dataType.equals(MR_SESSION)) {
                        allowedUsers.addAll(Arrays.asList(readMR, editMR, manageMR, readQC, manageQC));
                    } else if (dataType.equals(QC)) {
                        allowedUsers.addAll(Arrays.asList(readQC, manageQC));
                    }
                    return allowedUsers.contains(user);
                }
            } else if (project.equals(outsideProject)) {
                return false; // only users in the fullPermissions list can edit/delete these
            } else {
                final List<User> permittedUsers = new ArrayList<>();
                permittedUsers.add(owner);
                if (dataType.equals(MR_SESSION)) {
                    permittedUsers.add(manageMR);
                    if (operation == Operation.EDIT) {
                        permittedUsers.add(editMR);
                    }
                }
                if (dataType.equals(QC)) {
                    permittedUsers.add(manageQC);
                }
                if (operation == Operation.EDIT) {
                    permittedUsers.add(member);
                }
                return permittedUsers.contains(user);
            }
        }

        private boolean canDoIn(User user, Project project, Experiment experiment) {
            if (!existsIn(project, experiment)) { // if the experiment doesn't even exist in the project, just stop
                return false;
            } else if (project.equals(experiment.getPrimaryProject())) {
                return canDoToExperimentsIn(user, project, experiment.getDataType()); // experiment not in a shared context
            } else if (operation == Operation.EDIT) {
                return canDoToExperimentsIn(user, project, experiment.getDataType()) && canDoToExperimentsIn(user, experiment.getPrimaryProject(), experiment.getDataType());
            } else {
                return canDoToExperimentsIn(user, project, experiment.getDataType()); // delete case is handled here, since "delete" means "unshare" when specifying a shared project context
            }
        }

        private boolean canDo(User user, Experiment experiment) {
            if (operation == Operation.READ) {
                for (Project project : experimentLabelsMap.get(experiment).keySet()) {
                    if (canDoToExperimentsIn(user, project, experiment.getDataType())) {
                        return true;
                    }
                }
                return false;
            } else {
                return canDoToExperimentsIn(user, experiment.getPrimaryProject(), experiment.getDataType());
            }
        }

        protected abstract void populateExperimentLabelMap();

        @Override
        public void run() {
            if (includeSubject) populateSubjectLabelMap();
            populateExperimentLabelMap();
            for (Experiment experiment : experimentLabelsMap.keySet()) {
                final Map<Project, String> subjectLabelMap = (includeSubject) ? subjectLabelsMap.get(experiment.getSubject()) : new HashMap<>();
                final Map<Project, String> experimentLabelMap = experimentLabelsMap.get(experiment);
                for (User user : allUsers) {
                    if (includeProject) {
                        if (includeSubject) {
                            runChecksProjectTrueSubjectTrue(experiment, user, subjectLabelMap, experimentLabelMap);
                        } else {
                            runChecksProjectTrueSubjectFalse(experiment, user, experimentLabelMap);
                        }
                    } else {
                        if (includeSubject) {
                            runChecksProjectFalseSubjectTrue(experiment, user, subjectLabelMap, experimentLabelMap);
                        } else {
                            runChecksProjectFalseSubjectFalse(experiment, user, experimentLabelMap);
                        }
                    }
                }
            }
        }

        private void runChecksProjectTrueSubjectTrue(Experiment experiment, User user, Map<Project, String> subjectLabelMap, Map<Project, String> experimentLabelMap) {
            final Subject subject = experiment.getSubject();
            final List<String> possibleSubjectIdentifiers = new ArrayList<>(subjectLabelMap.values());
            possibleSubjectIdentifiers.add(subject.getAccessionNumber());
            for (String validExperimentLabel : experimentLabelMap.values()) {
                performNonexistentCall(user, new TestUrl(nonexistent.getId(), nonexistentSubject.getLabel(), validExperimentLabel));
            }
            performNonexistentCall(user, new TestUrl(nonexistent.getId(), nonexistentSubject.getLabel(), experiment.getAccessionNumber()));
            for (Project project : allProjects) {
                for (String subjectIdentifier : possibleSubjectIdentifiers) {
                    final boolean existsInProject = existsIn(project, experiment);
                    final boolean permitted = canDoIn(user, project, experiment);
                    final TestUrl idVariant = new TestUrl(project.getId(), subjectIdentifier, experiment.getAccessionNumber());
                    if (permitted) {
                        performAllowedCall(user, idVariant);
                    } else if (existsInProject) {
                        performDisallowedCall(user, idVariant);
                    } else {
                        performNonexistentCall(user, idVariant);
                    }
                    for (Map.Entry<Project, String> experimentEntry : experimentLabelMap.entrySet()) {
                        final TestUrl testUrl = new TestUrl(project.getId(), subjectIdentifier, experimentEntry.getValue());
                        if (!existsInProject || !project.equals(experimentEntry.getKey())) {
                            performNonexistentCall(user, testUrl);
                        } else if (permitted) {
                            performAllowedCall(user, testUrl);
                        } else {
                            performDisallowedCall(user, testUrl);
                        }
                    }
                }
            }
        }

        private void runChecksProjectTrueSubjectFalse(Experiment experiment, User user, Map<Project, String> experimentLabelMap) {
            for (String validExperimentLabel : experimentLabelMap.values()) {
                performNonexistentCall(user, new TestUrl().project(nonexistent.getId()).experiment(validExperimentLabel));
            }
            performNonexistentCall(user, new TestUrl().project(nonexistent.getId()).experiment(experiment.getAccessionNumber()));
            for (Project project : allProjects) {
                final boolean existsInProject = existsIn(project, experiment);
                final boolean permitted = canDoIn(user, project, experiment);
                final TestUrl idVariant = new TestUrl().project(project.getId()).experiment(experiment.getAccessionNumber());
                if (permitted) {
                    performAllowedCall(user, idVariant);
                } else if (existsInProject) {
                    performDisallowedCall(user, idVariant);
                }
                else {
                    performNonexistentCall(user, idVariant);
                }
                for (Map.Entry<Project, String> experimentEntry : experimentLabelMap.entrySet()) {
                    final TestUrl testUrl = new TestUrl().project(project.getId()).experiment(experimentEntry.getValue());
                    if (!existsInProject || !project.equals(experimentEntry.getKey())) {
                        performNonexistentCall(user, testUrl);
                    } else if (permitted) {
                        performAllowedCall(user, testUrl);
                    } else {
                        performDisallowedCall(user, testUrl);
                    }
                }
            }
        }

        private void runChecksProjectFalseSubjectTrue(Experiment experiment, User user, Map<Project, String> subjectLabelMap, Map<Project, String> experimentLabelMap) {
            for (String subjectLabel : subjectLabelMap.values()) {
                performNonexistentCall(user, new TestUrl().subject(subjectLabel).experiment(experiment.getAccessionNumber())); // even though experiment ID is correct, this is an incorrect subject ID
                for (String experimentLabel : experimentLabelMap.values()) {
                    performNonexistentCall(user, new TestUrl().subject(subjectLabel).experiment(experimentLabel));
                }
            }
            final Subject subject = experiment.getSubject();
            final boolean allowed = canDo(user, experiment);
            final TestUrl idVariant = new TestUrl().subject(subject.getAccessionNumber()).experiment(experiment.getAccessionNumber());
            if (allowed) {
                performAllowedCall(user, idVariant);
            } else {
                performDisallowedCall(user, idVariant);
            }
            for (Map.Entry<Project, String> experimentMapEntry : experimentLabelMap.entrySet()) {
                final TestUrl labelVariant = new TestUrl().subject(subject.getAccessionNumber()).experiment(experimentMapEntry.getValue());
                if (allowed) {
                    performAllowedCall(user, labelVariant);
                } else {
                    performDisallowedCall(user, labelVariant);
                }
            }
        }

        private void runChecksProjectFalseSubjectFalse(Experiment experiment, User user, Map<Project, String> experimentLabelMap) {
            for (String validExperimentLabel : experimentLabelMap.values()) {
                performNonexistentCall(user, new TestUrl().experiment(validExperimentLabel)); // label alone is insufficient
            }
            final TestUrl idVariant = new TestUrl().experiment(experiment.getAccessionNumber());
            if (canDo(user, experiment)) {
                performAllowedCall(user, idVariant);
            } else {
                performDisallowedCall(user, idVariant);
            }
        }
    }

    protected class SubjectAssessorTest extends ExperimentTest {
        @Override
        protected void populateExperimentLabelMap() {
            for (Project project : allProjects) {
                for (Subject subject : project.getSubjects()) {
                    for (SubjectAssessor experiment : subject.getExperiments()) {
                        if (!experimentLabelsMap.containsKey(experiment)) {
                            experimentLabelsMap.put(experiment, generateProjectLabelMap(experiment));
                        }
                    }
                }
            }
        }
    }

    protected class SessionAssessorTest extends ExperimentTest {
        @Override
        protected void populateExperimentLabelMap() {
            for (Project project : allProjects) {
                for (Subject subject : project.getSubjects()) {
                    for (ImagingSession session : subject.getSessions()) {
                        for (SessionAssessor sessionAssessor : session.getAssessors()) {
                            if (!experimentLabelsMap.containsKey(sessionAssessor)) {
                                experimentLabelsMap.put(sessionAssessor, generateProjectLabelMap(sessionAssessor));
                            }
                        }
                    }
                }
            }
        }
    }

}
