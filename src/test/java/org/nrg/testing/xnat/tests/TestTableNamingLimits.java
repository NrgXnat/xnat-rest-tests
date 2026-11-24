package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.Users;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_11_0;
import org.nrg.xnat.versions.Xnat_1_8_4;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.nrg.testing.TestGroups.SEARCH;

public class TestTableNamingLimits extends BaseXnatRestTest {

    // XNAT-6374: custom variable names chosen so their search columns collide unless XNAT keeps them apart. The first two
    // share their first 63 characters once prefixed with XNAT_MRSESSIONDATA_FIELD_MAP_; the others clean or lower-case to
    // the same column name. Each value names its variable, so a column showing another variable's data is visible.
    private static final String              FIELD_MAP        = "XNAT_MRSESSIONDATA_FIELD_MAP";
    private static final String              LONG_ALPHA       = "radiotherapy_planning_dose_constraint_alpha";
    private static final String              LONG_BETA        = "radiotherapy_planning_dose_constraint_beta";
    private static final Map<String, Object> CUSTOM_VARIABLES = new LinkedHashMap<>();

    static {
        CUSTOM_VARIABLES.put(LONG_ALPHA, "long-alpha-value");
        CUSTOM_VARIABLES.put(LONG_BETA, "long-beta-value");
        CUSTOM_VARIABLES.put("dose-alpha", "dose-hyphen-value");
        CUSTOM_VARIABLES.put("dose_alpha", "dose-underscore-value");
        CUSTOM_VARIABLES.put("Segment", "segment-upper-value");
        CUSTOM_VARIABLES.put("segment", "segment-lower-value");
    }

    private User longUsernameUser;
    private XnatInterface longUserInterface;
    private final Project longIdProject = new Project(RandomHelper.randomID(32));
    private final Subject subject = new Subject(longIdProject);
    private final ImagingSession session = new MRSession(longIdProject, subject);
    private final ManualQC manualQC = new ManualQC(longIdProject, subject, session);

    @BeforeClass
    public void setupScenario() {
        final String username = RandomHelper.randomLetters(24);
        longUsernameUser = Users.genericAccount().username(username).password(username);
        mainAdminInterface().createUser(longUsernameUser);
        longUserInterface = interfaceFor(longUsernameUser);
        longUserInterface.createProject(longIdProject);
        // Set directly rather than through the session's fields, which lower-case variable names and would merge Segment and segment.
        for (final Map.Entry<String, Object> variable : CUSTOM_VARIABLES.entrySet()) {
            longUserInterface.queryBase().queryParam("xnat:mrSessionData/fields/field[name=" + variable.getKey() + "]/field", variable.getValue()).
                    put(longUserInterface.subjectAssessorUrl(session)).then().assertThat().statusCode(200);
        }
    }

    @AfterClass(alwaysRun = true)
    public void removeProject() {
        restDriver.deleteProjectSilently(longUsernameUser, longIdProject);
    }

    @Test(groups = SEARCH)
    @AddedIn(Xnat_1_8_4.class)
    public void testLongSiteSearch() {
        final String searchXML = longUserInterface.queryBase().urlEncodingEnabled(false).get(formatRestUrl("/search/saved/@" + DataType.MANUAL_QC.getXsiType())).
                then().assertThat().statusCode(200).and().extract().response().asString();
        String cachedSearchId = null;
        for (int i = 0; i < 5; i++) {
            cachedSearchId = longUserInterface.jsonQuery().queryParam("cache", true).queryParam("refresh", true).body(searchXML).post(formatRestUrl("search")).
                    then().assertThat().statusCode(200).and().extract().path("ResultSet.ID"); // try to cache a temporary search table to check XNAT-6784
        }
        longUserInterface.jsonQuery().get(formatRestUrl("/search", cachedSearchId)).then().assertThat().body("ResultSet.Result.label", Matchers.hasItem(manualQC.getLabel()));
    }

    @Test(groups = SEARCH)
    @AddedIn(Xnat_1_11_0.class)
    public void testLongCustomVariableColumns() {
        assertOwnValues(searchSession(LONG_ALPHA, LONG_BETA), LONG_ALPHA, LONG_BETA); // used to fail with "column reference ... is ambiguous"
    }

    @Test(groups = SEARCH)
    @AddedIn(Xnat_1_11_0.class)
    public void testFilterOnLongCustomVariable() {
        final String criterion = "<xdat:child_set method=\"AND\"><xdat:criteria override_value_formatting=\"0\">" +
                                 "<xdat:schema_field>xnat:mrSessionData." + FIELD_MAP + "=" + LONG_ALPHA + "</xdat:schema_field>" +
                                 "<xdat:comparison_type>=</xdat:comparison_type><xdat:value>" + CUSTOM_VARIABLES.get(LONG_ALPHA) + "</xdat:value>" +
                                 "</xdat:criteria></xdat:child_set>";
        final List<Map<String, Object>> rows = search(searchXml("xnat:mrSessionData", sessionColumns(LONG_ALPHA, LONG_BETA), criterion));
        assertThat(rows.stream().map(row -> row.get("label")).collect(Collectors.toList()), Matchers.contains(session.getLabel()));
    }

    @Test(groups = SEARCH)
    @AddedIn(Xnat_1_11_0.class)
    public void testCustomVariablesThatCleanToTheSameName() {
        assertOwnValues(searchSession("dose-alpha", "dose_alpha"), "dose-alpha", "dose_alpha");
        assertOwnValues(searchSession("Segment", "segment"), "Segment", "segment");
    }

    @Test(groups = SEARCH)
    @AddedIn(Xnat_1_11_0.class)
    public void testLongCustomVariableOnJoinedType() {
        final String columns = column("xnat:subjectData", "SUBJECT_LABEL", null, 0) + column("xnat:mrSessionData", FIELD_MAP, LONG_ALPHA, 1);
        final List<Map<String, Object>> rows = search(searchXml("xnat:subjectData", columns, ""));
        assertThat(rows, Matchers.hasSize(1));
        assertThat(rows.get(0).values(), Matchers.hasItem(CUSTOM_VARIABLES.get(LONG_ALPHA)));
    }

    private List<Map<String, Object>> searchSession(final String... variables) {
        return search(searchXml("xnat:mrSessionData", sessionColumns(variables), ""));
    }

    private List<Map<String, Object>> search(final String searchXml) {
        return longUserInterface.jsonQuery().body(searchXml).post(formatRestUrl("search")).
                then().assertThat().statusCode(200).and().extract().jsonPath().getList("ResultSet.Result");
    }

    private void assertOwnValues(final List<Map<String, Object>> rows, final String... variables) {
        assertThat(rows, Matchers.hasSize(1));
        final List<Object> expected = Arrays.stream(variables).map(CUSTOM_VARIABLES::get).collect(Collectors.toList());
        final List<Object> actual   = rows.get(0).values().stream().filter(CUSTOM_VARIABLES::containsValue).collect(Collectors.toList());
        assertThat(actual, Matchers.containsInAnyOrder(expected.toArray()));
    }

    private static String sessionColumns(final String... variables) {
        final StringBuilder columns = new StringBuilder(column("xnat:mrSessionData", "LABEL", null, 0));
        for (int i = 0; i < variables.length; i++) {
            columns.append(column("xnat:mrSessionData", FIELD_MAP, variables[i], i + 1));
        }
        return columns.toString();
    }

    private static String column(final String element, final String fieldId, final String value, final int sequence) {
        final String id = value == null ? fieldId : fieldId + "=" + value;
        return "<xdat:search_field><xdat:element_name>" + element + "</xdat:element_name><xdat:field_ID>" + id + "</xdat:field_ID>" +
               "<xdat:sequence>" + sequence + "</xdat:sequence><xdat:type>string</xdat:type><xdat:header>" + id + "</xdat:header>" +
               (value == null ? "" : "<xdat:value>" + value + "</xdat:value>") + "</xdat:search_field>";
    }

    private String searchXml(final String rootElement, final String columns, final String criteria) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<xdat:bundle ID=\"\" allow-diff-columns=\"0\" secure=\"false\" brief-description=\"XNAT-6374\" " +
               "xmlns:xdat=\"http://nrg.wustl.edu/security\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
               "<xdat:root_element_name>" + rootElement + "</xdat:root_element_name>" + columns +
               "<xdat:search_where method=\"AND\"><xdat:criteria override_value_formatting=\"0\">" +
               "<xdat:schema_field>" + rootElement + "/project</xdat:schema_field><xdat:comparison_type>=</xdat:comparison_type>" +
               "<xdat:value>" + longIdProject.getId() + "</xdat:value></xdat:criteria>" + criteria + "</xdat:search_where></xdat:bundle>";
    }

}
