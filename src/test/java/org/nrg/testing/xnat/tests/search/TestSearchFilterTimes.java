package org.nrg.testing.xnat.tests.search;

import org.apache.commons.lang3.StringUtils;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.SearchColumn;
import org.nrg.xnat.pogo.search.SearchFieldTypes;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.search.XnatSearchParams;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiFunction;

import static org.nrg.xnat.pogo.search.XnatSearchParams.SortOrder.ASC;
import static org.nrg.xnat.pogo.search.XnatSearchParams.SortOrder.DESC;

public class TestSearchFilterTimes extends BaseSearchFilterTest {

    private final BiFunction<Scan, SearchRow, Boolean> scanCheckerFunction = (scan, row) -> scan.getSession().getLabel().equals(row.get("session_label"))
            && scan.getId().equals(row.get("id"))
            && StringUtils.equals(serialize(scan.getStartTime()), row.get(MR_SCAN_DISPLAY_FIELD_ID.toLowerCase()));
    private final SearchColumn mrStartTimeSearchColumn = buildExpectedSearchColumn(MR_SCAN_DISPLAY_FIELD_ID.toLowerCase(), SearchFieldTypes.TIME, DataType.MR_SCAN, MR_SCAN_START_TIME_DISPLAY_NAME);

    private final XnatSearchDocument mrStartTimeDisplayFieldSearchDocument = readSearchFromFile();
    private final SearchValidator<Scan> mrStartTimeDisplayFieldSearchValidator = new SearchValidator<>(
            mrStartTimeSearchColumn,
            mrStartTimeDisplayFieldSearchDocument,
            scanCheckerFunction
    );
    private final XdatCriteria mrStartTimeDisplayFieldSearchCriteria = new XdatCriteria().schemaField(DataType.MR_SCAN.getXsiType() + "." + MR_SCAN_DISPLAY_FIELD_ID);
    
    private final XnatSearchDocument mrStartTimeSchemaFieldSearchDocument = readSearchFromFile();
    private final SearchValidator<Scan> mrStartTimeSchemaFieldSearchValidator = new SearchValidator<>(
            mrStartTimeSearchColumn,
            mrStartTimeSchemaFieldSearchDocument,
            scanCheckerFunction
    );
    private final XdatCriteria mrStartTimeSchemaFieldSearchCriteria = new XdatCriteria().schemaField(MR_SCAN_START_TIME_SCHEMA_PATH);

    @BeforeClass(groups = TestGroups.SEARCH)
    private void initCriteria() {
        mrStartTimeDisplayFieldSearchDocument.addSearchCriterion(mrStartTimeDisplayFieldSearchCriteria);
        mrStartTimeSchemaFieldSearchDocument.addSearchCriterion(mrStartTimeSchemaFieldSearchCriteria);
    }

    @BeforeMethod(groups = TestGroups.SEARCH)
    private void resetCriteria() {
        mrStartTimeDisplayFieldSearchCriteria.value(null);
        mrStartTimeSchemaFieldSearchCriteria.value(null);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldEquals() {
        mrStartTimeDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value("10:10:10");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t2At101010);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldNotEquals() {
        mrStartTimeDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value("10:10:10");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At012345, t1At090909, t1At012346, t2At111111, t1At202020, t1At080808, t1Timeless);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldGreaterThan() {
        mrStartTimeDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN)
                .value("08:08:08");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At090909, t2At101010, t2At111111, t1At202020);

        mrStartTimeDisplayFieldSearchCriteria.value("08:08:07.999");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At090909, t2At101010, t2At111111, t1At202020, t1At080808);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldGreaterThanOrEquals() {
        mrStartTimeDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS)
                .value("08:08:08");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At090909, t2At101010, t2At111111, t1At202020, t1At080808);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldLessThan() {
        mrStartTimeDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.LESS_THAN)
                .value("09:09:09");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At012345, t1At012346, t1At080808);

        mrStartTimeDisplayFieldSearchCriteria.value("09:09:09.5");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At012345, t1At012346, t1At080808, t1At090909);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldLessThanOrEquals() {
        mrStartTimeDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS)
                .value("09:09:09");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At012345, t1At012346, t1At080808, t1At090909);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7777")
    public void testSearchEngineFilterTimeDisplayFieldIn() {
        mrStartTimeDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.IN)
                .value("09:09:09, 10:10:10");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At090909, t2At101010);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldBetween() {
        mrStartTimeDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value("08:08:08 AND 10:10:10");
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At080808, t1At090909, t2At101010);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldIsNull() {
        mrStartTimeDisplayFieldSearchCriteria.comparisonType(ComparisonType.IS_NULL);
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1Timeless);
    }

    @Test
    public void testSearchEngineFilterTimeDisplayFieldIsNotNull() {
        mrStartTimeDisplayFieldSearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        mrStartTimeDisplayFieldSearchValidator.performAndValidateSearch(t1At012345 , t1At090909, t2At101010, t1At012346, t2At111111, t1At202020, t1At080808);
    }

    @Test
    public void testSearchEngineFilterTimeSchemaFieldEquals() {
        mrStartTimeSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value("10:10:10");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t2At101010);
    }

    @Test
    public void testSearchEngineFilterTimeSchemaFieldNotEquals() {
        mrStartTimeSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value("10:10:10");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At012345, t1At090909, t1At012346, t2At111111, t1At202020, t1At080808);
    }

    @Test
    public void testSearchEngineFilterTimeSchemaFieldGreaterThan() {
        mrStartTimeSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN)
                .value("08:08:08");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At090909, t2At101010, t2At111111, t1At202020);

        mrStartTimeSchemaFieldSearchCriteria.value("08:08:07.999");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At090909, t2At101010, t2At111111, t1At202020, t1At080808);
    }

    @Test
    public void testSearchEngineFilterTimeSchemaFieldGreaterThanOrEquals() {
        mrStartTimeSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS)
                .value("08:08:08");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At090909, t2At101010, t2At111111, t1At202020, t1At080808);
    }

    @Test
    public void testSearchEngineFilterTimeSchemaFieldLessThan() {
        mrStartTimeSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.LESS_THAN)
                .value("09:09:09");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At012345, t1At012346, t1At080808);

        mrStartTimeSchemaFieldSearchCriteria.value("09:09:09.5");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At012345, t1At012346, t1At080808, t1At090909);
    }

    @Test
    public void testSearchEngineFilterTimeSchemaFieldLessThanOrEquals() {
        mrStartTimeSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS)
                .value("09:09:09");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At012345, t1At012346, t1At080808, t1At090909);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7777")
    public void testSearchEngineFilterTimeSchemaFieldIn() {
        mrStartTimeSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.IN)
                .value("09:09:09, 10:10:10");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At090909, t2At101010);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7780")
    public void testSearchEngineFilterTimeSchemaFieldBetween() {
        mrStartTimeSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value("08:08:08 AND 10:10:10");
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At080808, t1At090909, t2At101010);
    }

    @Test
    public void testSearchEngineFilterTimeSchemaFieldIsNull() {
        mrStartTimeSchemaFieldSearchCriteria.comparisonType(ComparisonType.IS_NULL);
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1Timeless);
    }

    @Test
    public void testSearchEngineFilterTimeSchemaFieldIsNotNull() {
        mrStartTimeSchemaFieldSearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        mrStartTimeSchemaFieldSearchValidator.performAndValidateSearch(t1At012345, t1At090909, t2At101010, t1At012346, t2At111111, t1At202020, t1At080808);
    }

    @Test
    public void testSearchEngineSortingTimeDisplayField() {
        final XnatSearchDocument sortingSearchDocument = readSearchFromFile();
        final SearchValidator<Scan> sortingSearchValidator = new SearchValidator<>(
                mrStartTimeSearchColumn,
                sortingSearchDocument,
                scanCheckerFunction
        );

        final XnatSearchParams searchParams = new XnatSearchParams().sortBy(MR_SCAN_DISPLAY_FIELD_ID).sortOrder(DESC);
        sortingSearchValidator.performAndValidateSearch(searchParams, t1Timeless, t1At202020, t2At111111, t2At101010, t1At090909, t1At080808, t1At012346, t1At012345);

        searchParams.setSortOrder(ASC);
        sortingSearchValidator.performAndValidateSearch(searchParams, t1At012345, t1At012346, t1At080808, t1At090909, t2At101010, t2At111111, t1At202020, t1Timeless);
    }

    private String serialize(LocalTime localTime) {
        return (localTime == null) ? "" : DateTimeFormatter.ISO_TIME.format(localTime);
    }

    private XnatSearchDocument readSearchFromFile() {
        return readXmlFromFile("default_project_mr_scan_search.xml", new TemplateReplacements().project(testProject));
    }

}
