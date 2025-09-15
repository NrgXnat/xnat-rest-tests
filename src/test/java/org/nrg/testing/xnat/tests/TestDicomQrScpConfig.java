package org.nrg.testing.xnat.tests;

import io.restassured.http.Method;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.xnat.versions.Xnat_1_10_0;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;

import static org.hamcrest.Matchers.equalTo;
import static org.nrg.testing.TestGroups.*;

@Test(groups = {DICOM_SCP})
@TestRequires(admin = true)
@AddedIn(Xnat_1_10_0.class)
public class TestDicomQrScpConfig extends BaseXnatRestTest {
    private String allowValue = null;
    private String enableValue = null;

    @BeforeClass
    public void retrieveValues() {
        final String allowUrl = formatXapiUrl("dicom-qr-scp/site/allow");
        allowValue = mainQueryBase().get(allowUrl).then()
                .extract().asString();

        final String enableUrl = formatXapiUrl("dicom-qr-scp/site/enableSitewide");
        enableValue = mainQueryBase().get(enableUrl).then()
                .extract().asString();
    }

    @AfterClass
    public void restoreValues() {
        if (null != allowValue) {
            final String setAllowUrl = formatXapiUrl("dicom-qr-scp/site/allow/" + allowValue);
            mainAdminQueryBase().put(setAllowUrl).then();
        }
        
        if (null != enableValue) {
            final String setEnableUrl = formatXapiUrl("dicom-qr-scp/site/enableSitewide/" + enableValue);
            mainAdminQueryBase().put(setEnableUrl).then();
        }
    }

    @Test
    @TestedApiSpec(method = Method.GET, url="/xapi/dicom-qr-scp/site/allow")
    public void testGetAllowExternalDicomQueries() {
        final String url = formatXapiUrl("dicom-qr-scp/site/allow");
        mainQueryBase().get(url).then().assertThat().statusCode(200);
    }

    @Test
    @TestedApiSpec(method = Method.PUT, url="/xapi/dicom-qr-scp/site/allow")
    public void testPutAllowExternalDicomQueries() {
        final String setTrueUrl = formatXapiUrl("dicom-qr-scp/site/allow/true");
        mainAdminQueryBase().put(setTrueUrl).then()
                .assertThat().statusCode(200);
        final String getUrl = formatXapiUrl("dicom-qr-scp/site/allow");
        mainQueryBase().get(getUrl).then()
                .body(equalTo("true"));

        final String setFalseUrl = formatXapiUrl("dicom-qr-scp/site/allow/false");
        mainQueryBase().put(setFalseUrl).then()
                .assertThat().statusCode(403);
        mainAdminQueryBase().put(setFalseUrl).then()
                .assertThat().statusCode(200);
        mainQueryBase().get(getUrl).then()
                .body(equalTo("false"));
    }

    @Test
    @TestedApiSpec(method = Method.GET, url="/xapi/dicom-qr-scp/site/enableSitewide")
    public void testGetEnableSitewideDicomQueries() {
        final String url = formatXapiUrl("dicom-qr-scp/site/enableSitewide");
        mainQueryBase().get(url).then().assertThat().statusCode(200);
    }

    @Test
    @TestedApiSpec(method = Method.PUT, url="/xapi/dicom-qr-scp/site/enableSitewide")
    public void testPutEnableSitewideDicomQueries() {
        final String setTrueUrl = formatXapiUrl("dicom-qr-scp/site/enableSitewide/true");
        mainAdminQueryBase().put(setTrueUrl).then()
                .assertThat().statusCode(200);
        final String getUrl = formatXapiUrl("dicom-qr-scp/site/enableSitewide");
        mainQueryBase().get(getUrl).then()
                .body(equalTo("true"));

        final String setFalseUrl = formatXapiUrl("dicom-qr-scp/site/enableSitewide/false");
        mainQueryBase().put(setFalseUrl).then()
                .assertThat().statusCode(403);
        mainAdminQueryBase().put(setFalseUrl).then()
                .assertThat().statusCode(200);
        mainQueryBase().get(getUrl).then()
                .body(equalTo("false"));
    }
}
