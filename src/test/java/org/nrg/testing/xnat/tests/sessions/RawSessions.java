package org.nrg.testing.xnat.tests.sessions;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xnat.pogo.users.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.AssertJUnit.*;

/**
 * Sends requests to one XNAT node with nothing but a session ID.
 *
 * <p>The clients from grxnat's {@code XnatInterface} log in again whenever XNAT rejects their session, so tests that
 * use them can't see a session being lost or ended. Nothing here logs in again, and no redirect is followed, so a
 * rejected session, or a redirect to the login page, is what the test sees.</p>
 */
class RawSessions {

    /**
     * XNAT checks the CSRF token on {@code /data} only for requests that look like they come from a browser. Tests of
     * that check send this {@code User-Agent}.
     */
    static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern CSRF_TOKEN       = Pattern.compile("XNAT_CSRF=([^;\\s]+)");
    private static final Pattern LOGIN_METHOD     = Pattern.compile("name=\"login_method\"[^>]*value=\"([^\"]+)\"");
    private static final String  DEFAULT_PROVIDER = "localdb";

    private final String baseUrl;

    RawSessions(final String baseUrl) {
        this.baseUrl = StringUtils.stripEnd(baseUrl, "/");
    }

    String baseUrl() {
        return baseUrl;
    }

    String url(final String path) {
        return baseUrl + "/" + StringUtils.stripStart(path, "/");
    }

    /**
     * @return A request with no credentials and no session, which doesn't follow redirects.
     */
    RequestSpecification anonymous() {
        return RestAssured.given().redirects().follow(false);
    }

    /**
     * @return A request that carries the session ID and nothing else.
     */
    RequestSpecification with(final String sessionId) {
        return anonymous().sessionId(sessionId);
    }

    /**
     * Logs in with basic auth on {@code /data/auth}.
     *
     * @return The response, which sets the new session's cookies.
     */
    Response basicAuthLogin(final String username, final String password) {
        final Response response = anonymous().auth().preemptive().basic(username, password).get(url("data/auth"));
        assertEquals("Basic-auth login as " + username + " on " + baseUrl, 200, response.statusCode());
        assertNotNull("No session ID from basic-auth login as " + username + " on " + baseUrl, response.getSessionId());
        return response;
    }

    /**
     * Logs in with basic auth on {@code /data/auth}.
     *
     * @return The new session's ID.
     */
    String loginWithBasicAuth(final String username, final String password) {
        return basicAuthLogin(username, password).getSessionId();
    }

    String loginWithBasicAuth(final User user) {
        return loginWithBasicAuth(user.getUsername(), user.getPassword());
    }

    /**
     * Logs in the way the login page does: it loads the page, which starts an anonymous session, and then posts the
     * form to {@code /login} with that session.
     */
    FormLogin loginWithForm(final String username, final String password) {
        final Response page = anonymous().get(url("app/template/Login.vm"));
        assertEquals("Login page on " + baseUrl, 200, page.statusCode());
        final String preLoginSessionId = page.getSessionId();
        assertNotNull("Loading the login page on " + baseUrl + " didn't start a session", preLoginSessionId);

        final Matcher loginMethod = LOGIN_METHOD.matcher(page.asString());
        final Response response = with(preLoginSessionId)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("login_method", loginMethod.find() ? loginMethod.group(1) : DEFAULT_PROVIDER)
                .post(url("login"));
        assertEquals("Form login as " + username + " on " + baseUrl, 302, response.statusCode());
        return new FormLogin(preLoginSessionId, response.getSessionId(), response.getHeader("Location"));
    }

    /**
     * @return The CSRF token XNAT keeps in the session.
     */
    String csrfToken(final String sessionId) {
        final Response response = with(sessionId).queryParam("CSRF", true).get(url("data/auth"));
        assertEquals("Reading the CSRF token on " + baseUrl, 200, response.statusCode());
        final Matcher token = CSRF_TOKEN.matcher(response.asString());
        assertTrue("No CSRF token in: " + response.asString(), token.find());
        return token.group(1);
    }

    /**
     * Fails unless the session is logged in as the user.
     */
    void assertLoggedInAs(final String sessionId, final User user) {
        assertLoggedInAs(sessionId, user.getUsername());
    }

    void assertLoggedInAs(final String sessionId, final String username) {
        final Response response = whoAmI(sessionId);
        assertEquals("Session " + sessionId + " on " + baseUrl + " should be logged in as " + username + ", but got " + describe(response), 200, response.statusCode());
        assertEquals("Session " + sessionId + " on " + baseUrl, username, response.asString().trim());
    }

    /**
     * Fails unless XNAT treats a request with the session as not logged in: a 401, a redirect to the login page, or the
     * guest user on a site that doesn't require a login.
     */
    void assertRejected(final String sessionId) {
        final Response response = whoAmI(sessionId);
        final boolean rejected = response.statusCode() == 401
                                 || (response.statusCode() == 302 && isLoginPage(response.getHeader("Location")))
                                 || (response.statusCode() == 200 && "guest".equals(response.asString().trim()));
        assertTrue("Session " + sessionId + " on " + baseUrl + " should be rejected, but got " + describe(response), rejected);
    }

    /**
     * Fails unless every request with the session is sent to change the user's password first.
     */
    void assertSentToChangePassword(final String sessionId) {
        final Response response = whoAmI(sessionId);
        assertEquals("Session " + sessionId + " on " + baseUrl + " should be sent to change its password, but got " + describe(response), 302, response.statusCode());
        assertTrue("Session " + sessionId + " on " + baseUrl + " was redirected to " + response.getHeader("Location"),
                   StringUtils.contains(response.getHeader("Location"), "XDATScreen_UpdateUser.vm"));
    }

    /**
     * Logs out with {@code DELETE /data/JSESSION}.
     */
    void logout(final String sessionId) {
        final Response response = with(sessionId).delete(url("data/JSESSION"));
        assertEquals("Logging out session " + sessionId + " on " + baseUrl, 200, response.statusCode());
    }

    void logoutQuietly(final String sessionId) {
        if (sessionId != null) {
            try {
                with(sessionId).delete(url("data/JSESSION"));
            } catch (Exception ignored) {
                // Cleanup only.
            }
        }
    }

    /**
     * @return The sessions this node lists for the user, from {@code GET /xapi/users/active/{username}}.
     */
    List<String> activeSessions(final String adminSessionId, final String username) {
        final Response response = with(adminSessionId).get(url("xapi/users/active/" + username));
        if (response.statusCode() == 304) {
            return Collections.emptyList(); // XNAT's answer when the user has no sessions
        }
        assertEquals("Listing " + username + "'s sessions on " + baseUrl, 200, response.statusCode());
        return new ArrayList<>(Arrays.asList(response.as(String[].class)));
    }

    /**
     * Ends all of the user's sessions this node knows about, with {@code DELETE /xapi/users/active/{username}}.
     *
     * @return The sessions it ended. XNAT answers 404 when the node has none of the user's sessions, which is an empty
     *         list here.
     */
    List<String> endSessions(final String adminSessionId, final String username) {
        final Response response = with(adminSessionId).delete(url("xapi/users/active/" + username));
        if (response.statusCode() == 404) {
            return Collections.emptyList();
        }
        assertEquals("Ending " + username + "'s sessions on " + baseUrl, 200, response.statusCode());
        return new ArrayList<>(Arrays.asList(response.as(String[].class)));
    }

    String readSiteSetting(final String adminSessionId, final String setting) {
        final Response response = with(adminSessionId).get(url("xapi/siteConfig/" + setting));
        assertEquals("Reading site setting " + setting + " on " + baseUrl, 200, response.statusCode());
        return response.asString();
    }

    void writeSiteSetting(final String adminSessionId, final String setting, final String value) {
        final Response response = with(adminSessionId).contentType("text/plain").body(value).post(url("xapi/siteConfig/" + setting));
        assertEquals("Setting site setting " + setting + " to '" + value + "' on " + baseUrl, 200, response.statusCode());
    }

    static boolean isLoginPage(final String location) {
        return StringUtils.containsAny(location, "Login.vm", "/login");
    }

    private Response whoAmI(final String sessionId) {
        return with(sessionId).get(url("xapi/users/username"));
    }

    private static String describe(final Response response) {
        final String location = response.getHeader("Location");
        return "HTTP " + response.statusCode() + (location != null ? " to " + location : "") + ": " + StringUtils.abbreviate(StringUtils.normalizeSpace(response.asString()), 200);
    }

    /**
     * What a form login returns: the session the login page started, the session the login sets, if it sets one, and
     * where it redirects.
     */
    static final class FormLogin {
        final String preLoginSessionId;
        final String sessionId;
        final String location;

        FormLogin(final String preLoginSessionId, final String sessionId, final String location) {
            this.preLoginSessionId = preLoginSessionId;
            this.sessionId = sessionId;
            this.location = location;
        }
    }

}
