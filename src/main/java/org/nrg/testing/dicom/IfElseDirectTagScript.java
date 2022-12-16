package org.nrg.testing.dicom;

public class IfElseDirectTagScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck( "(0008,0060)", "if");
        root.putValueEqualCheck( "(0008,1080)", "this");
        root.putValueEqualCheck( "(0008,0050)", "elseif");
        root.putValueEqualCheck( "(0008,103E)", "thisone");
        root.putValueEqualCheck( "(0008,1030)", "else");
        root.putValueEqualCheck( "(0008,1090)", "nowthis");
        root.putValueEqualCheck( "(0008,0005)", "ISO_IR 100");
        root.putValueEqualCheck( "(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
        root.putValueEqualCheck( "(0008,0012)", "20100719");
        root.putValueEqualCheck( "(0008,0013)", "131758");
        root.putValueEqualCheck( "(0008,0020)", "20100430");
        root.putValueEqualCheck( "(0008,0021)", "20100430");

        return root;
    }

}
