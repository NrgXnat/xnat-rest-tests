package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_7;

import java.util.function.Consumer;

import static org.nrg.xnat.enums.DicomEditVersion.DE_4;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationClassicConditionals extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> STANDARD_CONDITIONAL_VALIDATION = (root) -> {
        root.putNonexistenceChecks("(0028,3003)");
        root.putValueEqualCheck("(0028,9002)", "0");
        root.putValueEqualCheck("(0012,0020)", "Hello");
        root.putNonexistenceChecks("(0012,0030)", "(0012,0040)", "(0012,0050)", "(0008,2122)", "(0008,2124)");
        root.putValueEqualCheck("(0020,1204)", "100");
        root.putValueEqualCheck("(0020,1206)", "50");
        root.putValueEqualCheck("(2010,0040)", "GOOD");
        root.putValueEqualCheck("(2010,0050)", "GOOD");
        root.putValueEqualCheck("(2010,0060)", "GOOD");
        root.putValueEqualCheck("(2010,0080)", "GOOD");
        root.putValueEqualCheck("(2010,00A8)", "GOOD");
        root.putNonexistenceChecks("(2010,00A9)");
        root.putValueEqualCheck("(2010,0140)", "GOOD");
        root.putValueEqualCheck("(2010,0150)", "GOOD");
        root.putValueEqualCheck("(2200,0005)", "GOOD");
        root.putNonexistenceChecks("(2200,0006)");
    };
    private static final Consumer<RootDicomObject> PRIVATE_CONDITIONAL_VALIDATION = (root) -> {
        root.putValueEqualCheck("(3006,0088", "Nice");
        root.putNonexistenceChecks("(3008,0066)", "(2001,110c)");
    };

    public void testStandardConditionalsDE4() {
        new BasicAnonymizationTest("standardConditionals.das")
                .withDicomEditVersion(DE_4)
                .withValidation(STANDARD_CONDITIONAL_VALIDATION)
                .run();
    }

    public void testStandardConditionalsDE6() {
        new BasicAnonymizationTest("standardConditionals.das")
                .withValidation(STANDARD_CONDITIONAL_VALIDATION)
                .run();
    }

    public void testPrivateConditionalsDE4() {
        new BasicAnonymizationTest("privateConditionals.das")
                .withDicomEditVersion(DE_4)
                .withValidation(PRIVATE_CONDITIONAL_VALIDATION)
                .run();
    }

    public void testPrivateConditionalsDE6() {
        new BasicAnonymizationTest("privateConditionals.das")
                .withValidation(PRIVATE_CONDITIONAL_VALIDATION)
                .run();
    }

    /**
     * Tests DE-58
     */
    @AddedIn(Xnat_1_8_7.class)
    public void testStandardConditionalsWithFunctionDE6() {
        new BasicAnonymizationTest("standardConditionalWithFunction.das")
                .withValidation((root) -> {
                    root.putExistenceChecks("(2001,0010)");
                    root.putNonexistenceChecks("(2005,0010)");
                }).run();
    }

}
