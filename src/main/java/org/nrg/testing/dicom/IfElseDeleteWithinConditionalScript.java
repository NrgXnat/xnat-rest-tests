package org.nrg.testing.dicom;

public class IfElseDeleteWithinConditionalScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0060)", "MR");
        root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
        root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
        root.putValueEqualCheck("(0008,0012)", "20100719");
        root.putValueEqualCheck("(0008,0013)", "131758");
        root.putValueEqualCheck("(0008,0021)", "20100430");
        root.putNonexistenceChecks("(0008,0020)", "(0008,1080)","(0008,0050)", "(0008,103E)", "(0008,0031)", "(0008,1090)");

        return root;
    }

}
