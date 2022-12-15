package org.nrg.testing.dicom;

public class IfElseScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0060)", "if");
        root.putValueEqualCheck("(0008,1080)", "this");
        root.putValueEqualCheck("(0008,0080)", "BU SCHOOL OF MEDICINE");
        root.putValueEqualCheck("(0008,1030)", "else");
        root.putValueEqualCheck("(0008,1090)", "thisone");
        root.putValueEqualCheck("(0008,0070)", "Philips Medical Systems");

        return root;
    }

}
