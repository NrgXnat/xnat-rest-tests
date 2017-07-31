package org.nrg.testing.xnat.tests;

import org.nrg.testing.xnat.BaseRestTest;
import org.nrg.xnat.pojo.Project;
import org.nrg.xnat.pojo.resources.ProjectResource;
import org.nrg.xnat.pojo.resources.Resource;
import org.nrg.xnat.pojo.resources.ResourceFile;
import org.testng.annotations.Test;

public class TestProjectURIs extends BaseRestTest {

    @Test
    public void testPrearchiveConfig() {
        try {
            restDriver.createProject(mainUser, testSpecificProject);

            for (int code : new int[]{4, 0, 11}) {
                mainCredentials().expect().statusCode(200).when().put(restDriver.formatRestUrl("projects", testSpecificProject.getId(), "prearchive_code", Integer.toString(code)));
            }
        } catch (Exception | Error throwable) {
            restDriver.deleteProjectSilently(mainUser, testSpecificProject);
            throw throwable;
        }
    }

    @Test
    public void testProjectDeleteCrossover() {
        final Resource resource1 = new ProjectResource().folder("TESTING").addResourceFile(new ResourceFile().name("louie.jpg"));
        final Resource resource2 = new ProjectResource().folder("TESTING2").addResourceFile(new ResourceFile().name("louie.jpg"));
        Project projDelCross1 = new Project().addResource(resource1);
        Project projDelCross2 = new Project().addResource(resource2);

        try {
            restDriver.createProject(mainUser, projDelCross1);
            restDriver.createProject(mainUser, projDelCross2);
            restDriver.validateResource(mainUser, resource1);
            restDriver.deleteProject(mainUser, projDelCross2);
            restDriver.validateResource(mainUser, resource1);
        } catch (Exception | Error throwable) {
            restDriver.deleteProjectSilently(mainUser, projDelCross1);
            restDriver.deleteProjectSilently(mainUser, projDelCross2);
            throw throwable;
        }
    }

}
