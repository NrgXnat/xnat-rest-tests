package org.nrg.testing.dicom;

public class StandardConditionalWithFunctionScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putExistenceChecks("(2001,0010)");
        root.putNonexistenceChecks("(2005,0010)");

        return root;
    }

}
