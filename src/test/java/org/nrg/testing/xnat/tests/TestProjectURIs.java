package org.nrg.testing.xnat.tests;

import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.ProjectResource;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.testng.annotations.Test;

import static org.nrg.testing.TestGroups.PREARCHIVE;
import static org.nrg.testing.TestGroups.RESOURCES;

public class TestProjectURIs extends BaseXnatRestTest {

    @Test(groups = PREARCHIVE)
    public void testPrearchiveConfig() {
        try {
            mainInterface().createProject(testSpecificProject);

            for (int code : new int[]{4, 0, 11}) {
                mainCredentials().expect().statusCode(200).when().put(formatRestUrl("projects", testSpecificProject.getId(), "prearchive_code", Integer.toString(code)));
            }
        } catch (Exception | Error throwable) {
            restDriver.deleteProjectSilently(mainUser, testSpecificProject);
            throw throwable;
        }
    }

    @Test(groups = RESOURCES)
    public void testProjectDeleteCrossover() {
        final Resource resource1 = new ProjectResource().folder("TESTING").addResourceFile(new ResourceFile().extension(new SimpleResourceFileExtension(getDataFile("louie.jpg"))));
        final Resource resource2 = new ProjectResource().folder("TESTING2").addResourceFile(new ResourceFile().extension(new SimpleResourceFileExtension(getDataFile("louie.jpg"))));
        Project projDelCross1 = new Project().addResource(resource1);
        Project projDelCross2 = new Project().addResource(resource2);

        try {
            mainInterface().createProject(projDelCross1);
            mainInterface().createProject(projDelCross2);

            restDriver.validateResource(mainUser, resource1);
            mainInterface().deleteProject(projDelCross2);
            restDriver.validateResource(mainUser, resource1);
        } catch (Exception | Error throwable) {
            restDriver.deleteProjectSilently(mainUser, projDelCross1);
            restDriver.deleteProjectSilently(mainUser, projDelCross2);
            throw throwable;
        }
    }

}
