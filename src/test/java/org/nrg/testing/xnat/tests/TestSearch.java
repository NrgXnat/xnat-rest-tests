package org.nrg.testing.xnat.tests;

import com.jayway.restassured.path.json.JsonPath;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.testng.annotations.Test;

import java.util.Map;

import static org.nrg.xnat.pogo.DataType.MR_SCAN;
import static org.nrg.xnat.pogo.DataType.MR_SESSION;
import static org.testng.Assert.assertNotEquals;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

public class TestSearch extends BaseXnatRestTest {

    @Test
    public void testElements() {
        final JsonPath elements = mainCredentials().given().queryParam("format", "json").get(formatRestUrl("search", "elements")).
                then().assertThat().statusCode(200).
                and().extract().jsonPath().setRoot("ResultSet.Result");

        // These can be changed by a site administrator, but these are the default values.
        final Map<String, Object> mrSession = lookupElement(MR_SESSION.getXsiType(), elements);
        assertNotEquals(null, mrSession);
        assertEquals(MR_SESSION.getSingularName(), mrSession.get("SINGULAR"));
        assertEquals(MR_SESSION.getPluralName(), mrSession.get("PLURAL"));

        assertNull(lookupElement(MR_SCAN.getXsiType(), elements));

        // Again, a site administrator can mess with this, but xnat:investigatorData is the only default unsecured
        assertEquals("false", lookupElement("xnat:investigatorData", elements).get("SECURED"));
        assertEquals(1, elements.getList("findAll { it.SECURED == 'false' }").size());
    }

    private Map<String, Object> lookupElement(String xsiType, JsonPath elements) {
        return elements.get(String.format("find { it.ELEMENT_NAME == '%s' }", xsiType));
    }

}
