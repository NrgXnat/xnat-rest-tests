package org.nrg.testing.xnat.tests;

import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseRestTest;
import org.nrg.xnat.pojo.Project;
import org.testng.annotations.*;

// Adapted from tests originally written by James on 2014-02-24
public class TestProjectValidation extends BaseRestTest {

    private Project project;

    @BeforeMethod
    public void addProjectValidationProject() {
        project = new Project().runningTitle(RandomHelper.randomID()).title(RandomHelper.randomID()).addAlias(RandomHelper.randomID());
        restDriver.createProject(mainAdminUser, project);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteProjectValidationProject() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test
    public void testDuplicateId() {
        mainCredentials().put(restDriver.projectUrl(project)).then().assertThat().statusCode(403);
    }

    @Test
    public void testProjectIdAsTitle() {
        mainAdminCredentials().given().queryParam("name", project.getId().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testProjectIdAsSecondaryId() {
        mainAdminCredentials().given().queryParam("secondary_ID", project.getId().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testProjectIdAsAlias() {
        mainAdminCredentials().given().queryParam("alias", project.getId().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testDuplicateTitle() {
        mainAdminCredentials().given().queryParam("name", project.getTitle().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testTitleAsSecondaryId() {
        mainAdminCredentials().given().queryParam("secondary_ID", project.getTitle().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testTitleAsProjectId() {
        mainAdminCredentials().put(restDriver.projectUrl(new Project(project.getTitle()))).then().assertThat().statusCode(403);
    }

    @Test
    public void testTitleAsAlias() {
        mainAdminCredentials().given().queryParam("alias", project.getTitle().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testDuplicateSecondaryId() {
        mainAdminCredentials().given().queryParam("secondary_ID", project.getRunningTitle().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testSecondaryIdAsProjectId() {
        mainAdminCredentials().put(restDriver.projectUrl(new Project(project.getRunningTitle()))).then().assertThat().statusCode(403);
    }

    @Test
    public void testSecondaryIdAsTitle() {
        mainAdminCredentials().given().queryParam("name", project.getRunningTitle().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testSecondaryIdAsAlias() {
        mainAdminCredentials().given().queryParam("alias", project.getRunningTitle().toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testDuplicateAlias() {
        mainAdminCredentials().given().queryParam("alias", project.getAliases().get(0).toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testAliasAsProjectId() {
        mainAdminCredentials().put(restDriver.projectUrl(new Project(project.getAliases().get(0)))).then().assertThat().statusCode(403);
    }

    @Test
    public void testAliasAsTitle() {
        mainAdminCredentials().given().queryParam("name", project.getAliases().get(0).toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

    @Test
    public void testAliasAsSecondaryId() {
        mainAdminCredentials().given().queryParam("secondary_ID", project.getAliases().get(0).toUpperCase()).put(restDriver.projectUrl(new Project())).then().assertThat().statusCode(403);
    }

}
