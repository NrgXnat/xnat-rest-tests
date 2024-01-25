package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Tag;
import org.nrg.testing.dicom.RootDicomObject;

import java.util.function.Consumer;

public class TestAnonymizationSyntaxMiscellanea extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> COMMON_VALIDATION = (root) -> root.putValueEqualCheck(Tag.CodeValue, "INSERTED STRING");

    public void testMultilineString() {
        new SyntaxTest("multilineString.das")
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    public void testMultilineAssignString() {
        new SyntaxTest("multilineAssignString.das")
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    public void testMultilineAssignStringAsVariable() {
        new SyntaxTest("multilineAssignStringVariable.das")
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    public void testMultilineTernary() {
        new SyntaxTest("multilineTernary.das")
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    private class SyntaxTest extends BasicAnonymizationTest {

        SyntaxTest(String scriptName) {
            super(SYNTAX_MISCELLANEA, scriptName);
        }

    }
}
