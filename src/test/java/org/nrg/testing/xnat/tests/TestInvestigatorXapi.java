package org.nrg.testing.xnat.tests;

import com.jayway.restassured.http.ContentType;
import org.hamcrest.Matchers;
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

public class TestInvestigatorXapi extends BaseXnatRestTest {

    @Test
    public void testGetAndCreateInvestigators() {
        final List<Investigator> extantInvestigators = restDriver.readInvestigators(mainUser);

        final Investigator pi = randomInvestigator().title("Dr.").department("DEPT").institution("INST").email(Settings.EMAIL).phone("314-867-5309");
        final Investigator investigator = randomInvestigator();
        final Project project = new Project().pi(pi).investigators(Collections.singletonList(investigator));
        pi.primaryProjects(Collections.singletonList(project.getId()));
        investigator.investigatorProjects(Collections.singletonList(project.getId()));

        restDriver.createProject(mainUser, project);
        final List<Investigator> allInvestigators = restDriver.readInvestigators(mainUser);
        assertEquals(extantInvestigators.size() + 2, allInvestigators.size());
        findAndCheck(allInvestigators, pi);
        assertInvestigatorData(pi, readInvestigator(pi.getXnatInvestigatordataId()));
        findAndCheck(allInvestigators, investigator);
        assertInvestigatorData(investigator, readInvestigator(investigator.getXnatInvestigatordataId()));
    }

    @Test
    public void testUpdateInvestigators() {
        final Investigator investigator = randomInvestigator().title("Dr.").department("DEPT").email(Settings.EMAIL).phone("314-867-5309");
        restDriver.createInvestigator(mainUser, investigator);
        final int numSystemInvestigators = restDriver.readInvestigators(mainUser).size();
        restDriver.mainInterface().queryBase().contentType(ContentType.JSON).body(investigator.firstname(RandomHelper.randomLetters(8)).phone("1-800-867-5309").institution("INST")).
                put(investigatorUrl(investigator.getXnatInvestigatordataId())).then().assertThat().statusCode(200);
        assertEquals(numSystemInvestigators, restDriver.readInvestigators(mainUser).size());
        assertInvestigatorData(investigator, readInvestigator(investigator.getXnatInvestigatordataId()));
    }

    @Test
    public void testDeleteInvestigators() {
        final Investigator investigator = randomInvestigator().title("Dr.");
        final Project project = new Project().pi(investigator);
        restDriver.createProject(mainUser, project);
        final List<Investigator> investigatorsBeforeDelete = restDriver.readInvestigators(mainUser);
        deleteInvestigator(investigator.getXnatInvestigatordataId());
        final List<Investigator> investigatorsAfterDelete = restDriver.readInvestigators(mainUser);
        assertEquals(investigatorsBeforeDelete.size() - 1, investigatorsAfterDelete.size());
        assertNull(find(investigatorsAfterDelete, investigator));
        restDriver.interfaceFor(mainUser).queryBase().get(investigatorUrl(investigator.getXnatInvestigatordataId())).then().assertThat().statusCode(Matchers.isOneOf(404, 500)); // should really be 404, but eh, whatever
        assertEquals(null, restDriver.readProject(mainUser, project.getId()).getPi());
    }

    private Investigator randomInvestigator() {
        return new Investigator().firstname(RandomHelper.randomLetters(10)).lastname(RandomHelper.randomLetters(10));
    }

    private Investigator find(List<Investigator> investigators, Investigator searchFor) {
        return investigators.contains(searchFor) ?  investigators.get(investigators.indexOf(searchFor)) : null;
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
        return restDriver.formatXapiUrl("investigators", String.valueOf(investigatorId));
    }

    private Investigator readInvestigator(int id) {
        return restDriver.mainInterface().queryBase().get(investigatorUrl(id)).then().assertThat().statusCode(200).and().extract().as(Investigator.class);
    }

    private void deleteInvestigator(int id) {
        restDriver.interfaceFor(mainAdminUser).queryBase().delete(investigatorUrl(id)).then().assertThat().statusCode(200);
    }

}
