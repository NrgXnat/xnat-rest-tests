package org.nrg.testing.xnat.tests.search;

import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.nrg.testing.TestGroups.SEARCH;
import static org.testng.AssertJUnit.assertEquals;

public class TestDynamicSearchSubjects extends BaseDynamicSearchTest {

    private static final String ID = "ID";
    private static final String URI = "URI";
    private static final String PROJECT = "project";
    private static final String LABEL = "label";
    private static final String GENDER = "gender";
    private static final String HANDEDNESS = "handedness";
    private static final String EDUCATION = "education";
    private static final String RACE = "race";
    private static final String ETHNICITY = "ethnicity";
    private static final String GROUP = "group";
    private static final String YOB = "yob";
    private static final String DOB = "dob";
    private static final String AGE = "age";
    private static final String HEIGHT = "height";
    private static final String WEIGHT = "weight";
    private static final String SRC = "src";
    private static final String IMAGE_SESSION_ID = "xnat:imageSessionData/id";
    private static final String IMAGE_SESSION_LABEL = "xnat:imageSessionData/label";
    private static final String MR_SESSION_FIELD_STRENGTH = "xnat:mrSessionData/fieldStrength";
    private static final String MR_SESSION_ID = "xnat:mrSessionData/id";
    private static final String MR_SESSION_LABEL = "xnat:mrSessionData/label";
    private static final String MR_SCAN_ID = "xnat:mrScanData/id";
    private static final String MR_SCAN_SERIES_DESCRIPTION = "xnat:mrScanData/series_description";
    private static final String MR_SCAN_QUALITY = "xnat:mrScanData/quality";

    private static final Function<Subject, Map<String, String>> minimalMapping = (subject) -> {
        final Map<String, String> asMap = new HashMap<>();
        asMap.put(ID, subject.getAccessionNumber());
        asMap.put(URI, subjectUri(subject));
        return asMap;
    };

    private static final Function<Subject, Map<String, String>> defaultMapping = (subject) -> {
        final Map<String, String> asMap = minimalMapping.apply(subject);
        asMap.put(PROJECT, subject.getPrimaryProject().getId());
        asMap.put(LABEL, subject.getLabel());
        return asMap;
    };

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSubjectSearchDefaultColumns() {
        final SearchResponse queryResponse = querySubjects(new HashMap<>());

        new DynamicSearchResultValidator<Subject>()
                .uniquenessKey(ID)
                .objectMappingFunction(defaultMapping)
                .keysToIgnore(Arrays.asList("insert_user", "insert_date"))
                .validateDynamicSearchResponse(
                        queryResponse,
                        Arrays.asList(publicSubject, customGroupSubject, customGroupSubjectDob, customGroupSubjectAge),
                        Collections.singletonList(ownerSubject)
                );

        assertEquals(4, queryResponse.getTotalRecords());
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSubjectSearchMinimalColumns() {
        final SearchResponse queryResponse = querySubjects(mapWithColumns(ID));

        new DynamicSearchResultValidator<Subject>()
                .uniquenessKey(ID)
                .objectMappingFunction(minimalMapping)
                .validateDynamicSearchResponse(
                        queryResponse,
                        Arrays.asList(publicSubject, customGroupSubject, customGroupSubjectDob, customGroupSubjectAge),
                        Collections.singletonList(ownerSubject)
                );

        assertEquals(4, queryResponse.getTotalRecords());
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSubjectSearchDemographicsColumns() {
        final SearchResponse queryResponse = querySubjects(
                mapWithColumns(
                        LABEL, PROJECT, GENDER, HANDEDNESS, EDUCATION, RACE, ETHNICITY, GROUP, YOB, DOB, AGE, HEIGHT, WEIGHT, SRC
                )
        );

        new DynamicSearchResultValidator<Subject>()
                .uniquenessKey(ID)
                .objectMappingFunction(
                        (subject) -> {
                            final Map<String, String> asMap = defaultMapping.apply(subject);
                            asMap.put(GENDER, subject.getGender().toString());
                            asMap.put(HANDEDNESS, subject.getHandedness().toString());
                            asMap.put(EDUCATION, zeroFallback(subject.getEducation()));
                            asMap.put(RACE, nullFallback(subject.getRace()));
                            asMap.put(ETHNICITY, nullFallback(subject.getEthnicity()));
                            asMap.put(GROUP, nullFallback(subject.getGroup()));
                            asMap.put(YOB, zeroFallback(subject.getYob()));
                            asMap.put(DOB, serializeLocalDate(subject.getDob()));
                            asMap.put(AGE, zeroFallback(subject.getAge()));
                            asMap.put(HEIGHT, coerceDouble(subject.getHeight()));
                            asMap.put(WEIGHT, coerceDouble(subject.getWeight()));
                            asMap.put(SRC, nullFallback(subject.getSrc()));
                            return asMap;
                        }
                )
                .validateDynamicSearchResponse(
                        queryResponse,
                        Arrays.asList(publicSubject, customGroupSubject, customGroupSubjectDob, customGroupSubjectAge),
                        Collections.singletonList(ownerSubject)
                );

        assertEquals(4, queryResponse.getTotalRecords());
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSubjectSearchGenericSessionJoin() {
        final SearchResponse queryResponse = querySubjects(
                collaboratorProject,
                mapWithColumns(
                        IMAGE_SESSION_ID, IMAGE_SESSION_LABEL, IMAGE_SESSION_MODALITY
                ),
                mainUser
        );

        new DynamicSearchResultValidator<Subject>()
                .uniquenessCompositeKeys(Arrays.asList(IMAGE_SESSION_ID.toLowerCase(), ID)) // session IDs are unique, but if multiple subjects dont have sessions, we need to disambiguate the empty session IDs
                .multiObjectMappingFunction(
                        subjectJoinedToExperimentMapping(
                                x -> true,
                                (subjectAssessor, sessionMap) -> {
                                    sessionMap.put(IMAGE_SESSION_ID.toLowerCase(), nullFallback(subjectAssessor.getAccessionNumber()));
                                    sessionMap.put(IMAGE_SESSION_LABEL.toLowerCase(), nullFallback(subjectAssessor.getLabel()));
                                    sessionMap.put(IMAGE_SESSION_MODALITY.toLowerCase(), nullFallback(subjectAssessor.getSpecificFields().get(MODALITY)));
                                }
                        )
                ).validateDynamicSearchResponse(
                        queryResponse,
                        Arrays.asList(collaboratorSubject, collaboratorSubjectNoExpts, publicSubject),
                        Collections.singletonList(ownerSubject)
                );

        assertEquals(5, queryResponse.getTotalRecords());
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSubjectSearchMrSessionJoinImplicitFilter() {
        final SearchResponse queryResponse = querySubjects(
                collaboratorProject,
                mapWithColumns(
                        MR_SESSION_LABEL, MR_SESSION_ID, MR_SESSION_FIELD_STRENGTH
                ),
                mainUser
        );

        new DynamicSearchResultValidator<Subject>()
                .uniquenessCompositeKeys(Arrays.asList(MR_SESSION_ID.toLowerCase(), ID)) // session IDs are unique, but if multiple subjects dont have sessions, we need to disambiguate the empty session IDs
                .multiObjectMappingFunction(
                        subjectJoinedToExperimentMapping(
                                x -> x instanceof MRSession,
                                (subjectAssessor, sessionMap) -> {
                                    sessionMap.put(MR_SESSION_LABEL.toLowerCase(), nullFallback(subjectAssessor.getLabel()));
                                    sessionMap.put(MR_SESSION_ID.toLowerCase(), nullFallback(subjectAssessor.getAccessionNumber()));
                                    sessionMap.put(MR_SESSION_FIELD_STRENGTH.toLowerCase(), nullFallback(subjectAssessor.getSpecificFields().get(FIELD_STRENGTH)));
                                }
                        )
                ).validateDynamicSearchResponse(
                        queryResponse,
                        Arrays.asList(collaboratorSubject, collaboratorSubjectNoExpts, publicSubject),
                        Collections.singletonList(ownerSubject)
                );

        assertEquals(3, queryResponse.getTotalRecords());
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSubjectSearchMrSessionJoinScans() {
        final List<String> joinColumns = Arrays.asList(MR_SESSION_LABEL, MR_SCAN_ID, MR_SCAN_SERIES_DESCRIPTION, MR_SCAN_QUALITY);
        final SearchResponse queryResponse = querySubjects(
                collaboratorProject,
                mapWithColumns(joinColumns.toArray(new String[]{})),
                mainUser
        );

        new DynamicSearchResultValidator<Subject>()
                .uniquenessCompositeKeys(Arrays.asList(MR_SCAN_ID.toLowerCase(), MR_SESSION_LABEL.toLowerCase(), ID))
                .multiObjectMappingFunction(
                        (subject) -> {
                            final Map<String, String> asMap = minimalMapping.apply(subject);
                            final List<ImagingSession> sessions = subject
                                    .getSessions()
                                    .stream()
                                    .filter(session -> session instanceof MRSession)
                                    .collect(Collectors.toList());
                            if (sessions.isEmpty()) {
                                for (String joinColumn : joinColumns) {
                                    asMap.put(joinColumn.toLowerCase(), "");
                                }
                                return Collections.singletonList(asMap);
                            }
                            final List<Map<String, String>> serialization = new ArrayList<>();
                            for (ImagingSession session : sessions) {
                                final Map<String, String> baseSessionMap = new HashMap<>(asMap);
                                for (String joinColumn : joinColumns) {
                                    baseSessionMap.put(joinColumn.toLowerCase(), "");
                                }
                                baseSessionMap.put(MR_SESSION_LABEL.toLowerCase(), session.getLabel());
                                if (session.getScans().isEmpty()) {
                                    serialization.add(baseSessionMap);
                                } else {
                                    for (Scan scan : session.getScans()) {
                                        final Map<String, String> scanMap = new HashMap<>(baseSessionMap);
                                        scanMap.put(MR_SCAN_ID.toLowerCase(), scan.getId());
                                        scanMap.put(MR_SCAN_SERIES_DESCRIPTION.toLowerCase(), nullFallback(scan.getSeriesDescription()));
                                        scanMap.put(MR_SCAN_QUALITY.toLowerCase(), nullFallback(scan.getQuality()));
                                        serialization.add(scanMap);
                                    }
                                }
                            }
                            return serialization;
                        }
                ).validateDynamicSearchResponse(
                        queryResponse,
                        Arrays.asList(collaboratorSubject, collaboratorSubjectNoExpts, publicSubject),
                        Collections.singletonList(ownerSubject)
                );

        assertEquals(3, queryResponse.getTotalRecords());
    }

    private SearchResponse querySubjects(Map<String, String> queryParams) {
        return querySubjects(customUserGroupProject, queryParams, mainUser);
    }

    private SearchResponse querySubjects(Project project, Map<String, String> queryParams, User authUser) {
        return queryDynamicSearchEndpoint(
                mainInterface().projectSubjectsUrl(project),
                queryParams,
                authUser
        );
    }

    private static String subjectUri(Subject subject) {
        return "/data/subjects/" + subject.getAccessionNumber();
    }

    private String zeroFallback(int value) {
        return (value == 0) ? "" : String.valueOf(value);
    }

    private String coerceDouble(int value) {
        return (value == 0) ? "" : value + ".0";
    }

    private Function<Subject, List<Map<String, String>>> subjectJoinedToExperimentMapping(Predicate<SubjectAssessor> experimentFilter, BiConsumer<SubjectAssessor, Map<String, String>> experimentMapper) {
        return (subject) -> {
            final Map<String, String> asMap = minimalMapping.apply(subject);
            final List<SubjectAssessor> subjectAssessors = subject
                    .getExperiments()
                    .stream()
                    .filter(experimentFilter)
                    .collect(Collectors.toList());
            if (subjectAssessors.isEmpty()) {
                subjectAssessors.add(new ImagingSession().label(""));
            }
            return subjectAssessors
                    .stream()
                    .map(
                            (session) -> {
                                final Map<String, String> sessionMap = new HashMap<>(asMap);
                                experimentMapper.accept(session, sessionMap);
                                return sessionMap;
                            }
                    ).collect(Collectors.toList());
        };
    }

}
