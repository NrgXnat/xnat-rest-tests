package org.nrg.testing.xnat.tests;

import org.nrg.testing.LegacyComparison;
import org.nrg.testing.file.FileIO;
import org.nrg.testing.xnat.BaseRestTest;
import org.nrg.xdat.bean.XnatProjectdataBean;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.extensions.project.ProjectXMLPutExtension;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

public class TestProjectResources extends BaseRestTest {

    private final List<Project> testProjects = new ArrayList<>();

    @AfterClass(alwaysRun = true)
    public void removeProjects() {
        for (Project project : testProjects) {
            restDriver.deleteProjectSilently(mainAdminUser, project);
        }
    }

    @Test
    public void testProjectList() {
        mainCredentials().given().queryParam("format", "html").get(formatRestUrl("projects")).then().statusCode(200);
    }

    @Test
    public void testQueryCRUD() {
        final Project project = registerProject();
        final String alias = project.getId() + "_ALIAS";
        project.addAlias(alias);

        mainCredentials().given().queryParam("format", "xml").queryParam("req_format", "qs").queryParam("alias", alias).
                put(restDriver.projectUrl(project)).
                then().statusCode(200);

        mainCredentials().given().queryParam("format", "xml").get(restDriver.projectUrl(project)).then().statusCode(200);
        mainCredentials().given().queryParam("format", "xml").get(restDriver.projectUrl(new Project(alias))).then().statusCode(200);
    }

    @Test
    public void testProjectXmlCRUD() {
        final File original = FileIO.getDataFile("test_project_v1.xml");
        final File updated  = FileIO.getDataFile("test_project_v2.xml");

        final Project project = registerProject().extension(new ProjectXMLPutExtension(restDriver.interfaceFor(mainUser), original));

        restDriver.createProject(mainUser, project);
        compare(project, original);
        restDriver.createProject(mainUser, project.extension(new ProjectXMLPutExtension(restDriver.interfaceFor(mainUser), updated))); // update project
        compare(project, updated);
        restDriver.createProject(mainUser, project); // resubmit (no change)
        compare(project, updated);
        restDriver.deleteProject(mainUser, project);
        mainCredentials().given().queryParam("format", "xml").get(restDriver.projectUrl(project)).then().statusCode(404); // confirm deleted
    }

    private Project registerProject() {
        final Project project = new Project();
        testProjects.add(project);
        return project;
    }

    private void compare(Project project, File expectedProjectXml) {
        final File actualXml = restDriver.saveBinaryResponseToFile(mainCredentials().given().queryParam("format", "xml").get(restDriver.projectUrl(project)));

        LegacyComparison.compareBeanXML(actualXml, expectedProjectXml,
                Collections.<Class, List<String>>singletonMap(XnatProjectdataBean.class, Arrays.asList("ID", "studyProtocol", "secondary_ID")));
    }

}
