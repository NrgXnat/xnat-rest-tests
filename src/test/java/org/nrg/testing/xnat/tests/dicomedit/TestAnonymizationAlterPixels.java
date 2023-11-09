package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.AlterPixelsBasicScript;
import org.nrg.testing.dicom.AlterPixelsOverflowScript;
import org.nrg.testing.dicom.AlterPixelsSimpleOverflowScript;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_1;
import org.nrg.xnat.versions.Xnat_1_8_9;

@AddedIn(Xnat_1_8_1.class)
@TestRequires(admin = true, data = TestData.SAMPLE_1)
public class TestAnonymizationAlterPixels extends BaseAnonymizationTest {

    @AddedIn(Xnat_1_8_9.class)
    public void testAlterPixelsOverflow() {
        new BasicAnonymizationTest("alterPixelsOverflow.das")
                .withData(TestData.SAMPLE_1)
                .withValidation(new AlterPixelsOverflowScript())
                .run();
    }

    public void testAlterPixelsBasic() {
        new BasicAnonymizationTest("alterPixelsBasic.das")
                .withData(TestData.SAMPLE_1)
                .withValidation(new AlterPixelsBasicScript())
                .run();
    }

    @AddedIn(Xnat_1_8_9.class)
    public void testAlterPixelsSimpleOverflow() {
        new BasicAnonymizationTest("alterPixelsSimpleOverflow.das")
                .withData(TestData.SAMPLE_1)
                .withValidation(new AlterPixelsSimpleOverflowScript())
                .run();
    }

}
