package org.nrg.testing.xnat.tests;

import com.jayway.restassured.internal.http.Method;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.matchers.ValidSchemaMatcher;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.testng.annotations.Test;

public class TestSchemasOnlyApi extends BaseXnatRestTest {

    @Test
    @TestedApiSpec(method = Method.GET, url = {
            "/xapi/schemas/{namespace}/{schema}",
            "/xapi/schemas/{schema}"
    })
    public void testSchemasSchemaGet() {
        for (String schema : mainCredentials().get(formatXapiUrl("schemas")).as(String[].class)) {
            mainCredentials().get(formatXnatUrl("schemas", schema + ".xsd")).then().assertThat().statusCode(200).and().body(ValidSchemaMatcher.INSTANCE);
        }
    }

}
