package org.nrg.testing.xnat.tests.search;

import com.google.common.collect.Iterables;
import org.nrg.testing.CollectionUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.DisplayVersion;
import org.nrg.xnat.pogo.search.DisplayVersionField;
import org.nrg.xnat.pogo.search.SearchColumn;
import org.nrg.xnat.pogo.search.SearchFieldList;
import org.nrg.xnat.pogo.search.SearchFieldTypes;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.rest.SerializationUtils;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

@AddedIn(Xnat_1_8_0.class)
public class TestSearchJoins extends BaseSearchFilterTest {

    private static final BiFunction<Subject, SearchRow, Boolean> joinedMrMatchesLabel =
            joinedMrMatchesProperty("xnat_mrsessiondata_label", ImagingSession::getLabel);
    private static final BiFunction<Subject, SearchRow, Boolean> joinedMrMatchesScanner =
            joinedMrMatches(
                    (session, row) -> row.get("xnat_mrsessiondata_scanner").equals(session == null ? "" : session.getSpecificFields().get(MR_SCANNER_SCHEMA_PATH))
            );
    private static final BiFunction<Subject, SearchRow, Boolean> joinedMrScanMatchesId =
            joinedMrScanMatchesProperty("xnat_mrscandata_id", Scan::getId);
    private static final BiFunction<Subject, SearchRow, Boolean> joinedMrScanMatchesSeriesDescription =
            joinedMrScanMatchesProperty("xnat_mrscandata_series_description", Scan::getSeriesDescription);
    private static final BiFunction<Subject, SearchRow, Boolean> joinedMrScanMatchesType =
            joinedMrScanMatchesProperty("xnat_mrscandata_type", Scan::getType);

    @Test
    public void testSearchEngineSubjectListingJoinMrSessionMrScan() {
        final BiFunction<Subject, SearchRow, Boolean> subjectCheckerFunction = and(
                Arrays.asList(
                        subjectMatchesLabel,
                        subjectMatchesGroup,
                        joinedMrMatchesLabel,
                        joinedMrMatchesScanner,
                        joinedMrScanMatchesId,
                        joinedMrScanMatchesSeriesDescription,
                        joinedMrScanMatchesType
                )
        );
        final XnatSearchDocument subjectSearchDocument = readSearchFromFile()
                .addSearchField(DataType.SUBJECT, GROUP_DISPLAY_FIELD_ID, SearchFieldTypes.STRING, GROUP_DISPLAY_NAME);
        final SearchValidator<Subject> subjectSearchValidator = new SearchValidator<>(
                groupSearchColumn,
                subjectSearchDocument,
                subjectCheckerFunction
        );
        final List<SearchColumn> expectedMrSessionDetailedColumns = readSearchColumnsFromFile("mr_session_detailed_columns.json");
        final List<SearchColumn> expectedMrScanAllSubsetColumns = readSearchColumnsFromFile("mr_scan_all_columns_subset.json");
        for (SearchColumn expectedColumn : expectedMrSessionDetailedColumns) {
            subjectSearchValidator.addSearchColumn(expectedColumn);
        }
        for (SearchColumn expectedColumn : expectedMrScanAllSubsetColumns) {
            subjectSearchValidator.addSearchColumn(expectedColumn);
        }

        final SearchFieldList mrSessionSearchFieldList = mainInterface().readSearchFieldList(DataType.MR_SESSION);
        final DisplayVersion detailedMrSessionListing = mrSessionSearchFieldList.detailedListing();
        final List<DisplayVersionField> visibleMrSessionFields = detailedMrSessionListing.visibleFields();

        assertEquals(
                expectedMrSessionDetailedColumns.stream().map(SearchColumn::getId).collect(Collectors.toSet()),
                detailedMrSessionListing.visibleFields().stream().map(DisplayVersionField::getId).collect(Collectors.toSet())
        );

        for (DisplayVersionField displayVersionField : visibleMrSessionFields) {
            subjectSearchDocument.addSearchField(displayVersionField.toSearchField());
        }

        final DisplayVersion allMrScanListing = mainInterface().readSearchFieldList(DataType.MR_SCAN).allListing();
        for (SearchColumn scanSearchColumn : expectedMrScanAllSubsetColumns) {
            final DisplayVersionField displayVersionField = allMrScanListing.getFields()
                    .stream()
                    .filter(field -> field.getId().equals(scanSearchColumn.getId()))
                    .findAny()
                    .orElseThrow(RuntimeException::new);
            subjectSearchDocument.addSearchField(displayVersionField.toSearchField());
        }

        subjectSearchValidator.performAndValidateSearch(subjectSymmetric, subjectFrobenius, subjectCyclic, subjectSpecialLinear, subjectNull);
    }

    private XnatSearchDocument readSearchFromFile() {
        return readXmlFromFile("default_project_subject_search.xml", new TemplateReplacements().project(testProject));
    }

    private List<SearchColumn> readSearchColumnsFromFile(String fileName) {
        try {
            return SerializationUtils.deserializeList(
                    XnatInterface.XNAT_REST_MAPPER.readValue(
                            searchFile(fileName),
                            List.class
                    ),
                    SearchColumn.class
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static MRSession findJoinedMrSession(Subject subject) {
        return CollectionUtils.ofType(subject.getSessions(), MRSession.class)
                .stream()
                .max(Comparator.comparing(ImagingSession::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static MRScan findJoinedMrScan(Subject subject) {
        final MRSession joinedMr = findJoinedMrSession(subject);
        if (joinedMr == null) {
            return null;
        }
        return Iterables.getLast(CollectionUtils.ofType(joinedMr.getScans(), MRScan.class));
    }

    private static BiFunction<Subject, SearchRow, Boolean> joinedMrMatches(BiFunction<ImagingSession, SearchRow, Boolean> mrMatcher) {
        return (subject, row) -> mrMatcher.apply(findJoinedMrSession(subject), row);
    }

    private static BiFunction<Subject, SearchRow, Boolean> joinedMrScanMatches(BiFunction<Scan, SearchRow, Boolean> scanMatcher) {
        return (subject, row) -> scanMatcher.apply(findJoinedMrScan(subject), row);
    }

    private static BiFunction<Subject, SearchRow, Boolean> joinedMrMatchesProperty(String property, Function<ImagingSession, String> getter) {
        return joinedMrMatches(
                (session, row) -> row.get(property).equals(session == null ? "" : getter.apply(session))
        );
    }

    private static BiFunction<Subject, SearchRow, Boolean> joinedMrScanMatchesProperty(String property, Function<Scan, String> getter) {
        return joinedMrScanMatches(
                (scan, row) -> row.get(property).equals(scan == null ? "" : getter.apply(scan))
        );
    }

}
