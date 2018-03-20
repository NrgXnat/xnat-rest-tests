package org.nrg.testing.xnat.tests;

import com.jayway.restassured.response.Response;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.file.FileIO;
import org.nrg.testing.util.TestNgUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.ProjectResource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.Test;

public class TestUserAccess extends BaseXnatRestTest {

    @Test
    public void testValidAccess() {
        final Response response = Settings.mainCredentials().expect().statusCode(200).given().queryParam("format", "xml").get(restDriver.formatRestUrl("projects"));
        TestNgUtils.assertNonempty(response.asString());
    }

    @Test
    public void testInvalidAccess() {
        restDriver.invalidCredentials().expect().statusCode(401).given().queryParam("format", "xml").get(restDriver.formatRestUrl("projects"));
    }

    @Test
    @TestRequires(users = 1)
    public void testNonExpiringUserRestCalls() {
        final User nonExpiringUser = getGenericUser();
        restDriver.assignUserToRoles(mainAdminUser, nonExpiringUser, "non_expiring");
        restDriver.createProject(nonExpiringUser, new Project().addResource(new ProjectResource().folder("test-resource").addResourceFile(new ResourceFile().extension(new SimpleResourceFileExtension(FileIO.getDataFile("louie.jpg"))))));
    }

}
