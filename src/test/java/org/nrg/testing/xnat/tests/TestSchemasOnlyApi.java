package org.nrg.testing.xnat.tests;

import io.restassured.http.Method;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.matchers.ValidSchemaMatcher;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.testng.annotations.Test;

public class TestSchemasOnlyApi extends BaseXnatRestTest {

    @Test
    @TestedApiSpec(method = Method.GET, url = {
            "/schemas/{namespace}/{schema}",
            "/schemas/{schema}",
            "/xapi/{namespace}/{schema}",
            "/xapi/{schema}"
    })
    @Basic
    public void testSchemasSchemaGet() {
        for (String schema : mainQueryBase().get(formatXapiUrl("schemas")).as(String[].class)) {
            mainQueryBase().get(formatXnatUrl("schemas", schema + ".xsd")).then().assertThat().statusCode(200).and().body(ValidSchemaMatcher.INSTANCE);
            mainQueryBase().get(formatXapiUrl(schema + ".xsd")).then().assertThat().statusCode(200).and().body(ValidSchemaMatcher.INSTANCE);
        }
    }

}
