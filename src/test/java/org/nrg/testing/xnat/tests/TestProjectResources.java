package org.nrg.testing.xnat.tests;

import org.nrg.testing.LegacyComparison;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xdat.bean.XnatProjectdataBean;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.extensions.project.ProjectXMLPutExtension;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

public class TestProjectResources extends BaseXnatRestTest {

    private final List<Project> testProjects = new ArrayList<>();

    @AfterClass(alwaysRun = true)
    public void removeProjects() {
        for (Project project : testProjects) {
            restDriver.deleteProjectSilently(mainAdminUser, project);
        }
    }

    @Test
    public void testProjectList() {
        mainQueryBase().given().queryParam("format", "html").get(formatRestUrl("projects")).then().statusCode(200);
    }

    @Test
    public void testQueryCRUD() {
        final Project project = registerProject();
        final String alias = project.getId() + "_ALIAS";
        project.addAlias(alias);

        mainQueryBase().queryParam("format", "xml").queryParam("req_format", "qs").queryParam("alias", alias).
                put(mainInterface().projectUrl(project)).
                then().statusCode(200);

        mainQueryBase().queryParam("format", "xml").get(mainInterface().projectUrl(project)).then().statusCode(200);
        mainQueryBase().queryParam("format", "xml").get(mainInterface().projectUrl(new Project(alias))).then().statusCode(200);
    }

    @Test
    public void testProjectXmlCRUD() {
        final File original = getDataFile("test_project_v1.xml");
        final File updated  = getDataFile("test_project_v2.xml");

        final Project project = registerProject().extension(new ProjectXMLPutExtension(original));

        mainInterface().createProject(project);
        compare(project, original);
        mainInterface().createProject(project.extension(new ProjectXMLPutExtension(updated))); // update project
        TimeUtils.sleep(1000); // cache update
        compare(project, updated);
        mainInterface().createProject(project); // resubmit (no change)
        TimeUtils.sleep(1000); // cache update
        compare(project, updated);
        mainInterface().deleteProject(project);
        TimeUtils.sleep(1000); // cache update
        mainQueryBase().queryParam("format", "xml").get(mainInterface().projectUrl(project)).then().statusCode(404); // confirm deleted
    }

    private Project registerProject() {
        final Project project = new Project();
        testProjects.add(project);
        return project;
    }

    private void compare(Project project, File expectedProjectXml) {
        final File actualXml = restDriver.saveBinaryResponseToFile(mainQueryBase().queryParam("format", "xml").get(mainInterface().projectUrl(project)));

        LegacyComparison.compareBeanXML(actualXml, expectedProjectXml,
                Collections.singletonMap(XnatProjectdataBean.class, Arrays.asList("ID", "studyProtocol", "secondary_ID", "active")));
    }

}
