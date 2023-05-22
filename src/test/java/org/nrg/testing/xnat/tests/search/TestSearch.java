package org.nrg.testing.xnat.tests.search;

import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.SearchElement;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.search.XnatSearchParams;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.nrg.xnat.pogo.DataType.MR_SESSION;
import static org.testng.AssertJUnit.assertEquals;

public class TestSearch extends BaseSearchTest {

    private static final String originalResourceFile = "mrSearchXML.xml";

    @Test(groups = {PERMISSIONS})
    public void testElements() {
        final List<SearchElement> searchElements = mainAdminInterface().readSearchElements();
        // These can be changed by a site administrator, but these are the default values.

        final SearchElement mrSession = searchElements.stream()
                .filter(element -> MR_SESSION.getXsiType().equals(element.getElementName()))
                .findFirst()
                .orElseThrow(RuntimeException::new);
        assertEquals(MR_SESSION.getSingularName(), mrSession.getSingularName());
        assertEquals(MR_SESSION.getPluralName(), mrSession.getPluralName());

        // Again, a site administrator can mess with this, but xnat:investigatorData is the only default unsecured
        final List<SearchElement> unsecuredElements = searchElements.stream()
                .filter(element -> !element.isSecured())
                .collect(Collectors.toList());
        assertEquals(1, unsecuredElements.size());
        assertEquals("xnat:investigatorData", unsecuredElements.get(0).getElementName());
    }

    @Test
    public void testBasicSearch() {
        final Project project = registerTempProject();
        final Subject subject = new Subject(project);
        final ImagingSession session1 = new MRSession(project, subject).date(LocalDate.parse("2012-01-01"));
        final ImagingSession session2 = new MRSession(project, subject).date(LocalDate.parse("2015-05-03"));
        mainInterface().createProject(project);

        final XnatSearchDocument searchBody = readXmlFromFile(originalResourceFile, new TemplateReplacements().project(project));

        final SearchResponse cachedSearch = mainAdminInterface().cacheSearch(searchBody);
        final XnatSearchParams sortingParams = new XnatSearchParams().sortBy("date").sortOrder(XnatSearchParams.SortOrder.ASC);

        final SearchResponse ascendingResults = mainAdminInterface().retrieveCachedSearchResults(cachedSearch, sortingParams);
        assertResultsMatch(ascendingResults, session1, session2);

        final SearchResponse descendingResults = mainAdminInterface().retrieveCachedSearchResults(cachedSearch, sortingParams.sortOrder(XnatSearchParams.SortOrder.DESC));
        assertResultsMatch(descendingResults, session2, session1);
    }

    private void assertResultsMatch(SearchResponse searchResponse, ImagingSession... sessions) {
        assertEquals(
                Arrays.stream(sessions).map(ImagingSession::getAccessionNumber).collect(Collectors.toList()),
                searchResponse.getResult().stream().map(SearchRow::getSessionId).collect(Collectors.toList())
        );
    }

}
