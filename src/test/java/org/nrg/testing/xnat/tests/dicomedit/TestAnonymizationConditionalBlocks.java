package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.IfElseAlterPixelsScript;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_7;

import java.util.function.Consumer;

@TestRequires(admin = true, data = TestData.ANON_2)
@AddedIn(Xnat_1_8_7.class) // Tests DE-45
public class TestAnonymizationConditionalBlocks extends BaseAnonymizationTest {
    
    private static final Consumer<RootDicomObject> REUSED_IF_ELSE_VALIDATION = (root) -> {
        root.putValueEqualCheck("(0008,0060)", "if");
        root.putValueEqualCheck("(0008,1080)", "this");
        root.putValueEqualCheck("(0008,0050)", "elseif");
        root.putValueEqualCheck("(0008,103E)", "thisone");
        root.putValueEqualCheck("(0008,1030)", "else");
        root.putValueEqualCheck("(0008,1090)", "nowthis");
        root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
        root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
        root.putValueEqualCheck("(0008,0012)", "20100719");
        root.putValueEqualCheck("(0008,0013)", "131758");
        root.putValueEqualCheck("(0008,0020)", "20100430");
        root.putValueEqualCheck("(0008,0021)", "20100430");
    };

    public void testIfElseBlocks() {
        new BasicAnonymizationTest("ifElseBlocks.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0060)", "if");
                    root.putValueEqualCheck("(0008,1080)", "this");
                    root.putValueEqualCheck("(0008,0050)", "elseif");
                    root.putValueEqualCheck("(0008,103E)", "thisone");
                    root.putValueEqualCheck("(0008,1030)", "else");
                    root.putValueEqualCheck("(0008,1090)", "nowthis");
                }).run();
    }

    public void testIfElse() {
        new BasicAnonymizationTest("ifElse.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0060)", "if");
                    root.putValueEqualCheck("(0008,1080)", "this");
                    root.putValueEqualCheck("(0008,0080)", "BU SCHOOL OF MEDICINE");
                    root.putValueEqualCheck("(0008,1030)", "else");
                    root.putValueEqualCheck("(0008,1090)", "thisone");
                    root.putValueEqualCheck("(0008,0070)", "Philips Medical Systems");
                }).run();
    }

    public void testMultipleElseIf() {
        new BasicAnonymizationTest("multipleElseIf.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0005)", "if");
                    root.putValueEqualCheck("(0008,0012)", "this");
                    root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
                    root.putValueEqualCheck("(0008,0013)", "131758");
                    root.putValueEqualCheck("(0008,0014)", "1.3.46.670589.11.5730.5");
                    root.putValueEqualCheck("(0008,0020)", "20100430");
                    root.putValueEqualCheck("(0008,0021)", "elseif1");
                    root.putValueEqualCheck("(0008,0022)", "thisone");
                    root.putValueEqualCheck("(0008,002a)", "20100430130441.40");
                    root.putValueEqualCheck("(0008,0030)", "123437");
                    root.putValueEqualCheck("(0008,0031)", "130441.40");
                    root.putValueEqualCheck("(0008,0033)", "131758");
                    root.putValueEqualCheck("(0008,0050)", "elseif2");
                    root.putValueEqualCheck("(0008,0060)", "nowthis");
                    root.putValueEqualCheck("(0008,0070)", "Philips Medical Systems");
                    root.putValueEqualCheck("(0008,0080)", "BU SCHOOL OF MEDICINE");
                    root.putValueEqualCheck("(0008,0102)", "DCM");
                    root.putValueEqualCheck("(0008,1010)", "PHILIPS-13EFFD8");
                    root.putValueEqualCheck("(0008,1030)", "else");
                    root.putValueEqualCheck("(0008,103e)", "here");
                    root.putValueEqualCheck("(0018,0087)", "3");
                    root.putValueEqualCheck("(0018,0088)", "2");
                    root.putValueEqualCheck("(0018,0095)", "226");
                    root.putValueEqualCheck("(0018,1000)", "05730");
                    root.putValueEqualCheck("(0018,1020)", "2.6.3\\2.6.3.4");
                    root.putValueEqualCheck("(0018,1030)", "PDW_TSE CLEAR");
                    root.putValueEqualCheck("(0018,5100)", "elseif3");
                    root.putValueEqualCheck("(0018,9004)", "thenthis");
                    root.putValueEqualCheck("(0018,9005)", "TSE");
                    root.putValueEqualCheck("(0018,9008)", "SPIN");
                }).run();
    }

    public void testIfElseDirectTag() {
        new BasicAnonymizationTest("ifElseDirectTag.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0060)", "if");
                    root.putValueEqualCheck("(0008,1080)", "this");
                    root.putValueEqualCheck("(0008,0050)", "elseif");
                    root.putValueEqualCheck("(0008,103E)", "thisone");
                    root.putValueEqualCheck("(0008,1030)", "else");
                    root.putValueEqualCheck("(0008,1090)", "nowthis");
                    root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
                    root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
                    root.putValueEqualCheck("(0008,0012)", "20100719");
                    root.putValueEqualCheck("(0008,0013)", "131758");
                    root.putValueEqualCheck("(0008,0020)", "20100430");
                    root.putValueEqualCheck("(0008,0021)", "20100430");
                }).run();
    }

    public void testIfElseNoNewlineBlock() {
        new BasicAnonymizationTest("ifElseNoNewlineBlock.das")
                .withValidation(REUSED_IF_ELSE_VALIDATION)
                .run();
    }

    public void testIfElseDirectPrivateTag() {
        new BasicAnonymizationTest("ifElseDirectPrivateTag.das")
                .withValidation(REUSED_IF_ELSE_VALIDATION)
                .run();
    }

    public void testIfElseDirectSequencePath() {
        new BasicAnonymizationTest("ifElseDirectSequencePath.das")
                .withValidation(REUSED_IF_ELSE_VALIDATION)
                .run();
    }

    public void testIfElseDoesNotEqual() {
        new BasicAnonymizationTest("ifElseDoesNotEqual.das")
                .withValidation(REUSED_IF_ELSE_VALIDATION)
                .run();
    }

    public void testIfElseLiteralTabs() {
        new BasicAnonymizationTest("ifElseLiteralTabs.das")
                .withValidation(REUSED_IF_ELSE_VALIDATION)
                .run();
    }

    public void testIfElseUnresolvableCondition() {
         new BasicAnonymizationTest("ifElseUnresolvableCondition.das")
                 .withValidation((root) -> {
                     root.putValueEqualCheck( "(0008,0060)", "MR");
                     root.putValueEqualCheck( "(0008,1080)", "this");
                     root.putValueEqualCheck( "(0008,0050)", "thisone");
                     root.putValueEqualCheck( "(0008,0005)", "ISO_IR 100");
                     root.putValueEqualCheck( "(0008,0008)", "else");
                     root.putValueEqualCheck( "(0008,0012)", "20100719");
                     root.putValueEqualCheck( "(0008,0013)", "else2");
                 }).run();
    }

    public void testIfElseNestedConditionals() {
        new BasicAnonymizationTest("nestedConditionals.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0060)", "MR");
                    root.putValueEqualCheck("(0008,0050)", "if");
                    root.putValueEqualCheck("(0008,103E)", "thisone");
                    root.putValueEqualCheck("(0008,1030)", "else");
                    root.putValueEqualCheck("(0008,1090)", "nowthis");
                    root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
                    root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
                }).run();
    }

    public void testIfElseMatches() {
        new BasicAnonymizationTest("ifElseMatches.das")
                .withValidation(REUSED_IF_ELSE_VALIDATION)
                .run();
    }

    public void testIfElseDoesNotMatch() {
        new BasicAnonymizationTest("ifElseDoesNotMatch.das")
                .withValidation(REUSED_IF_ELSE_VALIDATION)
                .run();
    }

    public void testIfElseDeleteWithinConditional() {
        new BasicAnonymizationTest("ifElseDeleteWithinConditional.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0060)", "MR");
                    root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
                    root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
                    root.putValueEqualCheck("(0008,0012)", "20100719");
                    root.putValueEqualCheck("(0008,0013)", "131758");
                    root.putValueEqualCheck("(0008,0021)", "20100430");
                    root.putNonexistenceChecks("(0008,0020)", "(0008,1080)","(0008,0050)", "(0008,103E)", "(0008,0031)", "(0008,1090)");
                }).run();
    }

    public void testIfElseRetainPrivateTags() {
        new BasicAnonymizationTest("ifElseRetainPrivateTags.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0060)", "MR");
                    root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
                    root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
                    root.putValueEqualCheck("(0008,0012)", "20100719");
                    root.putValueEqualCheck("(0008,0013)", "131758");
                    root.putValueEqualCheck("(0008,0021)", "20100430");
                    root.putExistenceChecks("(2001,1010)","(2001,1012)","(2001,1014)","(2001,105f)","(2001,1020)");
                    root.putNonexistenceChecks("(2005,1013)","(2005,106f)","(2005,1020)","(2005,1252)","(2005,1402)");
                }).run();
    }

    public void testIfElseVariableScope() {
        new BasicAnonymizationTest("ifElseVariableScope.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0060)", "if");
                    root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
                    root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
                    root.putValueEqualCheck("(0008,1030)", "2131");
                    root.putValueEqualCheck("(0008,1090)", "foobar");
                    root.putValueEqualCheck("(0008,0070)", "123456");
                }).run();
    }

    public void testIfElseAlterPixels() {
        new BasicAnonymizationTest("ifElseAlterPixels.das")
                .withValidation(new IfElseAlterPixelsScript())
                .run();
    }

}
