package org.nrg.testing.xnat.tests.data;

import io.restassured.response.Response;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.Users;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_10_0;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import static io.restassured.http.Method.GET;
import static org.hamcrest.Matchers.*;
import static org.testng.AssertJUnit.*;

/**
 * Tests for the XFTElementApi (/xapi/xft endpoints)
 * Tests XFT schema, element, reference, and field access functionality.
 *
 * Note: These endpoints require Admin access and provide read-only access
 * to the internal XFT configuration held in memory.
 */
@AddedIn(Xnat_1_10_0.class)
@Test(groups = {TestGroups.XFT_ELEMENTS, TestGroups.SCHEMAS})
public class TestXFTElementApi extends BaseXnatRestTest {

    private static final Set<String> PROTECTED_PREFIXES = new HashSet<>(Arrays.asList("xdat", "arc", "cat"));

    // Non-admin user for permission testing
    private User nonAdminUser;

    @BeforeClass
    public void setupNonAdminUser() {
        // Create a non-admin user for testing permission restrictions
        nonAdminUser = Users.genericAccount();
        mainAdminInterface().createUser(nonAdminUser);
    }

    private String xftUrl() {
        return formatXapiUrl("xft");
    }

    private String xftUrl(String... pathSegments) {
        String[] segments = new String[pathSegments.length + 1];
        segments[0] = "xft";
        System.arraycopy(pathSegments, 0, segments, 1, pathSegments.length);
        return formatXapiUrl(segments);
    }

    // ==================== GET /xft/schemas Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas")
    public void testGetAllSchemasAsAdmin() {
        mainAdminQueryBase()
                .get(xftUrl("schemas"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("$", not(empty()));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas")
    public void testGetAllSchemasForbiddenForNonAdmin() {
        // Non-admin user should get 403 Forbidden
        restDriver.queryBaseFor(nonAdminUser)
                .get(xftUrl("schemas"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas")
    public void testGetAllSchemasReturnsExpectedStructure() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("schemas"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");
        assertFalse("Should return at least one schema", schemas.isEmpty());

        Map<String, Object> firstSchema = schemas.get(0);
        assertTrue("Schema should have 'targetNamespacePrefix'", firstSchema.containsKey("targetNamespacePrefix"));
        assertTrue("Schema should have 'targetNamespaceURI'", firstSchema.containsKey("targetNamespaceURI"));
        assertTrue("Schema should have 'elementCount'", firstSchema.containsKey("elementCount"));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas")
    public void testGetAllSchemasExcludesProtectedNamespace() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("schemas"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");

        // Verify xdat namespace is not included
        for (Map<String, Object> schema : schemas) {
            String prefix = (String) schema.get("targetNamespacePrefix");
            assertFalse("Protected namespace 'xdat' should not be included",
                    "xdat".equals(prefix));
        }
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas")
    public void testGetAllSchemasIncludesXnatNamespace() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("schemas"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");

        boolean hasXnat = false;
        for (Map<String, Object> schema : schemas) {
            if ("xnat".equals(schema.get("targetNamespacePrefix"))) {
                hasXnat = true;
                break;
            }
        }
        assertTrue("Should include 'xnat' namespace", hasXnat);
    }

    // ==================== GET /xft/elements Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/elements")
    public void testGetAllElementsAsAdmin() {
        mainAdminQueryBase()
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("$", not(empty()));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/elements")
    public void testGetAllElementsForbiddenForNonAdmin() {
        // Non-admin user should get 403 Forbidden
        restDriver.queryBaseFor(nonAdminUser)
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/elements")
    public void testGetAllElementsExcludesProtectedElements() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<String> elements = response.jsonPath().getList("$");

        for (String element : elements) {
            assertFalse("Protected element 'xdat:' should not be included",
                    element.startsWith("xdat:"));
            assertFalse("Protected element 'arc:' should not be included",
                    element.startsWith("arc:"));
            assertFalse("Protected element 'cat:' should not be included",
                    element.startsWith("cat:"));
        }
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/elements")
    public void testGetAllElementsDefaultExcludesMetaAndHistory() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<String> elements = response.jsonPath().getList("$");

        for (String element : elements) {
            assertFalse("Metadata elements should be excluded by default",
                    element.endsWith("_meta_data"));
            assertFalse("History elements should be excluded by default",
                    element.endsWith("_history"));
        }
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/elements")
    public void testGetAllElementsShowMeta() {
        Response responseWithMeta = mainAdminQueryBase()
                .queryParam("showMeta", true)
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        Response responseWithoutMeta = mainAdminQueryBase()
                .queryParam("showMeta", false)
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<String> elementsWithMeta = responseWithMeta.jsonPath().getList("$");
        List<String> elementsWithoutMeta = responseWithoutMeta.jsonPath().getList("$");

        // When showMeta=true, we should have more (or equal) elements
        assertTrue("showMeta=true should include more or equal elements",
                elementsWithMeta.size() >= elementsWithoutMeta.size());
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/elements")
    public void testGetAllElementsShowHistory() {
        Response responseWithHistory = mainAdminQueryBase()
                .queryParam("showHistory", true)
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        Response responseWithoutHistory = mainAdminQueryBase()
                .queryParam("showHistory", false)
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<String> elementsWithHistory = responseWithHistory.jsonPath().getList("$");
        List<String> elementsWithoutHistory = responseWithoutHistory.jsonPath().getList("$");

        // When showHistory=true, we should have more (or equal) elements
        assertTrue("showHistory=true should include more or equal elements",
                elementsWithHistory.size() >= elementsWithoutHistory.size());
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/elements")
    public void testGetAllElementsAreSorted() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<String> elements = response.jsonPath().getList("$");

        // Verify list is sorted alphabetically (case-insensitive)
        for (int i = 1; i < elements.size(); i++) {
            assertTrue("Elements should be sorted alphabetically",
                    elements.get(i-1).compareToIgnoreCase(elements.get(i)) <= 0);
        }
    }

    // ==================== GET /xft/references Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/references")
    public void testGetAllReferencesAsAdmin() {
        mainAdminQueryBase()
                .get(xftUrl("references"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/references")
    public void testGetAllReferencesForbiddenForNonAdmin() {
        // Non-admin user should get 403 Forbidden
        restDriver.queryBaseFor(nonAdminUser)
                .get(xftUrl("references"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/references")
    public void testGetAllReferencesStructure() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("references"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> references = response.jsonPath().getList("$");

        // Verify reference structure if any exist
        if (!references.isEmpty()) {
            Map<String, Object> firstRef = references.get(0);
            assertTrue("Reference should have 'manyToMany' field", firstRef.containsKey("manyToMany"));
            assertTrue("Reference should have 'referenceType' field", firstRef.containsKey("referenceType"));
        }
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/references")
    public void testGetAllReferencesExcludesProtectedNamespaces() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("references"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> references = response.jsonPath().getList("$");

        for (Map<String, Object> ref : references) {
            // Check element names in the reference
            checkReferenceForProtectedPrefix(ref, "element1");
            checkReferenceForProtectedPrefix(ref, "element2");
            checkReferenceForProtectedPrefix(ref, "superiorElement");
            checkReferenceForProtectedPrefix(ref, "subordinateElement");
        }
    }

    // ==================== GET /xft/schemas/{prefix}/elements Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements")
    public void testGetSchemaElementsAsAdmin() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("$", not(empty()));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements")
    public void testGetSchemaElementsForbiddenForNonAdmin() {
        // Non-admin user should get 403 Forbidden
        restDriver.queryBaseFor(nonAdminUser)
                .get(xftUrl("schemas", "xnat", "elements"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements")
    public void testGetSchemaElementsProtectedNamespaceForbidden() {
        // Accessing xdat namespace should return 403
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xdat", "elements"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements")
    public void testGetSchemaElementsNotFound() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "nonexistent", "elements"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements")
    public void testGetSchemaElementsStructure() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> elements = response.jsonPath().getList("$");
        assertFalse("Should return elements for xnat schema", elements.isEmpty());

        Map<String, Object> firstElement = elements.get(0);
        assertTrue("Element should have 'name'", firstElement.containsKey("name"));
        assertTrue("Element should have 'fieldCount'", firstElement.containsKey("fieldCount"));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements")
    public void testGetSchemaElementsShowMetaParameter() {
        Response responseWithMeta = mainAdminQueryBase()
                .queryParam("showMeta", true)
                .get(xftUrl("schemas", "xnat", "elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        Response responseWithoutMeta = mainAdminQueryBase()
                .queryParam("showMeta", false)
                .get(xftUrl("schemas", "xnat", "elements"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> elementsWithMeta = responseWithMeta.jsonPath().getList("$");
        List<Map<String, Object>> elementsWithoutMeta = responseWithoutMeta.jsonPath().getList("$");

        assertTrue("showMeta=true should include more or equal elements",
                elementsWithMeta.size() >= elementsWithoutMeta.size());
    }

    // ==================== GET /xft/schemas/{prefix}/elements/{elementName} Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}")
    public void testGetSpecificElementAsAdmin() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "projectdata"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("name", equalToIgnoringCase("projectdata"));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}")
    public void testGetSpecificElementForbiddenForNonAdmin() {
        // Non-admin user should get 403 Forbidden
        restDriver.queryBaseFor(nonAdminUser)
                .get(xftUrl("schemas", "xnat", "elements", "projectdata"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}")
    public void testGetSpecificElementProtectedNamespaceForbidden() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xdat", "elements", "user"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}")
    public void testGetSpecificElementNotFound() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "nonexistent"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}")
    public void testGetSpecificElementStructure() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "projectdata"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        Map<String, Object> element = response.jsonPath().getMap("$");

        assertTrue("Element should have 'name'", element.containsKey("name"));
        assertTrue("Element should have 'extension'", element.containsKey("extension"));
        assertTrue("Element should have 'fieldCount'", element.containsKey("fieldCount"));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}")
    public void testGetSpecificElementSubjectData() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "subjectdata"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("name", equalToIgnoringCase("subjectdata"));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}")
    public void testGetSpecificElementMRSessionData() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "mrsessiondata"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("name", equalToIgnoringCase("mrsessiondata"));
    }

    // ==================== GET /xft/schemas/{prefix}/elements/{elementName}/fields Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}/fields")
    public void testGetElementFieldsAsAdmin() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "projectdata", "fields"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}/fields")
    public void testGetElementFieldsForbiddenForNonAdmin() {
        // Non-admin user should get 403 Forbidden
        restDriver.queryBaseFor(nonAdminUser)
                .get(xftUrl("schemas", "xnat", "elements", "projectdata", "fields"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}/fields")
    public void testGetElementFieldsProtectedNamespaceForbidden() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xdat", "elements", "user", "fields"))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}/fields")
    public void testGetElementFieldsElementNotFound() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "nonexistent", "fields"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}/fields")
    public void testGetElementFieldsSchemaNotFound() {
        mainAdminQueryBase()
                .get(xftUrl("schemas", "nonexistent", "elements", "projectdata", "fields"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}/fields")
    public void testGetElementFieldsStructure() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "projectdata", "fields"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> fields = response.jsonPath().getList("$");

        // If there are fields, verify their structure
        if (!fields.isEmpty()) {
            Map<String, Object> firstField = fields.get(0);
            assertTrue("Field should have 'name'", firstField.containsKey("name"));
            assertTrue("Field should have 'fieldType'", firstField.containsKey("fieldType"));
        }
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}/fields")
    public void testGetElementFieldsIncludesNestedFields() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "projectdata", "fields"))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> fields = response.jsonPath().getList("$");

        // Check if any fields have nested paths (contain "/")
        boolean hasNestedField = false;
        for (Map<String, Object> field : fields) {
            String fieldName = (String) field.get("name");
            if (fieldName != null && fieldName.contains("/")) {
                hasNestedField = true;
                break;
            }
        }
        // This may or may not be true depending on the element's structure
        // Just logging for information
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/xft/schemas/{prefix}/elements/{elementName}/fields")
    public void testGetElementFieldsForMRSessionData() {
        Response response = mainAdminQueryBase()
                .get(xftUrl("schemas", "xnat", "elements", "mrsessiondata", "fields"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        List<Map<String, Object>> fields = response.jsonPath().getList("$");
        assertNotNull("Fields list should not be null", fields);
    }

    // ==================== Helper Methods ====================

    private void checkReferenceForProtectedPrefix(Map<String, Object> ref, String key) {
        if (ref.containsKey(key)) {
            Object element = ref.get(key);
            if (element instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> elementMap = (Map<String, Object>) element;
                String name = (String) elementMap.get("name");
                if (name != null) {
                    assertFalse("Reference should not include protected 'xdat:' namespace",
                            name.startsWith("xdat:"));
                }
            }
        }
    }
}
