package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.TimeUtils;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.experiments.Experiment;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.nrg.testing.TestGroups.SEARCH;

public class TestDynamicSearchSubjectAssessors extends BaseDynamicSearchTest {

    private static final List<ImagingSession> readableSessions = Arrays.asList(publicMr, publicPet, collaboratorSubjectMr, collaboratorSubjectPet, customGroupMrSession, mrSession1305Pounds, mrSession150Pounds, mrSession200Pounds, mrSessionNoWeight, pet20110101T030000, pet20110101T030001, pet20120202T130001, pet20160102T200000, petTimeless);
    private static final String LABEL = "label";
    private static final String ID = "ID";
    private static final String SESSION_ID = "session_ID";
    private static final String URI = "URI";
    private static final String PROJECT = "project";
    private static final String DATE = "date";
    private static final String XSI_TYPE = "xsiType";
    private static final String MR_SESSION_ID = "xnat:mrSessionData/id";
    private static final String EXPERIMENT_DATE = "xnat:experimentData/date";
    private static final String IMAGE_SESSION_UID = "xnat:imageSessionData/UID";
    private static final List<String> ADDED_SESSION_COLUMNS = Arrays.asList(EXPERIMENT_DATE, IMAGE_SESSION_MODALITY, IMAGE_SESSION_UID);

    private static final Function<Experiment, Map<String, String>> mapId =
            mapField(ID, Experiment::getAccessionNumber);

    private static final Function<Experiment, Map<String, String>> mapProject =
            mapField(PROJECT, experiment -> experiment.getPrimaryProject().getId());

    private static final Function<Experiment, Map<String, String>> mapXsiType =
            mapField(XSI_TYPE, experiment -> experiment.getDataType().getXsiType());

    private static final Function<Experiment, Map<String, String>> mapDate =
            mapField(DATE, experiment -> serializeLocalDate(experiment.getDate()));

    private static final Function<Experiment, Map<String, String>> mapLabel =
            mapField(LABEL, Experiment::getLabel);

    private static final Function<ImagingSession, Map<String, String>> mapUid =
            mapSessionField(UID, session -> nullFallback(session.getSpecificFields().get(UID)));

    private static final Function<ImagingSession, Map<String, String>> mapModality =
            mapSessionField(MODALITY, session -> nullFallback(session.getSpecificFields().get(MODALITY)));

    private static final Function<ImagingSession, Map<String, String>> mapSessionId =
            mapSessionField(SESSION_ID, ImagingSession::getAccessionNumber);

    private static final Function<ImagingSession, Map<String, String>> mapMrSessionId =
            mapSessionField(MR_SESSION_ID.toLowerCase(), ImagingSession::getAccessionNumber);

    private static final Function<Experiment, Map<String, String>> minimalMapping =
            mapField(URI, TestDynamicSearchSubjectAssessors::experimentUri);

    private static final Function<Experiment, Map<String, String>> mappingWithMinimalColumns = compose(
            minimalMapping,
            mapId,
            mapProject,
            mapXsiType
    );

    private static final Function<Experiment, Map<String, String>> defaultMapping = compose(
            mappingWithMinimalColumns,
            mapDate,
            mapLabel
    );

    private static final Function<ImagingSession, Map<String, String>> sessionWithExtraColumnMapper = composeSessionFunctions(
            wrap(minimalMapping),
            wrap(mapDate),
            wrap(mapXsiType),
            wrap(mapProject),
            mapSessionId,
            mapUid,
            mapModality
    );

    private static final Function<ImagingSession, Map<String, String>> mrFilteredMapper = composeSessionFunctions(
            wrap(minimalMapping),
            mapMrSessionId
    );

    private static final DynamicSearchResultValidator<ImagingSession> sessionWithExtraColumnsValidator = new DynamicSearchResultValidator<ImagingSession>()
            .uniquenessKey(SESSION_ID) // once we add session-level columns, the name of the id column gets changed, interesting
            .objectMappingFunction(sessionWithExtraColumnMapper);

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSubjectAssessorSearchDefaultColumns() {
        final List<Experiment> readableExperiments = new ArrayList<>(readableSessions);
        readableExperiments.add(publicMrQc);
        new DynamicSearchResultValidator<Experiment>()
                .uniquenessKey(ID)
                .objectMappingFunction(defaultMapping)
                .keysToIgnore(Collections.singletonList("insert_date"))
                .validateDynamicSearchResponse(
                        queryExperiments(new HashMap<>()),
                        readableExperiments,
                        Arrays.asList(protectedMr, privateMr, customGroupPetSession, customGroupQc)
                );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSubjectAssessorSearchMinimalColumns() {
        new DynamicSearchResultValidator<Experiment>()
                .uniquenessKey(ID)
                .objectMappingFunction(mappingWithMinimalColumns)
                .validateDynamicSearchResponse(
                        queryExperiments(mapWithColumns(ID)),
                        readableSessions,
                        Arrays.asList(protectedMr, privateMr, customGroupPetSession)
                );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSessionSearchSessionColumns() {
        for (boolean fullSchemaPath : Arrays.asList(true, false)) {
            final String[] columnsRequested = ADDED_SESSION_COLUMNS
                    .stream()
                    .map(field -> (fullSchemaPath) ? field : stripPath(field))
                    .toArray(String[]::new);

            sessionWithExtraColumnsValidator.validateDynamicSearchResponse(
                    queryExperiments(mapWithColumns(columnsRequested)),
                    readableSessions,
                    Arrays.asList(protectedMr, privateMr, customGroupPetSession)
            );
        }
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSessionSearchFilters() {
        runSessionFilteringTest(
                Collections.singletonMap(UID, "1.2.*"),
                Collections.singletonList(collaboratorSubjectPet),
                Arrays.asList(publicMr, collaboratorSubjectMr, publicPet, privateMr)
        );

        for (String dateFilter : Arrays.asList(TimeUtils.AMERICAN_DATE.format(july7th2018), "07/01/2018-07/14/2018")) {
            runSessionFilteringTest(
                    Collections.singletonMap(DATE, dateFilter),
                    Collections.singletonList(mrSession150Pounds),
                    Arrays.asList(mrSession1305Pounds, mrSession200Pounds, publicMr, privateMr)
            );
        }

        final Map<String, String> queryParams = mapWithColumns(ADDED_SESSION_COLUMNS.toArray(new String[0]));
        queryParams.put(XSI_TYPE, DataType.MR_SESSION.getXsiType());

        new DynamicSearchResultValidator<ImagingSession>()
                .uniquenessKey(MR_SESSION_ID.toLowerCase()) // restricting xsiType changes ID keyname yet again
                .objectMappingFunction(
                        composeSessionFunctions(
                                mrFilteredMapper,
                                mapModality,
                                wrap(mapDate),
                                mapUid
                        )
                ).validateDynamicSearchResponse(
                        queryExperiments(queryParams),
                        Arrays.asList(publicMr, collaboratorSubjectMr, customGroupMrSession, mrSession1305Pounds, mrSession150Pounds, mrSession200Pounds, mrSessionNoWeight),
                        Arrays.asList(publicPet, collaboratorSubjectPet, pet20110101T030000, pet20110101T030001, pet20120202T130001, pet20160102T200000, petTimeless, protectedMr, privateMr, customGroupPetSession)
                );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicSessionSearchJoinAssessors() {
        final String sessionAssessorId = "xnat:imageAssessorData/id";
        final String sessionAssessorLabel = "xnat:imageAssessorData/label";
        final Map<String, String> queryParams = mapWithColumns(sessionAssessorId, sessionAssessorLabel);
        queryParams.put(XSI_TYPE, DataType.IMAGE_SESSION.getXsiType());

        new DynamicSearchResultValidator<ImagingSession>()
                .uniquenessCompositeKeys(Arrays.asList(SESSION_ID, sessionAssessorId.toLowerCase()))
                .multiObjectMappingFunction(
                        (session) -> {
                            final Map<String, String> sessionRepresentation = composeSessionFunctions(
                                    mapSessionId,
                                    wrap(minimalMapping),
                                    wrap(mapXsiType),
                                    wrap(mapProject)
                            ).apply(session);
                            final List<SessionAssessor> assessorsToSerialize = new ArrayList<>(session.getAssessors());
                            if (assessorsToSerialize.isEmpty()) {
                                assessorsToSerialize.add(new SessionAssessor().label(""));
                            }
                            return assessorsToSerialize
                                    .stream()
                                    .map(assessor -> {
                                        final Map<String, String> assessorMap = new HashMap<>(sessionRepresentation);
                                        assessorMap.put(sessionAssessorId.toLowerCase(), nullFallback(assessor.getAccessionNumber()));
                                        assessorMap.put(sessionAssessorLabel.toLowerCase(), nullFallback(assessor.getLabel()));
                                        return assessorMap;
                                    }).collect(Collectors.toList());
                        }
                ).validateDynamicSearchResponse(
                        queryExperiments(queryParams),
                        Arrays.asList(publicMr, publicPet, collaboratorSubjectMr, collaboratorSubjectPet),
                        Arrays.asList(customGroupPetSession, privateMr, protectedMr)
                );
    }

    private SearchResponse queryExperiments(Map<String, String> queryParams) {
        return queryExperiments(queryParams, mainUser);
    }

    private SearchResponse queryExperiments(Map<String, String> queryParams, User authUser) {
        return queryDynamicSearchEndpoint(
                formatRestUrl("experiments"),
                queryParams,
                authUser
        );
    }

    private String stripPath(String schemaRef) {
        return schemaRef.split("/")[1];
    }

    private void runSessionFilteringTest(Map<String, String> addedQueryParams, List<ImagingSession> sessionsToAssertIncluded, List<ImagingSession> sessionsToAssertExcluded) {
        final Map<String, String> queryParams = mapWithColumns(ADDED_SESSION_COLUMNS.toArray(new String[0]));
        queryParams.putAll(addedQueryParams);

        sessionWithExtraColumnsValidator.validateDynamicSearchResponse(
                queryExperiments(queryParams),
                sessionsToAssertIncluded,
                sessionsToAssertExcluded
        );
    }

    private static String experimentUri(Experiment experiment) {
        return "/data/experiments/" + experiment.getAccessionNumber();
    }

    private static Function<Experiment, Map<String, String>> mapField(String fieldName, Function<Experiment, String> fieldSerializer) {
        return (experiment) -> Collections.singletonMap(fieldName, fieldSerializer.apply(experiment));
    }

    private static Function<ImagingSession, Map<String, String>> mapSessionField(String fieldName, Function<ImagingSession, String> fieldSerializer) {
        return (session) -> Collections.singletonMap(fieldName, fieldSerializer.apply(session));
    }

    @SafeVarargs
    private static Function<Experiment, Map<String, String>> compose(Function<Experiment, Map<String, String>>... mappers) {
        return composeFunctions(mappers);
    }

    @SafeVarargs
    private static Function<ImagingSession, Map<String, String>> composeSessionFunctions(Function<ImagingSession, Map<String, String>>... mappers) {
        return composeFunctions(mappers);
    }

    private static Function<ImagingSession, Map<String, String>> wrap(Function<Experiment, Map<String, String>> mapper) {
        return mapper::apply;
    }

}
