package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.search.SortOrder;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.search.XnatSearchParams;
import org.nrg.xnat.util.RandomUtils;
import org.nrg.xnat.versions.Xnat_1_8_9;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TestSearchPaginationAndCaching extends BaseSearchFilterTest {

    private final SearchValidator<Subject> subjectSearchValidator = new SearchValidator<>(
            and(subjectMatchesLabel, subjectMatchesGroup)
    );

    @Test
    public void testSearchEnginePaginationConsistencyWithoutSorting() {
        runSearchEnginePaginatedConsistencyTest(false, false);
    }

    @Test
    @AddedIn(Xnat_1_8_9.class) // See XNAT-7371
    public void testSearchEnginePaginationConsistencyWithoutSortingResultsChanging() {
        runSearchEnginePaginatedConsistencyTest(true, false);
    }

    @Test
    public void testSearchEnginePaginationConsistencyWithSorting() {
        runSearchEnginePaginatedConsistencyTest(false, true);
    }

    @Test
    @AddedIn(Xnat_1_8_9.class) // See XNAT-7371
    public void testSearchEnginePaginationConsistencyWithSortingResultsChanging() {
        runSearchEnginePaginatedConsistencyTest(true, true);
    }

    @SuppressWarnings("unused")
    @Test
    public void testSearchEngineStoredSearchRefresh() {
        final Project project = registerTempProject();
        final Subject subj001 = new Subject(project, "SUBJ_001").group("A");
        final Subject subj002 = new Subject(project, "SUBJ_002").group("B");
        final Subject subj003 = new Subject(project, "SUBJ_003").group("C");
        mainInterface().createProject(project);

        final XnatSearchDocument searchDocument = mainInterface().getDefaultSearch(project, DataType.SUBJECT)
                .tag("")
                .id("TEST_STORED_SEARCH_" + RandomUtils.randomID(20))
                .secure(true)
                .briefDescription("test stored search")
                .addSearchField(groupSearchField)
                .addAllowedUser(mainUser);

        mainInterface().uploadStoredSearch(searchDocument);

        mainInterface().cacheSearch(searchDocument);
        searchDocument.addSearchCriterion(
                new XdatCriteria()
                        .schemaField("xnat:subjectData.SUB_PROJECT_IDENTIFIER=" + project.getId())
                        .comparisonType(ComparisonType.EQUALS)
                        .value(subj001.getLabel())
        );
        mainInterface().cacheSearch(searchDocument);

        final SearchResponse possiblySurprisinglyCachedFilteredResults = mainInterface().readStoredSearchResults(searchDocument.getId());
        subjectSearchValidator.validateSearchResponseResult(
                true,
                possiblySurprisinglyCachedFilteredResults.getResult(),
                Collections.singletonList(subj001)
        );

        final SearchResponse refreshedResults = mainInterface().readStoredSearchResults(searchDocument.getId(), new XnatSearchParams().refresh(true).cache(true));
        subjectSearchValidator.validateSearchResponseResult(
                false,
                refreshedResults.getResult(),
                project.getSubjects()
        );
    }

    private void runSearchEnginePaginatedConsistencyTest(boolean addSubjectsDuringPageRetrieval, boolean sorted) {
        final Project largeProject = registerTempProject();
        final int numSubjects = 200;
        final int subjectsPerSearch = 10;
        for (int i = 0; i < numSubjects; i++) {
            final String id = RandomUtils.randomID(20).toLowerCase(); // workaround for XNAT-7798
            new Subject(largeProject, id).group(id);
        }
        mainInterface().createProject(largeProject);

        final SearchResponse cachedSearch = mainInterface().cacheSearch(
                readSearchFromFile(largeProject).addSearchField(groupSearchField)
        );
        final List<SearchRow> aggregatedResults = new ArrayList<>();
        final List<Subject> cachedSubjects = largeProject.getSubjects(); // this list is a copy, so we don't have to worry about it being modified
        final XnatSearchParams searchParams = sorted ? new XnatSearchParams().sortBy(groupSearchColumn.getKey()).sortOrder(SortOrder.ASC) : new XnatSearchParams();

        for (int i = 0; i < numSubjects; i += subjectsPerSearch) {
            final SearchResponse response = mainInterface().retrieveCachedSearchResults(
                    cachedSearch,
                    searchParams
                            .limit(subjectsPerSearch)
                            .offset(i)
            );
            aggregatedResults.addAll(response.getResult());
            if (addSubjectsDuringPageRetrieval) {
                mainInterface().createSubject(new Subject(largeProject));
            }
        }

        subjectSearchValidator.validateSearchResponseResult(
                sorted,
                aggregatedResults,
                sorted ?
                        cachedSubjects.stream().sorted(Comparator.comparing(Subject::getLabel)).collect(Collectors.toList()) :
                        cachedSubjects
        );
    }

    private XnatSearchDocument readSearchFromFile(Project project) {
        return readXmlFromFile("default_project_subject_search.xml", new TemplateReplacements().project(project));
    }

}
