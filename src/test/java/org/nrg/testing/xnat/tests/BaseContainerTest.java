package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_9_2;
import org.testng.annotations.BeforeClass;

import java.util.List;
import org.nrg.testing.annotations.MutatesServerState;

@TestRequires(users = 1, plugins = PluginRegistry.CS_PLUGIN_ID)
@MutatesServerState
public class BaseContainerTest extends BaseXnatRestTest {
    protected XnatInterface containerManagerInterface;

    @BeforeClass
    protected void baseSetupContainerServiceTestUser() {
        if (XnatTestingVersionManager.testedVersionPrecedes(Xnat_1_9_2.class)) {
            containerManagerInterface = mainAdminInterface();
        } else {
            List<User> users =  createGenericUsers(1);
            final User containerManagerUser = users.get(0);
            mainAdminInterface().assignUserToRoles(containerManagerUser, "ContainerManager", "Privileged");
            containerManagerInterface = interfaceFor(containerManagerUser);
        }
    }

}
