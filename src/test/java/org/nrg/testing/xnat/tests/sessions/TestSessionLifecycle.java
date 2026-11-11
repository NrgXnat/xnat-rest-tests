package org.nrg.testing.xnat.tests.sessions;

import io.restassured.response.Response;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.nrg.testing.TestGroups.AUTHENTICATION;
import static org.nrg.testing.TestGroups.SESSIONS;
import static org.testng.AssertJUnit.*;

/**
 * How a session starts, is used and ends on one XNAT node. These use bare session IDs ({@link RawSessions}), so a
 * session that XNAT rejects fails the test instead of being replaced by a new login.
 *
 * <p>{@code xnat.baseurl} must be a single node, not a load balancer in front of several.</p>
 */
@Test(groups = {AUTHENTICATION, SESSIONS})
public class TestSessionLifecycle extends BaseXnatRestTest {

    private static final long SHORT_TIMEOUT_SECONDS = 60;
    private static final long TIMEOUT_WAIT_SECONDS  = 75;

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
    public void testBasicAuthLoginStartsSession() {
        final User   user    = getGenericUser();
        final String session = node.loginWithBasicAuth(user);

        node.assertLoggedInAs(session, user);
        final Response jsession = node.with(session).get(node.url("data/JSESSION"));
        assertEquals(200, jsession.statusCode());
        assertEquals("GET /data/JSESSION should return the session's own ID", session, jsession.asString().trim());
    }

    @Test
    @TestRequires(users = 1)
    public void testFormLoginStartsNewSession() {
        final User                  user  = getGenericUser();
        final RawSessions.FormLogin login = node.loginWithForm(user.getUsername(), user.getPassword());

        assertFalse("A successful login shouldn't go back to the login page, but went to " + login.location, RawSessions.isLoginPage(login.location));
        assertNotNull("A form login should set a session cookie", login.sessionId);
        assertFalse("A form login should start a new session, not keep the login page's", login.sessionId.equals(login.preLoginSessionId));
        node.assertLoggedInAs(login.sessionId, user);
        node.assertRejected(login.preLoginSessionId);
    }

    @Test
    @TestRequires(users = 1)
    public void testFormLoginWithWrongPasswordIsRefused() {
        final User                  user  = getGenericUser();
        final RawSessions.FormLogin login = node.loginWithForm(user.getUsername(), user.getPassword() + "-wrong");

        assertTrue("A failed login should go back to the login page, but went to " + login.location, RawSessions.isLoginPage(login.location));
        node.assertRejected(login.sessionId != null ? login.sessionId : login.preLoginSessionId);
    }

    @Test
    @TestRequires(users = 1)
    public void testLogoutEndsSession() {
        final User   user    = getGenericUser();
        final String session = node.loginWithBasicAuth(user);
        node.assertLoggedInAs(session, user);

        node.logout(session);
        node.assertRejected(session);
    }

    @Test
    @TestRequires(users = 1)
    public void testUiLogoutEndsSession() {
        final User   user    = getGenericUser();
        final String session = node.loginWithBasicAuth(user);
        node.assertLoggedInAs(session, user);

        final Response logout = node.with(session).get(node.url("app/action/LogoutUser"));
        assertEquals(302, logout.statusCode());
        assertTrue("Logging out should go to the login page, but went to " + logout.getHeader("Location"), RawSessions.isLoginPage(logout.getHeader("Location")));
        node.assertRejected(session);
    }

    @Test
    @TestRequires(users = 1)
    public void testActiveSessionsAreListedAndCanBeEnded() {
        final User   user       = getGenericUser();
        final String first      = node.loginWithBasicAuth(user);
        final String second     = node.loginWithBasicAuth(user);
        final String loggedOut  = node.loginWithBasicAuth(user);

        assertTrue(node.activeSessions(adminSession, user.getUsername()).containsAll(Arrays.asList(first, second, loggedOut)));

        node.logout(loggedOut);
        final List<String> afterLogout = node.activeSessions(adminSession, user.getUsername());
        assertFalse("A session that logged out shouldn't be listed", afterLogout.contains(loggedOut));
        assertTrue(afterLogout.containsAll(Arrays.asList(first, second)));

        final List<String> ended = node.endSessions(adminSession, user.getUsername());
        assertEquals(new HashSet<>(Arrays.asList(first, second)), new HashSet<>(ended));
        node.assertRejected(first);
        node.assertRejected(second);
        assertTrue(node.activeSessions(adminSession, user.getUsername()).isEmpty());
    }

    /**
     * XNAT checks the CSRF token on {@code /data} only for requests from a browser, so these requests send a browser's
     * {@code User-Agent}.
     */
    @Test
    @TestRequires(users = 1)
    public void testBrowserRequestNeedsSessionsCsrfToken() {
        final User   user          = getGenericUser();
        final String session       = node.loginWithBasicAuth(user);
        final String otherSession  = node.loginWithBasicAuth(user);
        final String token         = node.csrfToken(session);
        final String otherToken    = node.csrfToken(otherSession);
        assertFalse("Each session should have its own CSRF token", token.equals(otherToken));

        assertRefused(session, null, "no CSRF token");
        assertRefused(session, "not-the-token", "a wrong CSRF token");
        assertRefused(session, otherToken, "another session's CSRF token");

        final Project  project  = registerTempProject();
        final Response response = putProjectFromBrowser(session, token, project);
        assertEquals("Creating project " + project.getId() + " with the session's CSRF token", 200, response.statusCode());
        assertTrue(projectExists(project));
    }

    /**
     * Sets the session timeout to a minute, starts a session, and leaves it alone for longer than that. Any request with
     * the session resets its idle time, so the test doesn't touch it while it waits.
     */
    @Test
    @TestRequires(users = 1)
    public void testSessionTimeoutSettingIsHonoured() throws InterruptedException {
        final User   user     = getGenericUser();
        final String original = node.readSiteSetting(adminSession, "sessionTimeout");

        final long     before = System.currentTimeMillis();
        final Response login;
        node.writeSiteSetting(adminSession, "sessionTimeout", "1 minute");
        try {
            login = node.basicAuthLogin(user.getUsername(), user.getPassword());
        } finally {
            node.writeSiteSetting(adminSession, "sessionTimeout", original);
        }
        final long after = System.currentTimeMillis();

        final String expiration = login.getCookie("SESSION_EXPIRATION_TIME");
        assertNotNull("The login should set SESSION_EXPIRATION_TIME", expiration);
        final long expiresAt = Long.parseLong(expiration);
        assertTrue("SESSION_EXPIRATION_TIME should be about a minute after the login, but was " + (expiresAt - before) + " ms",
                   expiresAt >= before + TimeUnit.SECONDS.toMillis(SHORT_TIMEOUT_SECONDS) - 1000 && expiresAt <= after + TimeUnit.SECONDS.toMillis(SHORT_TIMEOUT_SECONDS) + 1000);

        // A session started after the setting is put back keeps the usual timeout, so it should outlast the wait.
        final String shortSession   = login.getSessionId();
        final String controlSession = node.loginWithBasicAuth(user);

        Thread.sleep(TimeUnit.SECONDS.toMillis(TIMEOUT_WAIT_SECONDS));

        node.assertRejected(shortSession);
        node.assertLoggedInAs(controlSession, user);
    }

    private Response putProjectFromBrowser(final String session, final String csrfToken, final Project project) {
        return (csrfToken == null ? node.with(session) : node.with(session).queryParam("XNAT_CSRF", csrfToken))
                .header("User-Agent", RawSessions.BROWSER_USER_AGENT)
                .put(node.url("data/projects/" + project.getId()));
    }

    private void assertRefused(final String session, final String csrfToken, final String what) {
        final Project  project  = registerTempProject();
        final Response response = putProjectFromBrowser(session, csrfToken, project);
        assertTrue("A browser request with " + what + " should be refused, but got HTTP " + response.statusCode(), response.statusCode() >= 400);
        assertFalse("A browser request with " + what + " shouldn't create project " + project.getId(), projectExists(project));
    }

    private boolean projectExists(final Project project) {
        final int status = node.with(adminSession).get(node.url("data/projects/" + project.getId())).statusCode();
        assertTrue("Unexpected status " + status + " looking for project " + project.getId(), status == 200 || status == 404);
        return status == 200;
    }

}
