package org.nrg.testing.xnat.tests.sessions;

import io.restassured.response.Response;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.Users;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.rest.XnatAliasToken;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.nrg.testing.TestGroups.AUTHENTICATION;
import static org.nrg.testing.TestGroups.SESSIONS;
import static org.testng.AssertJUnit.*;

/**
 * The ways into a session other than the login page and basic auth with a password. Each test checks that the path
 * ends with a working session for the right user, using bare session IDs ({@link RawSessions}).
 *
 * <p>{@code xnat.baseurl} must be a single node, not a load balancer in front of several.</p>
 */
@Test(groups = {AUTHENTICATION, SESSIONS})
public class TestSessionLoginPaths extends BaseXnatRestTest {

    private static final Pattern PAGE_CSRF_TOKEN = Pattern.compile("csrfToken\\s*=\\s*'([^']+)'");

    private final RawSessions node = new RawSessions(Settings.BASEURL);
    private       String      adminSession;

    @BeforeClass
    private void loginAdmin() {
        adminSession = node.loginWithBasicAuth(mainAdminUser);
    }

    @AfterClass(alwaysRun = true)
    private void logoutAdmin() {
        node.logoutQuietly(adminSession);
    }

    @Test
    @TestRequires(users = 1)
    public void testAliasTokenLoginStartsSessionForItsUser() {
        final User           user    = getGenericUser();
        final XnatAliasToken token   = mainAdminInterface().generateAliasToken(user);
        final String         session = node.loginWithBasicAuth(token.getAlias(), token.getSecret());

        node.assertLoggedInAs(session, user);
    }

    @Test
    @TestRequires(users = 1)
    public void testSudoSwitchesSessionToTargetUser() {
        final User   target  = getGenericUser();
        final String session = node.loginWithBasicAuth(mainAdminUser);
        try {
            // Without the CSRF token, the action does nothing.
            node.with(session).formParam("sudo_login", target.getUsername()).post(node.url("app/action/XDATSudoLogin"));
            node.assertLoggedInAs(session, mainAdminUser);

            final Response sudo = node.with(session)
                                      .formParam("sudo_login", target.getUsername())
                                      .formParam("XNAT_CSRF", node.csrfToken(session))
                                      .post(node.url("app/action/XDATSudoLogin"));
            assertEquals(200, sudo.statusCode());
            node.assertLoggedInAs(session, target);
        } finally {
            node.logoutQuietly(session);
        }
    }

    /**
     * The link in a forgot-password email carries an alias token for the user. Following it logs the user in, and the
     * session has to change the password before it can do anything else.
     */
    @Test
    @TestRequires(users = 1)
    public void testForgotPasswordLinkStartsSessionThatMustChangePassword() {
        final User           user  = getGenericUser();
        final XnatAliasToken token = mainAdminInterface().generateAliasToken(user);

        final Response page = node.anonymous()
                                  .queryParam("a", token.getAlias())
                                  .queryParam("s", token.getSecret())
                                  .get(node.url("app/template/XDATScreen_UpdateUser.vm"));
        assertEquals(200, page.statusCode());
        assertTrue("The page should be the change-password form for " + user.getUsername(),
                   page.asString().contains("name=\"xdat:user.login\" value=\"" + user.getUsername() + "\""));
        final String session = page.getSessionId();
        assertNotNull("Following the link should start a session", session);

        node.assertSentToChangePassword(session);
    }

    /**
     * With email verification off and new accounts enabled automatically, registering logs the new user in.
     */
    @Test
    public void testRegistrationLogsInNewUser() {
        final User   newUser                  = Users.genericAccount();
        final String originalUserRegistration = node.readSiteSetting(adminSession, "userRegistration");
        final String originalVerification     = node.readSiteSetting(adminSession, "emailVerification");
        node.writeSiteSetting(adminSession, "userRegistration", "true");
        node.writeSiteSetting(adminSession, "emailVerification", "false");
        try {
            final Response page = node.anonymous().get(node.url("app/template/Register.vm"));
            assertEquals(200, page.statusCode());
            final String session = page.getSessionId();
            assertNotNull("The registration page should start a session", session);
            final Matcher token = PAGE_CSRF_TOKEN.matcher(page.asString());
            assertTrue("No CSRF token on the registration page", token.find());

            final Response registration = node.with(session)
                                              .formParam("XNAT_CSRF", token.group(1))
                                              .formParam("xdat:user.login", newUser.getUsername())
                                              .formParam("xdat:user.primary_password", newUser.getPassword())
                                              .formParam("xdat:user.email", newUser.getEmail())
                                              .formParam("xdat:user.firstname", newUser.getFirstName())
                                              .formParam("xdat:user.lastname", newUser.getLastName())
                                              .post(node.url("app/action/XDATRegisterUser"));
            assertTrue("Registering " + newUser.getUsername() + " got HTTP " + registration.statusCode(), registration.statusCode() < 400);
            node.assertLoggedInAs(session, newUser);
        } finally {
            node.writeSiteSetting(adminSession, "userRegistration", originalUserRegistration);
            node.writeSiteSetting(adminSession, "emailVerification", originalVerification);
        }
    }

}
