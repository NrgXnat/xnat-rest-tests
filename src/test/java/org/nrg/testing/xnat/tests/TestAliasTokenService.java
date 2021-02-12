package org.nrg.testing.xnat.tests;

import com.jayway.restassured.response.Response;
import com.jayway.restassured.response.ValidatableResponse;
import org.hamcrest.Matchers;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.versions.XnatVersionList;
import org.nrg.testing.xnat.versions.Xnat_1_7_4;
import org.nrg.testing.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.rest.Credentials;
import org.nrg.xnat.rest.XnatAliasToken;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.nrg.xnat.rest.Credentials.build;
import static com.jayway.restassured.RestAssured.given;
import static org.testng.AssertJUnit.assertEquals;

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

    private Response proxyAliasTokenCall(User authUser, User targetUser) {
        return (authUser == null ? given() : build(authUser)).get(formatRestUrl("/services/tokens/issue/user/", targetUser.getUsername()));
    }

    private XnatAliasToken readTokenFromResponse(Response response) {
        if (XnatVersionList.testedVersionPrecedes(Xnat_1_8_0.class)) { // see: XNAT-5498
            return response.then().assertThat().statusCode(200).and().extract().jsonPath().getObject("", XnatAliasToken.class);
        } else {
            return response.then().assertThat().statusCode(200).and().extract().as(XnatAliasToken.class);
        }
    }

    private void checkAliasTokenForMainUser(XnatAliasToken aliasToken) {
        checkAliasToken(aliasToken, 200, 404);
    }

    private void checkAliasTokenForAdmin(XnatAliasToken aliasToken) {
        checkAliasToken(aliasToken, 200, 200);
    }

    private void checkAliasToken(XnatAliasToken aliasToken, int mainUserProjectStatusCode, int otherUserProjectStatusCode) {
        build(aliasToken).get(restDriver.projectUrl(project)).then().assertThat().statusCode(mainUserProjectStatusCode);
        build(aliasToken).get(restDriver.projectUrl(otherUserProject)).then().assertThat().statusCode(otherUserProjectStatusCode);
    }

    private XnatAliasToken selfAliasTokenTest() {
        final XnatAliasToken aliasToken = mainInterface().generateAliasToken();
        checkAliasTokenForMainUser(aliasToken);
        return aliasToken;
    }

    private void proxyAliasTokenTest(boolean openXnat) {
        final XnatAliasToken selfProxyToken = readTokenFromResponse(proxyAliasTokenCall(mainAdminUser, mainAdminUser)); // only admins can do proxy token generation
        checkAliasTokenForAdmin(selfProxyToken);

        proxyAliasTokenCall(null, mainUser).then().assertThat().statusCode(projectCodeBadToken(openXnat));
        proxyAliasTokenCall(otherUser, mainUser).then().assertThat().statusCode(403).and().body(Matchers.containsString("admin"));

        final XnatAliasToken proxyToken = readTokenFromResponse(proxyAliasTokenCall(mainAdminUser, mainUser));
        checkAliasTokenForMainUser(proxyToken);
    }

    private void validationAliasTokenTest() {
        final XnatAliasToken aliasToken = selfAliasTokenTest();
        assertEquals(mainUser.getUsername(), responseForValidate(aliasToken).then().assertThat().statusCode(200).and().extract().jsonPath().getString("valid"));

        final XnatAliasToken bogusToken = new XnatAliasToken("1-2-3-4-5-6-7-8", "hidden secret number");
        final ValidatableResponse response = responseForValidate(bogusToken).then().assertThat();
        if (XnatVersionList.testedVersionFollows(Xnat_1_7_4.class)) {
            response.statusCode(404);
        } else {
            response.statusCode(200).and().assertThat().body(Matchers.equalTo("{}"));
        }
        checkAliasToken(bogusToken, 401, 401);

        Credentials.build(aliasToken).get(formatRestUrl("/services/tokens/invalidate", aliasToken.getAlias(), aliasToken.getSecret())).then().assertThat().statusCode(200);
        checkAliasToken(aliasToken, invalidatedTokenCode(), invalidatedTokenCode());
    }

    private Response responseForValidate(XnatAliasToken aliasToken) {
        return Credentials.build(mainUser).get(formatRestUrl("services/tokens/validate", aliasToken.getAlias(), aliasToken.getSecret()));
    }

    private int projectCodeBadToken(boolean openXnat) {
        return openXnat ? 403 : 401;
    }

    private int invalidatedTokenCode() {
        return XnatVersionList.testedVersionFollows(Xnat_1_7_4.class) ? 401 : 403;
    }

}
