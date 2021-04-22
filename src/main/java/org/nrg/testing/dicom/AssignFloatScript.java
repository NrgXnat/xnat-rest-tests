package org.nrg.testing.dicom;

public class AssignFloatScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putValueEqualCheck("(0018,9182)", "30");
        return root;
    }

}
