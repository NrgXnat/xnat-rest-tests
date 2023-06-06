package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.SearchFieldTypes;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.search.XnatSearchParams;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.nrg.xnat.pogo.search.SortOrder.ASC;
import static org.nrg.xnat.pogo.search.SortOrder.DESC;

/**
 * "integer" fields are surprisingly rare. In particular, I couldn't find a single example in base XNAT of a field that:
 *   1. Exists in the schema as an explicit "integer" field, AND
 *   2. Has a corresponding display field.
 *  It's fairly easy to find either/OR, but not both. So, I'm using two different fields for these tests unfortunately:
 *  xnat:experimentData/delay for the schema field, and a derived count of DTI scan count per MR session as a display field.
 */
@AddedIn(Xnat_1_8_0.class)
public class TestSearchFilterIntegers extends BaseSearchFilterTest {

    private final XnatSearchDocument t1SearchDocument = readSearchFromFile()
            .addSearchField(DataType.MR_SESSION, MR_T1_SCAN_COUNT_FIELD_ID, SearchFieldTypes.INTEGER, T1_SCAN_COUNT_DISPLAY_NAME);
    private final SearchValidator<ImagingSession> t1SearchValidator = new SearchValidator<>(
            t1SearchColumn,
            t1SearchDocument,
            and(Arrays.asList(mrMatchesLabel, mrMatchesT1Count))
    );
    private final XdatCriteria t1SearchCriteria = new XdatCriteria().schemaField(DataType.MR_SESSION.getXsiType() + "." + MR_T1_SCAN_COUNT_FIELD_ID);

    private final XnatSearchDocument delaySearchDocument = readSearchFromFile()
            .addSearchField(DataType.MR_SESSION, MR_DELAY_SCHEMA_PATH, SearchFieldTypes.INTEGER, DELAY_DISPLAY_NAME);
    private final SearchValidator<ImagingSession> delaySearchValidator = new SearchValidator<>(
            delaySearchColumn,
            delaySearchDocument,
            and(Arrays.asList(mrMatchesLabel, mrMatchesDelay))
    );
    private final XdatCriteria delaySearchCriteria = new XdatCriteria().schemaField(MR_DELAY_SCHEMA_PATH);

    // these are just aliases to make the asserts clearer
    private final MRSession mrSession10000Delay = mrSession1305Pounds;
    private final MRSession mrSession5000Delay = mrSession150Pounds;
    private final MRSession mrSession1234Delay = mrSession200Pounds;
    private final MRSession mrSessionNoDelay = mrSessionNoWeight;
    private final MRSession mrSession3T1Scans = mrSession200Pounds;
    private final MRSession mrSession2T1Scans = mrSession1305Pounds;
    private final MRSession mrSession1T1Scan = mrSession150Pounds;
    private final MRSession mrSessionWithNoScans = mrSessionNoWeight;

    @BeforeClass(groups = TestGroups.SEARCH)
    private void initCriteria() {
        delaySearchDocument.addSearchCriterion(delaySearchCriteria);
        t1SearchDocument.addSearchCriterion(t1SearchCriteria);
    }

    @BeforeMethod(groups = TestGroups.SEARCH)
    private void resetCriteria() {
        delaySearchCriteria.value(null);
        t1SearchCriteria.value(null);
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldEquals() {
        t1SearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value("2");
        t1SearchValidator.performAndValidateSearch(mrSession2T1Scans);
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldNotEquals() {
        t1SearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value("2");
        t1SearchValidator.performAndValidateSearch(mrSession1T1Scan, mrSession3T1Scans, mrSessionWithNoScans);
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldGreaterThan() {
        t1SearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN)
                .value("1");
        t1SearchValidator.performAndValidateSearch(mrSession2T1Scans, mrSession3T1Scans);
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldGreaterThanOrEquals() {
        t1SearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS)
                .value("1");
        t1SearchValidator.performAndValidateSearch(mrSession1T1Scan, mrSession2T1Scans, mrSession3T1Scans);
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldLessThan() {
        t1SearchCriteria
                .comparisonType(ComparisonType.LESS_THAN)
                .value("2");
        t1SearchValidator.performAndValidateSearch(mrSession1T1Scan); // seems weird that "0" is represented as a blank here and not picked up
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldLessThanOrEquals() {
        t1SearchCriteria
                .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS)
                .value("2");
        t1SearchValidator.performAndValidateSearch(mrSession1T1Scan, mrSession2T1Scans); // seems weird that "0" is represented as a blank here and not picked up
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7777")
    public void testSearchEngineFilterIntegerDisplayFieldIn() {
        t1SearchCriteria
                .comparisonType(ComparisonType.IN)
                .value("1, 2");
        t1SearchValidator.performAndValidateSearch(mrSession1T1Scan, mrSession2T1Scans);
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldBetween() {
        t1SearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value("1 AND 3");
        t1SearchValidator.performAndValidateSearch(mrSession1T1Scan, mrSession2T1Scans, mrSession3T1Scans);
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldIsNull() {
        t1SearchCriteria.comparisonType(ComparisonType.IS_NULL);
        t1SearchValidator.performAndValidateSearch(mrSessionWithNoScans);
    }

    @Test
    public void testSearchEngineFilterIntegerDisplayFieldIsNotNull() {
        t1SearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        t1SearchValidator.performAndValidateSearch(mrSession1T1Scan, mrSession2T1Scans, mrSession3T1Scans);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldEquals() {
        delaySearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value("10000");
        delaySearchValidator.performAndValidateSearch(mrSession10000Delay);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldNotEquals() {
        delaySearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value("10000");
        delaySearchValidator.performAndValidateSearch(mrSession5000Delay, mrSession1234Delay);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldGreaterThan() {
        delaySearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN)
                .value("5000");
        delaySearchValidator.performAndValidateSearch(mrSession10000Delay);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldGreaterThanOrEquals() {
        delaySearchCriteria
                .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS)
                .value("5000");
        delaySearchValidator.performAndValidateSearch(mrSession5000Delay, mrSession10000Delay);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldLessThan() {
        delaySearchCriteria
                .comparisonType(ComparisonType.LESS_THAN)
                .value("5000");
        delaySearchValidator.performAndValidateSearch(mrSession1234Delay);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldLessThanOrEquals() {
        delaySearchCriteria
                .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS)
                .value("5000");
        delaySearchValidator.performAndValidateSearch(mrSession1234Delay, mrSession5000Delay);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7777")
    public void testSearchEngineFilterIntegerSchemaFieldIn() {
        delaySearchCriteria
                .comparisonType(ComparisonType.IN)
                .value("5000, 10000");
        delaySearchValidator.performAndValidateSearch(mrSession5000Delay, mrSession10000Delay);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldBetween() {
        delaySearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value("1234 AND 5000");
        delaySearchValidator.performAndValidateSearch(mrSession1234Delay, mrSession5000Delay);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldIsNull() {
        delaySearchCriteria.comparisonType(ComparisonType.IS_NULL);
        delaySearchValidator.performAndValidateSearch(mrSessionNoDelay);
    }

    @Test
    public void testSearchEngineFilterIntegerSchemaFieldIsNotNull() {
        delaySearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        delaySearchValidator.performAndValidateSearch(mrSession10000Delay, mrSession5000Delay, mrSession1234Delay);
    }

    // pre-sorting not available for SubQuery fields, so we have to cache the search first
    @Test
    public void testSearchEngineSortingIntegerDisplayField() {
        final XnatSearchDocument sortingSearchDocument = readSearchFromFile()
                .addSearchField(DataType.MR_SESSION, MR_T1_SCAN_COUNT_FIELD_ID, SearchFieldTypes.INTEGER, T1_SCAN_COUNT_DISPLAY_NAME);
        final SearchValidator<ImagingSession> sortingSearchValidator = new SearchValidator<>(
                t1SearchColumn,
                sortingSearchDocument,
                and(Arrays.asList(mrMatchesLabel, mrMatchesT1Count))
        );

        final XnatSearchParams searchParams = new XnatSearchParams()
                .sortBy(MR_T1_SCAN_COUNT_FIELD_ID.toLowerCase().replace('=', '_'))
                .sortOrder(DESC);
        final SearchResponse cachedSearch = mainInterface().cacheSearch(sortingSearchDocument);
        sortingSearchValidator.validateSearchResults(
                true,
                false,
                mainInterface().retrieveCachedSearchResults(cachedSearch, searchParams),
                mrSessionWithNoScans, mrSession3T1Scans, mrSession2T1Scans, mrSession1T1Scan // "0" is a null in this case unfortunately
        );

        searchParams.sortOrder(ASC);
        sortingSearchValidator.validateSearchResults(
                true,
                false,
                mainInterface().retrieveCachedSearchResults(cachedSearch, searchParams),
                mrSession1T1Scan, mrSession2T1Scans, mrSession3T1Scans, mrSessionWithNoScans
        );
    }

    private XnatSearchDocument readSearchFromFile() {
        return readXmlFromFile("default_project_mr_session_search.xml", new TemplateReplacements().project(testProject));
    }

}
