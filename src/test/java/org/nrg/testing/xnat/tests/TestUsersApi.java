package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.rest.PermissionsException;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.AssertJUnit.*;

public class TestUsersApi extends BaseXnatRestTest {

    @Test
    @TestRequires(users = 1, admin = true)
    public void testRestletUsersCall() {
        final User genericUser = getGenericUser();
        final XnatInterface nonadminAuth = interfaceFor(genericUser);

        mainAdminInterface().setSiteUserListRestriction(false);
        verifyUserPresent(mainAdminInterface().readSiteUsers(), genericUser.getUsername());
        verifyUserPresent(nonadminAuth.readSiteUsers(), genericUser.getUsername());

        mainAdminInterface().setSiteUserListRestriction(true);
        try {
            nonadminAuth.readSiteUsers();
            fail("Attempt to read site user list as nonadmin should have failed with strict security on.");
        } catch (PermissionsException ignored) {}
        verifyUserPresent(mainAdminInterface().readSiteUsers(), genericUser.getUsername());
    }

    private void verifyUserPresent(List<User> users, String expectedUsername) {
        final Optional<User> foundUser = users.stream().filter(user -> user.getUsername().equals(expectedUsername)).findFirst();
        assertTrue(foundUser.isPresent());
        assertEquals("Test", foundUser.get().getFirstName());
    }

}
