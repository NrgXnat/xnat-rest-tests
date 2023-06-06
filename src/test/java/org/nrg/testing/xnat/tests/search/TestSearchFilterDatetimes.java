package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.SearchColumn;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.search.XnatSearchParams;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.BiFunction;

import static org.nrg.xnat.pogo.search.SortOrder.ASC;
import static org.nrg.xnat.pogo.search.SortOrder.DESC;

@AddedIn(Xnat_1_8_0.class)
public class TestSearchFilterDatetimes extends BaseSearchFilterTest {

    private final BiFunction<PETSession, SearchRow, Boolean> petCheckerFunction = (session, row) -> session.getLabel().equals(row.get("xnat_petsessiondata_project_identifier_" + testProject.getId().toLowerCase()))
            && session.getSpecificFields().get(PET_TRACER_START_SCHEMA_PATH).equals(row.get(PET_TRACER_START_FIELD_ID.toLowerCase()));
    private final SearchColumn petStartSearchColumn = buildExpectedSearchColumn(PET_TRACER_START_FIELD_ID.toLowerCase(), "dateTime", DataType.PET_SESSION, PET_TRACER_START_DISPLAY_NAME);

    private SearchValidator<PETSession> petTracerStartDisplayFieldSearchValidator;
    private final XdatCriteria petTracerStartDisplayFieldSearchCriteria = new XdatCriteria().schemaField(DataType.PET_SESSION.getXsiType() + "." + PET_TRACER_START_FIELD_ID);

    private SearchValidator<PETSession> petTracerStartSchemaFieldSearchValidator;
    private final XdatCriteria petTracerStartSchemaFieldSearchCriteria = new XdatCriteria().schemaField(PET_TRACER_START_SCHEMA_PATH);

    @BeforeClass(groups = TestGroups.SEARCH)
    private void initCriteria() {
        final XnatSearchDocument petTracerStartDisplayFieldSearchDocument = readSearchFromRest();
        petTracerStartDisplayFieldSearchValidator = new SearchValidator<>(
                petStartSearchColumn,
                petTracerStartDisplayFieldSearchDocument,
                petCheckerFunction
        );
        final XnatSearchDocument petTracerStartSchemaFieldSearchDocument = readSearchFromRest();
        petTracerStartSchemaFieldSearchValidator = new SearchValidator<>(
                petStartSearchColumn,
                petTracerStartSchemaFieldSearchDocument,
                petCheckerFunction
        );

        petTracerStartDisplayFieldSearchDocument.addSearchCriterion(petTracerStartDisplayFieldSearchCriteria);
        petTracerStartSchemaFieldSearchDocument.addSearchCriterion(petTracerStartSchemaFieldSearchCriteria);
    }

    @BeforeMethod(groups = TestGroups.SEARCH)
    private void resetCriteria() {
        petTracerStartDisplayFieldSearchCriteria.value(null);
        petTracerStartSchemaFieldSearchCriteria.value(null);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7786")
    public void testSearchEngineFilterDateTimeDisplayFieldEquals() {
        petTracerStartDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value(TIMESTAMP_20110101T030000);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20110101T030000);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7786")
    public void testSearchEngineFilterDateTimeDisplayFieldNotEquals() {
        petTracerStartDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value(TIMESTAMP_20110101T030000);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20110101T030001, pet20120202T130001, pet20160102T200000, petTimeless);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7786")
    public void testSearchEngineFilterDateTimeDisplayFieldGreaterThan() {
        petTracerStartDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN)
                .value(TIMESTAMP_20110101T030001);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20120202T130001, pet20160102T200000);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7786")
    public void testSearchEngineFilterDateTimeDisplayFieldGreaterThanOrEquals() {
        petTracerStartDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS)
                .value(TIMESTAMP_20110101T030001);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20110101T030001, pet20120202T130001, pet20160102T200000);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7786")
    public void testSearchEngineFilterDateTimeDisplayFieldLessThan() {
        petTracerStartDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.LESS_THAN)
                .value(TIMESTAMP_20110101T030001);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20110101T030000);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7786")
    public void testSearchEngineFilterDateTimeDisplayFieldLessThanOrEquals() {
        petTracerStartDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS)
                .value(TIMESTAMP_20110101T030001);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20110101T030000, pet20110101T030001);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7777")
    public void testSearchEngineFilterDateTimeDisplayFieldIn() {
        petTracerStartDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.IN)
                .value(TIMESTAMP_20110101T030000 + ", " + TIMESTAMP_20110101T030001);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20110101T030000, pet20110101T030001);
    }

    @Test
    public void testSearchEngineFilterDateTimeDisplayFieldBetween() {
        petTracerStartDisplayFieldSearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value(TIMESTAMP_20110101T030001 + " AND " + TIMESTAMP_20120202T130001);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20110101T030001, pet20120202T130001);
    }

    @Test
    public void testSearchEngineFilterDateTimeDisplayFieldIsNull() {
        petTracerStartDisplayFieldSearchCriteria.comparisonType(ComparisonType.IS_NULL);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(petTimeless);
    }

    @Test
    public void testSearchEngineFilterDateTimeDisplayFieldIsNotNull() {
        petTracerStartDisplayFieldSearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        petTracerStartDisplayFieldSearchValidator.performAndValidateSearch(pet20110101T030000, pet20110101T030001, pet20120202T130001, pet20160102T200000);
    }

    @Test
    public void testSearchEngineFilterDateTimeSchemaFieldEquals() {
        petTracerStartSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value(TIMESTAMP_20110101T030000);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20110101T030000);
    }

    @Test
    public void testSearchEngineFilterDateTimeSchemaFieldNotEquals() {
        petTracerStartSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value(TIMESTAMP_20110101T030000);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20110101T030001, pet20120202T130001, pet20160102T200000);
    }

    @Test
    public void testSearchEngineFilterDateTimeSchemaFieldGreaterThan() {
        petTracerStartSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN)
                .value(TIMESTAMP_20110101T030001);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20120202T130001, pet20160102T200000);
    }

    @Test
    public void testSearchEngineFilterDateTimeSchemaFieldGreaterThanOrEquals() {
        petTracerStartSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS)
                .value(TIMESTAMP_20110101T030001);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20110101T030001, pet20120202T130001, pet20160102T200000);
    }

    @Test
    public void testSearchEngineFilterDateTimeSchemaFieldLessThan() {
        petTracerStartSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.LESS_THAN)
                .value(TIMESTAMP_20110101T030001);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20110101T030000);
    }

    @Test
    public void testSearchEngineFilterDateTimeSchemaFieldLessThanOrEquals() {
        petTracerStartSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS)
                .value(TIMESTAMP_20110101T030001);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20110101T030000, pet20110101T030001);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7777")
    public void testSearchEngineFilterDateTimeSchemaFieldIn() {
        petTracerStartSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.IN)
                .value(TIMESTAMP_20110101T030000 + ", " + TIMESTAMP_20110101T030001);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20110101T030000, pet20110101T030001);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7780")
    public void testSearchEngineFilterDateTimeSchemaFieldBetween() {
        petTracerStartSchemaFieldSearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value(TIMESTAMP_20110101T030001 + " AND " + TIMESTAMP_20120202T130001);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20110101T030001, pet20120202T130001);
    }

    @Test
    public void testSearchEngineFilterDateTimeSchemaFieldIsNull() {
        petTracerStartSchemaFieldSearchCriteria.comparisonType(ComparisonType.IS_NULL);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(petTimeless);
    }

    @Test
    public void testSearchEngineFilterDateTimeSchemaFieldIsNotNull() {
        petTracerStartSchemaFieldSearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        petTracerStartSchemaFieldSearchValidator.performAndValidateSearch(pet20110101T030000, pet20110101T030001, pet20120202T130001, pet20160102T200000);
    }

    @Test
    public void testSearchEngineSortingDateTimeDisplayField() {
        final XnatSearchDocument petTracerStartDisplayFieldSearchDocument = readSearchFromRest();
        final SearchValidator<PETSession> sortingSearchValidator = new SearchValidator<>(
                petStartSearchColumn,
                petTracerStartDisplayFieldSearchDocument,
                petCheckerFunction
        );

        final XnatSearchParams searchParams = new XnatSearchParams().sortBy(PET_TRACER_START_FIELD_ID).sortOrder(DESC);
        sortingSearchValidator.performAndValidateSearch(searchParams, petTimeless, pet20160102T200000, pet20120202T130001, pet20110101T030001, pet20110101T030000);

        searchParams.setSortOrder(ASC);
        sortingSearchValidator.performAndValidateSearch(searchParams, pet20110101T030000, pet20110101T030001, pet20120202T130001, pet20160102T200000, petTimeless);
    }

    private XnatSearchDocument readSearchFromRest() {
        return mainInterface().getDefaultSearch(testProject, DataType.PET_SESSION);
    }

}
