package org.nrg.testing.xnat.tests;

import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import org.apache.commons.lang.RandomStringUtils;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.Attributes;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.pogo.dqr.DqrCFindRow;
import org.nrg.xnat.pogo.dqr.DqrCMoveSpec;
import org.nrg.xnat.pogo.dqr.DqrDateRange;
import org.nrg.xnat.pogo.dqr.DqrImportRequestStudy;
import org.nrg.xnat.pogo.dqr.DqrProjectSettings;
import org.nrg.xnat.pogo.dqr.DqrSearchResponse;
import org.nrg.xnat.pogo.dqr.DqrSeriesSearchResponse;
import org.nrg.xnat.pogo.dqr.DqrSeriesRepresentation;
import org.nrg.xnat.pogo.dqr.DqrStudyRepresentation;
import org.nrg.xnat.pogo.dqr.DqrPatientRepresentation;
import org.nrg.xnat.pogo.dqr.ExecutedPacsRequest;
import org.nrg.xnat.pogo.dqr.PacsAvailability;
import org.nrg.xnat.pogo.dqr.PacsConnection;
import org.nrg.xnat.pogo.dqr.PacsSearchCriteria;
import org.nrg.xnat.pogo.dqr.QueuedPacsRequest;
import org.nrg.xnat.pogo.experiments.Experiment;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Collection;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

@TestRequires(specificPluginRequirements = {@PluginRequirement(pluginId = "dicom-query-retrieve")})
public class TestDicomQueryRetrieve extends BaseXnatRestTest {

    private Project project;

    private final String PACS_AE_TITLE = Settings.DQR_PACS_AE_TITLE;
    private final String PACS_IP_ADDRESS = Settings.DQR_PACS_IP_ADDRESS;
    private final Integer PACS_PORT = Settings.DQR_PACS_PORT;
    private final String SCP_RECEIVER_AE_TITLE = Settings.DQR_SCP_RECEIVER_AE_TITLE;
    private final Integer SCP_RECEIVER_PORT = Settings.DQR_SCP_RECEIVER_PORT;
    private Integer PACS_ID;
    private final String PATIENT_NAME = "Weaver, Frank";
    private final String BASIC_QUERY_STUDY_INSTANCE_UID = "2.25.37375090832796046021266229626258542660";
    private final List<String> UUIDS_FOR_MULTIPLE_STUDY_TEST = Arrays.asList("2.25.283693626620512335671062727446518124353",
            "2.25.27474240684859391888104654239285917278");
    private final List<String> UUIDS_FOR_TEST_WITH_RELABEL = Arrays.asList("2.25.52201266192949052747593142428215101106",
            "2.25.39837004826281297885438269249933390833");
    private final String START_DATE = "20110101";
    private final String END_DATE = "20110101";
    private final File testCsvImportFile = getDataFile("dicom_query_retrieve/test_csv_dqr_retrieval.csv");

    private static final Map<String, Integer>   HEADER_TO_TAG_MAP  = Stream.of(new Object[][]{
            {"Relabel Accession Number", Tag.AccessionNumber},
            {"Relabel Study Date", Tag.StudyDate},
            {"Relabel Study ID", Tag.StudyID},
            {"Relabel Patient ID", Tag.PatientID},
            {"Relabel Patient Name", Tag.PatientName},
            {"Relabel Patient Birth Date", Tag.PatientBirthDate}})
            .collect(Collectors.toMap(entry -> (String) entry[0], entry -> (Integer) entry[1]));

    @BeforeClass
    public void setupDQRNeeds() {
        if (PACS_AE_TITLE == null || PACS_IP_ADDRESS == null ||SCP_RECEIVER_AE_TITLE == null) {
            throw new SkipException("Skipping DQR tests due to lack of input configuration elements.");
        }
        String aeTitle = PACS_AE_TITLE +RandomStringUtils.randomAlphabetic(5);
        PacsConnection pacsConnection = new PacsConnection();
        pacsConnection.aeTitle(aeTitle);
        pacsConnection.setHost(PACS_IP_ADDRESS);
        pacsConnection.setQueryRetrievePort(PACS_PORT);
        pacsConnection.setLabel(aeTitle);
        pacsConnection.queryable(true);
        pacsConnection.setDefaultQrAe(true);
        PACS_ID = mainAdminInterface().registerPacs(pacsConnection);

        DicomScpReceiver receiver = new DicomScpReceiver()
                .aeTitle(SCP_RECEIVER_AE_TITLE)
                .port(SCP_RECEIVER_PORT)
                .enabled(true)
                .customProcessing(true)
                .directArchive(false)
                .identifier("dqrObjectIdentifier")
                .anonymizationEnabled(true)
                .whitelistEnabled(false);
        mainAdminInterface().createOrUpdateDicomScpReceiver(receiver);

        //providing one thread of availability to PACS for all days of the week
        for (DayOfWeek day : DayOfWeek.values()) {
            PacsAvailability pacsAvailability = PacsAvailability.withDefaultOptions(day, PACS_ID);
            mainAdminInterface().configurePacsAvailability(pacsAvailability);
        }

    }

    @BeforeMethod
    public void setupProjectNeeds() {
        mainAdminInterface().assignUserToRoles(mainUser, "Dqr");

        project = new Project();
        mainInterface().createProject(project);

        assertTrue(mainInterface().pingPacs(PACS_ID).isSuccessful());

        mainAdminInterface().enableDqrForProject(project);
    }

    @AfterClass
    public void teardownDQRNeeds() {
        mainAdminInterface().deletePacsConnection(PACS_ID);
    }

    @AfterMethod
    public void teardownProjectNeeds() {
        mainAdminInterface().disableDqrForProject(project);

        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test
    public void testProjectSettings() {
        assertTrue(mainAdminInterface().readDqrProjectSettings().stream().map(DqrProjectSettings::getProjectId)
                .collect(Collectors.toList()).contains(project.getId()));
        assertTrue(mainAdminInterface().readDqrForProject(project).getDqrEnabled());
        assertTrue(mainAdminInterface().readDqrEnabledStatusOnProject(project));
    }

    @Test
    public void testBasicQuery() {
        PacsSearchCriteria searchCriteria = new PacsSearchCriteria();
        searchCriteria.pacsId(PACS_ID);
        searchCriteria.patientName(PATIENT_NAME);
        List<DqrStudyRepresentation> listOfStudies = mainInterface().studyCFind(searchCriteria);
        assertTrue(listOfStudies.stream().map(DqrStudyRepresentation::getPatient).map(DqrPatientRepresentation::getName)
                .allMatch(PATIENT_NAME::equals));
    }

    @Test
    public void testDateRangeQuery() {
        PacsSearchCriteria searchCriteria = new PacsSearchCriteria();
        searchCriteria.pacsId(PACS_ID);
        searchCriteria.patientName(PATIENT_NAME);
        DqrDateRange dateRange = new DqrDateRange();
        dateRange.setStart(START_DATE);
        dateRange.setEnd(END_DATE);
        searchCriteria.setStudyDateRange(dateRange);
        List<DqrStudyRepresentation> listOfStudies = mainInterface().studyCFind(searchCriteria);
        assertTrue(listOfStudies.stream().map(DqrStudyRepresentation::getPatient).map(DqrPatientRepresentation::getName)
                .allMatch(PATIENT_NAME::equals));
    }

    @Test
    public void testBasicDQRImport() {
        DqrSeriesSearchResponse resp = getSeriesForImport(Collections.singletonList(BASIC_QUERY_STUDY_INSTANCE_UID));
        DqrImportRequestStudy elementsForImportStudy = getElementsForImportStudy(BASIC_QUERY_STUDY_INSTANCE_UID, resp,
                Collections.emptyMap());
        DqrCMoveSpec importRequestCommands = setupImportRequestCommands(Collections.singletonList(elementsForImportStudy));

        List<QueuedPacsRequest> listOfImports = mainInterface().issueCMove(importRequestCommands);

        List<Long> listOfImportIds = listOfImports.stream().map(QueuedPacsRequest::getId).collect(Collectors.toList());

        assertTrue(runUntilImportNoLongerInQueue(listOfImportIds));

        assertTrue(checkIfImportsInHistory(listOfImportIds));

        assertTrue(checkAllStudiesArePresent(listOfImports.stream().map(QueuedPacsRequest::getStudyId)
                .collect(Collectors.toList())));
    }

    @Test
    public void testImportMultipleStudies() {
        DqrSeriesSearchResponse resp = getSeriesForImport(UUIDS_FOR_MULTIPLE_STUDY_TEST);
        List<DqrImportRequestStudy> seriesSortedByStudy = new ArrayList<>();
        for (String uuid : UUIDS_FOR_MULTIPLE_STUDY_TEST) {
            seriesSortedByStudy.add(getElementsForImportStudy(uuid, resp, Collections.emptyMap()));
        }
        DqrCMoveSpec importRequestCommands = setupImportRequestCommands(seriesSortedByStudy);

        List<QueuedPacsRequest> listOfImports = mainInterface().issueCMove(importRequestCommands);

        List<Long> listOfImportIds = listOfImports.stream().map(QueuedPacsRequest::getId).collect(Collectors.toList());

        assertTrue(runUntilImportNoLongerInQueue(listOfImportIds));

        assertTrue(checkIfImportsInHistory(listOfImportIds));

        assertTrue(checkAllStudiesArePresent(listOfImports.stream().map(QueuedPacsRequest::getStudyId)
                .collect(Collectors.toList())));
    }

    @Test
    public void testImportWithRelabeling() {
        DqrSeriesSearchResponse resp = getSeriesForImport(UUIDS_FOR_TEST_WITH_RELABEL);
        List<DqrImportRequestStudy> seriesSortedByStudy = new ArrayList<>();
        List<String> namesOfSessionsToBeImported = new ArrayList<>();
        for (String uuid : UUIDS_FOR_TEST_WITH_RELABEL) {
            String relabeledSessionName = ("session_relabel_" + uuid).replace('.', '_');
            Map<String, String> relabelMapForUUID = createRelabelMap("subject_relabel_" + uuid,
                    relabeledSessionName);
            seriesSortedByStudy.add(getElementsForImportStudy(uuid, resp, relabelMapForUUID));
            namesOfSessionsToBeImported.add(relabeledSessionName);
        }

        DqrCMoveSpec importRequestCommands = setupImportRequestCommands(seriesSortedByStudy);

        List<QueuedPacsRequest> listOfImports = mainInterface().issueCMove(importRequestCommands);

        List<Long> listOfImportIds = listOfImports.stream().map(QueuedPacsRequest::getId).collect(Collectors.toList());

        assertTrue(runUntilImportNoLongerInQueue(listOfImportIds));

        assertTrue(checkIfImportsInHistory(listOfImportIds));

        assertTrue(checkAllStudiesArePresent(namesOfSessionsToBeImported));
    }

    @Test
    public void testImportUsingCSVMethod() {
        List<DqrCFindRow> csvSearchResponses = mainInterface().studyCFindByCsv(PACS_ID, testCsvImportFile);
        List<String> studyInstanceUids = csvSearchResponses.stream().map(DqrCFindRow::getStudies)
                .flatMap(Collection::stream).map(DqrStudyRepresentation::getStudyInstanceUid)
                .collect(Collectors.toList());
        List<Map<String, String>> relabelMaps = csvSearchResponses.stream().map(DqrCFindRow::getRelabelMap)
                .collect(Collectors.toList());
        Map<String, Map<String, String>> uidToRelabelMapMap = Streams
                .zip(studyInstanceUids.stream(), relabelMaps.stream(), Maps::immutableEntry)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        DqrSeriesSearchResponse seriesSearchResponse = getSeriesForImport(studyInstanceUids);
        List<DqrImportRequestStudy> seriesSortedByStudy = new ArrayList<>();
        for (String uuid : studyInstanceUids) {
            Map<String, String> relabelMapForUUID = uidToRelabelMapMap.get(uuid);
            seriesSortedByStudy.add(getElementsForImportStudy(uuid, seriesSearchResponse, relabelMapForUUID));
        }

        DqrCMoveSpec importRequestCommands = setupImportRequestCommands(seriesSortedByStudy);

        //Need to disable the sitewide anonymization script in order to ensure that the Patient ID
        //header does not fail to work (these two can interfere with each other).
        mainAdminInterface().disableSiteAnonScript();

        List<QueuedPacsRequest> listOfImports = mainInterface().issueCMove(importRequestCommands);

        List<Long> listOfImportIds = listOfImports.stream().map(QueuedPacsRequest::getId).collect(Collectors.toList());

        assertTrue(runUntilImportNoLongerInQueue(listOfImportIds));

        assertTrue(checkIfImportsInHistory(listOfImportIds));

        assertTrue(checkVerboseRelabelMapChangesArePresent(uidToRelabelMapMap));
    }

    private Map<String, String> createRelabelMap(String subjectRelabel, String sessionRelabel) {
        Map<String, String> relabelMap = new HashMap<>();
        relabelMap.put("Subject", subjectRelabel);
        relabelMap.put("Session", sessionRelabel);
        return relabelMap;
    }

    private DqrImportRequestStudy getElementsForImportStudy(String studyInstanceUid, DqrSeriesSearchResponse response,
                                                          Map<String, String> relabelMap) {
        DqrSearchResponse<DqrSeriesRepresentation> responseSeries = response.get(studyInstanceUid);
        List<String> collectedUniqueIds = responseSeries.getResults().stream()
                .map(DqrSeriesRepresentation::getUniqueIdentifier).collect(Collectors.toList());
        List<String> collectedSeriesDescriptions = responseSeries.getResults().stream()
                .map(DqrSeriesRepresentation::getSeriesDescription).collect(Collectors.toList());

        return setupStudiesMap(relabelMap, collectedSeriesDescriptions, collectedUniqueIds, studyInstanceUid);
    }

    private DqrImportRequestStudy setupStudiesMap(Map<String, String> relabelMap, List<String> collectedSeriesDescriptions,
                                                List<String> collectedUniqueIds, String studyInstanceUid) {
        DqrImportRequestStudy importRequestStudy = new DqrImportRequestStudy();
        importRequestStudy.relabelMap(relabelMap);
        importRequestStudy.seriesDescriptions(collectedSeriesDescriptions);
        importRequestStudy.setSeriesInstanceUids(collectedUniqueIds);
        importRequestStudy.studyInstanceUid(studyInstanceUid);
        return importRequestStudy;
    }

    private DqrCMoveSpec setupImportRequestCommands(List<DqrImportRequestStudy> studies) {
        DqrCMoveSpec importSpecification = new DqrCMoveSpec();
        importSpecification.aeTitle(SCP_RECEIVER_AE_TITLE);
        importSpecification.forceImport(true);
        importSpecification.pacsId(PACS_ID);
        importSpecification.port(SCP_RECEIVER_PORT);
        importSpecification.projectId(project.getId());
        importSpecification.studies(studies);
        return importSpecification;
    }

    private DqrSeriesSearchResponse getSeriesForImport(List<String> studyInstanceUids) {
        return mainInterface().aggregatedSeriesCFinds(PACS_ID, studyInstanceUids);
    }

    private boolean runUntilImportNoLongerInQueue(List<Long> idsForCurrentRequest) {
        return await().atMost(2, TimeUnit.MINUTES)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> mainInterface().readDqrQueue().stream().map(ExecutedPacsRequest::getId)
                        .filter(idsForCurrentRequest::contains).findAny().orElse(null), equalTo(null))
                == null;
    }

    private boolean checkIfImportsInHistory(List<Long> idsOfImportInHistory) {
        return mainInterface().readDqrHistory().stream()
                .filter(req -> idsOfImportInHistory.contains(req.getId()))
                .map(ExecutedPacsRequest::getStatus)
                .allMatch("RECEIVED"::equals);
    }

    private boolean checkAllStudiesArePresent(List<String> allImportStudyLabels) {
        Project checkImportProject = mainInterface().readProject(project.getId());
        Set<String> experimentLabels = checkImportProject.getSubjects().stream().map(Subject::getExperiments)
                .flatMap(Collection::stream).map(Experiment::getLabel).collect(Collectors.toSet());
        return experimentLabels.containsAll(allImportStudyLabels);
    }

    private boolean checkVerboseRelabelMapChangesArePresent(Map<String, Map<String, String>> relabelMaps) {
        Project checkImportProject = mainInterface().readProject(project.getId());
        List<ImagingSession> sessions = checkImportProject.getSubjects().stream().map(Subject::getSessions)
                .flatMap(Collection::stream).collect(Collectors.toList());
        for (ImagingSession session : sessions) {
            List<File> allDicomsForSession = restDriver.downloadAllDicomFromSession(mainUser, project,
                    session.getSubject(), session);
            for (File dicom : allDicomsForSession) {
                Attributes a = DicomUtils.readDicom(dicom);
                Map<String, String> currentRelabelMap = relabelMaps.get(a.getString(Tag.StudyInstanceUID));
                for(Map.Entry entry : currentRelabelMap.entrySet()) {
                    if (HEADER_TO_TAG_MAP.containsKey(entry.getKey())) {
                        if (!a.getString(HEADER_TO_TAG_MAP.get(entry.getKey()))
                                .equals(entry.getValue())) {
                            return false;
                        }
                    } else if (entry.getKey().equals("Subject")) {
                        if (!session.getSubject().getLabel().equals(entry.getValue())) {
                            return false;
                        }
                    } else if (entry.getKey().equals("Session")) {
                        if (!session.getLabel().equals(entry.getValue())) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}