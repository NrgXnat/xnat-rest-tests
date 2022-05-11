package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.apache.log4j.Logger;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.ProjectScript;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.enums.ShareMethod;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.nrg.xnat.enums.DicomEditVersion.DE_6;
import static org.testng.AssertJUnit.assertTrue;

import static org.testng.AssertJUnit.assertEquals;

@TestRequires(plugins = {"batchSharePlugin"}, data = {TestData.SAMPLE_1, TestData.SAMPLE_2})
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
        performBasicShare(ShareMethod.STANDARD_SHARE);

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
        performBasicShare(ShareMethod.COPY);

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
        assertTrue(project2.getSubjects().size() == 0);

        Project project3 = new Project();
        mainAdminInterface().createProject(project3);
        assertTrue(project3.getSubjects().size() == 0);

        List<ShareRequest> requestList = new ArrayList<>();

        List<Object> elements = new ArrayList<>();
        elements.add(subject);
        elements.add(session);

        addElementsToBatchShare(ShareMethod.STANDARD_SHARE, requestList, project2, elements);
        performBatchShareAction(requestList, mainAdminUser);

        List <ShareRequest> copyRequestList = new ArrayList<>();
        addElementsToBatchShare(ShareMethod.COPY, copyRequestList, project3, elements);
        performBatchShareAction(copyRequestList, mainAdminUser);

        Project originalProject = mainInterface().readProject(project.getId());
        assertEquals(originalProject.getSubjects().size() + originalProject.getSecondarySubjects().size(), 1);

        JsonPath originalProjectExperiments = mainQueryBase().get(formatRestUrl("projects/{id}/experiments"), originalProject.getId()).then().assertThat().statusCode(200).and().extract().jsonPath();
        Integer totalOriginalProjectExperiments = Integer.valueOf(originalProjectExperiments.getString("ResultSet.totalRecords"));
        assertEquals(totalOriginalProjectExperiments.intValue(), 1);

        Project sharedProject = mainInterface().readProject(project2.getId());
        assertEquals(sharedProject.getSubjects().size() + sharedProject.getSecondarySubjects().size(), 1);

        JsonPath sharedProjectExperiments = mainQueryBase().get(formatRestUrl("projects/{id}/experiments"), originalProject.getId()).then().assertThat().statusCode(200).and().extract().jsonPath();
        Integer totalSharedProjectExperiments = Integer.valueOf(sharedProjectExperiments.getString("ResultSet.totalRecords"));
        assertEquals(totalSharedProjectExperiments.intValue(), 1);


        Project copiedProject = mainInterface().readProject(project3.getId());
        assertEquals(copiedProject.getSubjects().size() + copiedProject.getSecondarySubjects().size(), 1);

        JsonPath copiedProjectExperiments = mainQueryBase().get(formatRestUrl("projects/{id}/experiments"), originalProject.getId()).then().assertThat().statusCode(200).and().extract().jsonPath();
        Integer totalCopiedProjectExperiments = Integer.valueOf(copiedProjectExperiments.getString("ResultSet.totalRecords"));
        assertEquals(totalCopiedProjectExperiments.intValue(), 1);

        restDriver.deleteProjectSilently(mainAdminUser, project3);
    }

    @Test
    public void testShareFromMultipleProjects() {
        Project project3 = new Project();
        Subject subject2 = new Subject(project3);
        MRSession session2 = new MRSession(project3, subject2);
        session2.extension(new SessionImportExtension(session2, TestData.SAMPLE_2.toFile()));
        mainAdminInterface().createProject(project3);

        mainInterface().getAccessionNumber(subject2);
        mainInterface().getAccessionNumber(session2);

        List<ShareRequest> requestList = new ArrayList<>();

        List<Object> elements = new ArrayList<>();
        elements.add(subject);
        elements.add(session);
        elements.add(subject2);
        elements.add(session2);

        addElementsToBatchShare(ShareMethod.STANDARD_SHARE, requestList, project2, elements);

        performBatchShareAction(requestList, mainAdminUser);

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

        permissionsSession.extension(new SessionImportExtension(session, TestData.SAMPLE_1.toFile()));

        Project receiverProject = new Project();

        permissionsProject.addOwner(ownerUser);
        permissionsProject.addMember(memberUser);
        permissionsProject.addCollaborator(collaboratorUser);

        receiverProject.addMember(memberUser);

        mainInterface().createProject(permissionsProject);
        mainInterface().getAccessionNumber(permissionsSubject);
        mainInterface().getAccessionNumber(permissionsSession);

        mainInterface().createProject(receiverProject);

        List<ShareRequest> requestList = new ArrayList<>();

        List<Object> elements = new ArrayList<>();
        elements.add(permissionsSubject);
        elements.add(permissionsSession);

        runPermissionsChecksForUserType("owner", permissionsProject, receiverProject, requestList, elements, ownerUser);
        runPermissionsChecksForUserType("member", permissionsProject, receiverProject, requestList, elements, memberUser);
        runPermissionsChecksForUserType("collaborator", permissionsProject, receiverProject, requestList, elements, collaboratorUser);

        restDriver.deleteProjectSilently(mainAdminUser, permissionsProject);
        restDriver.deleteProjectSilently(mainAdminUser, receiverProject);
    }

    @Test
    public void testBatchCopyAnonymization() {
        mainInterface().setProjectAnonScript(project2, projScript);

        try {
            restDriver.clearPrearchiveSessions(mainUser, project);
        } catch (Throwable throwable) {
            LOG.warn(throwable);
        }

        performBasicShare(ShareMethod.COPY);

        Project sharedProject = mainInterface().readProject(project2.getId());
        Subject sharedSubject = sharedProject.findSubject(subject.getLabel());
        ImagingSession sharedExperiment = (ImagingSession) sharedSubject.findSubjectAssessor(session.getLabel());

        final List<File> files = downloadResourceFiles(sharedExperiment, sharedSubject, sharedProject);

        new ProjectScript().validateScriptRan(files);
    }

    @Test
    public void testResourceFileBatchShareEditing() {
        restDriver.validateResource(mainUser, subjectResource);

        performBasicShare(ShareMethod.COPY);

        restDriver.validateResource(mainUser, subjectResource);

        Project sharedProject = mainInterface().readProject(project2.getId());
        Subject sharedSubject = sharedProject.findSubject(subject.getLabel());
        List<Resource> sharedResources = sharedSubject.getResources();
        SubjectResource sharedSubjectResource = (SubjectResource) sharedResources.get(0);
        ResourceFile sharedResourceFile = sharedSubjectResource.getResourceFiles().get(0);

        sharedResourceFile.extension(new SimpleResourceFileExtension(overwrittenResourceFile));
        mainInterface().overwriteResourceFile(sharedSubjectResource, sharedResourceFile);

        restDriver.validateResource(mainUser, sharedSubjectResource);
    }

    public void performBasicShare(ShareMethod shareMethod) {
        assertTrue(project2.getSubjects().size() == 0);

        List<ShareRequest> requestList = new ArrayList<>();

        List<Object> elements = new ArrayList<>();
        elements.add(subject);
        elements.add(session);

        addElementsToBatchShare(shareMethod, requestList, project2, elements);

        performBatchShareAction(requestList, mainAdminUser);
    }

    public void addElementsToBatchShare(ShareMethod shareMethod, List<ShareRequest> requestList, Project sharedToProject, List<Object> elements) {

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
    }

    public void runPermissionsChecksForUserType(String userType, Project permissionsProject, Project receiverProject, List<ShareRequest> requestList, List<Object> elements, User user) {
        mainQueryBase().contentType(ContentType.JSON).body(STOP_PROJECT_SHARING_STRING).post(formatXnatUrl("REST/services/features?group={group}_" + userType), permissionsProject.getId()).then().assertThat().statusCode(200);

        addElementsToBatchShare(ShareMethod.STANDARD_SHARE, requestList, receiverProject, elements);

        performBatchShareFailureAction(requestList, user);

        mainQueryBase().contentType(ContentType.JSON).body(RESTART_PROJECT_SHARING_STRING).post(formatXnatUrl("REST/services/features?group={group}_" + userType), permissionsProject.getId()).then().assertThat().statusCode(200);

        performBatchShareAction(requestList, user);
    }

    public void performBatchShareAction(List<ShareRequest> requestList, User user) {
        String trackingId = interfaceFor(user).launchBatchShare(requestList);

        final long start = System.currentTimeMillis();
        boolean succeeded;
        do {
            final JsonPath json = interfaceFor(user)
                    .jsonQuery()
                    .get(formatXapiUrl("event_tracking", trackingId))
                    .then().assertThat().statusCode(200).and().extract().jsonPath();

            try {
                succeeded = json.getBoolean("succeeded");
            } catch (NullPointerException e) {
                TimeUtils.sleep(5000);
                continue;
            }
            break;
        } while (System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(20));
    }

    public void performBatchShareFailureAction(List<ShareRequest> shareRequests, User user) {
        interfaceFor(user).queryBase().contentType(ContentType.JSON).body(shareRequests).post(formatXapiUrl("batch_share")).then().assertThat().statusCode(400);
    }

    private List<File> downloadResourceFiles(ImagingSession session, Subject inputSubject, Project inputProject) {
        final List<File> dicomFiles = new ArrayList<>();
        final List<Scan> scans = mainInterface().readScans(inputProject, inputSubject, session);
        for (Scan scan : scans) {
            final Resource dicom = mainInterface().findResource(scan.getScanResources(), "DICOM");
            for (ResourceFile file : dicom.getResourceFiles()) {
                dicomFiles.add(restDriver.saveBinaryResponseToFile(restDriver.interfaceFor(mainUser).queryBase().get(mainInterface().resourceFileUrl(dicom, file))));
            }
        }
        return dicomFiles;
    }
}
