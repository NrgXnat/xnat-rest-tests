package org.nrg.testing.xnat.tests.example;

import org.nrg.testing.example.BaseCustomTest;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertTrue;

public class TestSchemaOnlySubinterfaceExample extends BaseCustomTest {

    @Test
    public void testSchemaOnlySubinterfaceExample() {
        assertTrue(mainSchemaOnlyExampleSubinterface().readSchemaFromSchemaOnlyApi("xnat.xsd").contains("xnat:mrSessionData"));
    }

}
