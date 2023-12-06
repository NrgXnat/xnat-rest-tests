package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.AnonConstants;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_10;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@TestRequires(admin = true, data = TestData.ANON_2)
@AddedIn(Xnat_1_8_10.class)
public class TestAnonymizationNormalizeString extends BaseAnonymizationTest {

    private static final String COMMON_SOURCE_VALUE = "Déscrîptîøn plüs $pec|ål ¢haraçtêrs";
    private static final String ALT_SOURCE_VALUE = "Dêscr_ptïøn plûs $pec|Æl ©hara©tËrs";
    private static final String CHAR_SET = "ISO_IR 100";
    private static final int PRIVATE_ID_TAG = 0x00550010;
    private static final int PRIVATE_ELEMENT_1 = 0x00551015;
    private static final int PRIVATE_ELEMENT_2 = 0x00551016;
    private static final Consumer<Attributes> specialCharAdder = (dicom) -> {
        dicom.setSpecificCharacterSet(CHAR_SET);
        dicom.setString(Tag.SeriesDescription, VR.LO, COMMON_SOURCE_VALUE);
        dicom.setString(PRIVATE_ID_TAG, VR.LO, "REST TEST");
        dicom.setString(PRIVATE_ELEMENT_1, VR.LT, ALT_SOURCE_VALUE);
        try {
            dicom.setBytes(PRIVATE_ELEMENT_2, VR.UN, dicom.getBytes(PRIVATE_ELEMENT_1));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };
    private static final LocallyCacheableDicomTransformation ANON2_WITH_SPECIAL_CHARS = new LocallyCacheableDicomTransformation("normalize_string_common")
            .data(TestData.ANON_2)
            .createZip()
            .simpleTransform((dicom) -> {
                specialCharAdder.accept(dicom);
                dicom.getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence).setString(Tag.StudyDescription, VR.LO, "D±scr¡pt¡Øn plÜs $pec|Æl ©hara©tËrs");
            });
    private static final String EXPECTED_NORMALIZED_VALUE = "D_scr_pt__n pl_s $pec|_l _hara_t_rs";
    private static final Consumer<RootDicomObject> COMMON_VALIDATION = (root) -> {
        root.putNonexistenceChecks("(0008,0112)");
        root.putValueEqualCheck("(0008,103E)", EXPECTED_NORMALIZED_VALUE);
    };

    /**
     * Tests basic usage of the new normalizeString function added for DE-108 to remove non-ASCII characters.
     */
    public void testBasicNormalizeStringUsage() {
        new NormalizeStringTest("normalizeString.das").run();
    }

    /**
     * The behavior in this test is not desirable, but I'm sticking this test in for the time being so that
     * the behavior doesn't change without us noticing.
     * See DE-124 and DE-126
     */
    public void testBasicNormalizeStringUsageImplicitVr() {
        new NormalizeStringTest("normalizeStringImplicit.das")
                .withData(
                        new LocallyCacheableDicomTransformation("implicit_vr_non_ascii")
                                .createZip()
                                .data(TestData.DICOM_WEB_PETMR2_PT)
                                .simpleTransform(specialCharAdder)
                ).withValidation((root) -> {
                    COMMON_VALIDATION.accept(root);
                    root.putValueEqualCheck(
                            "(0055,1015)",
                            formatExtendedStringPostNormalize(ALT_SOURCE_VALUE) + "\\" + formatExtendedStringPostNormalize(" ")
                    );
                }).run();
    }

    /**
     * Tests usage of the new normalizeString function added for DE-108 to remove non-ASCII characters, but with
     * a user-specific replacement string.
     */
    public void testBasicNormalizeStringUsageWithCustomReplacement() {
        new NormalizeStringTest("normalizeStringCustomReplacement.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,103E)", EXPECTED_NORMALIZED_VALUE.replace("_", ":)"));
                }).run();
    }

    /**
     * Tests usage of the new normalizeString function added for DE-108 to remove non-ASCII characters, but with an
     * input value of a string literal rather than being pulled from a tag.
     */
    public void testBasicNormalizeStringRawValue() {
        new NormalizeStringTest("normalizeStringRawValue.das").run();
    }

    /**
     * Tests usage of the new normalizeString function added for DE-108 to remove non-ASCII characters, but with an
     * input value of a string literal that has been stored in a variable.
     */
    public void testBasicNormalizeStringVariable() {
        new NormalizeStringTest("normalizeStringVariable.das").run();
    }

    /**
     * Tests usage of the new normalizeString function added for DE-108 to remove non-ASCII characters with the
     * "assign-if-exists" ?= operator to make sure the functionality still works if the tag is present, and that
     * the tag is not improperly inserted if not present.
     */
    public void testNormalizeStringAssignIfExists() {
        new NormalizeStringTest("normalizeStringIfExists.das").run();
    }

    /**
     * Tests usage of the new normalizeString function added for DE-108 to remove non-ASCII characters, but with a
     * tagpath pointing at an element within a sequence.
     */
    public void testNormalizeStringSequence() {
        new NormalizeStringTest("normalizeStringSequence.das")
                .withValidation((root) -> {
                    root.putSequenceCheck("(0008,1111)", (item) -> {
                        item.putValueEqualCheck("(0008,1030)", EXPECTED_NORMALIZED_VALUE);
                    });
                }).run();
    }

    /**
     * Tests usage of the new normalizeString function added for DE-108 to remove non-ASCII characters, but with a
     * private element of a reasonable string VR as input.
     */
    public void testNormalizeStringPrivateElement() {
        new NormalizeStringTest("normalizeStringPrivateElements.das")
                .withValidation((root) -> root.putValueEqualCheck("(0055,1015)", EXPECTED_NORMALIZED_VALUE, VR.LT))
                .run();
    }

    /**
     * The behavior in this test is not desirable, but I'm sticking this test in for the time being so that
     * the behavior doesn't change without us noticing.
     * See DE-124 and DE-126
     */
    public void testNormalizeStringUnElement() {
        new NormalizeStringTest("normalizeStringUn.das")
                .withValidation((root) -> root.putValueEqualCheck(
                        "(0055,1016)",
                        formatExtendedStringPostNormalize(ALT_SOURCE_VALUE) + "\\" + formatExtendedStringPostNormalize("\\00"),
                        VR.UN
                )).run();
    }

    /**
     * Tests usage of the new normalizeString function added for DE-108 to remove non-ASCII characters, but with an
     * nonsensical DICOM tag. The goal is to see that even if DicomEdit doesn't know what a tag is, we can still handle
     * it properly here as long as it has a proper string VR. This is essentially a proxy for the case where we
     * don't update dcm4che quickly enough and our data dictionary gets out-of-date.
     */
    public void testNormalizeStringVrPreservation() {
        new NormalizeStringTest("normalizeStringVrPreservation.das")
                .withData(
                        new LocallyCacheableDicomTransformation("normalize_string_vr")
                                .createZip()
                                .data(TestData.ANON_2)
                                .simpleTransform((dicom) -> {
                                    dicom.setSpecificCharacterSet(CHAR_SET);
                                    dicom.setString(AnonConstants.FAKE_DICOM_TAG, VR.LT, COMMON_SOURCE_VALUE);
                                })
                ).withValidation((root) -> root.putValueEqualCheck(AnonConstants.FAKE_DICOM_TAG, EXPECTED_NORMALIZED_VALUE, VR.LT))
                .run();
    }

    /**
     * Tests usage of the new normalizeString function added for DE-108 to remove non-ASCII characters, but pushed a bit
     * to the limit on our character handling. Tests that Japanese [in this case Kanji and Hiragana, without Katakana]
     * characters are replaced.
     */
    public void testNormalizeStringJapanese() {
        new NormalizeStringTest("normalizeStringJapanese.das")
                .withData(
                        new LocallyCacheableDicomTransformation("normalize_string_japanese")
                                .createZip()
                                .data(TestData.ANON_2)
                                .simpleTransform((dicom) -> {
                                    dicom.setSpecificCharacterSet("", "ISO 2022 IR 87");
                                    dicom.setString(Tag.PatientBirthName, VR.PN, "Yamada^Tarou=山田^太郎=やまだ^たろう"); // example from PS3.5 section H.3
                                })
                ).withValidation((root) -> root.putValueEqualCheck("(0010,1005)", "Yamada^Tarou=__^__=___^___"))
                .run();
    }

    private class NormalizeStringTest extends BasicAnonymizationTest {

        NormalizeStringTest(String scriptName) {
            super(scriptName);
            withData(ANON2_WITH_SPECIAL_CHARS);
            withValidation(COMMON_VALIDATION);
        }

    }

    // this will hopefully be thrown out later. What DicomEdit is doing right now with UN elements is not good
    private String formatExtendedStringPostNormalize(String inputString) {
        return inputString
                .codePoints()
                .mapToObj(x -> {
                    if (x > 127) {
                        return formatExtendedStringPostNormalize("\\" + Integer.toHexString(x).toUpperCase());
                    } else {
                        return String.valueOf(x);
                    }
                }).collect(Collectors.joining("\\"));
    }

}
