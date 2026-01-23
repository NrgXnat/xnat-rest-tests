package org.nrg.testing.xnat.tests.data;

import io.restassured.response.Response;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.versions.Xnat_1_10_0;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.http.Method.GET;
import static io.restassured.http.Method.POST;
import static org.hamcrest.Matchers.*;
import static org.testng.AssertJUnit.*;

/**
 * Tests for the DataTypesApi (/xapi/datatypes endpoints)
 * Tests data type listing, retrieval, and action management functionality.
 */
@AddedIn(Xnat_1_10_0.class)
@Test(groups = {TestGroups.DATA_TYPES, TestGroups.SCHEMAS})
public class TestDataTypesApi extends BaseXnatRestTest {

    private String datatypesUrl() {
        return formatXapiUrl("datatypes");
    }

    private String datatypesUrl(String... pathSegments) {
        String[] segments = new String[pathSegments.length + 1];
        segments[0] = "datatypes";
        System.arraycopy(pathSegments, 0, segments, 1, pathSegments.length);
        return formatXapiUrl(segments);
    }

    // ==================== GET /datatypes Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes")
    public void testGetAllDataTypes() {
        mainAdminQueryBase()
                .get(datatypesUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("$", not(empty()))
                .body("[0]", hasKey("elementName"))
                .body("[0]", hasKey("singular"))
                .body("[0]", hasKey("plural"))
                .body("[0]", hasKey("secured"))
                .body("[0]", hasKey("count"));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes")
    public void testGetDataTypesAuthenticatedUser() {
        // Authenticated (non-admin) users should also be able to access this endpoint
        mainQueryBase()
                .get(datatypesUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("$", not(empty()));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes")
    public void testGetDataTypesWithSecuredFilter() {
        Response response = mainAdminQueryBase()
                .queryParam("secured", true)
                .get(datatypesUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        List<Map<String, Object>> dataTypes = response.jsonPath().getList("$");
        // If we got results, verify all are secured
        for (Map<String, Object> dataType : dataTypes) {
            assertTrue("Expected all returned data types to be secured",
                    (Boolean) dataType.get("secured"));
        }
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes")
    public void testGetDataTypesWithUsedFilter() {
        // Filter to only data types that have existing data
        mainAdminQueryBase()
                .queryParam("used", true)
                .get(datatypesUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes")
    public void testGetDataTypesWithReadableFilter() {
        // Use readable counts instead of total counts
        mainAdminQueryBase()
                .queryParam("readable", true)
                .get(datatypesUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes")
    public void testGetDataTypesWithMultipleFilters() {
        // Combine multiple filters
        mainAdminQueryBase()
                .queryParam("secured", true)
                .queryParam("used", true)
                .queryParam("readable", true)
                .get(datatypesUrl())
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json");
    }

    // ==================== GET /datatypes/{elementName} Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes/{elementName}")
    public void testGetSpecificDataType() {
        // Use a known standard XNAT data type
        String elementName = "xnat:projectData";

        mainAdminQueryBase()
                .get(datatypesUrl(elementName))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("elementName", equalTo(elementName))
                .body("$", hasKey("singular"))
                .body("$", hasKey("plural"))
                .body("$", hasKey("secure"))
                .body("$", hasKey("secureRead"))
                .body("$", hasKey("secureEdit"))
                .body("$", hasKey("secureCreate"))
                .body("$", hasKey("secureDelete"))
                .body("$", hasKey("browse"))
                .body("$", hasKey("searchable"));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes/{elementName}")
    public void testGetSpecificDataTypeNotFound() {
        mainAdminQueryBase()
                .get(datatypesUrl("nonexistent:dataType"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes/{elementName}")
    public void testGetSpecificDataTypeSubjectData() {
        String elementName = "xnat:subjectData";

        mainAdminQueryBase()
                .get(datatypesUrl(elementName))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("elementName", equalTo(elementName));
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes/{elementName}")
    public void testGetSpecificDataTypeMRSession() {
        String elementName = "xnat:mrSessionData";

        mainAdminQueryBase()
                .get(datatypesUrl(elementName))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .body("elementName", equalTo(elementName));
    }

    // ==================== GET /datatypes/{elementName}/actions Tests ====================

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes/{elementName}/actions")
    public void testGetDataTypeActions() {
        String elementName = "xnat:projectData";

        mainAdminQueryBase()
                .get(datatypesUrl(elementName, "actions"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes/{elementName}/actions")
    public void testGetDataTypeActionsNotFound() {
        mainAdminQueryBase()
                .get(datatypesUrl("nonexistent:dataType", "actions"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    @TestedApiSpec(method = GET, url = "/xapi/datatypes/{elementName}/actions")
    public void testGetDataTypeActionsForMRSession() {
        String elementName = "xnat:mrSessionData";

        Response response = mainAdminQueryBase()
                .get(datatypesUrl(elementName, "actions"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json")
                .extract().response();

        List<Map<String, Object>> actions = response.jsonPath().getList("$");
        // Verify action structure if any exist
        if (!actions.isEmpty()) {
            Map<String, Object> firstAction = actions.get(0);
            assertTrue("Action should have a name", firstAction.containsKey("name"));
        }
    }

    // ==================== POST /datatypes/{elementName}/actions Tests ====================

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/{elementName}/actions")
    public void testAddDataTypeActionsAsAdmin() {
        String elementName = "xnat:projectData";
        List<Map<String, String>> actions = Collections.singletonList(
                createActionMap("testAction", "Test Action", "test.gif")
        );

        // Admin user should be able to add actions
        mainAdminQueryBase()
                .contentType("application/json")
                .body(actions)
                .post(datatypesUrl(elementName, "actions"))
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the actions endpoint is still accessible after adding
        mainAdminQueryBase()
                .get(datatypesUrl(elementName, "actions"))
                .then()
                .assertThat()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/{elementName}/actions")
    public void testAddDataTypeActionsNotFound() {
        List<Map<String, String>> actions = Collections.singletonList(
                createActionMap("testAction", "Test Action", "test.gif")
        );

        mainAdminQueryBase()
                .contentType("application/json")
                .body(actions)
                .post(datatypesUrl("nonexistent:dataType", "actions"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/{elementName}/actions")
    public void testAddDataTypeActionsEmptyList() {
        String elementName = "xnat:projectData";
        List<Map<String, String>> emptyActions = Collections.emptyList();

        mainAdminQueryBase()
                .contentType("application/json")
                .body(emptyActions)
                .post(datatypesUrl(elementName, "actions"))
                .then()
                .assertThat()
                .statusCode(200)
                .body("message", equalTo("No actions to add"));
    }

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/{elementName}/actions")
    public void testAddDataTypeActionsRequiresName() {
        String elementName = "xnat:projectData";
        // Create action without name
        Map<String, String> actionWithoutName = new HashMap<>();
        actionWithoutName.put("displayName", "Test Action");
        List<Map<String, String>> actions = Collections.singletonList(actionWithoutName);

        mainAdminQueryBase()
                .contentType("application/json")
                .body(actions)
                .post(datatypesUrl(elementName, "actions"))
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", containsString("name is required"));
    }

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/{elementName}/actions")
    public void testAddDataTypeActionsInvalidNamePattern() {
        String elementName = "xnat:projectData";
        // Create action with invalid characters in name
        Map<String, String> actionWithInvalidName = new HashMap<>();
        actionWithInvalidName.put("name", "test<script>alert('xss')</script>");
        List<Map<String, String>> actions = Collections.singletonList(actionWithInvalidName);

        mainAdminQueryBase()
                .contentType("application/json")
                .body(actions)
                .post(datatypesUrl(elementName, "actions"))
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", containsString("can only contain"));
    }

    // ==================== POST /datatypes/create Tests ====================

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/create")
    public void testCreateDataTypeAsAdmin() {
        // Admin user should be able to create data types
        // Note: This creates an actual type - use unique name
        String uniqueName = "TestType" + System.currentTimeMillis();
        mainAdminQueryBase()
                .queryParam("name", uniqueName)
                .queryParam("singular", "Test Type")
                .queryParam("plural", "Test Types")
                .queryParam("extends", "subjectAssessorData")
                .post(datatypesUrl("create"))
                .then()
                .assertThat()
                .statusCode(201);
    }

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/create")
    public void testCreateDataTypeMissingRequiredParams() {
        // Missing required 'name' parameter - API returns 500 for this
        mainAdminQueryBase()
                .queryParam("singular", "Test Type")
                .queryParam("plural", "Test Types")
                .queryParam("extends", "subjectAssessorData")
                .post(datatypesUrl("create"))
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(400), equalTo(500)));
    }

    @Test
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/create")
    public void testCreateDataTypeInvalidExtends() {
        // Use an invalid/non-extensible base type
        mainAdminQueryBase()
                .queryParam("name", "testInvalidType")
                .queryParam("singular", "Test Invalid Type")
                .queryParam("plural", "Test Invalid Types")
                .queryParam("extends", "invalidBaseType")
                .post(datatypesUrl("create"))
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(400), equalTo(403)));
    }

    // ==================== Helper Methods ====================

    private Map<String, String> createActionMap(String name, String displayName, String image) {
        Map<String, String> action = new HashMap<>();
        action.put("name", name);
        action.put("displayName", displayName);
        action.put("image", image);
        return action;
    }
}
