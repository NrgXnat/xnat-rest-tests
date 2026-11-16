package org.nrg.testing.xnat.tests.sessions;

import io.restassured.response.Response;
import org.nrg.testing.TestNgUtils;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.conf.XNATProperties;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.nrg.testing.TestGroups.AUTHENTICATION;
import static org.nrg.testing.TestGroups.SESSIONS;
import static org.testng.AssertJUnit.*;

/**
 * Sessions on an XNAT with more than one node, where any request can reach any node.
 *
 * <p>These run only when {@value #NODE_B_BASEURL} names a second node of the XNAT that {@code xnat.baseurl} points to:
 * the same database and archive, reached directly rather than through a load balancer. Otherwise they're skipped.
 * {@code compose/two-node} starts such an XNAT.</p>
 *
 * <p>Tests marked {@link ExpectedFailure} describe what a login should do on such an XNAT but doesn't yet. They're
 * skipped or ignored according to {@code xnat.testBehavior.expectedFailures}; set it to {@code run} to see them
 * fail.</p>
 */
@Test(groups = {AUTHENTICATION, SESSIONS})
public class TestSessionsCrossNode extends BaseXnatRestTest {

    public static final  String NODE_B_BASEURL  = "xnat.nodeB.baseurl";
    private static final String TRACKING_ISSUE  = "NrgXnat/xnat#165";

    private final RawSessions nodeA = new RawSessions(Settings.BASEURL);
    private       RawSessions nodeB;
    private       String      adminSessionA;
    private       String      adminSessionB;

    @BeforeClass
    private void connectToNodeB() {
        final String nodeBUrl = new XNATProperties().getStringProperty(NODE_B_BASEURL, null);
        TestNgUtils.assumeTrue(nodeBUrl != null, "Property " + NODE_B_BASEURL + " must name a second node for tests in " + getClass().getSimpleName());
        nodeB = new RawSessions(nodeBUrl);
        assertFalse(NODE_B_BASEURL + " should be a different node from xnat.baseurl", nodeA.baseUrl().equals(nodeB.baseUrl()));

        adminSessionA = nodeA.loginWithBasicAuth(mainAdminUser);
        adminSessionB = nodeB.loginWithBasicAuth(mainAdminUser);
    }

    @AfterClass(alwaysRun = true)
    private void logoutAdmin() {
        nodeA.logoutQuietly(adminSessionA);
        if (nodeB != null) {
            nodeB.logoutQuietly(adminSessionB);
        }
    }

    @Test
    @ExpectedFailure(jiraIssue = TRACKING_ISSUE)
    public void testSessionFromNodeAWorksOnNodeB() {
        final User   user    = newUser();
        final String session = nodeA.loginWithBasicAuth(user);
        nodeA.assertLoggedInAs(session, user);

        nodeB.assertLoggedInAs(session, user);
    }

    @Test
    @ExpectedFailure(jiraIssue = TRACKING_ISSUE)
    public void testCsrfTokenFromNodeAWorksOnNodeB() {
        final User    user    = newUser();
        final String  session = nodeA.loginWithBasicAuth(user);
        final String  token   = nodeA.csrfToken(session);
        final Project project = registerTempProject();

        final Response response = nodeB.with(session)
                                       .queryParam("XNAT_CSRF", token)
                                       .header("User-Agent", RawSessions.BROWSER_USER_AGENT)
                                       .put(nodeB.url("data/projects/" + project.getId()));
        assertEquals("Creating project " + project.getId() + " on node B with node A's session and CSRF token", 200, response.statusCode());
    }

    /**
     * This passes today only because node B doesn't know node A's sessions at all. It's here so that a session that
     * works on every node can't outlive a logout on one of them.
     */
    @Test
    public void testLogoutOnNodeAEndsSessionOnNodeB() {
        final User   user    = newUser();
        final String session = nodeA.loginWithBasicAuth(user);
        nodeA.logout(session);

        nodeA.assertRejected(session);
        nodeB.assertRejected(session);
    }

    @Test
    @ExpectedFailure(jiraIssue = TRACKING_ISSUE)
    public void testNodeBListsSessionsFromNodeA() {
        final User   user    = newUser();
        final String session = nodeA.loginWithBasicAuth(user);

        assertTrue("Node B should list " + user.getUsername() + "'s session from node A", nodeB.activeSessions(adminSessionB, user.getUsername()).contains(session));
    }

    @Test
    @ExpectedFailure(jiraIssue = TRACKING_ISSUE)
    public void testEndingSessionsOnNodeAEndsThemOnNodeB() {
        final User   user    = newUser();
        final String session = nodeB.loginWithBasicAuth(user);
        nodeB.assertLoggedInAs(session, user);

        nodeA.endSessions(adminSessionA, user.getUsername());
        nodeB.assertRejected(session);
    }

    /**
     * Creates the user here, rather than with {@code @TestRequires(users = ...)}, so that nothing is created when there
     * is no second node and the tests are skipped.
     */
    private User newUser() {
        return createGenericUsers(1).get(0);
    }

}
