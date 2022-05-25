package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import org.apache.log4j.Logger;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.ProjectScript;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.enums.ShareMethod;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.Experiment;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.SubjectResource;
import org.nrg.xnat.pogo.sharing.ShareRequest;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.nrg.xnat.enums.DicomEditVersion.DE_6;

import static org.testng.AssertJUnit.assertEquals;

@TestRequires(plugins = {"batchSharePlugin"}, data = {TestData.SAMPLE_1, TestData.SAMPLE_2, TestData.ANON_2})
public class TestBatchSharePlugin extends BaseXnatRestTest {
    private static final Logger LOG = Logger.getLogger(TestBatchSharePlugin.class);

    private Project project;
    private Project project2;
    private Subject subject;
    private MRSession session;

    private static final int SCANS_IN_SESSION_1 = 3;
    private static final int FILES_PER_SCAN = 176;
    private static final int SCANS_IN_SESSION_2 = 10;
    private static final int TOTAL_FILES_SESSION_2 = 324;

    private static final String STOP_PROJECT_SHARING_STRING = "{\'key\':\'project_sharing\',\'banned\':\'true\'}";
    private static final String RESTART_PROJECT_SHARING_STRING = "{\'key\':\'project_sharing\',\'banned\':\'false\'}";

    private final AnonScript projScript = XnatObjectUtils.anonScriptFromFile(DE_6, "projectAnon.das");

    private final File originalResourceFile = getDataFile("batch_share/original/resource_file.txt");
    private final File overwrittenResourceFile = getDataFile("batch_share/overwritten/resource_file.txt");

    private Resource subjectResource;
    private ResourceFile resourceFile;

    @BeforeMethod
    public void setupBatchShareTesting() {

        project = new Project();
        project2 = new Project();
        subject = new Subject(project);
        session = new MRSession(project, subject);

        session.extension(new SessionImportExtension(session, TestData.SAMPLE_1.toFile()));

        subjectResource = new SubjectResource(project, subject, "ORIGINAL");
        resourceFile = new ResourceFile(subjectResource, "resource_file.txt");

        resourceFile.extension(new SimpleResourceFileExtension(originalResourceFile));

        mainInterface().createProject(project);
        mainInterface().createProject(project2);

        mainInterface().getAccessionNumber(subject);
        mainInterface().getAccessionNumber(session);
    }

    @AfterMethod()
    public void tearDownBatchShareTesting() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
        restDriver.deleteProjectSilently(mainAdminUser, project2);
    }

    @Test
    public void testStandardShareFunctionality() {
        performBatchShareAction(mainUser, project2, ShareMethod.STANDARD_SHARE, subject, session);

        Project sharedProject = mainInterface().readProject(project2.getId());
        Subject sharedSubject = sharedProject.findSecondarySubject(subject.getLabel());
        ImagingSession sharedAssessor = (ImagingSession) sharedSubject.findSubjectAssessor(session.getLabel());

        final List<Scan> scansInSession = mainInterface().readScans(sharedProject, sharedSubject, sharedAssessor);

        assertEquals(subject.getAccessionNumber(), sharedSubject.getAccessionNumber());
        assertEquals(session.getAccessionNumber(), sharedAssessor.getAccessionNumber());
        assertEquals(scansInSession.size(), SCANS_IN_SESSION_1);
        for (Scan scan : scansInSession) {
            assertEquals(scan.getScanResources().get(0).getFileCount(), FILES_PER_SCAN);
        }
    }

    @Test
    public void testCopyFunctionality() {
        performBatchShareAction(mainUser, project2, ShareMethod.COPY, subject, session);

        Project sharedProject = mainInterface().readProject(project2.getId());
        Subject sharedSubject = sharedProject.findSubject(subject.getLabel());
        ImagingSession sharedExperiment = (ImagingSession) sharedSubject.findSubjectAssessor(session.getLabel());

        final List<Scan> scansInSession = mainInterface().readScans(sharedProject, sharedSubject, sharedExperiment);

        assertEquals(subject.getLabel(), sharedSubject.getLabel());
        assertEquals(session.getLabel(), sharedExperiment.getLabel());
        assertEquals(scansInSession.size(), SCANS_IN_SESSION_1);
        for (Scan scan : scansInSession) {
            assertEquals(scan.getScanResources().get(0).getFileCount(), FILES_PER_SCAN);
        }
    }

    //MDACC-150 - testing to ensure that sharing data and then copying that same data to another project does not create
    //multiple copies of that data within a given project. After share and copy all projects should have exactly one
    //version of the subject/experiment
    @Test
    public void testMultipleSharedElements() {
        assertEquals(0, project2.getSubjects().size());

        final Project project3 = new Project();
        mainInterface().createProject(project3);
        assertEquals(0, project3.getSubjects().size());

        performBatchShareAction(mainUser, project2, ShareMethod.STANDARD_SHARE, subject, session);
        performBatchShareAction(mainUser, project3, ShareMethod.COPY, subject, session);

        checkProjectHasSingleSession(project.getId()); // original
        checkProjectHasSingleSession(project2.getId()); // shared
        checkProjectHasSingleSession(project3.getId()); // copied

        restDriver.deleteProjectSilently(mainAdminUser, project3);
    }

    @Test
    public void testShareFromMultipleProjects() {
        Project project3 = new Project();
        Subject subject2 = new Subject(project3);
        MRSession session2 = new MRSession(project3, subject2);
        session2.extension(new SessionImportExtension(session2, TestData.SAMPLE_2.toFile()));
        mainInterface().createProject(project3);

        mainInterface().getAccessionNumber(subject2);
        mainInterface().getAccessionNumber(session2);

        performBatchShareAction(mainUser, project2, ShareMethod.STANDARD_SHARE, subject, session, subject2, session2);

        Project sharedProject = mainInterface().readProject(project2.getId());
        Subject sharedSubject = sharedProject.findSecondarySubject(subject.getLabel());
        ImagingSession sharedSession = (ImagingSession) sharedSubject.findSubjectAssessor(session.getLabel());

        Subject sharedSubject2 = sharedProject.findSecondarySubject(subject2.getLabel());
        ImagingSession sharedSession2 = (ImagingSession) sharedSubject2.findSubjectAssessor(session2.getLabel());

        final List<Scan> scansInSession1 = mainInterface().readScans(sharedProject, sharedSubject, sharedSession);

        assertEquals(subject.getAccessionNumber(), sharedSubject.getAccessionNumber());
        assertEquals(session.getAccessionNumber(), sharedSession.getAccessionNumber());
        assertEquals(scansInSession1.size(), SCANS_IN_SESSION_1);
        for (Scan scan : scansInSession1) {
            assertEquals(scan.getScanResources().get(0).getFileCount(), FILES_PER_SCAN);
        }

        final List<Scan> scansInSession2 = mainInterface().readScans(sharedProject, sharedSubject2, sharedSession2);

        assertEquals(subject2.getAccessionNumber(), sharedSubject2.getAccessionNumber());
        assertEquals(session2.getAccessionNumber(), sharedSession2.getAccessionNumber());
        assertEquals(scansInSession2.size(), SCANS_IN_SESSION_2);

        int totalFilesSessionTwo = 0;

        for (Scan scan : scansInSession2) {
            totalFilesSessionTwo += scan.getScanResources().get(0).getFileCount();
        }
        assertEquals(totalFilesSessionTwo, TOTAL_FILES_SESSION_2);

        restDriver.deleteProjectSilently(mainAdminUser, project3);
    }

    @Test
    @TestRequires(users = 3)
    public void testUserPermissions() {
        User ownerUser = getGenericUser();
        User memberUser = getGenericUser();
        User collaboratorUser = getGenericUser();

        Project permissionsProject = new Project();
        Subject permissionsSubject = new Subject(permissionsProject);
        MRSession permissionsSession = new MRSession(permissionsProject, permissionsSubject);

        permissionsSession.extension(new SessionImportExtension(permissionsSession, TestData.SAMPLE_1.toFile()));

        Project receiverProject = new Project();

        permissionsProject.addOwner(ownerUser);
        permissionsProject.addMember(memberUser);
        permissionsProject.addCollaborator(collaboratorUser);

        receiverProject.addMember(ownerUser);
        receiverProject.addMember(memberUser);
        receiverProject.addMember(collaboratorUser);

        mainInterface().createProject(permissionsProject);
        mainInterface().getAccessionNumber(permissionsSubject);
        mainInterface().getAccessionNumber(permissionsSession);

        mainInterface().createProject(receiverProject);

        runPermissionsChecksForUserType("owner", permissionsProject, receiverProject, ownerUser, permissionsSubject, permissionsSession);
        runPermissionsChecksForUserType("member", permissionsProject, receiverProject, memberUser, permissionsSubject, permissionsSession);
        runPermissionsChecksForUserType("collaborator", permissionsProject, receiverProject, collaboratorUser, permissionsSubject, permissionsSession);

        restDriver.deleteProjectSilently(mainAdminUser, permissionsProject);
        restDriver.deleteProjectSilently(mainAdminUser, receiverProject);
    }

    @Test
    public void testBatchCopyAnonymization() {
        Project sourceProject = new Project();
        Project destProject = new Project();
        Subject sourceSubj = new Subject(sourceProject);
        MRSession sourceSesh = new MRSession(sourceProject, sourceSubj);

        sourceSesh.extension(new SessionImportExtension(sourceSesh, TestData.ANON_2.toFile()));

        mainInterface().createProject(sourceProject);
        mainInterface().createProject(destProject);

        mainInterface().getAccessionNumber(sourceSubj);
        mainInterface().getAccessionNumber(sourceSesh);

        mainInterface().setProjectAnonScript(destProject, projScript);

        try {
            restDriver.clearPrearchiveSessions(mainUser, sourceProject);
        } catch (Throwable throwable) {
            LOG.warn(throwable);
        }

        performBatchShareAction(mainUser, destProject, ShareMethod.COPY, sourceSubj, sourceSesh);

        Project sharedProject = mainInterface().readProject(destProject.getId());
        Subject sharedSubject = sharedProject.findSubject(sourceSubj.getLabel());
        ImagingSession sharedExperiment = (ImagingSession) sharedSubject.findSubjectAssessor(sourceSesh.getLabel());

        final List<File> files = restDriver.downloadAllDicomFromSession(mainUser, sharedProject, sharedSubject, sharedExperiment);

        new ProjectScript().validateScriptRan(files);

        Project originalProject = mainInterface().readProject(sourceProject.getId());
        Subject originalSubject = originalProject.findSubject(sourceSubj.getLabel());
        ImagingSession originalExperiment = (ImagingSession) originalSubject.findSubjectAssessor(sourceSesh.getLabel());

        final List<File> sourceProjectFiles = restDriver.downloadAllDicomFromSession(mainUser, originalProject, originalSubject, originalExperiment);

        new ProjectScript().validateScriptDidntRun(sourceProjectFiles);

        restDriver.deleteProjectSilently(mainAdminUser, sourceProject);
        restDriver.deleteProjectSilently(mainAdminUser, destProject);
    }

    @Test
    public void testResourceFileBatchShareEditing() {
        restDriver.validateResource(mainUser, subjectResource);

        performBatchShareAction(mainUser, project2, ShareMethod.COPY, subject, session);

        restDriver.validateResource(mainUser, subjectResource);

        Project sharedProject = mainInterface().readProject(project2.getId());
        Subject sharedSubject = sharedProject.findSubject(subject.getLabel());
        List<Resource> sharedResources = sharedSubject.getResources();
        SubjectResource sharedSubjectResource = (SubjectResource) sharedResources.get(0);
        ResourceFile sharedResourceFile = sharedSubjectResource.getResourceFiles().get(0);

        sharedResourceFile.extension(new SimpleResourceFileExtension(overwrittenResourceFile));
        mainInterface().overwriteResourceFile(sharedSubjectResource, sharedResourceFile);

        restDriver.validateResource(mainUser, sharedSubjectResource);
        restDriver.validateResource(mainUser, subjectResource);
    }

    public List<ShareRequest> addElementsToBatchShare(ShareMethod shareMethod, Project sharedToProject, List<Object> elements) {
        List<ShareRequest> requestList = new ArrayList<>();
        for (Object element : elements) {
            ShareRequest request = new ShareRequest();
            request.setOperation(shareMethod);
            request.setDestinationProject(sharedToProject.getId());
            if (element instanceof Subject) {
                request.setId(((Subject) element).getAccessionNumber());
            } else if (element instanceof Experiment) {
                request.setId(((Experiment) element).getAccessionNumber());
            }
            requestList.add(request);
        }

        return requestList;
    }

    public void runPermissionsChecksForUserType(String userType, Project permissionsProject, Project receiverProject, User user, Object... elements) {
        mainQueryBase().contentType(ContentType.JSON).body(STOP_PROJECT_SHARING_STRING).post(formatXnatUrl("REST/services/features?group={group}_" + userType), permissionsProject.getId()).then().assertThat().statusCode(200);

        performBatchShareFailureAction(user, receiverProject, ShareMethod.STANDARD_SHARE, elements);

        mainQueryBase().contentType(ContentType.JSON).body(RESTART_PROJECT_SHARING_STRING).post(formatXnatUrl("REST/services/features?group={group}_" + userType), permissionsProject.getId()).then().assertThat().statusCode(200);

        performBatchShareAction(user, receiverProject, ShareMethod.STANDARD_SHARE, elements);
    }

    public void performBatchShareAction(User user, Project sharedToProject, ShareMethod shareMethod, Object... elements) {
        final List<ShareRequest> requestList = addElementsToBatchShare(shareMethod, sharedToProject, Arrays.asList(elements));
        final XnatInterface xnatInterface = interfaceFor(user);
        xnatInterface.waitForTrackedEventSuccessful(xnatInterface.launchBatchShare(requestList));
    }

    public void performBatchShareFailureAction(User user, Project sharedToProject, ShareMethod shareMethod, Object... elements) {
        List<ShareRequest> requestList = addElementsToBatchShare(shareMethod, sharedToProject, Arrays.asList(elements));
        interfaceFor(user).queryBase().contentType(ContentType.JSON).body(requestList).post(formatXapiUrl("batch_share")).then().assertThat().statusCode(400);
    }

    private void checkProjectHasSingleSession(String projectId) {
        final Project project = mainInterface().readProject(projectId);
        assertEquals(project.getSubjects().size() + project.getSecondarySubjects().size(), 1);

        mainQueryBase().get(formatRestUrl("projects/{id}/experiments"), project.getId()).then().assertThat().statusCode(200).
                and().body("ResultSet.totalRecords", equalTo("1"));
    }

}
