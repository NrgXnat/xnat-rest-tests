package org.nrg.testing.dicom;

public class IfElseVariableScopeScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0060)", "if");
        root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
        root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
        root.putValueEqualCheck("(0008,1030)", "2131");
        root.putValueEqualCheck("(0008,1090)", "foobar");
        root.putValueEqualCheck("(0008,0070)", "123456");

        return root;
    }

}
