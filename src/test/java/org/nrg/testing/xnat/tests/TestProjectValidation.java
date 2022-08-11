package org.nrg.testing.xnat.tests;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.testng.annotations.*;

import static org.nrg.testing.TestGroups.VALIDATION;

// Adapted from tests originally written by James on 2014-02-24
@Test(groups = VALIDATION)
public class TestProjectValidation extends BaseXnatRestTest {

    private Project project;

    @BeforeMethod
    private void addProjectValidationProject() {
        project = new Project().runningTitle(RandomHelper.randomID()).title(RandomHelper.randomID()).addAlias(RandomHelper.randomID());
        mainAdminInterface().createProject(project);
    }

    @AfterMethod(alwaysRun = true)
    private void deleteProjectValidationProject() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test
    public void testDuplicateId() {
        mainCredentials().put(mainInterface().projectUrl(project)).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testProjectIdAsTitle() {
        mainAdminCredentials().given().queryParam("name", project.getId().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testProjectIdAsSecondaryId() {
        mainAdminCredentials().given().queryParam("secondary_ID", project.getId().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testProjectIdAsAlias() {
        mainAdminCredentials().given().queryParam("alias", project.getId().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testDuplicateTitle() {
        mainAdminCredentials().given().queryParam("name", project.getTitle().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testTitleAsSecondaryId() {
        mainAdminCredentials().given().queryParam("secondary_ID", project.getTitle().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testTitleAsProjectId() {
        mainAdminCredentials().put(mainInterface().projectUrl(new Project(project.getTitle()))).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testTitleAsAlias() {
        mainAdminCredentials().given().queryParam("alias", project.getTitle().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testDuplicateSecondaryId() {
        mainAdminCredentials().given().queryParam("secondary_ID", project.getRunningTitle().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testSecondaryIdAsProjectId() {
        mainAdminCredentials().put(mainInterface().projectUrl(new Project(project.getRunningTitle()))).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testSecondaryIdAsTitle() {
        mainAdminCredentials().given().queryParam("name", project.getRunningTitle().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testSecondaryIdAsAlias() {
        mainAdminCredentials().given().queryParam("alias", project.getRunningTitle().toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testDuplicateAlias() {
        mainAdminCredentials().given().queryParam("alias", project.getAliases().get(0).toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testAliasAsProjectId() {
        mainAdminCredentials().put(mainInterface().projectUrl(new Project(project.getAliases().get(0)))).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testAliasAsTitle() {
        mainAdminCredentials().given().queryParam("name", project.getAliases().get(0).toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }

    @Test
    public void testAliasAsSecondaryId() {
        mainAdminCredentials().given().queryParam("secondary_ID", project.getAliases().get(0).toUpperCase()).put(mainInterface().projectUrl(new Project())).then().assertThat().statusCode(acceptableCodes());
    }
    
    private Matcher<Integer> acceptableCodes() {
        return Matchers.isOneOf(403, 409);
    }

}
