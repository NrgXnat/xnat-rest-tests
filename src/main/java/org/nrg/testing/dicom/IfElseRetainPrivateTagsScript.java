package org.nrg.testing.dicom;

public class IfElseRetainPrivateTagsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0060)", "MR");
        root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
        root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
        root.putValueEqualCheck("(0008,0012)", "20100719");
        root.putValueEqualCheck("(0008,0013)", "131758");
        root.putValueEqualCheck("(0008,0021)", "20100430");
        root.putExistenceChecks("(2001,1010)","(2001,1012)","(2001,1014)","(2001,105f)","(2001,1020)");
        root.putNonexistenceChecks("(2005,1013)","(2005,106f)","(2005,1020)","(2005,1252)","(2005,1402)");

        return root;
    }

}
