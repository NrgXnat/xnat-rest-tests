package org.nrg.testing.dicom;

public class ProjectScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putNonexistenceChecks("(0044,000a)");
        return root;
    }

    @Override
    protected RootDicomObject generateValidationObjectForNonrunningScript() {
        final RootDicomObject root = new RootDicomObject();
        root.putValueEqualCheck("(0044,000a)", "RITTER");
        return root;
    }

}
