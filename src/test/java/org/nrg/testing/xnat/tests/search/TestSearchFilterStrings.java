package org.nrg.testing.xnat.tests.search;

import org.apache.commons.lang3.StringUtils;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Subject;
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

import java.util.Arrays;
import java.util.function.BiFunction;

import static org.nrg.xnat.pogo.search.SortOrder.ASC;
import static org.nrg.xnat.pogo.search.SortOrder.DESC;

@AddedIn(Xnat_1_8_0.class)
public class TestSearchFilterStrings extends BaseSearchFilterTest {

    private final BiFunction<Subject, SearchRow, Boolean> subjectCheckerFunction = and(Arrays.asList(subjectMatchesLabel, subjectMatchesGroup));

    private final XnatSearchDocument groupSearchDocument = readSearchFromFile()
            .addSearchField(DataType.SUBJECT, GROUP_DISPLAY_FIELD_ID, SearchFieldTypes.STRING, GROUP_DISPLAY_NAME);
    private final SearchValidator<Subject> groupSearchValidator = new SearchValidator<>(
            groupSearchColumn,
            groupSearchDocument,
            subjectCheckerFunction
    );
    private final XdatCriteria groupSearchCriteria = new XdatCriteria().schemaField(DataType.SUBJECT.getXsiType() + "." + GROUP_DISPLAY_FIELD_ID);

    private final XnatSearchDocument groupSchemaSearchDocument = readSearchFromFile()
            .addSearchField(DataType.SUBJECT, GROUP_SCHEMA_PATH, SearchFieldTypes.STRING, GROUP_DISPLAY_NAME);
    private final SearchValidator<Subject> groupSchemaSearchValidator = new SearchValidator<>(
            groupSearchColumn,
            groupSchemaSearchDocument,
            subjectCheckerFunction
    );
    private final XdatCriteria groupSchemaSearchCriteria = new XdatCriteria().schemaField(GROUP_SCHEMA_PATH);

    @BeforeClass(groups = TestGroups.SEARCH)
    private void initCriteria() {
        groupSearchDocument.addSearchCriterion(groupSearchCriteria);
        groupSchemaSearchDocument.addSearchCriterion(groupSchemaSearchCriteria);
    }

    @BeforeMethod(groups = TestGroups.SEARCH)
    private void resetCriteria() {
        groupSearchCriteria.value(null);
        groupSchemaSearchCriteria.value(null);
    }

    @Test
    public void testSearchEngineFilterStringDisplayFieldEquals() {
        groupSearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value(subjectSymmetric.getGroup());
        groupSearchValidator.performAndValidateSearch(subjectSymmetric);
    }

    @Test
    public void testSearchEngineFilterStringDisplayFieldNotEquals() {
        groupSearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value(subjectSymmetric.getGroup());
        groupSearchValidator.performAndValidateSearch(
                subjectSpecialLinear, subjectFrobenius, subjectCyclic, subjectNull
        );
    }

    @Test
    public void testSearchEngineFilterStringDisplayFieldLike() {
        groupSearchCriteria
                .comparisonType(ComparisonType.LIKE)
                .value("S%");
        groupSearchValidator.performAndValidateSearch(
                subjectSymmetric, subjectSpecialLinear
        );

        groupSearchCriteria.setValue("%s");
        groupSearchValidator.performAndValidateSearch(subjectFrobenius);

        groupSearchCriteria.setValue("%met%");
        groupSearchValidator.performAndValidateSearch(subjectSymmetric);
    }

    @Test
    public void testSearchEngineFilterStringDisplayFieldBetween() {
        groupSearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value("B AND D");
        groupSearchValidator.performAndValidateSearch(
                subjectCyclic
        );
    }

    @Test
    public void testSearchEngineFilterStringDisplayFieldIsNull() {
        groupSearchCriteria.setComparisonType(ComparisonType.IS_NULL);
        groupSearchValidator.performAndValidateSearch(subjectNull);
    }

    @Test
    public void testSearchEngineFilterStringDisplayFieldIsNotNull() {
        groupSearchCriteria.setComparisonType(ComparisonType.IS_NOT_NULL);
        groupSearchValidator.performAndValidateSearch(
                subjectSymmetric, subjectSpecialLinear, subjectFrobenius, subjectCyclic
        );
    }

    @Test
    public void testSearchEngineFilterStringSchemaFieldEquals() {
        groupSchemaSearchCriteria
                .comparisonType(ComparisonType.EQUALS)
                .value(subjectSymmetric.getGroup());
        groupSchemaSearchValidator.performAndValidateSearch(subjectSymmetric);
    }

    @Test
    public void testSearchEngineFilterStringSchemaFieldNotEquals() {
        groupSchemaSearchCriteria
                .comparisonType(ComparisonType.NOT_EQUALS)
                .value(subjectSymmetric.getGroup());
        groupSchemaSearchValidator.performAndValidateSearch(
                subjectSpecialLinear, subjectFrobenius, subjectCyclic // this behaves differently from the display field version. weird
        );
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7779")
    public void testSearchEngineFilterStringSchemaFieldLike() {
        groupSchemaSearchCriteria
                .comparisonType(ComparisonType.LIKE)
                .value("S%");
        groupSchemaSearchValidator.performAndValidateSearch(
                subjectSymmetric, subjectSpecialLinear
        );

        groupSchemaSearchCriteria.setValue("%s");
        groupSchemaSearchValidator.performAndValidateSearch(subjectFrobenius);

        groupSchemaSearchCriteria.setValue("%met%");
        groupSchemaSearchValidator.performAndValidateSearch(subjectSymmetric);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-7780")
    public void testSearchEngineFilterStringSchemaFieldBetween() {
        groupSchemaSearchCriteria
                .comparisonType(ComparisonType.BETWEEN)
                .value("B AND D");
        groupSchemaSearchValidator.performAndValidateSearch(
                subjectCyclic
        );
    }

    @Test
    public void testSearchEngineFilterStringSchemaFieldIsNull() {
        groupSchemaSearchCriteria.setComparisonType(ComparisonType.IS_NULL);
        groupSchemaSearchValidator.performAndValidateSearch(subjectNull);
    }

    @Test
    public void testSearchEngineFilterStringSchemaFieldIsNotNull() {
        groupSchemaSearchCriteria.setComparisonType(ComparisonType.IS_NOT_NULL);
        groupSchemaSearchValidator.performAndValidateSearch(
                subjectSymmetric, subjectSpecialLinear, subjectFrobenius, subjectCyclic
        );
    }

    @Test
    public void testSearchEngineSortingStringDisplayField() {
        final XnatSearchDocument sortingSearchDocument = readSearchFromFile()
                .addSearchField(DataType.SUBJECT, GROUP_DISPLAY_FIELD_ID, SearchFieldTypes.STRING, GROUP_DISPLAY_NAME);
        final SearchValidator<Subject> sortingSearchValidator = new SearchValidator<>(
                groupSearchColumn,
                sortingSearchDocument,
                subjectCheckerFunction
        );

        final XnatSearchParams searchParams = new XnatSearchParams().sortBy(GROUP_DISPLAY_FIELD_ID).sortOrder(DESC);
        sortingSearchValidator.performAndValidateSearch(searchParams, subjectNull, subjectSymmetric, subjectSpecialLinear, subjectFrobenius, subjectCyclic);

        searchParams.setSortOrder(ASC);
        sortingSearchValidator.performAndValidateSearch(searchParams, subjectCyclic, subjectFrobenius, subjectSpecialLinear, subjectSymmetric, subjectNull);

        searchParams.setSortOrder(DESC);
        groupSearchCriteria.comparisonType(ComparisonType.IS_NOT_NULL);
        groupSearchValidator.performAndValidateSearch(searchParams, subjectSymmetric, subjectSpecialLinear, subjectFrobenius, subjectCyclic);
    }

    private XnatSearchDocument readSearchFromFile() {
        return readXmlFromFile("default_project_subject_search.xml", new TemplateReplacements().project(testProject));
    }

}
