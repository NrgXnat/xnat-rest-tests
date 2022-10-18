package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.apache.commons.io.FileUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.nrg.testing.TestGroups.SEARCH;
import static org.nrg.xnat.pogo.DataType.MR_SESSION;
import static org.testng.Assert.assertNotEquals;
import static org.testng.AssertJUnit.assertEquals;

public class TestSearch extends BaseXnatRestTest {

    private final File originalResourceFile = getDataFile("search/mrSearchXML.xml");

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testElements() {
        final JsonPath elements = mainCredentials().given().queryParam("format", "json").get(formatRestUrl("search", "elements")).
                then().assertThat().statusCode(200).
                and().extract().jsonPath().setRoot("ResultSet.Result");

        // These can be changed by a site administrator, but these are the default values.
        final Map<String, Object> mrSession = lookupElement(MR_SESSION.getXsiType(), elements);
        assertNotEquals(null, mrSession);
        assertEquals(MR_SESSION.getSingularName(), mrSession.get("SINGULAR"));
        assertEquals(MR_SESSION.getPluralName(), mrSession.get("PLURAL"));

        // Again, a site administrator can mess with this, but xnat:investigatorData is the only default unsecured
        assertEquals("false", lookupElement("xnat:investigatorData", elements).get("SECURED"));
        assertEquals(1, elements.getList("findAll { it.SECURED == 'false' }").size());
    }

    @Test(groups = {SEARCH})
    public void testBasicSearch() throws IOException {
        Project project = registerTempProject();
        Subject subject = new Subject(project);
        ImagingSession session1 = new MRSession(project, subject).date(LocalDate.parse("2012-01-01"));
        ImagingSession session2 = new MRSession(project, subject).date(LocalDate.parse("2015-05-03"));
        mainInterface().createProject(project);

        Project project2 = registerTempProject();
        Subject subject2 = new Subject();
        ImagingSession session3 = new MRSession(project2, subject2);
        mainInterface().createProject(project2);


        String rawTestString = FileUtils.readFileToString(originalResourceFile, "UTF-8");

        rawTestString = rawTestString.replace("PROJECT_NAME_FIELD", project.getId());

        rawTestString.replaceAll("SITE_URL_BASE", formatXnatUrl());

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("format", "json");
        queryParams.put("cache", "true");
        queryParams.put("refresh", "true");
        String queryReturn = mainAdminInterface().requestWithCsrfToken().contentType(ContentType.JSON)
                .body(rawTestString).queryParams(queryParams).post(formatRestUrl("/search/")).then()
                .assertThat().statusCode(200).and().extract().response().jsonPath().setRootPath("ResultSet")
                .getJsonObject("ID").toString();

        String filterURL = "/search/" + queryReturn;

        Map<String, String> filterQueryParams = new HashMap<>();
        filterQueryParams.put("format", "json");
        filterQueryParams.put("sortBy", "date");
        filterQueryParams.put("sortOrder", "ASC");

        JsonPath sortedOutput = mainAdminInterface().requestWithCsrfToken().queryParams(filterQueryParams)
                .get(formatRestUrl(filterURL)).then().assertThat().statusCode(200).and().extract()
                .response().jsonPath();

        List<LinkedHashMap> ascendingResults = sortedOutput.setRootPath("ResultSet").getJsonObject("Result");

        assertEquals(ascendingResults.size(), 2);

        assertEquals(ascendingResults.get(0).get("session_id"), session1.getAccessionNumber());
        assertEquals(ascendingResults.get(1).get("session_id"), session2.getAccessionNumber());

        filterQueryParams.replace("sortOrder", "DESC");

        JsonPath inverseSortedOutput = mainAdminInterface().requestWithCsrfToken().queryParams(filterQueryParams)
                .get(formatRestUrl(filterURL)).then().assertThat().statusCode(200).and().extract()
                .response().jsonPath();

        List<LinkedHashMap> descendingResults = inverseSortedOutput.setRootPath("ResultSet").getJsonObject("Result");

        assertEquals(descendingResults.get(0).get("session_id"), session2.getAccessionNumber());
        assertEquals(descendingResults.get(1).get("session_id"), session1.getAccessionNumber());
    }

    private Map<String, Object> lookupElement(String xsiType, JsonPath elements) {
        return elements.get(String.format("find { it.ELEMENT_NAME == '%s' }", xsiType));
    }

}
