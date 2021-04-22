package org.nrg.testing.dicom;

public class StandardDeleteScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putNonexistenceChecks("(0008,0105)", "(0008,0033)", "(0008,002A)", "(0008,1110)");
        return root;
    }

}
