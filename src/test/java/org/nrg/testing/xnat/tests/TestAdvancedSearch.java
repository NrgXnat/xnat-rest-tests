package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Share;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.rest.NotFoundException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.nrg.testing.TestGroups.SEARCH;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class TestAdvancedSearch extends BaseXnatRestTest {
    private static final String SUBJECT_LABEL = "subject_label";
    private static final String SUBJECT = "subject";
    private static final String XNAT_MRSESSIONDATA_LABEL = "xnat_mrsessiondata_label";
    private static final String XNAT_MRSCANDATA_ID = "xnat_mrscandata_id";
    private static final String XNAT_QCMANUALASSESSORDATA_EXPT_ID = "xnat_qcmanualassessordata_expt_id";
    private static final String EXPT_ID = "expt_id";
    private static final String SESSION = "session";
    private static final String LABEL = "label";
    private static final String XNAT_SUBJECTDATA_SUBJECT_LABEL = "xnat_subjectdata_subject_label";
    private static final String LAST_SUBJECT_SCAN = "lastSubjectScan";
    private static final String LAST_SESSION_SCAN = "lastSessionScan";
    private static final String FIRST_SESSION_SCAN = "firstSessionScan";
    private static final String QC = "qc";
    private static final String RESULT_SET_DOT_RESULT = "ResultSet.Result";
    private static final String ID = "id";
    private static final String FIRST_SESSION_QC = "firstSessionQC";
    private static final List<String> SUBJECT_JOIN_KEYS = Arrays.asList(SUBJECT_LABEL, XNAT_MRSESSIONDATA_LABEL, XNAT_MRSCANDATA_ID, XNAT_QCMANUALASSESSORDATA_EXPT_ID);
    private static final List<String> QC_JOIN_KEYS = Arrays.asList(XNAT_MRSESSIONDATA_LABEL, XNAT_SUBJECTDATA_SUBJECT_LABEL, XNAT_MRSCANDATA_ID, EXPT_ID);
    private static final List<String> SESSION_JOIN_KEYS = Arrays.asList(LABEL, XNAT_SUBJECTDATA_SUBJECT_LABEL, XNAT_MRSCANDATA_ID, XNAT_QCMANUALASSESSORDATA_EXPT_ID);
    private static final List<String> SCAN_JOIN_KEYS = Arrays.asList(XNAT_MRSESSIONDATA_LABEL, XNAT_SUBJECTDATA_SUBJECT_LABEL, ID, XNAT_QCMANUALASSESSORDATA_EXPT_ID);
    private List<String> subjectLabels = null;
    private List<String> sessionLabels = null;
    private List<String> scanIds = null;
    private List<String> qcLabel = null;
    private List<String> qcIds = null;
    private Set<String> subjectLabelsSet = null;
    private Set<String> sessionLabelsSet = null;
    private Set<String> scanIdsSet = null;
    private Set<String> qcIdsSet = null;
    private Map<String, Object> targetObject = null;

    @BeforeClass
    private void addTestProjects() {
        final Project mp = generateEntireProject(3);
        targetObject = getJoinTargets(mp.getSubjects().get(1));
        customizeJoinTargets();
        mainInterface().createProject(mp);

        final Project privateProject = generateEntireProject(2);
        mainAdminInterface().createProject(privateProject);

        final Project publicProject = generateEntireProject(2);
        publicProject.setAccessibility(Accessibility.PUBLIC);
        mainAdminInterface().createProject(publicProject);

        final List<Share> shares = Collections.singletonList(new Share(mp));

        final Project sharedProject = generateEntireProject(3);
        final Subject sharedSubject = sharedProject.getSubjects().get(0);
        final Subject sharedSubjectOfSharedSession = sharedProject.getSubjects().get(2);
        sharedSubject.setShares(shares);
        sharedSubjectOfSharedSession.setShares(shares);
        mainAdminInterface().createProject(sharedProject);

        final MRSession sharedSession = new MRSession(sharedProject, sharedSubjectOfSharedSession);
        generateQC(sharedSession, 2);
        generateMrScan(sharedSession, 2);
        sharedSession.setShares(shares);
        mainAdminInterface().createSubjectAssessor(sharedSession);

        final List<Subject> validSubjects = Stream.concat(mp.getSubjects().stream(), publicProject.getSubjects().stream()).collect(Collectors.toList());
        final List<ImagingSession> validSessions = validSubjects.stream().flatMap(s -> s.getSessions().stream()).collect(Collectors.toList());
        final List<Scan> validScans = validSessions.stream().flatMap(s -> s.getScans().stream()).collect(Collectors.toList());
        final List<SessionAssessor> validQcs = validSessions.stream().flatMap(s -> s.getAssessors().stream()).collect(Collectors.toList());

        validSubjects.add(sharedSubject);
        validSubjects.add(sharedSubjectOfSharedSession);
        validSessions.add(sharedSession);

        subjectLabels = validSubjects.stream().map(Subject::getLabel).sorted().collect(Collectors.toList());
        sessionLabels = validSessions.stream().map(ImagingSession::getLabel).sorted().collect(Collectors.toList());
        scanIds = validScans.stream().map(Scan::getId).sorted().collect(Collectors.toList());
        qcLabel = validQcs.stream().map(SessionAssessor::getLabel).sorted().collect(Collectors.toList());
        qcIds = validQcs.stream().map(SessionAssessor::getAccessionNumber).sorted().collect(Collectors.toList());

        subjectLabelsSet = subjectLabels.stream().collect(Collectors.toSet());
        sessionLabelsSet = sessionLabels.stream().collect(Collectors.toSet());
        scanIdsSet = scanIds.stream().collect(Collectors.toSet());
        qcIdsSet = qcIds.stream().collect(Collectors.toSet());
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testSubjectSearch() throws IOException {
        List<Map<String, Object>> results = getRestCallResults("subject_search.xml");

        assertEquals(subjectLabels.size(), results.size());
        assertEquals(subjectLabels, getValuesByField(results, SUBJECT_LABEL));
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testSessionSearch() throws IOException {
        List<Map<String, Object>> results = getRestCallResults("mrsession_search.xml");

        assertEquals(sessionLabels.size(), results.size());
        assertEquals(sessionLabels, getValuesByField(results, LABEL));
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testScanSearch() throws IOException {
        List<Map<String, Object>> results = getRestCallResults("mrscan_search.xml");

        assertEquals(scanIds.size(), results.size());
        assertEquals(scanIds, getValuesByField(results, ID));
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testQcSearch() throws IOException {
        List<Map<String, Object>> results = getRestCallResults("qc_search.xml");

        assertEquals(qcLabel.size(), results.size());
        assertEquals(qcLabel, getValuesByField(results, LABEL));
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testSubjectJoinSearch() throws IOException {
        List<Map<String, Object>> results = getRestCallResults("subject_session_scan_qc_search.xml");

        assertEquals(subjectLabels.size(), results.size());
        assertTrue(sessionLabelsSet.containsAll(getValuesByField(results, XNAT_MRSESSIONDATA_LABEL)));
        assertTrue(scanIdsSet.containsAll(getValuesByField(results, XNAT_MRSCANDATA_ID)));
        assertTrue(qcIdsSet.containsAll(getValuesByField(results, XNAT_QCMANUALASSESSORDATA_EXPT_ID)));

        Map<String, Object> targetResult = findResult(results, SUBJECT_LABEL, ((Subject) targetObject.get(SUBJECT)).getLabel());
        List<String> expectedValues = getExpectedValues(targetObject, Arrays.asList(SUBJECT, SESSION, QC, LAST_SUBJECT_SCAN));

        //The closest in date condition: Subject-Date Added, MRSession-Date, QC-Date, Scan-the last subject's scan.
        assertEquals(expectedValues, getValuesByKeys(targetResult, SUBJECT_JOIN_KEYS));
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testSessionJoinSearch() throws IOException {
        List<Map<String, Object>> results = getRestCallResults("session_subject_scan_qc_search.xml");

        assertEquals(sessionLabels.size(), results.size());
        assertTrue(subjectLabelsSet.containsAll(getValuesByField(results, XNAT_SUBJECTDATA_SUBJECT_LABEL)));
        assertTrue(scanIdsSet.containsAll(getValuesByField(results, XNAT_MRSCANDATA_ID)));
        assertTrue(qcIdsSet.containsAll(getValuesByField(results, XNAT_QCMANUALASSESSORDATA_EXPT_ID)));

        Map<String, Object> targetResult = findResult(results, LABEL, ((MRSession) targetObject.get(SESSION)).getLabel());
        List<String> expectedValues = getExpectedValues(targetObject, Arrays.asList(SUBJECT, SESSION, QC, LAST_SESSION_SCAN));

        //The closest in date condition: Subject-Date Added, MRSession-Date, QC-Date, Scan-the last session's scan.
        assertEquals(expectedValues, getValuesByKeys(targetResult, SESSION_JOIN_KEYS));
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    @ExpectedFailure(jiraIssue = "XNAT-7662")
    public void testQcJoinSearch() throws IOException {
        List<Map<String, Object>> results = getRestCallResults("qc_subject_session_scan_search.xml");

        assertEquals(qcLabel.size(), results.size());
        assertTrue(subjectLabelsSet.containsAll(getValuesByField(results, XNAT_SUBJECTDATA_SUBJECT_LABEL)));
        assertTrue(sessionLabelsSet.containsAll(getValuesByField(results, XNAT_MRSESSIONDATA_LABEL)));
        assertTrue(scanIdsSet.containsAll(getValuesByField(results, XNAT_MRSCANDATA_ID)));

        Map<String, Object> targetResult = findResult(results, EXPT_ID, ((ManualQC) targetObject.get(QC)).getAccessionNumber());
        List<String> expectedValues = getExpectedValues(targetObject, Arrays.asList(SUBJECT, SESSION, QC, FIRST_SESSION_SCAN));

        //Test failed as the joined scan is not consistent.
        assertEquals(expectedValues, getValuesByKeys(targetResult, QC_JOIN_KEYS));
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    @ExpectedFailure(jiraIssue = "XNAT-7662")
    public void testScanJoinSearch() throws IOException {
        List<Map<String, Object>> results = getRestCallResults("scan_subject_session_qc_search.xml");

        assertEquals(scanIds.size(), results.size());
        assertTrue(subjectLabelsSet.containsAll(getValuesByField(results, XNAT_SUBJECTDATA_SUBJECT_LABEL)));
        assertTrue(sessionLabelsSet.containsAll(getValuesByField(results, XNAT_MRSESSIONDATA_LABEL)));
        assertTrue(qcIdsSet.containsAll(getValuesByField(results, XNAT_QCMANUALASSESSORDATA_EXPT_ID)));

        Map<String, Object> targetResult = findResult(results, ID, ((MRScan) targetObject.get(FIRST_SESSION_SCAN)).getId());
        List<String> expectedValues = getExpectedValues(targetObject, Arrays.asList(SUBJECT, SESSION, FIRST_SESSION_QC, FIRST_SESSION_SCAN));

        //Test failed as the joined QC is not consistent.
        assertEquals(expectedValues, getValuesByKeys(targetResult, SCAN_JOIN_KEYS));
    }

    private Map<String, Object> findResult(final List<Map<String, Object>> results, final String key, final String value) {
        return results.stream().filter(r -> StringUtils.equalsAnyIgnoreCase(value, r.get(key).toString())).findFirst().orElseThrow(NotFoundException::new);
    }

    private List<String> getValuesByKeys(final Map<String, Object> target, final List<String> keys) {
        return keys.stream().map(key -> target.get(key).toString()).sorted().collect(Collectors.toList());
    }

    private List<String> getValuesByField(final List<Map<String, Object>> results, final String field) {
        return results.stream().map(result -> result.get(field).toString()).filter(StringUtils::isNotBlank).sorted().collect(Collectors.toList());
    }

    private List<Map<String, Object>> getRestCallResults(final String file) throws IOException {
        String rawTestString = FileUtils.readFileToString(getDataFile("search/" + file), "UTF-8")
                .replaceAll("SITE_URL_BASE/", formatXnatUrl());

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("format", "json");
        queryParams.put("refresh", "true");
        return mainInterface()
                .requestWithCsrfToken()
                .contentType(ContentType.JSON)
                .body(rawTestString)
                .queryParams(queryParams)
                .post(formatRestUrl("/search/"))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .extract()
                .response()
                .jsonPath()
                .getList(RESULT_SET_DOT_RESULT);
    }

    private Project generateEntireProject(int number) {
        Project p = registerTempProject();
        List<Subject> subjects = generateSubject(p, number);
        List<MRSession> mrSessions = subjects.stream().map(s -> generateMrSession(s, number)).flatMap(List::stream).collect(Collectors.toList());
        mrSessions.forEach(mr -> generateMrScan(mr, number));
        mrSessions.forEach(mr -> generateQC(mr, number));
        return p;
    }

    private List<Subject> generateSubject(final Project project, int number) {
        List<Subject> subjects = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            Subject subject = new Subject(project);
            subjects.add(subject);
        }
        return subjects;
    }

    private List<MRSession> generateMrSession(Subject subject, int number) {
        List<MRSession> mrSessions = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            MRSession session = new MRSession().date(LocalDate.parse("2012-01-01"));
            subject.addExperiment(session);
            mrSessions.add(session);
        }
        return mrSessions;
    }

    private List<MRScan> generateMrScan(MRSession session, int number) {
        List<MRScan> scans = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            String label = RandomStringUtils.random(10, true, true);
            MRScan scan = new MRScan(session, label);
            scans.add(scan);
        }
        return scans;
    }

    private List<ManualQC> generateQC(MRSession session, int number) {
        List<ManualQC> manualQCs = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            ManualQC qc = new ManualQC().date(LocalDate.parse("2012-01-01"));
            session.addAssessor(qc);
            manualQCs.add(qc);
        }
        return manualQCs;
    }

    private Map<String, Object> getJoinTargets(final Subject subject) {
        List<ImagingSession> sessions = subject.getSessions();
        ImagingSession session = sessions.get(1);
        ManualQC firstSessionQc = (ManualQC) session.getAssessors().get(0);
        ManualQC qc = (ManualQC) session.getAssessors().get(1);
        List<Scan> scans = sessions.get(sessions.size() - 1).getScans();
        MRScan lastSubjectScan = (MRScan) scans.get(scans.size() - 1);
        List<Scan> sessionScans = session.getScans();
        MRScan lastSessionScan = (MRScan) sessionScans.get(sessionScans.size() - 1);
        MRScan firstSessionScan = (MRScan) sessionScans.get(0);
        Map<String, Object> targets = new HashMap<>();
        targets.put(SUBJECT, subject);
        targets.put(SESSION, session);
        targets.put(LAST_SUBJECT_SCAN, lastSubjectScan);
        targets.put(LAST_SESSION_SCAN, lastSessionScan);
        targets.put(FIRST_SESSION_SCAN, firstSessionScan);
        targets.put(QC, qc);
        targets.put(FIRST_SESSION_QC, firstSessionQc);
        return targets;
    }

    private void customizeJoinTargets() {
        LocalDate today = LocalDate.now();
        ((MRSession) targetObject.get(SESSION)).date(today);
        ((ManualQC) targetObject.get(QC)).date(today);
    }

    private List<String> getExpectedValues(final Map<String, Object> target, List<String> keys) {
        return keys.stream().map(key -> {
            switch (key) {
                case SUBJECT:
                    return ((Subject) (target.get(SUBJECT))).getLabel();
                case SESSION:
                    return ((MRSession) (target.get(SESSION))).getLabel();
                case QC:
                    return ((ManualQC) (target.get(QC))).getAccessionNumber();
                case FIRST_SESSION_QC:
                    return ((ManualQC) (target.get(FIRST_SESSION_QC))).getAccessionNumber();
                case LAST_SESSION_SCAN:
                    return ((MRScan) (target.get(LAST_SESSION_SCAN))).getId();
                case LAST_SUBJECT_SCAN:
                    return ((MRScan) (target.get(LAST_SUBJECT_SCAN))).getId();
                case FIRST_SESSION_SCAN:
                    return ((MRScan) (target.get(FIRST_SESSION_SCAN))).getId();
            }
            return null;
        }).sorted().collect(Collectors.toList());
    }
}
