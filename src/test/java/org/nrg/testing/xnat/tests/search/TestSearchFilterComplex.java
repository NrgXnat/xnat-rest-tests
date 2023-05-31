package org.nrg.testing.xnat.tests.search;

import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.SearchFieldTypes;
import org.nrg.xnat.pogo.search.SearchMethod;
import org.nrg.xnat.pogo.search.XdatChildSet;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.testng.annotations.Test;

import java.util.Arrays;

public class TestSearchFilterComplex extends BaseSearchFilterTest {

    private final XnatSearchDocument combinedSearchDocument = readXmlFromFile("default_project_mr_session_search.xml", new TemplateReplacements().project(testProject))
            .addSearchField(DataType.MR_SESSION, MR_WEIGHT_SCHEMA_PATH, SearchFieldTypes.FLOAT, WEIGHT_DISPLAY_NAME)
            .addSearchField(DataType.MR_SESSION, MR_DELAY_SCHEMA_PATH, SearchFieldTypes.INTEGER, DELAY_DISPLAY_NAME);
    private final SearchValidator<ImagingSession> sessionSearchValidator = new SearchValidator<>(
            Arrays.asList(weightSearchColumn, delaySearchColumn),
            combinedSearchDocument,
            and(Arrays.asList(mrMatchesLabel, mrMatchesWeight, mrMatchesDelay))
    );

    @Test
    public void testSearchEngineFilterFloatIntegerComplex() {
        final XdatCriteria weightSearchCriteria = new XdatCriteria()
                .schemaField(MR_WEIGHT_SCHEMA_PATH)
                .comparisonType(ComparisonType.GREATER_THAN)
                .value("140");

        final XdatCriteria delaySearchCriteria = new XdatCriteria()
                .schemaField(MR_DELAY_SCHEMA_PATH)
                .comparisonType(ComparisonType.LESS_THAN)
                .value("2000");

        final XdatChildSet jointSearchCriteria = new XdatChildSet()
                .method(SearchMethod.AND)
                .addSearchCriterion(weightSearchCriteria)
                .addSearchCriterion(delaySearchCriteria);

        combinedSearchDocument.addSearchCriterion(jointSearchCriteria);
        sessionSearchValidator.performAndValidateSearch(mrSession200Pounds);

        jointSearchCriteria.method(SearchMethod.OR);
        sessionSearchValidator.performAndValidateSearch(mrSession150Pounds, mrSession200Pounds);
    }

}
