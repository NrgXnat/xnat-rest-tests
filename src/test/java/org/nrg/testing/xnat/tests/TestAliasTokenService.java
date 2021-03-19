package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.XnatConnectionConfig;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.rest.PermissionsException;
import org.nrg.xnat.versions.Xnat_1_7_4;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.rest.XnatAliasToken;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.jayway.restassured.RestAssured.given;
import static org.testng.AssertJUnit.*;

@TestRequires(users = 1)
public class TestAliasTokenService extends BaseXnatRestTest {

    private User otherUser;
    private final Project project = new Project().accessibility(Accessibility.PRIVATE);
    private final Project otherUserProject = new Project().accessibility(Accessibility.PRIVATE);

    @BeforeClass
    public void addPrivateProject() {
        otherUser = getGenericUser();
        mainInterface().createProject(project);
        interfaceFor(otherUser).createProject(otherUserProject);
    }

    @AfterClass(alwaysRun = true)
    public void revertState() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
        restDriver.deleteProjectSilently(mainAdminUser, otherUserProject);
        mainAdminInterface().closeXnat();
    }

    @TestRequires(closedXnat = true)
    @Test
    public void testSelfAliasTokenClosedXnat() {
        selfAliasTokenTest();
    }

    @TestRequires(openXnat = true)
    @Test
    public void testSelfAliasTokenOpenXnat() {
        selfAliasTokenTest();
    }

    @TestRequires(closedXnat = true)
    @Test
    public void testProxyAliasTokenClosedXnat() {
        proxyAliasTokenTest(false);
    }

    @TestRequires(openXnat = true)
    @Test
    public void testProxyAliasTokenOpenXnat() {
        proxyAliasTokenTest(true);
    }

    @TestRequires(closedXnat = true)
    @Test
    public void testAliasTokenValidationClosedXnat() {
        validationAliasTokenTest();
    }

    @TestRequires(openXnat = true)
    @Test
    public void testAliasTokenValidationOpenXnat() {
        validationAliasTokenTest();
    }

    private void checkAliasTokenForMainUser(XnatAliasToken aliasToken) {
        checkAliasToken(aliasToken, 200, 404);
    }

    private void checkAliasTokenForAdmin(XnatAliasToken aliasToken) {
        checkAliasToken(aliasToken, 200, 200);
    }

    private void checkAliasToken(XnatAliasToken aliasToken, int mainUserProjectStatusCode, int otherUserProjectStatusCode) {
        final XnatInterface aliasTokenAuth = authFromToken(aliasToken);
        aliasTokenAuth.queryBase().get(aliasTokenAuth.projectUrl(project)).then().assertThat().statusCode(mainUserProjectStatusCode);
        aliasTokenAuth.queryBase().get(aliasTokenAuth.projectUrl(otherUserProject)).then().assertThat().statusCode(otherUserProjectStatusCode);
    }

    private XnatAliasToken selfAliasTokenTest() {
        final XnatAliasToken aliasToken = mainInterface().generateAliasToken();
        checkAliasTokenForMainUser(aliasToken);
        return aliasToken;
    }

    private void proxyAliasTokenTest(boolean openXnat) {
        final XnatAliasToken selfProxyToken = mainAdminInterface().generateAliasToken(mainAdminUser); // only admins can do proxy token generation
        checkAliasTokenForAdmin(selfProxyToken);

        given().get(mainInterface().issueAliasTokenUrl(mainUser)).then().assertThat().statusCode(openXnat ? 403 : 401);
        try {
            final XnatInterface otherInterface = interfaceFor(otherUser);
            otherInterface.generateAliasToken(mainUser);
            fail("Proxy alias token generation as a non-admin should have failed");
        } catch (PermissionsException ignored) {}

        final XnatAliasToken proxyToken = mainAdminInterface().generateAliasToken(mainUser);
        checkAliasTokenForMainUser(proxyToken);
    }

    private void validationAliasTokenTest() {
        final XnatAliasToken aliasToken = selfAliasTokenTest();
        final XnatInterface tokenAuth = authFromToken(aliasToken);
        final XnatAliasToken bogusToken = new XnatAliasToken("1-2-3-4-5-6-7-8", "hidden secret number");
        for (XnatInterface xnatInterface : new XnatInterface[]{tokenAuth, mainInterface()}) {
            assertTrue(xnatInterface.validateAliasToken(aliasToken, mainUser.getUsername()));
            assertFalse(xnatInterface.validateAliasToken(bogusToken, mainUser.getUsername()));
        }
        checkAliasToken(bogusToken, 401, 401);

        tokenAuth.invalidateAliasToken(aliasToken);
        checkAliasToken(aliasToken, invalidatedTokenCode(), invalidatedTokenCode());
    }

    private int invalidatedTokenCode() {
        return XnatTestingVersionManager.testedVersionFollows(Xnat_1_7_4.class) ? 401 : 403;
    }

    private XnatInterface authFromToken(XnatAliasToken aliasToken) {
        final XnatConnectionConfig connectionConfig = new XnatConnectionConfig();
        connectionConfig.setVersionClass(Settings.XNAT_VERSION);
        connectionConfig.setSkipAuth(true);
        return XnatInterface.authenticate(Settings.BASEURL, aliasToken, connectionConfig);
    }

}
