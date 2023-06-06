package org.nrg.testing.xnat.tests.search;

import org.apache.commons.lang3.StringUtils;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.SearchColumn;
import org.nrg.xnat.pogo.search.SearchFieldTypes;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.search.XnatSearchParams;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static org.nrg.testing.TimeUtils.AMERICAN_DATE;
import static org.nrg.testing.TimeUtils.UNAMBIGUOUS_DATE;
import static org.nrg.xnat.pogo.search.SortOrder.ASC;
import static org.nrg.xnat.pogo.search.SortOrder.DESC;

@AddedIn(Xnat_1_8_0.class)
public class TestSearchFilterDates extends BaseSearchFilterTest {

    private static final List<DateTimeFormatter> supportedFormatters = Arrays.asList(UNAMBIGUOUS_DATE, AMERICAN_DATE);

    private final BiFunction<MRSession, SearchRow, Boolean> mrCheckerFunction = (session, row) -> mrMatchesLabel.apply(session, row)
            && StringUtils.equals((session.getDate() == null) ? "" : UNAMBIGUOUS_DATE.format(session.getDate()), row.get(MR_DATE_DISPLAY_FIELD_ID.toLowerCase()));
    private final SearchColumn mrDateSearchColumn = buildExpectedSearchColumn(MR_DATE_DISPLAY_FIELD_ID.toLowerCase(), SearchFieldTypes.DATE, DataType.MR_SESSION, MR_DATE_DISPLAY_NAME);

    private final XnatSearchDocument mrDateDisplayFieldSearchDocument = readSearchFromFile();
    private final SearchValidator<MRSession> mrDateDisplayFieldSearchValidator = new SearchValidator<>(
            mrDateSearchColumn,
            mrDateDisplayFieldSearchDocument,
            mrCheckerFunction
    );
    private final XdatCriteria mrDateDisplayFieldSearchCriteria = new XdatCriteria().schemaField(DataType.MR_SESSION.getXsiType() + "." + MR_DATE_DISPLAY_FIELD_ID);
    
    private final XnatSearchDocument mrDateSchemaFieldSearchDocument = readSearchFromFile();
    private final SearchValidator<MRSession> mrDateSchemaFieldSearchValidator = new SearchValidator<>(
            mrDateSearchColumn,
            mrDateSchemaFieldSearchDocument,
            mrCheckerFunction
    );
    private final XdatCriteria mrDateSchemaFieldSearchCriteria = new XdatCriteria().schemaField(MR_DATE_SCHEMA_PATH);

    // these are just aliases to make the asserts clearer
    private final MRSession mr20200320 = mrSession1305Pounds;
    private final MRSession mr20180707 = mrSession150Pounds;
    private final MRSession mr20210202 = mrSession200Pounds;
    private final MRSession mrNoDate = mrSessionNoWeight;

    @BeforeClass(groups = TestGroups.SEARCH)
    private void initCriteria() {
        mrDateDisplayFieldSearchDocument.addSearchCriterion(mrDateDisplayFieldSearchCriteria);
        mrDateSchemaFieldSearchDocument.addSearchCriterion(mrDateSchemaFieldSearchCriteria);
    }

    @BeforeMethod(groups = TestGroups.SEARCH)
    private void resetCriteria() {
        mrDateDisplayFieldSearchCriteria.value(null);
        mrDateSchemaFieldSearchCriteria.value(null);
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldEquals() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateDisplayFieldSearchCriteria
                    .comparisonType(ComparisonType.EQUALS)
                    .value(dateFormatter.format(march20th2020));
            mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20200320);
        }
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldNotEquals() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateDisplayFieldSearchCriteria
                    .comparisonType(ComparisonType.NOT_EQUALS)
                    .value(dateFormatter.format(march20th2020));
            mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20180707, mr20210202, mrNoDate);
        }
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldGreaterThan() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateDisplayFieldSearchCriteria
                    .comparisonType(ComparisonType.GREATER_THAN)
                    .value(dateFormatter.format(march20th2020));
            mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20210202);
        }
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldGreaterThanOrEquals() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateDisplayFieldSearchCriteria
                    .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS)
                    .value(dateFormatter.format(march20th2020));
            mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20200320, mr20210202);
        }
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldLessThan() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateDisplayFieldSearchCriteria
                    .comparisonType(ComparisonType.LESS_THAN)
                    .value(dateFormatter.format(march20th2020));
            mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20180707);
        }
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldLessThanOrEquals() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateDisplayFieldSearchCriteria
                    .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS)
                    .value(dateFormatter.format(march20th2020));
            mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20180707, mr20200320);
        }
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldBetween() {
        final LocalDate july8th2018 = july7th2018.plusDays(1);
        final LocalDate february1st2021 = february2nd2021.minusDays(1);
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateDisplayFieldSearchCriteria
                    .comparisonType(ComparisonType.BETWEEN)
                    .value(betweenTwoDates(dateFormatter, july7th2018, february2nd2021));
            mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20180707, mr20200320, mr20210202);

            mrDateDisplayFieldSearchCriteria.value(betweenTwoDates(dateFormatter, july8th2018, february1st2021));
            mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20200320);
        }
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldIsNull() {
        mrDateDisplayFieldSearchCriteria.comparisonType(ComparisonType.IS_NULL);
        mrDateDisplayFieldSearchValidator.performAndValidateSearch(mrNoDate);
    }

    @Test
    public void testSearchEngineFilterDateDisplayFieldIsNotNull() {
        mrDateDisplayFieldSearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        mrDateDisplayFieldSearchValidator.performAndValidateSearch(mr20180707, mr20200320, mr20210202);
    }
    
    @Test
    public void testSearchEngineFilterDateSchemaFieldEquals() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateSchemaFieldSearchCriteria
                    .comparisonType(ComparisonType.EQUALS)
                    .value(dateFormatter.format(march20th2020));
            mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20200320);
        }
    }

    @Test
    public void testSearchEngineFilterDateSchemaFieldNotEquals() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateSchemaFieldSearchCriteria
                    .comparisonType(ComparisonType.NOT_EQUALS)
                    .value(dateFormatter.format(march20th2020));
            mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20180707, mr20210202);
        }
    }

    @Test
    public void testSearchEngineFilterDateSchemaFieldGreaterThan() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateSchemaFieldSearchCriteria
                    .comparisonType(ComparisonType.GREATER_THAN)
                    .value(dateFormatter.format(march20th2020));
            mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20210202);
        }
    }

    @Test
    public void testSearchEngineFilterDateSchemaFieldGreaterThanOrEquals() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateSchemaFieldSearchCriteria
                    .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS)
                    .value(dateFormatter.format(march20th2020));
            mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20200320, mr20210202);
        }
    }

    @Test
    public void testSearchEngineFilterDateSchemaFieldLessThan() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateSchemaFieldSearchCriteria
                    .comparisonType(ComparisonType.LESS_THAN)
                    .value(dateFormatter.format(march20th2020));
            mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20180707);
        }
    }

    @Test
    public void testSearchEngineFilterDateSchemaFieldLessThanOrEquals() {
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateSchemaFieldSearchCriteria
                    .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS)
                    .value(dateFormatter.format(march20th2020));
            mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20180707, mr20200320);
        }
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7780")
    public void testSearchEngineFilterDateSchemaFieldBetween() {
        final LocalDate july8th2018 = july7th2018.plusDays(1);
        final LocalDate february1st2021 = february2nd2021.minusDays(1);
        for (DateTimeFormatter dateFormatter : supportedFormatters) {
            mrDateSchemaFieldSearchCriteria
                    .comparisonType(ComparisonType.BETWEEN)
                    .value(betweenTwoDates(dateFormatter, july7th2018, february2nd2021));
            mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20180707, mr20200320, mr20210202);

            mrDateSchemaFieldSearchCriteria.value(betweenTwoDates(dateFormatter, july8th2018, february1st2021));
            mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20200320);
        }
    }

    @Test
    public void testSearchEngineFilterDateSchemaFieldIsNull() {
        mrDateSchemaFieldSearchCriteria.comparisonType(ComparisonType.IS_NULL);
        mrDateSchemaFieldSearchValidator.performAndValidateSearch(mrNoDate);
    }

    @Test
    public void testSearchEngineFilterDateSchemaFieldIsNotNull() {
        mrDateSchemaFieldSearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        mrDateSchemaFieldSearchValidator.performAndValidateSearch(mr20180707, mr20200320, mr20210202);
    }

    @Test
    public void testSearchEngineSortingDateDisplayField() {
        final XnatSearchDocument sortingSearchDocument = readSearchFromFile();
        final SearchValidator<MRSession> sortingSearchValidator = new SearchValidator<>(
                mrDateSearchColumn,
                sortingSearchDocument,
                mrCheckerFunction
        );

        final XnatSearchParams searchParams = new XnatSearchParams().sortBy(MR_DATE_DISPLAY_FIELD_ID).sortOrder(DESC);
        sortingSearchValidator.performAndValidateSearch(searchParams, mrNoDate, mr20210202, mr20200320, mr20180707);

        searchParams.sortOrder(ASC);
        sortingSearchValidator.performAndValidateSearch(searchParams, mr20180707, mr20200320, mr20210202, mrNoDate);
    }

    private String betweenTwoDates(DateTimeFormatter formatter, LocalDate leftDate, LocalDate rightDate) {
        return formatter.format(leftDate) + " AND " + formatter.format(rightDate);
    }

    private XnatSearchDocument readSearchFromFile() {
        return readXmlFromFile("default_project_mr_session_search.xml", new TemplateReplacements().project(testProject));
    }

}
