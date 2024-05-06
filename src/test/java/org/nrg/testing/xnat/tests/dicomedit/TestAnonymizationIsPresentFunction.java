package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_9;

@AddedIn(Xnat_1_9.class)
public class TestAnonymizationIsPresentFunction extends BaseAnonymizationTest {

    public void testIsPresentStandard() {
        new IsPresentTest("standard.das")
                .validationExpectingElementsCorrect("(0008,1080)", "(0008,1090)")
                .run();
    }

    public void testIsPresentPrivateElement() {
        new IsPresentTest("private.das")
                .validationExpectingElementsCorrect("(0012,0010)", "(0012,0020)", "(0012,0021)")
                .run();
    }

    public void testIsPresentMultipleArgs() {
        new IsPresentTest("multipleArgs.das")
                .validationExpectingElementsCorrect("(0012,0010)", "(0012,0020)", "(0012,0021)", "(0012,0030)")
                .run();
    }

    public void testIsPresentLateElement() {
        new IsPresentTest("lateElement.das")
                .validationExpectingElementsCorrect("(0012,0010)", "(0012,0020)")
                .withData(
                        new LocallyCacheableDicomTransformation("anon2_late_fake_elements")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> dicom.setString(0x96001010, VR.LO, "VALUE"))
                ).run();
    }

    public void testIsPresentSequences() {
        new IsPresentTest("sequences.das")
                .validationExpectingElementsCorrect("(0012,0010)", "(0012,0020)", "(0012,0021)", "(0012,0030)", "(0012,0031)", "(0012,0040)", "(0012,0042)", "(0012,0050)")
                .withData(
                        new LocallyCacheableDicomTransformation("anon2_custom_sequences")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> {
                                    final Sequence sequence = dicom.newSequence(Tag.SpecificAbsorptionRateSequence, 0);
                                    sequence.add(new Attributes());
                                    final Attributes nonemptyItem = new Attributes();
                                    nonemptyItem.setString(Tag.CodeValue, VR.SH, "1234");
                                    sequence.add(nonemptyItem);
                                })
                ).run();
    }

    private class IsPresentTest extends BasicAnonymizationTest {

        private IsPresentTest(String scriptName) {
            super("isPresent", scriptName);
        }

        private BasicAnonymizationTest validationExpectingElementsCorrect(String... dicomElements) {
            return withValidation((dicom) -> {
                for (String element : dicomElements) {
                    dicom.putValueEqualCheck(element, "CORRECT");
                }
            });
        }

    }

}
