package org.nrg.testing.xnat.tests.eventservice;

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.dcm4che3.data.Tag;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.PetMrProcessingSetting;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.events.*;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.extensions.project.ProjectXMLPutExtension;
import org.nrg.xnat.pogo.extensions.subject.SubjectXMLPutExtension;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

@TestRequires(data = {TestData.SAMPLE_1, TestData.DICOM_WEB_PETMR1}, dicomScp = true)
@AddedIn(Xnat_1_8_0.class)
public class TestEventDetection extends BaseEventServiceTest {

    private final Subscription projectCreate = buildLoggingEvent(Event.PROJECT_EVENT_TYPE, EventStatus.CREATED);
    private final Subscription projectDelete = buildLoggingEvent(Event.PROJECT_EVENT_TYPE, EventStatus.DELETED);
    private final Subscription subjectCreated = buildLoggingEvent(Event.SUBJECT_EVENT_TYPE, EventStatus.CREATED);
    private final Subscription subjectDeleted = buildLoggingEvent(Event.SUBJECT_EVENT_TYPE, EventStatus.DELETED);
    private final Subscription sessionCreated = buildLoggingEvent(Event.SESSION_EVENT_TYPE, EventStatus.CREATED);
    private final Subscription sessionDeleted = buildLoggingEvent(Event.SESSION_EVENT_TYPE, EventStatus.DELETED);
    private final Subscription subjectAssessorCreated = buildLoggingEvent(Event.SUBJECT_ASSESSOR_TYPE, EventStatus.CREATED);
    private final Subscription subjectAssessorDeleted = buildLoggingEvent(Event.SUBJECT_ASSESSOR_TYPE, EventStatus.DELETED);
    private final Subscription scanCreated = buildLoggingEvent(Event.SCAN_EVENT_TYPE, EventStatus.CREATED);
    private final Subscription projectAssetCreated = buildLoggingEvent(Event.PROJECT_ASSET_TYPE, EventStatus.CREATED);
    private final Subscription imageAssessorCreated = buildLoggingEvent(Event.IMAGE_ASSESSOR_TYPE, EventStatus.CREATED);
    private final Subscription imageAssessorDeleted = buildLoggingEvent(Event.IMAGE_ASSESSOR_TYPE, EventStatus.DELETED);
    private final Project restProject = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final Subject restSubject = new Subject(restProject);
    private final MRSession restSession = new MRSession(restProject, restSubject);
    private final Project turbineProject = new Project();
    private final Project restXmlProject = new Project();
    private final Project xmlUploadProject = new Project();
    private Subject aaSubject, turbineSubject, xmlUploadSubject, importerSubject, restXmlSubject, petMrSubject;
    private MRSession aaSession, importerSession, splitMr, turbineSession;
    private PETSession splitPet;

    @BeforeClass
    public void setupSubscriptionsAndData() throws IOException {
        final String projectReplaceKey = "$PROJECT";
        final String subjectLabelReplaceKey = "$SUBJECT_LABEL";
        final String baseProjectXML = readDataFile("sample_project.xml");
        final String baseSubjecttXML = readDataFile("sample_subject.xml");

        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
        for (Subscription subscription : subscriptionsToCleanup) {
            mainAdminInterface().createSubscription(subscription);
        }
        mainInterface().createProject(restProject);
        projectsToCleanup.add(restProject);

        final Map<String, String> turbineProjectFormData = new HashMap<>();
        turbineProjectFormData.put("xnat:projectData/name", turbineProject.getId());
        turbineProjectFormData.put("xnat:projectData/secondary_ID", turbineProject.getId());
        turbineProjectFormData.put("xnat:projectData/ID", turbineProject.getId());
        turbineProjectFormData.put("arc:project/current_arc", "arc001");
        turbineProjectFormData.put("accessibility", Accessibility.PRIVATE.toString());
        mainInterface().requestWithCsrfToken().formParams(turbineProjectFormData).post(formatXnatUrl("/app/action/AddProject")).then().assertThat().statusCode(200);
        projectsToCleanup.add(turbineProject);
        mainInterface().setProjectPetMrSetting(turbineProject, PetMrProcessingSetting.SPLIT);

        aaSubject = new Subject(turbineProject);
        aaSession = new MRSession(turbineProject, aaSubject);
        final Map<Integer, String> aaHeaders = new HashMap<>();
        aaHeaders.put(Tag.PatientName, aaSubject.getLabel());
        aaHeaders.put(Tag.PatientID, aaSession.getLabel());
        new XnatCStore().data(TestData.SAMPLE_1).overwrittenHeaders(aaHeaders).sendDICOMToProject(turbineProject);
        final String splitSubjectBase = RandomHelper.randomID();
        petMrSubject = new Subject(turbineProject, splitSubjectBase);
        final Map<Integer, String> petMrHeaders = new HashMap<>();
        petMrHeaders.put(Tag.PatientName, splitSubjectBase);
        petMrHeaders.put(Tag.PatientID, splitSubjectBase + "_PETMR");
        new XnatCStore().data(TestData.DICOM_WEB_PETMR1).overwrittenHeaders(petMrHeaders).sendDICOMToProject(turbineProject);
        splitMr = new MRSession(turbineProject, petMrSubject, splitSubjectBase + "_MR");
        splitPet = new PETSession(turbineProject, petMrSubject, splitSubjectBase + "_PET");

        final File project3Xml = writeXmlToTempFile(baseProjectXML.replace(projectReplaceKey, restXmlProject.getId()));
        final File project4Xml = writeXmlToTempFile(baseProjectXML.replace(projectReplaceKey, xmlUploadProject.getId()));
        new ProjectXMLPutExtension(restXmlProject, project3Xml).create(mainInterface());
        projectsToCleanup.add(restXmlProject);
        postXml(project4Xml);
        projectsToCleanup.add(xmlUploadProject);
        xmlUploadSubject = new Subject(turbineProject);
        postXml(writeXmlToTempFile(baseSubjecttXML.replace(projectReplaceKey, turbineProject.getId()).replace(subjectLabelReplaceKey, xmlUploadSubject.getLabel())));

        turbineSubject = new Subject();
        final Map<String, String> subject2FormData = new HashMap<>();
        subject2FormData.put("xnat:subjectData/project", restProject.getId());
        subject2FormData.put("xnat:subjectData/label", turbineSubject.getLabel());
        mainInterface().requestWithCsrfToken().formParams(subject2FormData).post(formatXnatUrl("/app/action/EditSubjectAction")).then().assertThat().statusCode(200);

        restDriver.uploadToSessionZipImporter(TestData.SAMPLE_1, turbineProject);
        importerSubject = new Subject(turbineProject, "Sample_Patient");
        importerSession = new MRSession(turbineProject, importerSubject, "Sample_ID");

        restXmlSubject = new Subject(turbineProject);
        new SubjectXMLPutExtension(restXmlSubject, writeXmlToTempFile(baseSubjecttXML.replace(projectReplaceKey, turbineProject.getId()).replace(subjectLabelReplaceKey, restXmlSubject.getLabel())));
        mainInterface().createSubject(restXmlSubject);

        turbineSession = new MRSession(turbineProject, restXmlSubject);
        final Map<String, String> mrSessionFormData = new HashMap<>();
        mrSessionFormData.put("xnat:mrSessionData/project", turbineProject.getId());
        mrSessionFormData.put("xnat:mrSessionData/subject_id", restXmlSubject.getAccessionNumber());
        mrSessionFormData.put("xnat:mrSessionData/label:", turbineSession.getLabel());
        mainInterface().requestWithCsrfToken().formParams(mrSessionFormData).post(formatXnatUrl("/app/action/EditImageSessionAction")).then().assertThat().statusCode(200);

    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-6807")
    public void testProjectCreateEvent() {
        final Set<Project> created = Sets.newHashSet(restProject, turbineProject, restXmlProject, xmlUploadProject);
        final List<DeliveredEvent> projectEvents = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(projectCreate), created.size()
        );

        assertEquals(
                created.stream().map(Project::getId).collect(Collectors.toSet()),
                projectEvents.stream().map(event -> event.getTrigger().getLabel()).collect(Collectors.toSet())
        );
    }

    @Test
    public void testSubjectCreateEvent() {
        final Set<Subject> created = Sets.newHashSet(restSubject, turbineSubject, aaSubject, xmlUploadSubject, importerSubject, restXmlSubject, petMrSubject);
        final List<DeliveredEvent> subjectEvents = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subjectCreated), created.size()
        );

        assertEquals(
                created.stream().map(Subject::getLabel).collect(Collectors.toSet()),
                subjectEvents.stream().map(event -> event.getTrigger().getLabel()).collect(Collectors.toSet())
        );
    }

    @Test
    public void testSessionCreateEvent() {
        final Set<ImagingSession> created = Sets.newHashSet(restSession, aaSession, importerSession, splitMr, splitPet, turbineSession);
        final List<DeliveredEvent> sessionEvents = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(sessionCreated), created.size()
        );

        assertEquals(
                created.stream().map(ImagingSession::getLabel).collect(Collectors.toSet()),
                sessionEvents.stream().map(event -> event.getTrigger().getLabel()).collect(Collectors.toSet())
        );
    }

    private Subscription buildLoggingEvent(String eventType, EventStatus eventStatus) {
        final Subscription subscription = new SubscriptionBuilder().
                event(eventType, eventStatus).
                actionKey(Action.LOGGING_ACTION).
                build();
        subscriptionsToCleanup.add(subscription);
        return subscription;
    }

    private File writeXmlToTempFile(String xml) throws IOException {
        final File file = Files.createTempFile(Paths.get(Settings.TEMP_SUBDIR), "xml", "xml").toFile();
        FileUtils.write(file, xml, StandardCharsets.UTF_8);
        return file;
    }

    private void postXml(File file) {
        mainInterface().requestWithCsrfToken().multiPart("xml_to_store", file).formParam("allowdeletion", true).post(formatXnatUrl("/app/action/XMLUpload")).then().assertThat().statusCode(200);
    }

}
