package org.nrg.testing.example;

import org.nrg.xnat.subinterfaces.XnatFunctionalitySubinterface;

import java.util.Arrays;
import java.util.List;

public class SchemaOnlyExampleSubinterface extends XnatFunctionalitySubinterface {

    @Override
    public List<String> getHandledEndpoints() {
        return Arrays.asList(
                "/xapi/{namespace}/{schema}",
                "/xapi/{schema}"
        );
    }

    public String readSchemaFromSchemaOnlyApi(String schema) {
        return queryBase().get(formatXapiUrl(schema)).then().assertThat().statusCode(200).and().extract().asString();
    }

}
