package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.dicom.AnonConstants;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.xnat.versions.Xnat_1_8_10;

import java.util.function.Consumer;

public class TestAnonymizationSyntaxMiscellanea extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> COMMON_VALIDATION = (root) -> root.putValueEqualCheck(Tag.CodeValue, "INSERTED STRING");

    @AddedIn(Xnat_1_8_10.class)
    public void testMultilineString() {
        new SyntaxTest("multilineString.das")
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testMultilineAssignString() {
        new SyntaxTest("multilineAssignString.das")
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testMultilineAssignStringAsVariable() {
        new SyntaxTest("multilineAssignStringVariable.das")
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testMultilineTernary() {
        new SyntaxTest("multilineTernary.das")
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    public void testCommentLikeStrings() {
        new SyntaxTest("commentLike.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck(Tag.CodeValue, "//not a comment", VR.SH);
                    root.putValueEqualCheck(Tag.CodingSchemeDesignator, "https://stillnotacomment", VR.SH);
                    root.putValueEqualCheck(Tag.CodingSchemeVersion, "/NOT/A/COMMENT//", VR.SH);
                    root.putValueEqualCheck(Tag.CodeMeaning, "http//nope", VR.LO);
                    root.putValueEqualCheck(Tag.ImageType, AnonConstants.ANON2_IMAGE_TYPE, VR.CS);
                }).run();
    }

    private class SyntaxTest extends BasicAnonymizationTest {

        SyntaxTest(String scriptName) {
            super(SYNTAX_MISCELLANEA, scriptName);
        }

    }
}
