package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.ValidatableResponse;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.matchers.ValidSchemaMatcher;
import org.nrg.testing.annotations.DisallowXnatVersion;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.versions.Xnat_1_6dev;
import org.nrg.xnat.pogo.DataType;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.*;

@DisallowXnatVersion(disallowedVersions = Xnat_1_6dev.class)
public class TestXapiSchema extends BaseXnatRestTest {

    private static final int SCHEMA_COUNT_LOWER_BOUND = 10;
    private static final int DATATYPE_COUNT_LOWER_BOUND = 50;

    @Test
    @TestedApiSpec(method = Method.GET, url = "/xapi/schemas")
    public void testSchemas() {
        final ValidatableResponse schemas = mainCredentials().get(formatXapiUrl("schemas")).then().assertThat().statusCode(200).and();
        schemas.body("$", hasSize(greaterThan(SCHEMA_COUNT_LOWER_BOUND)));
        schemas.body("$", hasItems("xnat", "xdat", "assessments", "pipeline/workflow"));
    }

    @Test
    @TestedApiSpec(method = Method.GET, url = "/xapi/schemas/datatypes")
    public void testSchemasDatatypes() {
        final ValidatableResponse datatypesResponse = mainCredentials().get(formatXapiUrl("schemas/datatypes")).then().assertThat().statusCode(200).and();
        datatypesResponse.body("$", hasSize(greaterThan(DATATYPE_COUNT_LOWER_BOUND)));
        datatypesResponse.body("$", hasItems("arc:ArchiveSpecification", "arc:pipelineData", "prov:processStep", "val:protocolData", "val:protocolData_scan_check_comment", "wrk:workflowData", "xdat:element_access", "xdat:user", "xnat:imageScanData", "xnat:mrSessionData"));
    }

    @Test
    @TestedApiSpec(method = Method.POST, url = "/xapi/schemas/datatypes/elements")
    public void testSchemasDatatypesElements() {
        final ValidatableResponse response = mainCredentials().contentType(ContentType.JSON).
                body(Collections.singletonMap("dataTypes", Arrays.asList(DataType.MR_SESSION.getXsiType(), DataType.PET_SESSION.getXsiType()))).post(formatXapiUrl("schemas/datatypes/elements")).
                then().assertThat().statusCode(200).and();
        response.body("keySet().size()", is(2));
        for (String modality : Arrays.asList("MR", "PET")) {
            validateSessionDatatype(modality, response);
        }
    }

    @Test
    @TestedApiSpec(method = Method.GET, url ="/xapi/schemas/datatypes/elements/all")
    public void testSchemasDatatypesElementsAll() {
        final ValidatableResponse response = mainCredentials().get(formatXapiUrl("schemas/datatypes/elements/all")).then().assertThat().statusCode(200).and();
        response.body("keySet().size()", greaterThan(DATATYPE_COUNT_LOWER_BOUND));
        for (String modality : Arrays.asList("MR", "PET")) {
            validateSessionDatatype(modality, response);
        }
    }

    @Test
    @TestedApiSpec(method = Method.GET, url = "/schemas/datatypes/elements/{dataType}")
    public void testSchemasDatatypesElementsDatatype() {
        final ValidatableResponse response = mainCredentials().get(formatXapiUrl("schemas/datatypes/elements", DataType.MR_SESSION.getXsiType())).then().assertThat().statusCode(200).and();
        response.body("keySet().size()", is(1));
        validateSessionDatatype("MR", response);
    }

    @Test
    @TestedApiSpec(method = Method.POST, url = "/xapi/schemas/datatypes/names")
    public void testSchemasDatatypesNames() {
        final ValidatableResponse response = mainCredentials().formParam("dataTypes", DataType.MR_SESSION.getXsiType(), DataType.PET_SESSION.getXsiType()).
                post(formatXapiUrl("schemas/datatypes/names")).
                then().assertThat().statusCode(200).and();
        response.body("keySet().size()", is(2));
        for (DataType dataType : Arrays.asList(DataType.MR_SESSION, DataType.PET_SESSION)) {
            validateDatatypeName(dataType, response);
        }
    }

    @Test
    @TestedApiSpec(method = Method.GET, url = "/xapi/schemas/datatypes/names/all")
    public void testSchemasDatatypesNamesAll() {
        final ValidatableResponse response = mainCredentials().get(formatXapiUrl("schemas/datatypes/names/all")).then().assertThat().statusCode(200).and();
        response.body("keySet().size()", greaterThan(DATATYPE_COUNT_LOWER_BOUND));
        for (DataType dataType : Arrays.asList(DataType.MR_SESSION, DataType.PET_SESSION, DataType.CT_SESSION)) {
            validateDatatypeName(dataType, response);
        }
    }

    @Test
    @TestedApiSpec(method = Method.GET, url = "/xapi/schemas/datatypes/names/{dataType}")
    public void testSchemasDatatypesNamesDataType() {
        final ValidatableResponse response = mainCredentials().get(formatXapiUrl("schemas/datatypes/names", DataType.MR_SESSION.getXsiType())).then().assertThat().statusCode(200).and();
        response.body("keySet().size()", is(1));
        validateDatatypeName(DataType.MR_SESSION, response);
    }

    @Test
    @TestedApiSpec(method = Method.GET, url = {
            "/xapi/schemas/{namespace}/{schema}",
            "/xapi/schemas/{schema}"
    })
    public void testSchemasSchema() {
        final ValidatableResponse schemas = mainCredentials().get(formatXapiUrl("schemas")).then().assertThat().statusCode(200).and();
        schemas.body("$", hasSize(greaterThan(SCHEMA_COUNT_LOWER_BOUND)));
        for (String schema : schemas.extract().as(String[].class)) {
            mainCredentials().get(formatXapiUrl("schemas", schema)).then().assertThat().statusCode(200).and().body(ValidSchemaMatcher.INSTANCE);
        }
    }

    private void validateSessionDatatype(String modality, ValidatableResponse response) {
        final String rootPath = String.format("xnat_%sSessionData", modality.toLowerCase());
        response.body(rootPath + ".properName", is(modality.toUpperCase() + "Session"));
        response.body(rootPath + ".namespacePrefix", is("xnat"));
    }

    private void validateDatatypeName(DataType dataType, ValidatableResponse response) {
        final String rootPath = dataType.getXsiType().replace(":", "_");
        response.body(rootPath, hasSize(greaterThan(1)));
        response.body(rootPath, hasItems(dataType.getXsiType()));
    }

}
