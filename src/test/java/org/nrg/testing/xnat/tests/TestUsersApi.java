package org.nrg.testing.xnat.tests;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.rest.PermissionsException;
import org.nrg.xnat.versions.Xnat_1_8_6;
import org.testng.annotations.Test;
import java.util.List;
import java.util.Optional;
import static org.nrg.testing.TestGroups.USERS;
import static org.testng.AssertJUnit.*;
import org.nrg.testing.annotations.MutatesServerState;

@Test(groups = USERS)
@MutatesServerState
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
        expect403(nonadminAuth::readSiteUsers);
        verifyUserPresent(mainAdminInterface().readSiteUsers(), genericUser.getUsername());
    }
    @Test
    @AddedIn(Xnat_1_8_6.class)
    @TestRequires(users = 1, admin = true)
    public void testUserXml() {
        final User genericUser = getGenericUser();
        String userXml = mainAdminQueryBase().get(formatXnatUrl("/app/action/XDATActionRouter/xdataction/xml/search_element/xdat:user/search_field/xdat:user.login/search_value/", genericUser.getUsername(), "/popup/true")).getBody().prettyPrint();
        assertTrue(userXml.contains("xdat:firstname"));
        assertFalse(userXml.contains("xdat:primary_password"));
    }

    private void verifyUserPresent(List<User> users, String expectedUsername) {
        final Optional<User> foundUser = users.stream().filter(user -> user.getUsername().equals(expectedUsername)).findFirst();
        assertTrue(foundUser.isPresent());
        assertEquals("Test", foundUser.get().getFirstName());
    }

}
