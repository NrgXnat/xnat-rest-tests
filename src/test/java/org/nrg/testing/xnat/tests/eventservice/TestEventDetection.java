package org.nrg.testing.xnat.tests.eventservice;

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.dcm4che3.data.Tag;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.PetMrProcessingSetting;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.events.*;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.NonimagingAssessor;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.scans.PETScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.extensions.project.ProjectXMLPostExtension;
import org.nrg.xnat.pogo.extensions.project.ProjectXMLPutExtension;
import org.nrg.xnat.pogo.extensions.subject.SubjectXMLPutExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SubjectAssessorXMLExtension;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_3;
import org.nrg.xnat.versions.Xnat_1_8_4;
import org.nrg.xnat.versions.Xnat_1_10_1;
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

    // XNAT-8342 (1.10.1) removed PET/MR separation: PetMrProcessingSetting.SPLIT no
    // longer splits a PET/MR import into separate PET + MR sessions, so the split
    // scenario (and its expected session/assessor/scan events) only applies to
    // earlier versions. On 1.10.1+ the PET/MR leg of the setup is skipped entirely.
    // TODO: once the unsplit petmr session's event shape (label, scan events) is
    // chartered on 1.10.1+, re-add coverage for it instead of skipping.
    private static final boolean PETMR_SPLIT_SUPPORTED = XnatTestingVersionManager.testedVersionPrecedes(Xnat_1_10_1.class);

    private final String SAMPLE1_SERIES_DESC1 = "t1_mpr_1mm_p2_pos50";
    private final String SAMPLE1_SERIES_DESC2 = "t2_spc_1mm_p2";
    private final String XML_SERIES_DESC1 = "LOCALIZER";
    private final String XML_SERIES_DESC3 = "SCANNED DOCUMENT";
    private final String PROJECT_REPLACE_KEY = "$PROJECT";
    private final String SUBJECT_LABEL_REPLACE_KEY = "$SUBJECT_LABEL";
    private final String SUBJECT_ID_REPLACE_KEY = "$SUBJECT_ID";
    private final String SESSION_LABEL_REPLACE_KEY = "$SESSION_LABEL";
    private final String SUBJECT_ASSESSOR_LABEL_REPLACE_KEY = "$SUBJECT_ASSESSOR_LABEL";
    private final String BASE_PROJECT_XML = readEventServiceXml("sample_project.xml");
    private final String BASE_SUBJECT_XML = readEventServiceXml("sample_subject.xml");
    private final String BASE_SESSION_XML = readEventServiceXml("sample_session.xml");
    private final String BASE_SUBJECT_ASSESSOR_XML = readEventServiceXml("sample_subject_assessor.xml");
    private final DataType subjectAssessorDataType = new DataType("xnat_a:ygtssData", "YGTSS", "Yale Global Tic Severity Scale Assessment");
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
    private final Subscription imageAssessorUpdated = buildLoggingEvent(Event.IMAGE_ASSESSOR_TYPE, EventStatus.UPDATED);
    private final Project restProject = new Project(appendRandom("REST")).prearchiveCode(PrearchiveCode.MANUAL);
    private final Subject restSubject = new Subject(restProject, appendRandom("REST"));
    private final MRSession restSession = new MRSession(restProject, restSubject, appendRandom("REST"));
    private final SubjectAssessor restSubjectAssessor = new NonimagingAssessor(restProject, restSubject, appendRandom("REST")).dataType(subjectAssessorDataType);
    private final Project turbineProject = new Project(appendRandom("TURBINE"));
    private final Project restXmlProject = new Project(appendRandom("REST_XML"));
    private final Project restXmlPostProject = new Project(appendRandom("REST_XML_POST"));
    private final Project xmlUploadProject = new Project(appendRandom("XML_UPLOAD"));
    private Subject aaSubject, turbineSubject, xmlUploadSubject, importerSubject, restXmlSubject, petMrSubject;
    private MRSession aaSession, importerSession, splitMr, turbineSession, xmlUploadSession, restXmlSession;
    private PETSession splitPet;
    private SubjectAssessor xmlUploadSubjectAssessor, restXmlSubjectAssessor, turbineSubjectAssessor;
    private Scan aaScan1, aaScan2, aaScan3, importerScan1, importerScan2, importerScan3, splitMrScan1, splitMrScan2, splitPetScan,
        xmlUploadScan1, xmlUploadScan2, xmlUploadScan3, restXmlScan1, restXmlScan2, restXmlScan3, turbineScan1, turbineScan2, turbineScan3;

    @BeforeClass
    private void setupSubscriptionsAndData() throws IOException {
        mainAdminInterface().setupDataType(subjectAssessorDataType);
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
        aaSubject = new Subject(turbineProject, appendRandom("AA"));
        aaSession = new MRSession(turbineProject, aaSubject, appendRandom("AA"));
        final Map<Integer, String> aaHeaders = new HashMap<>();
        aaHeaders.put(Tag.PatientName, aaSubject.getLabel());
        aaHeaders.put(Tag.PatientID, aaSession.getLabel());
        new XnatCStore().data(TestData.SAMPLE_1).overwrittenHeaders(aaHeaders).sendDICOMToProject(turbineProject);
        if (PETMR_SPLIT_SUPPORTED) {
            mainInterface().setProjectPetMrSetting(turbineProject, PetMrProcessingSetting.SPLIT);
            final String splitSubjectBase = appendRandom("SPLIT");
            petMrSubject = new Subject(turbineProject, splitSubjectBase);
            final Map<Integer, String> petMrHeaders = new HashMap<>();
            petMrHeaders.put(Tag.PatientName, splitSubjectBase);
            petMrHeaders.put(Tag.PatientID, splitSubjectBase + "_PETMR");
            new XnatCStore().data(TestData.DICOM_WEB_PETMR1).overwrittenHeaders(petMrHeaders).sendDICOMToProject(turbineProject);
            splitMr = new MRSession(turbineProject, petMrSubject, splitSubjectBase + "_MR");
            splitPet = new PETSession(turbineProject, petMrSubject, splitSubjectBase + "_PET");
        }

        new ProjectXMLPutExtension(restXmlProject, generateProjectXml(restXmlProject)).create(mainInterface());
        projectsToCleanup.add(restXmlProject);
        postXml(generateProjectXml(xmlUploadProject));
        projectsToCleanup.add(xmlUploadProject);
        new ProjectXMLPostExtension(restXmlPostProject, generateProjectXml(restXmlPostProject)).create(mainInterface());
        projectsToCleanup.add(restXmlPostProject);

        xmlUploadSubject = new Subject(turbineProject);
        postXml(generateSubjectXml(xmlUploadSubject));

        turbineSubject = new Subject();
        final Map<String, String> subject2FormData = new HashMap<>();
        subject2FormData.put("xnat:subjectData/project", restProject.getId());
        subject2FormData.put("xnat:subjectData/label", turbineSubject.getLabel());
        mainInterface().requestWithCsrfToken().formParams(subject2FormData).post(formatXnatUrl("/app/action/EditSubjectAction")).then().assertThat().statusCode(200);

        restDriver.uploadToSessionZipImporter(TestData.SAMPLE_1, turbineProject);
        importerSubject = new Subject(turbineProject, "Sample_Patient");
        importerSession = new MRSession(turbineProject, importerSubject, "Sample_ID");

        restXmlSubject = new Subject(turbineProject);
        new SubjectXMLPutExtension(restXmlSubject, generateSubjectXml(restXmlSubject));
        mainInterface().createSubject(restXmlSubject);

        turbineSession = new MRSession(turbineProject, restXmlSubject); // use subject with already cached accession number
        final Map<String, Object> mrSessionFormData = new HashMap<>();
        mrSessionFormData.put("xnat:mrSessionData/project", turbineSession.getPrimaryProject().getId());
        mrSessionFormData.put("xnat:mrSessionData/subject_id", turbineSession.getSubject().getAccessionNumber());
        mrSessionFormData.put("xnat:mrSessionData/label", turbineSession.getLabel());
        mrSessionFormData.put("ELEMENT_0", DataType.MR_SESSION.getXsiType());
        turbineScan1 = new MRScan(turbineSession, "0").type("T1w");
        turbineScan2 = new MRScan(turbineSession, "1").type("T2w");
        addScanParams(mrSessionFormData, turbineSession.getPrimaryProject(), turbineScan1);
        addScanParams(mrSessionFormData, turbineSession.getPrimaryProject(), turbineScan2);
        mainInterface().requestWithCsrfToken().formParams(mrSessionFormData).post(formatXnatUrl("/app/action/EditImageSessionAction")).then().assertThat().statusCode(200);
        turbineScan3 = new MRScan(turbineSession, "2").type("T2w");
        addScanParams(mrSessionFormData, turbineSession.getPrimaryProject(), turbineScan3);
        mrSessionFormData.put("xnat:mrSessionData/ID", mainInterface().getAccessionNumber(turbineSession));
        mainInterface().requestWithCsrfToken().formParams(mrSessionFormData).post(formatXnatUrl("/app/action/EditImageSessionAction")).then().assertThat().statusCode(200);

        xmlUploadSession = new MRSession(turbineProject, restXmlSubject);
        postXml(generateSessionXml(xmlUploadSession));

        restXmlSession = new MRSession(turbineProject, restXmlSubject);
        new SubjectAssessorXMLExtension(restXmlSession, generateSessionXml(restXmlSession));
        mainInterface().createSubjectAssessor(restXmlSession);

        turbineSubjectAssessor = new NonimagingAssessor(turbineProject, restXmlSubject, RandomHelper.randomID());
        final Map<String, String> subjectAssessorFormData = new HashMap<>();
        subjectAssessorFormData.put("project", turbineSubjectAssessor.getPrimaryProject().getId());
        subjectAssessorFormData.put("xnat_a:ygtssData/project", turbineSubjectAssessor.getPrimaryProject().getId());
        subjectAssessorFormData.put("xnat_a:ygtssData.subject_ID", turbineSubjectAssessor.getSubject().getAccessionNumber());
        subjectAssessorFormData.put("xnat_a:ygtssData/label", turbineSubjectAssessor.getLabel());
        subjectAssessorFormData.put("ELEMENT_0", subjectAssessorDataType.getXsiType());

        mainInterface().requestWithCsrfToken().formParams(subjectAssessorFormData).post(formatXnatUrl("/app/action/ModifySubjectAssessorData")).then().assertThat().statusCode(200);

        restXmlSubjectAssessor = new NonimagingAssessor(turbineProject, restXmlSubject, RandomHelper.randomID());
        new SubjectAssessorXMLExtension(restXmlSubjectAssessor, generateSubjectAssessorXml(restXmlSubjectAssessor));
        mainInterface().createSubjectAssessor(restXmlSubjectAssessor);

        xmlUploadSubjectAssessor = new NonimagingAssessor(turbineProject, restXmlSubject, RandomHelper.randomID());
        postXml(generateSubjectAssessorXml(xmlUploadSubjectAssessor));

        aaScan1 = new MRScan(aaSession, "4").type(SAMPLE1_SERIES_DESC1);
        aaScan2 = new MRScan(aaSession, "5").type(SAMPLE1_SERIES_DESC1);
        aaScan3 = new MRScan(aaSession, "6").type(SAMPLE1_SERIES_DESC2);
        importerScan1 = new MRScan(importerSession, "4").type(SAMPLE1_SERIES_DESC1);
        importerScan2 = new MRScan(importerSession, "5").type(SAMPLE1_SERIES_DESC1);
        importerScan3 = new MRScan(importerSession, "6").type(SAMPLE1_SERIES_DESC2);
        if (PETMR_SPLIT_SUPPORTED) {
            splitMrScan1 = new MRScan(splitMr, "5436027").type("Aligned_T1toPET_BOX");
            splitMrScan2 = new MRScan(splitMr, "5442056").type("Aligned_T2FStoPET_BOX");
            splitPetScan = new PETScan(splitPet, "1").type("PET AC");
        }
        xmlUploadScan1 = new MRScan(xmlUploadSession, "1").type(XML_SERIES_DESC1);
        xmlUploadScan2 = new MRScan(xmlUploadSession, "2");
        xmlUploadScan3 = new Scan(xmlUploadSession, "3-OT").type(XML_SERIES_DESC3);
        restXmlScan1 = new MRScan(restXmlSession, "1").type(XML_SERIES_DESC1);
        restXmlScan2 = new MRScan(restXmlSession, "2");
        restXmlScan3 = new Scan(restXmlSession, "3-OT").type(XML_SERIES_DESC3);
    }

    @Test
    @AddedIn(Xnat_1_8_4.class) // XNAT-6807
    public void testProjectCreateEvent() {
        final Set<Project> created = Sets.newHashSet(restProject, turbineProject, restXmlProject, xmlUploadProject, restXmlPostProject);
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
        final Set<Subject> created = Sets.newHashSet(restSubject, turbineSubject, aaSubject, xmlUploadSubject, importerSubject, restXmlSubject);
        if (PETMR_SPLIT_SUPPORTED) {
            created.add(petMrSubject);
        }
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
        final Set<ImagingSession> created = Sets.newHashSet(restSession, aaSession, importerSession, turbineSession, restXmlSession, xmlUploadSession);
        if (PETMR_SPLIT_SUPPORTED) {
            created.add(splitMr);
            created.add(splitPet);
        }
        final List<DeliveredEvent> sessionEvents = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(sessionCreated), created.size()
        );

        assertEquals(
                created.stream().map(ImagingSession::getLabel).collect(Collectors.toSet()),
                sessionEvents.stream().map(event -> event.getTrigger().getLabel()).collect(Collectors.toSet())
        );
    }

    @Test
    @AddedIn(Xnat_1_8_3.class)
    public void testSubjectAssessorCreateEvent() {
        final Set<SubjectAssessor> created = Sets.newHashSet(restSession, aaSession, importerSession, turbineSession, restXmlSession, xmlUploadSession, restSubjectAssessor, turbineSubjectAssessor, xmlUploadSubjectAssessor, restXmlSubjectAssessor);
        if (PETMR_SPLIT_SUPPORTED) {
            created.add(splitMr);
            created.add(splitPet);
        }
        final List<DeliveredEvent> sessionEvents = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(subjectAssessorCreated), created.size()
        );

        assertEquals(
                created.stream().map(SubjectAssessor::getLabel).collect(Collectors.toSet()),
                sessionEvents.stream().map(event -> event.getTrigger().getLabel()).collect(Collectors.toSet())
        );
    }

    @Test
    public void testScanCreateEvent() {
        final Set<Scan> created = Sets.newHashSet(aaScan1, aaScan2, aaScan3, importerScan1, importerScan2, importerScan3,
                xmlUploadScan1, xmlUploadScan2, xmlUploadScan3, restXmlScan1, restXmlScan2, restXmlScan3, turbineScan1, turbineScan2, turbineScan3);
        if (PETMR_SPLIT_SUPPORTED) {
            created.add(splitMrScan1);
            created.add(splitMrScan2);
            created.add(splitPetScan);
        }
        final List<DeliveredEvent> scanEvents = mainAdminInterface().queryDeliveredEvents(
                buildDeliveredEventQueryForSubscription(scanCreated), created.size()
        );

        assertEquals(
                created.stream().map(scan -> scan.getId() + " - " + scan.getType()).collect(Collectors.toSet()),
                scanEvents.stream().map(event -> event.getTrigger().getLabel()).collect(Collectors.toSet())
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

    private void postXml(File file) {
        mainInterface().requestWithCsrfToken().multiPart("xml_to_store", file).formParam("allowdeletion", true).post(formatXnatUrl("/app/action/XMLUpload")).then().assertThat().statusCode(200);
    }

    private String readEventServiceXml(String fileName) {
        return readDataFile("event_service_sample_xml" + File.separator + fileName);
    }

    private File writeXmlToTempFile(String xml) throws IOException {
        final File file = Files.createTempFile(Paths.get(Settings.TEMP_SUBDIR), "xml", "xml").toFile();
        FileUtils.write(file, xml, StandardCharsets.UTF_8);
        return file;
    }

    private File generateProjectXml(Project project) throws IOException {
        return writeXmlToTempFile(BASE_PROJECT_XML.replace(PROJECT_REPLACE_KEY, project.getId()));
    }

    private File generateSubjectXml(Subject subject) throws IOException {
        return writeXmlToTempFile(BASE_SUBJECT_XML.replace(PROJECT_REPLACE_KEY, subject.getPrimaryProject().getId()).replace(SUBJECT_LABEL_REPLACE_KEY, subject.getLabel()));
    }

    private File generateSessionXml(ImagingSession session) throws IOException {
        return writeXmlToTempFile(
                BASE_SESSION_XML.
                        replace(PROJECT_REPLACE_KEY, session.getPrimaryProject().getId()).
                        replace(SUBJECT_ID_REPLACE_KEY, session.getSubject().getAccessionNumber()).
                        replace(SESSION_LABEL_REPLACE_KEY, session.getLabel())
        );
    }

    private File generateSubjectAssessorXml(SubjectAssessor subjectAssessor) throws IOException {
        return writeXmlToTempFile(
                BASE_SUBJECT_ASSESSOR_XML.
                        replace(PROJECT_REPLACE_KEY, subjectAssessor.getPrimaryProject().getId()).
                        replace(SUBJECT_ID_REPLACE_KEY, subjectAssessor.getSubject().getAccessionNumber()).
                        replace(SUBJECT_ASSESSOR_LABEL_REPLACE_KEY, subjectAssessor.getLabel())

        );
    }

    private void addScanParams(Map<String, Object> params, Project project, Scan scan) {
        final int id = Integer.parseInt(scan.getId());
        final String baseXmlPath = String.format("xnat:mrSessionData/scans/scan[%d][@xsi:type=xnat:mrScanData]", id);
        params.put(baseXmlPath + "/ID", id);
        params.put(baseXmlPath + "/project", project.getId());
        params.put(baseXmlPath + "/type", scan.getType());
        params.put(baseXmlPath + "/quality", "usable");
    }

    private String appendRandom(String baseName) {
        return baseName + RandomHelper.randomID(10);
    }

}
