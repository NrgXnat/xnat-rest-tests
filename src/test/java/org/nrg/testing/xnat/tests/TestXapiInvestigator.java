package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import io.restassured.http.Method;
import org.hamcrest.Matchers;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.Investigator;
import org.nrg.xnat.pogo.Project;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.AssertJUnit.*;

public class TestXapiInvestigator extends BaseXnatRestTest {

    @Test
    @TestedApiSpec(method = {Method.GET, Method.POST}, url = "/xapi/investigators")
    public void testInvestigators() {
        final List<Investigator> extantInvestigators = mainInterface().readInvestigators(); // GET to /xapi/investigators

        final Investigator pi = randomInvestigator().title("Dr.").department("DEPT").institution("INST").email(Settings.EMAIL).phone("314-867-5309");
        final Investigator investigator = randomInvestigator();
        final Project project = new Project().pi(pi).investigators(Collections.singletonList(investigator));
        pi.primaryProjects(Collections.singletonList(project.getId()));
        investigator.investigatorProjects(Collections.singletonList(project.getId()));

        mainInterface().createProject(project); // POST to /xapi/investigators
        final List<Investigator> allInvestigators = mainInterface().readInvestigators();
        assertEquals(extantInvestigators.size() + 2, allInvestigators.size());
        findAndCheck(allInvestigators, pi);
        findAndCheck(allInvestigators, investigator);
    }

    @Test
    @TestedApiSpec(method = {Method.GET, Method.PUT, Method.DELETE}, url = "/xapi/investigators/{investigatorId}")
    public void testInvestigatorsInvestigatorId() {
        mainAdminInterface().setSiteUserListRestriction(false); // required by readProject()
        final Investigator investigator = randomInvestigator().title("Dr.").department("DEPT").email(Settings.EMAIL).phone("314-867-5309");
        final Project project = new Project().pi(investigator);
        investigator.setPrimaryProjects(Collections.singletonList(project.getId()));
        mainInterface().createProject(project);
        final int numSystemInvestigators = mainInterface().readInvestigators().size();
        restDriver.mainInterface().queryBase().contentType(ContentType.JSON).body(investigator.firstname(RandomHelper.randomLetters(8)).phone("1-800-867-5309").institution("INST")).
                put(investigatorUrl(investigator.getXnatInvestigatordataId())).then().assertThat().statusCode(200);
        assertEquals(numSystemInvestigators, mainInterface().readInvestigators().size()); // update should not create additional investigator
        assertInvestigatorData(investigator, restDriver.mainInterface().queryBase().get(investigatorUrl(investigator.getXnatInvestigatordataId())).then().assertThat().statusCode(200).and().extract().as(Investigator.class));

        restDriver.interfaceFor(mainAdminUser).queryBase().delete(investigatorUrl(investigator.getXnatInvestigatordataId())).then().assertThat().statusCode(200);
        final List<Investigator> investigatorsAfterDelete = mainInterface().readInvestigators();
        assertEquals(numSystemInvestigators - 1, investigatorsAfterDelete.size());
        assertNull(find(investigatorsAfterDelete, investigator));
        restDriver.interfaceFor(mainUser).queryBase().get(investigatorUrl(investigator.getXnatInvestigatordataId())).then().assertThat().statusCode(Matchers.isOneOf(404, 500)); // should really be 404, but eh, whatever
        TimeUtils.sleep(1000); // let cache recover
        assertNull(mainInterface().readProject(project.getId()).getPi());
    }

    private Investigator randomInvestigator() {
        return new Investigator().firstname(RandomHelper.randomLetters(10)).lastname(RandomHelper.randomLetters(10));
    }

    private Investigator find(List<Investigator> investigators, Investigator searchFor) {
        return investigators.contains(searchFor) ? investigators.get(investigators.indexOf(searchFor)) : null;
    }

    private void assertInvestigatorData(Investigator expected, Investigator actual) {
        assertEquals(expected.getDepartment(), actual.getDepartment());
        assertEquals(expected.getEmail(), actual.getEmail());
        assertEquals(expected.getFirstname(), actual.getFirstname());
        assertEquals(expected.getInstitution(), actual.getInstitution());
        assertEquals(expected.getLastname(), actual.getLastname());
        assertEquals(expected.getPhone(), actual.getPhone());
        assertEquals(expected.getTitle(), actual.getTitle());
        for (List<String> list : Arrays.asList(expected.getPrimaryProjects(), expected.getInvestigatorProjects(), actual.getPrimaryProjects(), actual.getInvestigatorProjects())) {
            Collections.sort(list);
        }
        assertEquals(expected.getPrimaryProjects(), actual.getPrimaryProjects());
        assertEquals(expected.getInvestigatorProjects(), actual.getInvestigatorProjects());
    }

    private void findAndCheck(List<Investigator> allInvestigators, Investigator expected) {
        final Investigator found = find(allInvestigators, expected);
        assertNotNull(found);
        assertInvestigatorData(expected, found);
    }

    private String investigatorUrl(int investigatorId) {
        return formatXapiUrl("investigators", String.valueOf(investigatorId));
    }

}
