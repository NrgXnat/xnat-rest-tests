package org.nrg.testing.xnat.tests;

import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.Attributes;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.util.Version;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.PluginRegistry;
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
import org.nrg.xnat.pogo.dqr.DqrSettings;
import org.nrg.xnat.pogo.dqr.DqrSettings1x;
import org.nrg.xnat.pogo.dqr.DqrStudyRepresentation;
import org.nrg.xnat.pogo.dqr.DqrPatientRepresentation;
import org.nrg.xnat.pogo.dqr.ExecutedPacsRequest;
import org.nrg.xnat.pogo.dqr.PacsAvailability;
import org.nrg.xnat.pogo.dqr.PacsConnection;
import org.nrg.xnat.pogo.dqr.PacsSearchCriteria;
import org.nrg.xnat.pogo.dqr.QueuedPacsRequest;
import org.nrg.xnat.pogo.experiments.Experiment;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.users.User;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
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
import static org.testng.AssertJUnit.assertTrue;

@Slf4j
@TestRequires(specificPluginRequirements = {@PluginRequirement(pluginId = "dicom-query-retrieve")})
public class TestDicomQueryRetrieve extends BaseXnatRestTest {

    public static final String DQR_OBJECT_IDENTIFIER = "dqrObjectIdentifier";
    public static final String DQR_USER_ROLE = "Dqr";
    public static final Version DQR_2_0 = new Version("2.0");

    private static final String AVAILABILITY_CHECK_FREQUENCY = "5 seconds";
    private static final int MAX_PACS_REQUEST_ATTEMPTS = 1;
    private static final String DEFAULT_CALLING_AE_TITLE = "XNAT";

    private Project project;

    private final String PACS_DIMSE_AE_TITLE = Settings.DQR_PACS_DIMSE_AE_TITLE;
    private final String PACS_DIMSE_HOST = Settings.DQR_PACS_DIMSE_HOST;
    private final Integer PACS_DIMSE_PORT = Settings.DQR_PACS_DIMSE_PORT;
    private final String PACS_DICOMWEB_AE_TITLE = Settings.DQR_PACS_DICOMWEB_AE_TITLE;
    private final String PACS_DICOMWEB_ROOT_URL = Settings.DQR_PACS_DICOMWEB_ROOT_URL;
    private final String SCP_RECEIVER_AE_TITLE = Settings.DQR_SCP_RECEIVER_AE_TITLE;
    private final Integer SCP_RECEIVER_PORT = Settings.DQR_SCP_RECEIVER_PORT;

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

    @Data
    @Builder
    public static final class PacsTestData {
        Integer pacsId;
        String receiverAeTitle;
        Integer receiverPort;
    }
    private static final String PACS_DATA_PROVIDER = "pacsDataProvider";
    private final List<PacsTestData> data = new ArrayList<>();
    private final List<PacsConnection> pacsesToDelete = new ArrayList<>();
    @DataProvider(name = PACS_DATA_PROVIDER)
    public Object[][] pacsDataProvider() {
        return data.stream().map(pacsTestData -> new Object[] {pacsTestData}).toArray(Object[][]::new);
    }

    @BeforeClass
    public void setupDQRNeeds() {
        final Version dqrVersion = getPluginVersion(PluginRegistry.DQR_ID);
        final DqrSettings dqrSettings = (dqrVersion.lessThan(DQR_2_0) ? new DqrSettings1x() : new DqrSettings())
                .pacsAvailabilityCheckFrequency(AVAILABILITY_CHECK_FREQUENCY)
                .dqrMaxPacsRequestAttempts(MAX_PACS_REQUEST_ATTEMPTS)
                .dqrCallingAe(SCP_RECEIVER_AE_TITLE != null ? SCP_RECEIVER_AE_TITLE : DEFAULT_CALLING_AE_TITLE);
        mainAdminInterface().setDqrSettings(dqrSettings);

        mainAdminInterface().assignUserToRoles(mainUser, DQR_USER_ROLE);

        final List<PacsConnection> pacsConnections = mainAdminInterface().readAllPacsConnections();

        if (PACS_DIMSE_AE_TITLE != null && PACS_DIMSE_HOST != null && SCP_RECEIVER_AE_TITLE != null) {
            // Create or update DIMSE PACS connection
            PacsConnection dimsePacs = null;
            for (PacsConnection pacs : pacsConnections) {
                if (PACS_DIMSE_AE_TITLE.equals(pacs.getAeTitle()) && PACS_DIMSE_HOST.equals(pacs.getHost())
                    && !pacs.isDicomWebEnabled()) {
                    dimsePacs = pacs;
                    log.debug("Using existing DIMSE PACS {}", dimsePacs);
                    break;
                }
            }
            if (dimsePacs == null) {
                // Create a new PACS
                dimsePacs = new PacsConnection()
                        .aeTitle(PACS_DIMSE_AE_TITLE)
                        .host(PACS_DIMSE_HOST)
                        .queryRetrievePort(PACS_DIMSE_PORT)
                        .label(PACS_DIMSE_AE_TITLE + RandomStringUtils.randomAlphabetic(5))
                        .queryable(true);
                final int dimsePacsId = mainAdminInterface().registerPacs(dimsePacs);
                dimsePacs.setId(dimsePacsId);
                log.debug("Created new DIMSE PACS {}", dimsePacs);

                // We can delete it at the end of the test
                pacsesToDelete.add(dimsePacs);
            }

            addPacsAvailability(dimsePacs.getId());

            final DicomScpReceiver receiver = new DicomScpReceiver()
                    .aeTitle(SCP_RECEIVER_AE_TITLE)
                    .port(SCP_RECEIVER_PORT)
                    .enabled(true)
                    .customProcessing(true)
                    .directArchive(false)
                    .identifier(DQR_OBJECT_IDENTIFIER)
                    .anonymizationEnabled(true)
                    .whitelistEnabled(false);
            mainAdminInterface().createOrUpdateDicomScpReceiver(receiver);

            data.add(PacsTestData.builder()
                    .pacsId(dimsePacs.getId())
                    .receiverAeTitle(SCP_RECEIVER_AE_TITLE)
                    .receiverPort(SCP_RECEIVER_PORT)
                    .build());
        }

        if (getPluginVersion(PluginRegistry.DQR_ID).greaterThanOrEqualTo(DQR_2_0) &&
                PACS_DICOMWEB_AE_TITLE != null && PACS_DICOMWEB_ROOT_URL != null) {
            PacsConnection dicomwebPacs = null;
            for (PacsConnection pacs : pacsConnections) {
                if (pacs.isDicomWebEnabled() &&
                        PACS_DICOMWEB_AE_TITLE.equals(pacs.getAeTitle()) &&
                        PACS_DICOMWEB_ROOT_URL.equals(pacs.getDicomWebRootUrl())) {
                    dicomwebPacs = pacs;
                    log.debug("Found existing DICOMweb PACS {}", dicomwebPacs);
                    break;
                }
            }
            if (dicomwebPacs == null) {
                // Create a new PACS
                dicomwebPacs = new PacsConnection()
                        .aeTitle(PACS_DICOMWEB_AE_TITLE)
                        .label(PACS_DICOMWEB_AE_TITLE + RandomStringUtils.randomAlphabetic(5))
                        .queryable(true)
                        .dicomWebEnabled(true)
                        .dicomWebRootUrl(PACS_DICOMWEB_ROOT_URL)
                        .dicomObjectIdentifier(DQR_OBJECT_IDENTIFIER);
                final int dicomwebPacsId = mainAdminInterface().registerPacs(dicomwebPacs);
                dicomwebPacs.setId(dicomwebPacsId);
                log.debug("Created new DICOMweb PACS {}", dicomwebPacs);

                // We can delete it at the end of the test
                pacsesToDelete.add(dicomwebPacs);
            }

            addPacsAvailability(dicomwebPacs.getId());

            data.add(PacsTestData.builder()
                    .pacsId(dicomwebPacs.getId())
                    .receiverAeTitle(PACS_DICOMWEB_AE_TITLE)
                    .build());
        }

        if (data.isEmpty()) {
            throw new SkipException("No PACS test parameters defined");
        }
    }

    private void addPacsAvailability(Integer pacsId) {
        // Remove all existing availability
        mainAdminInterface().readPacsAvailability(pacsId).values().stream()
                .flatMap(List::stream)
                .forEach(availability -> mainAdminInterface().deletePacsAvailability(pacsId, availability.getId()));
        // create one thread of availability to PACS for all days of the week
        Arrays.stream(DayOfWeek.values()).forEach(
                day -> mainAdminInterface().configurePacsAvailability(PacsAvailability.withDefaultOptions(day, pacsId))
        );
    }

    @BeforeMethod
    public void setupProjectNeeds(final Object[] testArgs) {
        final PacsTestData testData = (PacsTestData) testArgs[0];
        if (!mainInterface().pingPacs(testData.getPacsId()).isSuccessful()) {
            throw new SkipException("Could not ping PACS " + testData.getPacsId());
        }

        project = new Project();
        mainInterface().createProject(project);

        mainAdminInterface().enableDqrForProject(project);
    }

    @AfterClass
    public void teardownDQRNeeds() {
        pacsesToDelete.forEach(
                pacs -> mainAdminInterface().deletePacsConnection(pacs.getId())
        );
    }

    @AfterMethod
    public void teardownProjectNeeds() {
        mainAdminInterface().disableDqrForProject(project);

        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test(dataProvider = PACS_DATA_PROVIDER)
    public void testProjectSettings(final PacsTestData testData) {
        assertTrue(mainAdminInterface().readDqrProjectSettings().stream().map(DqrProjectSettings::getProjectId)
                .collect(Collectors.toList()).contains(project.getId()));
        assertTrue(mainAdminInterface().readDqrForProject(project).getDqrEnabled());
        assertTrue(mainAdminInterface().readDqrEnabledStatusOnProject(project));
    }

    @Test(dataProvider = PACS_DATA_PROVIDER)
    public void testBasicQuery(final PacsTestData testData) {
        PacsSearchCriteria searchCriteria = new PacsSearchCriteria();
        searchCriteria.pacsId(testData.getPacsId());
        searchCriteria.patientName(PATIENT_NAME);
        List<DqrStudyRepresentation> listOfStudies = mainInterface().studyCFind(searchCriteria);
        assertTrue(listOfStudies.stream().map(DqrStudyRepresentation::getPatient).map(DqrPatientRepresentation::getName)
                .allMatch(PATIENT_NAME::equals));
    }

    @Test(dataProvider = PACS_DATA_PROVIDER)
    public void testDQRPermissions(final PacsTestData testData) {
        User newUser = createGenericUsers(1).get(0);
        PacsSearchCriteria searchCriteria = new PacsSearchCriteria();
        searchCriteria.pacsId(testData.getPacsId());
        searchCriteria.patientName(PATIENT_NAME);
        expect403(() -> interfaceFor(newUser).studyCFind(searchCriteria));
        mainAdminInterface().assignUserToRoles(newUser, DQR_USER_ROLE);
        List<DqrStudyRepresentation> listOfStudies = interfaceFor(newUser).studyCFind(searchCriteria);
        assertTrue(listOfStudies.stream().map(DqrStudyRepresentation::getPatient).map(DqrPatientRepresentation::getName)
                .allMatch(PATIENT_NAME::equals));
    }

    @Test(dataProvider = PACS_DATA_PROVIDER)
    public void testDateRangeQuery(final PacsTestData testData) {
        PacsSearchCriteria searchCriteria = new PacsSearchCriteria();
        searchCriteria.pacsId(testData.getPacsId());
        searchCriteria.patientName(PATIENT_NAME);
        DqrDateRange dateRange = new DqrDateRange();
        dateRange.setStart(START_DATE);
        dateRange.setEnd(END_DATE);
        searchCriteria.setStudyDateRange(dateRange);
        List<DqrStudyRepresentation> listOfStudies = mainInterface().studyCFind(searchCriteria);
        assertTrue(listOfStudies.stream().map(DqrStudyRepresentation::getPatient).map(DqrPatientRepresentation::getName)
                .allMatch(PATIENT_NAME::equals));
    }

    @Test(dataProvider = PACS_DATA_PROVIDER)
    public void testBasicDQRImport(final PacsTestData testData) {
        DqrSeriesSearchResponse resp = getSeriesForImport(testData.getPacsId(), Collections.singletonList(BASIC_QUERY_STUDY_INSTANCE_UID));
        DqrImportRequestStudy elementsForImportStudy = getElementsForImportStudy(BASIC_QUERY_STUDY_INSTANCE_UID, resp,
                Collections.emptyMap());
        DqrCMoveSpec importRequestCommands = setupImportRequestCommands(
                testData.getPacsId(), testData.getReceiverAeTitle(), testData.getReceiverPort(),
                Collections.singletonList(elementsForImportStudy)
        );

        List<QueuedPacsRequest> listOfImports = mainInterface().issueCMove(importRequestCommands);

        List<Long> listOfImportIds = listOfImports.stream().map(QueuedPacsRequest::getId).collect(Collectors.toList());

        assertTrue(runUntilImportNoLongerInQueue(listOfImportIds));

        waitForImportsReceivedStatus(listOfImportIds);

        checkAllStudiesArePresent(listOfImports.stream().map(QueuedPacsRequest::getStudyId)
                .collect(Collectors.toList()));
    }

    @Test(dataProvider = PACS_DATA_PROVIDER)
    public void testImportMultipleStudies(final PacsTestData testData) {
        DqrSeriesSearchResponse resp = getSeriesForImport(testData.getPacsId(), UUIDS_FOR_MULTIPLE_STUDY_TEST);
        List<DqrImportRequestStudy> seriesSortedByStudy = new ArrayList<>();
        for (String uuid : UUIDS_FOR_MULTIPLE_STUDY_TEST) {
            seriesSortedByStudy.add(getElementsForImportStudy(uuid, resp, Collections.emptyMap()));
        }
        DqrCMoveSpec importRequestCommands = setupImportRequestCommands(
                testData.getPacsId(), testData.getReceiverAeTitle(), testData.getReceiverPort(),
                seriesSortedByStudy
        );

        List<QueuedPacsRequest> listOfImports = mainInterface().issueCMove(importRequestCommands);

        List<Long> listOfImportIds = listOfImports.stream().map(QueuedPacsRequest::getId).collect(Collectors.toList());

        assertTrue(runUntilImportNoLongerInQueue(listOfImportIds));

        waitForImportsReceivedStatus(listOfImportIds);

        checkAllStudiesArePresent(listOfImports.stream().map(QueuedPacsRequest::getStudyId)
                .collect(Collectors.toList()));
    }

    @Test(dataProvider = PACS_DATA_PROVIDER)
    public void testImportWithRelabeling(final PacsTestData testData) {
        DqrSeriesSearchResponse resp = getSeriesForImport(testData.getPacsId(), UUIDS_FOR_TEST_WITH_RELABEL);
        List<DqrImportRequestStudy> seriesSortedByStudy = new ArrayList<>();
        List<String> namesOfSessionsToBeImported = new ArrayList<>();
        for (String uuid : UUIDS_FOR_TEST_WITH_RELABEL) {
            String relabeledSessionName = ("session_relabel_" + uuid).replace('.', '_');
            Map<String, String> relabelMapForUUID = createRelabelMap("subject_relabel_" + uuid,
                    relabeledSessionName);
            seriesSortedByStudy.add(getElementsForImportStudy(uuid, resp, relabelMapForUUID));
            namesOfSessionsToBeImported.add(relabeledSessionName);
        }

        DqrCMoveSpec importRequestCommands = setupImportRequestCommands(
                testData.getPacsId(), testData.getReceiverAeTitle(), testData.getReceiverPort(),
                seriesSortedByStudy
        );

        List<QueuedPacsRequest> listOfImports = mainInterface().issueCMove(importRequestCommands);

        List<Long> listOfImportIds = listOfImports.stream().map(QueuedPacsRequest::getId).collect(Collectors.toList());

        assertTrue(runUntilImportNoLongerInQueue(listOfImportIds));

        waitForImportsReceivedStatus(listOfImportIds);

        checkAllStudiesArePresent(namesOfSessionsToBeImported);
    }

    @Test(dataProvider = PACS_DATA_PROVIDER)
    public void testImportUsingCSVMethod(final PacsTestData testData) {
        List<DqrCFindRow> csvSearchResponses = mainInterface().studyCFindByCsv(testData.getPacsId(), testCsvImportFile);
        List<String> studyInstanceUids = csvSearchResponses.stream().map(DqrCFindRow::getStudies)
                .flatMap(Collection::stream).map(DqrStudyRepresentation::getStudyInstanceUid)
                .collect(Collectors.toList());
        List<Map<String, String>> relabelMaps = csvSearchResponses.stream().map(DqrCFindRow::getRelabelMap)
                .collect(Collectors.toList());
        Map<String, Map<String, String>> uidToRelabelMapMap = Streams
                .zip(studyInstanceUids.stream(), relabelMaps.stream(), Maps::immutableEntry)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        DqrSeriesSearchResponse seriesSearchResponse = getSeriesForImport(testData.getPacsId(), studyInstanceUids);
        List<DqrImportRequestStudy> seriesSortedByStudy = new ArrayList<>();
        for (String uuid : studyInstanceUids) {
            Map<String, String> relabelMapForUUID = uidToRelabelMapMap.get(uuid);
            seriesSortedByStudy.add(getElementsForImportStudy(uuid, seriesSearchResponse, relabelMapForUUID));
        }

        DqrCMoveSpec importRequestCommands = setupImportRequestCommands(
                testData.getPacsId(), testData.getReceiverAeTitle(), testData.getReceiverPort(),
                seriesSortedByStudy
        );

        //Need to disable the sitewide anonymization script in order to ensure that the Patient ID
        //header does not fail to work (these two can interfere with each other).
        mainAdminInterface().disableSiteAnonScript();

        List<QueuedPacsRequest> listOfImports = mainInterface().issueCMove(importRequestCommands);

        List<Long> listOfImportIds = listOfImports.stream().map(QueuedPacsRequest::getId).collect(Collectors.toList());

        assertTrue(runUntilImportNoLongerInQueue(listOfImportIds));

        waitForImportsReceivedStatus(listOfImportIds);

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

    private DqrCMoveSpec setupImportRequestCommands(final Integer pacsId,
                                                    final String receiverAeTitle,
                                                    final Integer receiverPort,
                                                    final List<DqrImportRequestStudy> studies) {
        DqrCMoveSpec importSpecification = new DqrCMoveSpec();
        importSpecification.aeTitle(receiverAeTitle);
        importSpecification.forceImport(true);
        importSpecification.pacsId(pacsId);
        importSpecification.port(receiverPort);
        importSpecification.projectId(project.getId());
        importSpecification.studies(studies);
        return importSpecification;
    }

    private DqrSeriesSearchResponse getSeriesForImport(final Integer pacsId, final List<String> studyInstanceUids) {
        return mainInterface().aggregatedSeriesCFinds(pacsId, studyInstanceUids);
    }

    private boolean runUntilImportNoLongerInQueue(List<Long> idsForCurrentRequest) {
        return await().atMost(2, TimeUnit.MINUTES)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> mainInterface().readDqrQueue().stream().map(ExecutedPacsRequest::getId)
                        .filter(idsForCurrentRequest::contains).findAny().orElse(null), equalTo(null))
                == null;
    }

    private void waitForImportsReceivedStatus(List<Long> idsOfImportInHistory) {
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> mainInterface().readDqrHistory().stream()
                        .filter(req -> idsOfImportInHistory.contains(req.getId()))
                        .map(ExecutedPacsRequest::getStatus)
                        .allMatch("RECEIVED"::equals));
    }

    private void checkAllStudiesArePresent(List<String> allImportStudyLabels) {
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Project checkImportProject = mainInterface().readProject(project.getId());
                    Set<String> experimentLabels = checkImportProject.getSubjects().stream().map(Subject::getExperiments)
                            .flatMap(Collection::stream).map(Experiment::getLabel).collect(Collectors.toSet());
                    return experimentLabels.containsAll(allImportStudyLabels);
                });
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