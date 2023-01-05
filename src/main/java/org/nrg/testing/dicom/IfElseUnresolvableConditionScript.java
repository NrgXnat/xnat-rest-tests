package org.nrg.testing.dicom;

public class IfElseUnresolvableConditionScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck( "(0008,0060)", "MR");
        root.putValueEqualCheck( "(0008,1080)", "this");
        root.putValueEqualCheck( "(0008,0050)", "thisone");
        root.putValueEqualCheck( "(0008,0005)", "ISO_IR 100");
        root.putValueEqualCheck( "(0008,0008)", "else");
        root.putValueEqualCheck( "(0008,0012)", "20100719");
        root.putValueEqualCheck( "(0008,0013)", "else2");

        return root;
    }

}
