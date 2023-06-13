package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;

@AddedIn(Xnat_1_8_0.class)
public class TestSearchFilterFloats extends BaseSearchFilterTest {

    private final XnatSearchDocument weightSearchDocument = readXmlFromFile("default_project_mr_session_search.xml", new TemplateReplacements().project(testProject))
            .addSearchField(weightSearchField);
    private final SearchValidator<ImagingSession> sessionSearchValidator = new SearchValidator<>(
            weightSearchColumn,
            weightSearchDocument,
            and(Arrays.asList(mrMatchesLabel, mrMatchesWeight))
    );
    private final XdatCriteria weightSearchCriteria = new XdatCriteria().schemaField(MR_WEIGHT_SCHEMA_PATH);

    @BeforeClass(groups = TestGroups.SEARCH)
    private void initCriteria() {
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
