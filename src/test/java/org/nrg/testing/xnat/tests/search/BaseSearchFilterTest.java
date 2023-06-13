package org.nrg.testing.xnat.tests.search;

import org.apache.commons.lang3.StringUtils;
import org.nrg.testing.TestGroups;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.search.SearchColumn;
import org.nrg.xnat.pogo.search.SearchField;
import org.nrg.xnat.pogo.search.SearchFieldTypes;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.search.XnatSearchParams;
import org.testng.annotations.BeforeClass;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.nrg.xnat.pogo.DataType.MR_SCAN;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

public class BaseSearchFilterTest extends BaseSearchTest {

    private static boolean setupDone = false;
    protected static final String GROUP_DISPLAY_NAME = "Group";
    protected static final String GROUP_DISPLAY_FIELD_ID = "SUB_GROUP";
    protected static final String GROUP_SCHEMA_PATH = "xnat:subjectData/group";
    protected static final String MR_SCANNER_SCHEMA_PATH = "xnat:mrSessionData/scanner";
    protected static final String MR_WEIGHT_SCHEMA_PATH = "xnat:mrSessionData/dcmPatientWeight";
    protected static final String WEIGHT_DISPLAY_NAME = "DICOM Patient Weight";
    protected static final String T1_SCAN_COUNT_DISPLAY_NAME = "T1 Scan Count";
    protected static final String MR_T1_SCAN_COUNT_FIELD_ID = "SCAN_COUNT_TYPE=T1";
    protected static final String MR_DELAY_SCHEMA_PATH = "xnat:mrSessionData/delay";
    protected static final String DELAY_DISPLAY_NAME = "Experiment Delay Field";
    protected static final String MR_DATE_SCHEMA_PATH = "xnat:mrSessionData/date";
    protected static final String MR_DATE_DISPLAY_NAME = "Date";
    protected static final String MR_DATE_DISPLAY_FIELD_ID = "DATE";
    protected static final String MR_LABEL_FIELD_ID = "LABEL";
    protected static final String MR_LABEL_DISPLAY_NAME = "Label";
    protected static final String MR_SCAN_START_TIME_DISPLAY_NAME = "startTime";
    protected static final String MR_SCAN_START_TIME_SCHEMA_PATH = "xnat:mrScanData/startTime";
    protected static final String MR_SCAN_DISPLAY_FIELD_ID = "STARTTIME";
    protected static final String PET_TRACER_START_SCHEMA_PATH = "xnat:petSessionData/tracer/startTime";
    protected static final String PET_TRACER_START_DISPLAY_NAME = "startTime";
    protected static final String PET_TRACER_START_FIELD_ID = "TRACER_STARTTIME";
    protected static final String T1 = "T1";
    protected static final String T2 = "T2";

    protected static final Project testProject = registerSuiteTempProject();
    protected static final Subject subjectSymmetric = new Subject(testProject).group("Symmetric"); // I'm so funny
    protected static final Subject subjectSpecialLinear = new Subject(testProject).group("Special linear");
    protected static final Subject subjectFrobenius = new Subject(testProject).group("Frobenius");
    protected static final Subject subjectCyclic = new Subject(testProject).group("Cyclic");
    protected static final Subject subjectNull = new Subject(testProject).group("");
    protected static final LocalDate march20th2020 = LocalDate.parse("2020-03-20");
    protected static final LocalDate july7th2018 = LocalDate.parse("2018-07-07");
    protected static final LocalDate february2nd2021 = LocalDate.parse("2021-02-02");
    protected static final MRSession mrSession1305Pounds = mrWithFields(subjectNull, "130.5", "10000", march20th2020, "");
    protected static final MRSession mrSession150Pounds = mrWithFields(subjectNull, "150", "5000", july7th2018, "");
    protected static final MRSession mrSession200Pounds = mrWithFields(subjectFrobenius, "200.0", "1234", february2nd2021, "");
    protected static final MRSession mrSessionNoWeight = mrWithFields(subjectNull, "", "", null, "SCANNING MRI DEVICE");
    protected static final Scan t1At012345 = new MRScan(mrSession1305Pounds, "1").seriesDescription(T1).type(T1).startTime(LocalTime.parse("01:23:45"));
    protected static final Scan t1At090909 = new MRScan(mrSession1305Pounds, "2").seriesDescription(T1).type(T1).startTime(LocalTime.parse("09:09:09"));
    protected static final Scan t2At101010 = new MRScan(mrSession1305Pounds, "3").seriesDescription(T2).type(T2).startTime(LocalTime.parse("10:10:10"));
    protected static final Scan t1At012346 = new MRScan(mrSession150Pounds, "1").seriesDescription(T1).type(T1).startTime(LocalTime.parse("01:23:46"));
    protected static final Scan t1Timeless = new MRScan(mrSessionNoWeight, "1").seriesDescription(T1).type(T1);
    protected static final Scan t1At202020 = new MRScan(mrSessionNoWeight, "2").seriesDescription(T1).type(T1).startTime(LocalTime.parse("20:20:20"));
    protected static final Scan t1At080808 = new MRScan(mrSessionNoWeight, "3").seriesDescription(T1).type(T1).startTime(LocalTime.parse("08:08:08"));
    protected static final Scan t2At111111 = new MRScan(mrSessionNoWeight, "4").seriesDescription(T2).type(T2).startTime(LocalTime.parse("11:11:11"));
    protected static final String TIMESTAMP_20110101T030000 = "2011-01-01 03:00:00.0";
    protected static final String TIMESTAMP_20110101T030001 = "2011-01-01 03:00:01.0";
    protected static final String TIMESTAMP_20120202T130001 = "2012-02-02 13:00:01.0";
    protected static final String TIMESTAMP_20160102T200000 = "2016-01-02 20:00:00.0";
    protected static final PETSession pet20110101T030000 = petWithFields(TIMESTAMP_20110101T030000);
    protected static final PETSession pet20110101T030001 = petWithFields(TIMESTAMP_20110101T030001);
    protected static final PETSession pet20120202T130001 = petWithFields(TIMESTAMP_20120202T130001);
    protected static final PETSession pet20160102T200000 = petWithFields(TIMESTAMP_20160102T200000);
    protected static final PETSession petTimeless = petWithFields("");

    protected static final SearchField groupSearchField = new SearchField()
            .elementName(DataType.SUBJECT)
            .fieldId(GROUP_DISPLAY_FIELD_ID)
            .type(SearchFieldTypes.STRING)
            .header(GROUP_DISPLAY_NAME);

    protected static final SearchField weightSearchField = new SearchField()
            .elementName(DataType.MR_SESSION)
            .fieldId(MR_WEIGHT_SCHEMA_PATH)
            .type(SearchFieldTypes.FLOAT)
            .header(WEIGHT_DISPLAY_NAME);

    protected static final SearchField t1CountSearchField = new SearchField()
            .elementName(DataType.MR_SESSION)
            .fieldId(MR_T1_SCAN_COUNT_FIELD_ID)
            .type(SearchFieldTypes.INTEGER)
            .header(T1_SCAN_COUNT_DISPLAY_NAME);

    protected static final SearchField delaySearchField = new SearchField()
            .elementName(DataType.MR_SESSION)
            .fieldId(MR_DELAY_SCHEMA_PATH)
            .type(SearchFieldTypes.INTEGER)
            .header(DELAY_DISPLAY_NAME);

    protected static final SearchField mrDateSearchField = new SearchField()
            .elementName(DataType.MR_SESSION)
            .fieldId(MR_DATE_DISPLAY_FIELD_ID)
            .type(SearchFieldTypes.DATE)
            .header(MR_DATE_DISPLAY_NAME);

    protected static final SearchField mrLabelSearchField = new SearchField()
            .elementName(DataType.MR_SESSION)
            .fieldId(MR_LABEL_FIELD_ID)
            .type(SearchFieldTypes.STRING)
            .header(MR_LABEL_DISPLAY_NAME);

    protected static final SearchColumn groupSearchColumn = groupSearchField.generateExpectedSearchColumn(true, true);
    protected static final SearchColumn weightSearchColumn = weightSearchField.generateExpectedSearchColumn(false, true);
    protected static final SearchColumn t1SearchColumn = t1CountSearchField.generateExpectedSearchColumn(true, true);
    protected static final SearchColumn delaySearchColumn = delaySearchField.generateExpectedSearchColumn(false, true);

    protected static final BiFunction<Subject, SearchRow, Boolean> subjectMatchesLabel =
            (subject, row) -> subject.getLabel().equals(row.getSubjectLabelInProject(subject.getProject()));
    protected static final BiFunction<Subject, SearchRow, Boolean> subjectMatchesGroup =
            (subject, row) -> StringUtils.equals(subject.getGroup(), row.get(GROUP_DISPLAY_FIELD_ID.toLowerCase()));
    protected static final BiFunction<ImagingSession, SearchRow, Boolean> mrMatchesLabel =
            (session, row) -> session.getLabel().equals(row.getMrSessionLabelInProject(session.getPrimaryProject()));
    protected static final BiFunction<ImagingSession, SearchRow, Boolean> mrMatchesWeight =
            (session, row) -> StringUtils.equals(session.getSpecificFields().get(MR_WEIGHT_SCHEMA_PATH), row.get(weightSearchColumn.getKey()));
    protected static final BiFunction<ImagingSession, SearchRow, Boolean> mrMatchesT1Count =
            (session, row) -> {
                final long scanCount = session
                        .getScans()
                        .stream()
                        .filter(scan -> T1.equals(scan.getType()))
                        .count();
                return row.get(t1SearchColumn.getKey()).equals(scanCount == 0 ? "" : String.valueOf(scanCount));
            };
    protected static final BiFunction<ImagingSession, SearchRow, Boolean> mrMatchesDelay =
            (session, row) -> StringUtils.equals(session.getSpecificFields().get(MR_DELAY_SCHEMA_PATH), row.get(delaySearchColumn.getKey()));

    @BeforeClass(groups = TestGroups.SEARCH)
    protected void setup() {
        if (!setupDone) {
            mainInterface().createProject(testProject);
            mrSession150Pounds.getSpecificFields().put(MR_WEIGHT_SCHEMA_PATH, "150.0"); // we want to set it up as 150 in initial setup, but it should behave as a float when filtering/retrieving
            mainAdminInterface().setupDataType(MR_SCAN);
            setupDone = true;
        }
    }

    protected <X> BiFunction<X, SearchRow, Boolean> and(List<BiFunction<X, SearchRow, Boolean>> functions) {
        return (session, row) -> functions.stream()
                .allMatch(function -> function.apply(session, row));
    }

    protected static MRSession mrWithFields(Subject subject, String weight, String delay, LocalDate date, String scanner) {
        final Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put(MR_WEIGHT_SCHEMA_PATH, weight);
        fieldMap.put(MR_DELAY_SCHEMA_PATH, delay);
        fieldMap.put(MR_SCANNER_SCHEMA_PATH, scanner);
        return new MRSession(testProject, subject)
                .specificFields(fieldMap)
                .date(date);
    }

    protected static PETSession petWithFields(String startTime) {
        return new PETSession(testProject, subjectNull)
                .specificFields(Collections.singletonMap(PET_TRACER_START_SCHEMA_PATH, startTime));
    }

    protected class SearchValidator<X> {
        private final List<SearchColumn> searchColumnsToCheck;
        private final XnatSearchDocument searchDocument;
        private final BiFunction<X, SearchRow, Boolean> responseChecker;

        SearchValidator(SearchColumn searchColumnToCheck, XnatSearchDocument searchDocument, BiFunction<X, SearchRow, Boolean> responseChecker) {
            this(Collections.singletonList(searchColumnToCheck), searchDocument, responseChecker);
        }

        SearchValidator(BiFunction<X, SearchRow, Boolean> responseChecker) {
            this(new ArrayList<>(), null, responseChecker);
        }

        SearchValidator(List<SearchColumn> searchColumnsToCheck, XnatSearchDocument searchDocument, BiFunction<X, SearchRow, Boolean> responseChecker) {
            this.searchColumnsToCheck = new ArrayList<>(searchColumnsToCheck); // make sure it's mutable
            this.searchDocument = searchDocument;
            this.responseChecker = responseChecker;
        }

        @SuppressWarnings("UnusedReturnValue")
        SearchValidator<X> addSearchColumn(SearchColumn searchColumn) {
            searchColumnsToCheck.add(searchColumn);
            return this;
        }

        @SafeVarargs
        protected final void performAndValidateSearch(X... expectedObjectsInResponse) {
            performAndValidateSearch(new XnatSearchParams(), expectedObjectsInResponse);
        }

        @SafeVarargs
        protected final void performAndValidateSearch(XnatSearchParams searchParams, X... expectedObjectsInResponse) {
            validateSearchResults(
                    searchParams.getSortBy() != null,
                    true,
                    mainInterface().performSearch(searchDocument, searchParams),
                    expectedObjectsInResponse
            );
        }

        // when retrieving results for an already cached search, the data involved with performExtraChecks isn't in the response, so we can skip it for those cases
        @SafeVarargs
        protected final void validateSearchResults(boolean enforceOrder, boolean performExtraChecks, SearchResponse searchResponse, X... expectedObjectsInResponse) {
            final List<X> expectedItems = Arrays.asList(expectedObjectsInResponse);
            assertEquals("all items expected to validate should be unique", expectedItems.size(), new HashSet<>(expectedItems).size()); // double check test developer error
            if (performExtraChecks) {
                assertEquals(expectedItems.size(), searchResponse.getTotalRecords());
                for (SearchColumn searchColumnToCheck : searchColumnsToCheck) {
                    assertTrue(searchResponse.getColumns().contains(searchColumnToCheck));
                }
            }
            validateSearchResponseResult(enforceOrder, searchResponse.getResult(), expectedItems);
        }

        protected void validateSearchResponseResult(boolean enforceOrder, List<SearchRow> searchResponseResult, List<X> expectedItems) {
            assertEquals(expectedItems.size(), searchResponseResult.size());
            if (enforceOrder) {
                for (int i = 0; i < expectedItems.size(); i++) {
                    assertTrue(responseChecker.apply(expectedItems.get(i), searchResponseResult.get(i)));
                }
            } else {
                for (X expectedObject : expectedItems) {
                    assertTrue(
                            searchResponseResult
                                    .stream()
                                    .anyMatch(row -> responseChecker.apply(expectedObject, row))
                    );
                }
            }
        }
    }

}
