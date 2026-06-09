package org.nrg.testing.xnat.tests;

import lombok.extern.slf4j.Slf4j;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_9_2;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import org.nrg.testing.annotations.MutatesServerState;

@Slf4j
@TestRequires(users = 1)
@AddedIn(Xnat_1_9_2.class)
@Test(groups = {PERMISSIONS})
@MutatesServerState
public class TestPrivilegedUserPermissions extends BaseXnatRestTest  {

    private XnatInterface privilegedUserInterface;
    private Project project;

    @BeforeClass
    public void setup() {
        final User privilegedUser = getGenericUser();
        mainAdminInterface().assignUserToRoles(privilegedUser,"Privileged");
        privilegedUserInterface = interfaceFor(privilegedUser);

        project = new Project();
        mainInterface().createProject(project);
    }

    @AfterClass(alwaysRun = true)
    public void deleteProject() {
        mainAdminInterface().deleteProject(project);
    }

    @Test
    public void testPrivilegedUserPermissions() {
        // Create command
        privilegedUserInterface
                .queryBase()
                .get(formatXapiUrl("/role/projects"))
                .then().assertThat().statusCode(200)
                .extract()
                .jsonPath()
                .getString("id[0]")
                .equals(project.getId());
    }

}
