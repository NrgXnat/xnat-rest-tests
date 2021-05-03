package org.nrg.testing.dicom;

public class IsMatchScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,1010)", "true");
        root.putValueEqualCheck("(0008,1040)", "false");

        return root;
    }

}
