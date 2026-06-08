package org.nrg.testing.xnat.tests;

import io.restassured.RestAssured;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.versions.Xnat_1_10_0;
import org.testng.annotations.Test;

import static org.nrg.testing.TestGroups.AUTHENTICATION;

/**
 * Verifies the global {@code Referrer-Policy} response header configured in
 * {@code SecurityConfig}. The header writer lives in the Spring Security
 * filter chain, so it should be applied to all responses going through that
 * chain regardless of authentication outcome.
 */
@AddedIn(Xnat_1_10_0.class)
@Test(groups = AUTHENTICATION)
public class TestReferrerPolicy extends BaseXnatRestTest {

    private static final String HEADER = "Referrer-Policy";
    private static final String EXPECTED = "strict-origin-when-cross-origin";

    /**
     * The Referrer-Policy header is set on successful authenticated XAPI
     * responses.
     */
    @Test
    public void testReferrerPolicyOnAuthenticatedXapiResponse() {
        mainAdminInterface().queryBase()
                .get(formatXapiUrl("siteConfig"))
                .then().assertThat()
                .statusCode(200)
                .header(HEADER, EXPECTED);
    }

    /**
     * The Referrer-Policy header is set on 401 responses to unauthenticated
     * requests against secured XAPI endpoints. Guards against a regression
     * where someone reorders the Spring Security DSL and the header writer
     * ends up downstream of the auth check, so failed-auth responses lose
     * the header.
     */
    @TestRequires(closedXnat = true)
    @Test
    public void testReferrerPolicyOnUnauthenticatedXapiResponse() {
        RestAssured.given()
                .get(formatXapiUrl("siteConfig"))
                .then().assertThat()
                .statusCode(401)
                .header(HEADER, EXPECTED);
    }

    /**
     * The Referrer-Policy header is set on the public login page, which is
     * served as a Velocity template on a permitAll path. This catches the
     * case where the header writer is only applied to XAPI / Spring MVC
     * endpoints and not to the Turbine-served screens.
     */
    @Test
    public void testReferrerPolicyOnLoginPage() {
        RestAssured.given()
                .get(formatXnatUrl("app", "template", "Login.vm"))
                .then().assertThat()
                .statusCode(200)
                .header(HEADER, EXPECTED);
    }
}
