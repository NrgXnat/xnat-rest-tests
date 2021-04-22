package org.nrg.testing.dicom;

public class AssignToNullScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0010,1005)", "");
        root.putValueEqualCheck("(0010,1050)", "");

        return root;
    }

}
