package org.nrg.testing.dicom;

public class PrivateConditionalsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putValueEqualCheck("(3006,0088", "Nice");
        root.putNonexistenceChecks("(3008,0066)", "(2001,110c)");
        return root;
    }

}
