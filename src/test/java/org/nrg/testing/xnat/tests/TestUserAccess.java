package org.nrg.testing.xnat.tests;

import com.jayway.restassured.response.Response;
import org.nrg.testing.util.TestNgUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.testng.annotations.Test;

public class TestUserAccess extends BaseXnatRestTest {

    @Test
    public void testValidAccess() {
        final Response response = Settings.mainCredentials().expect().statusCode(200).given().queryParam("format", "xml").get(restDriver.formatRestUrl("projects"));
        TestNgUtils.assertNonempty(response.asString());
    }

    @Test
    public void testInvalidAccess() {
        restDriver.invalidCredentials().expect().statusCode(401).given().queryParam("format", "xml").get(restDriver.formatRestUrl("projects"));
    }

}
