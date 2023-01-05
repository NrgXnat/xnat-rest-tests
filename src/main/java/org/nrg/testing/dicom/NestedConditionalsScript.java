package org.nrg.testing.dicom;

public class NestedConditionalsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0060)", "MR");
        root.putValueEqualCheck("(0008,0050)", "if");
        root.putValueEqualCheck("(0008,103E)", "thisone");
        root.putValueEqualCheck("(0008,1030)", "else");
        root.putValueEqualCheck("(0008,1090)", "nowthis");
        root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
        root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");

        return root;
    }

}
