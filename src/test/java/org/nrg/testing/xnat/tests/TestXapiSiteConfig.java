package org.nrg.testing.xnat.tests;

import io.restassured.RestAssured;
import io.restassured.http.Method;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.XnatConnectionConfig;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Uptime;
import org.nrg.xnat.rest.PermissionsException;
import org.nrg.xnat.versions.Xnat_1_8_4;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.*;

public class TestXapiSiteConfig extends BaseXnatRestTest {

    private static final String publicPreference = "siteId";
    private static final String authenticatedPreference = "sitewidePetTracers";
    private static final String adminPreference = "passwordComplexityMessage";
    private static final String customPreference = "samplePreferenceFromRestTests";
    private static final Map<String, Object> expectedSiteConfig = new HashMap<>();

    @BeforeMethod
    public void setSiteConfig() {
        expectedSiteConfig.put(publicPreference, "XNAT");
        expectedSiteConfig.put(authenticatedPreference, "PIB");
        expectedSiteConfig.put(adminPreference, "Custom complexity message");
        expectedSiteConfig.put(customPreference, "Added in REST tests");
        mainAdminInterface().postToSiteConfig(expectedSiteConfig);
    }

    @Test
    @TestRequires(openXnat = true)
    @AddedIn(Xnat_1_8_4.class)
    @TestedApiSpec(method = Method.GET, url = "/xapi/siteConfig")
    public void testGetSiteConfigOpenXnat() {
        testFullSiteConfig();
        validateSiteConfig(guestInterface().readSiteConfigAsMap(), false, false);
    }

    @Test
    @TestRequires(closedXnat = true)
    @AddedIn(Xnat_1_8_4.class)
    @TestedApiSpec(method = Method.GET, url = "/xapi/siteConfig")
    public void testGetSiteConfigClosedXnat() {
        testFullSiteConfig();
        RestAssured.given().get(formatXapiUrl("siteConfig")).then().assertThat().statusCode(401);
    }

    @Test
    @TestRequires(openXnat = true)
    @AddedIn(Xnat_1_8_4.class)
    @TestedApiSpec(method = Method.GET, url = "/xapi/siteConfig/{property}")
    public void testGetSiteConfigPropertyOpenXnat() {
        testGetSiteConfigByProperty();
    }

    @Test
    @TestRequires(closedXnat = true)
    @AddedIn(Xnat_1_8_4.class)
    @TestedApiSpec(method = Method.GET, url = "/xapi/siteConfig/{property}")
    public void testGetSiteConfigPropertyClosedXnat() {
        testGetSiteConfigByProperty();
    }

    @Test
    @TestRequires(openXnat = true)
    @AddedIn(Xnat_1_8_4.class)
    @TestedApiSpec(method = Method.POST, url = "/xapi/siteConfig")
    public void testPostSiteConfigMapOpenXnat() {
        testSiteConfigMapPost(true);
    }

    @Test
    @TestRequires(closedXnat = true)
    @AddedIn(Xnat_1_8_4.class)
    @TestedApiSpec(method = Method.POST, url = "/xapi/siteConfig")
    public void testPostSiteConfigMapClosedXnat() {
        testSiteConfigMapPost(false);
    }

    @Test
    @TestRequires(openXnat = true)
    @AddedIn(Xnat_1_8_4.class)
    @TestedApiSpec(method = Method.POST, url = "/xapi/siteConfig/{property}")
    public void testPostSiteConfigPropertyOpenXnat() {
        testSiteConfigPropertyPost(true);
    }

    @Test
    @TestRequires(closedXnat = true)
    @AddedIn(Xnat_1_8_4.class)
    @TestedApiSpec(method = Method.POST, url = "/xapi/siteConfig/{property}")
    public void testPostSiteConfigPropertyClosedXnat() {
        testSiteConfigPropertyPost(false);
    }

    @Test
    @TestedApiSpec(method = Method.GET, url = "/xapi/siteConfig/uptime")
    public void testUptime() {
        final Uptime firstUptime = mainInterface().readUptime();
        TimeUtils.sleep(5000);
        final Uptime secondUptime = mainInterface().readUptime();
        assertTrue(secondUptime.totalSeconds() - firstUptime.totalSeconds() > 0); // uptime should be monotone increasing
        assertTrue(Math.abs(secondUptime.totalSeconds() - firstUptime.totalSeconds() - 5) < 2); // uptimes are queried 5 seconds apart and should differ by about 5 seconds. Give it a little wiggle room to account for lag
    }

    private void testFullSiteConfig() {
        validateSiteConfig(mainAdminInterface().readSiteConfigAsMap(), true, true);
        validateSiteConfig(mainInterface().readSiteConfigAsMap(), true, false);
    }

    private void validateSiteConfig(Map<String, Object> siteConfig, boolean includeAuthenticated, boolean includeAdmin) {
        assertEquals(expectedSiteConfig.get(publicPreference), siteConfig.get(publicPreference));
        validatePreference(siteConfig, includeAuthenticated, authenticatedPreference);
        validatePreference(siteConfig, includeAdmin, adminPreference);
        validatePreference(siteConfig, includeAdmin, customPreference);
    }

    private void testGetSiteConfigByProperty() {
        validatePropertyGet(publicPreference, true, true);
        validatePropertyGet(authenticatedPreference, true, false);
        validatePropertyGet(adminPreference, false, false);
        validatePropertyGet(customPreference, false, false);
    }

    private void validatePropertyGet(String propertyName, boolean allowedForNonadmin, boolean allowedForAnon) {
        validatePreference(propertyName, mainAdminInterface(), true);
        validatePreference(propertyName, mainInterface(), allowedForNonadmin);
        validatePreference(propertyName, guestInterface(), allowedForAnon);
    }

    private void validatePreference(String preferenceName, XnatInterface xnatInterface, boolean readable) {
        if (readable) {
            assertEquals(expectedSiteConfig.get(preferenceName), xnatInterface.readSiteConfigPreference(preferenceName));
        } else {
            try {
                xnatInterface.readSiteConfigPreference(preferenceName);
                fail();
            } catch (PermissionsException ignored) {}
        }
    }

    private void validatePreference(Map<String, Object> retrievedSiteConfig, boolean expected, String preferenceName) {
        if (expected) {
            assertEquals(expectedSiteConfig.get(preferenceName), retrievedSiteConfig.get(preferenceName));
        } else {
            assertFalse(retrievedSiteConfig.containsKey(preferenceName));
        }
    }

    private void testSiteConfigMapPost(boolean openXnat) {
        final String newValue = "FDG";
        for (XnatInterface xnatInterface : Arrays.asList(mainInterface(), guestInterface())) {
            final Map<String, Object> cumulativePrefMap = new HashMap<>();
            for (Map.Entry<String, Object> expectedConfEntry : expectedSiteConfig.entrySet()) {
                cumulativePrefMap.put(expectedConfEntry.getKey(), expectedConfEntry.getValue());
                xnatInterface.disableAdminCheck();
                trySiteConfigPostExpectFailure(xnatInterface, Collections.singletonMap(expectedConfEntry.getKey(), "bad value"));
                trySiteConfigPostExpectFailure(xnatInterface, cumulativePrefMap);
            }
        }
        if (openXnat) {
            testGetSiteConfigPropertyOpenXnat();
        } else {
            testGetSiteConfigPropertyClosedXnat();
        }
        mainAdminInterface().postToSiteConfig(Collections.singletonMap(authenticatedPreference, newValue));
        assertEquals(newValue, mainAdminInterface().readSiteConfigPreference(authenticatedPreference));
    }

    private void trySiteConfigPostExpectFailure(XnatInterface xnatInterface, Map<String, Object> postedConfig) {
        try {
            xnatInterface.postToSiteConfig(postedConfig);
            fail("Should have been blocked");
        } catch (PermissionsException ignored) {}
    }

    private void testSiteConfigPropertyPost(boolean openXnat) {
        final String newValue = "FDG";
        for (XnatInterface xnatInterface : Arrays.asList(mainInterface(), guestInterface())) {
            for (Map.Entry<String, Object> expectedConfEntry : expectedSiteConfig.entrySet()) {
                xnatInterface.disableAdminCheck();
                try {
                    xnatInterface.postSiteConfigProperty(expectedConfEntry.getKey(), "badval");
                    fail("Should have been blocked");
                } catch (PermissionsException ignored) {}
            }
        }
        if (openXnat) {
            testGetSiteConfigPropertyOpenXnat();
        } else {
            testGetSiteConfigPropertyClosedXnat();
        }
        mainAdminInterface().postSiteConfigProperty(authenticatedPreference, newValue);
        assertEquals(newValue, mainAdminInterface().readSiteConfigPreference(authenticatedPreference));
    }

    private XnatInterface guestInterface() {
        final XnatConnectionConfig connectionConfig = new XnatConnectionConfig();
        connectionConfig.setSkipAuth(true);
        return XnatInterface.authenticateAsGuest(Settings.BASEURL, connectionConfig);
    }

}
