package org.nrg.testing.xnat.tests.search;

import org.apache.commons.lang3.StringUtils;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.SearchColumn;
import org.nrg.xnat.pogo.search.SearchFieldTypes;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

public class TestSearchFilterFloats extends BaseSearchFilterTest {

    private final SearchColumn weightSearchColumn = buildExpectedSearchColumn(WEIGHT_COLUMN_NAME, SearchFieldTypes.FLOAT, DataType.MR_SESSION, WEIGHT_DISPLAY_NAME);
    private final XnatSearchDocument weightSearchDocument = readXmlFromFile("default_project_mr_session_search.xml", new TemplateReplacements().project(testProject))
            .addSearchField(DataType.MR_SESSION, MR_WEIGHT_SCHEMA_PATH, SearchFieldTypes.FLOAT, WEIGHT_DISPLAY_NAME);
    final SearchValidator<MRSession> sessionSearchValidator = new SearchValidator<>(
            weightSearchColumn,
            weightSearchDocument,
            (session, row) -> mrMatchesLabel.apply(session, row)
                    && StringUtils.equals(session.getSpecificFields().get(MR_WEIGHT_SCHEMA_PATH), row.get(WEIGHT_COLUMN_NAME))
    );
    private final XdatCriteria weightSearchCriteria = new XdatCriteria().schemaField(MR_WEIGHT_SCHEMA_PATH);

    @BeforeClass(groups = TestGroups.SEARCH)
    private void initCriteria() {
        mrSession150Pounds.setSpecificFields(Collections.singletonMap(MR_WEIGHT_SCHEMA_PATH, "150.0")); // we want to set it up as 150 in initial setup, but it should behave as a float when filtering/retrieving
        weightSearchDocument.addSearchCriterion(weightSearchCriteria);
    }

    @BeforeMethod(groups = TestGroups.SEARCH)
    private void resetCriteria() {
        weightSearchCriteria.value(null);
    }

    @Test
    public void testSearchEngineFilterFloatSchemaFieldEquals() {
        weightSearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value("130.5");
        sessionSearchValidator.performAndValidateSearch(mrSession1305Pounds);

        weightSearchCriteria.setValue("150");
        sessionSearchValidator.performAndValidateSearch(
                mrSession150Pounds
        );

        weightSearchCriteria.setValue("150.0");
        sessionSearchValidator.performAndValidateSearch(
                mrSession150Pounds
        );

        weightSearchCriteria.setValue("200");
        sessionSearchValidator.performAndValidateSearch(
                mrSession200Pounds
        );

        weightSearchCriteria.setValue("200.0");
        sessionSearchValidator.performAndValidateSearch(
                mrSession200Pounds
        );
    }

    @Test
    public void testSearchEngineFilterFloatSchemaFieldNotEquals() {
        weightSearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value("130.5");
        sessionSearchValidator.performAndValidateSearch(
                mrSession150Pounds, mrSession200Pounds
        );
    }

    @Test
    public void testSearchEngineFilterFloatSchemaFieldGreaterThanOrEquals() {
        weightSearchCriteria
                .value("150")
                .comparisonType(ComparisonType.GREATER_THAN_OR_EQUALS);
        sessionSearchValidator.performAndValidateSearch(
                mrSession150Pounds, mrSession200Pounds
        );
    }

    @Test
    public void testSearchEngineFilterFloatSchemaFieldLessThan() {
        weightSearchCriteria
                .value("150")
                .comparisonType(ComparisonType.LESS_THAN);
        sessionSearchValidator.performAndValidateSearch(
                mrSession1305Pounds
        );
    }

    @Test
    public void testSearchEngineFilterFloatSchemaFieldLessThanOrEquals() {
        weightSearchCriteria
                .value("150")
                .comparisonType(ComparisonType.LESS_THAN_OR_EQUALS);
        sessionSearchValidator.performAndValidateSearch(
                mrSession1305Pounds, mrSession150Pounds
        );
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7777")
    public void testSearchEngineFilterFloatSchemaFieldIn() {
        weightSearchCriteria
                .comparisonType(ComparisonType.IN)
                .value("130.5, 150.0");
        sessionSearchValidator.performAndValidateSearch(
                mrSession1305Pounds, mrSession150Pounds
        );
    }

    @Test
    public void testSearchEngineFilterFloatSchemaFieldBetween() {
        weightSearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value("130.5 AND 200");
        sessionSearchValidator.performAndValidateSearch(
                mrSession1305Pounds, mrSession150Pounds, mrSession200Pounds
        );

        weightSearchCriteria
                .value("140 AND 190");
        sessionSearchValidator.performAndValidateSearch(
                mrSession150Pounds
        );
    }

    @Test
    public void testSearchEngineFilterFloatSchemaFieldIsNull() {
        weightSearchCriteria.comparisonType(ComparisonType.IS_NULL);
        sessionSearchValidator.performAndValidateSearch(
                mrSessionNoWeight
        );
    }

    @Test
    public void testSearchEngineFilterFloatSchemaFieldIsNotNull() {
        weightSearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        sessionSearchValidator.performAndValidateSearch(
                mrSession1305Pounds, mrSession150Pounds, mrSession200Pounds
        );
    }

}
