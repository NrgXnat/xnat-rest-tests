package org.nrg.testing.xnat.tests.data;

import io.restassured.response.Response;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.versions.Xnat_1_10_0;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.http.Method.DELETE;
import static io.restassured.http.Method.GET;
import static io.restassured.http.Method.POST;
import static org.hamcrest.Matchers.*;
import static org.testng.AssertJUnit.*;

/**
 * Tests for the DBBackedSchemaApi (/xapi/dbschemas endpoints)
 * Tests database-backed schema listing, loading, downloading, and deletion functionality.
 *
 * Note: These endpoints require Admin access and interact with database-backed schemas.
 * Some tests are read-only to avoid modifying system state during testing.
 */
@AddedIn(Xnat_1_10_0.class)
@Test(groups = {TestGroups.DB_SCHEMAS, TestGroups.SCHEMAS})
public class TestDBBackedSchemaApi extends BaseXnatRestTest {

    private String dbschemasUrl() {
        return formatXapiUrl("dbschemas");
    }

    private String dbschemasUrl(String... pathSegments) {
        String[] segments = new String[pathSegments.length + 1];
        segments[0] = "dbschemas";
        System.arraycopy(pathSegments, 0, segments, 1, pathSegments.length);
        return formatXapiUrl(segments);
    }

    // ==================== GET /dbschemas Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/dbschemas")
    public void testGetAllSchemasAsAdmin() {
        mainAdminQueryBase()
                .get(dbschemasUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/dbschemas")
    public void testGetAllSchemasReturnsExpectedStructure() {
        Response response = mainAdminQueryBase()
                .get(dbschemasUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");

        // If there are any DB-backed schemas, verify their structure
        if (!schemas.isEmpty()) {
            Map<String, Object> firstSchema = schemas.get(0);
            assertTrue("Schema should have 'id' field", firstSchema.containsKey("id"));
            assertTrue("Schema should have 'name' field", firstSchema.containsKey("name"));
            assertTrue("Schema should have 'content' field", firstSchema.containsKey("content"));
            assertTrue("Schema should have 'elements' field", firstSchema.containsKey("elements"));
        }
    }

    // ==================== DELETE /dbschemas/{id} Tests ====================

    @Test
    @TestedApiSpec(method = DELETE, url = "/xapi/dbschemas/{id}")
    public void testDeleteSchemaNotFound() {
        // Try to delete a schema that doesn't exist - API returns 500 for this
        mainAdminQueryBase()
                .delete(dbschemasUrl("999999999"))
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(404), equalTo(500)));
    }

    @Test
    @TestedApiSpec(method = DELETE, url = "/xapi/dbschemas/{id}")
    public void testDeleteSchemaInvalidId() {
        // Try to delete with an invalid ID format
        mainAdminQueryBase()
                .delete(dbschemasUrl("invalid-id"))
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(400), equalTo(404), equalTo(500)));
    }

    // ==================== POST /dbschemas/{id}/load Tests ====================

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/dbschemas/{id}/load")
    public void testLoadSchemaNotFound() {
        // Try to load a schema that doesn't exist - API returns 500 for this
        mainAdminQueryBase()
                .post(dbschemasUrl("999999999", "load"))
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(404), equalTo(500)));
    }

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/dbschemas/{id}/load")
    public void testLoadSchemaInvalidId() {
        // Try to load with an invalid ID format
        mainAdminQueryBase()
                .post(dbschemasUrl("invalid-id", "load"))
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(400), equalTo(404), equalTo(500)));
    }

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/dbschemas/{id}/load")
    public void testLoadExistingSchema() {
        // First, get all schemas to find one that exists
        Response response = mainAdminQueryBase()
                .get(dbschemasUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");

        // If there are DB-backed schemas, try to load one
        if (!schemas.isEmpty()) {
            Number schemaId = (Number) schemas.get(0).get("id");

            Response loadResponse = mainAdminQueryBase()
                    .post(dbschemasUrl(String.valueOf(schemaId.longValue()), "load"))
                    .then()
                    .assertThat()
                    .statusCode(200)
                    .contentType("application/json")
                    .extract().response();

            // Verify response contains status
            String status = loadResponse.jsonPath().getString("status");
            assertTrue("Load response should have status 'loaded' or 'already_loaded'",
                    "loaded".equals(status) || "already_loaded".equals(status));
        }
    }

    // ==================== GET /dbschemas/{id}/schema Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/dbschemas/{id}/schema")
    public void testDownloadSchemaNotFound() {
        // Try to download a schema that doesn't exist - API returns 500 for this
        mainAdminQueryBase()
                .get(dbschemasUrl("999999999", "schema"))
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(404), equalTo(500)));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/dbschemas/{id}/schema")
    public void testDownloadSchemaInvalidId() {
        // Try to download with an invalid ID format
        mainAdminQueryBase()
                .get(dbschemasUrl("invalid-id", "schema"))
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(400), equalTo(404), equalTo(500)));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/dbschemas/{id}/schema")
    public void testDownloadExistingSchema() {
        // First, get all schemas to find one that exists
        Response response = mainAdminQueryBase()
                .get(dbschemasUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");

        // If there are DB-backed schemas, try to download one
        if (!schemas.isEmpty()) {
            Number schemaId = (Number) schemas.get(0).get("id");
            String schemaName = (String) schemas.get(0).get("name");

            Response downloadResponse = mainAdminQueryBase()
                    .get(dbschemasUrl(String.valueOf(schemaId.longValue()), "schema"))
                    .then()
                    .assertThat()
                    .statusCode(200)
                    .contentType(containsString("xml"))
                    .header("Content-Disposition", containsString("attachment"))
                    .header("Content-Disposition", containsString(schemaName))
                    .extract().response();

            String content = downloadResponse.getBody().asString();
            assertNotNull("Schema content should not be null", content);
            assertTrue("Schema content should contain XML declaration or schema element",
                    content.contains("<?xml") || content.contains("<xs:schema") || content.contains("<xsd:schema"));
        }
    }

    // ==================== Schema Lifecycle Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/dbschemas")
    public void testSchemaElementsListFormat() {
        Response response = mainAdminQueryBase()
                .get(dbschemasUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");

        // If there are schemas with elements, verify elements list format
        for (Map<String, Object> schema : schemas) {
            Object elements = schema.get("elements");
            if (elements != null) {
                assertTrue("Elements should be a list", elements instanceof List);
            }
        }
    }

    @Test
    public void testSchemaApiConsistency() {
        // Get all schemas
        Response listResponse = mainAdminQueryBase()
                .get(dbschemasUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> schemas = listResponse.jsonPath().getList("$");

        // For each schema, verify we can access its download endpoint
        for (Map<String, Object> schema : schemas) {
            Number schemaId = (Number) schema.get("id");
            String content = (String) schema.get("content");

            // Verify schema download endpoint works
            Response downloadResponse = mainAdminQueryBase()
                    .get(dbschemasUrl(String.valueOf(schemaId.longValue()), "schema"))
                    .then()
                    .assertThat()
                    .statusCode(200)
                    .extract().response();

            String downloadedContent = downloadResponse.getBody().asString();

            // If content is available in list response, it should match downloaded content
            if (content != null && !content.isEmpty()) {
                assertEquals("Downloaded content should match list content",
                        content, downloadedContent);
            }
        }
    }
}
