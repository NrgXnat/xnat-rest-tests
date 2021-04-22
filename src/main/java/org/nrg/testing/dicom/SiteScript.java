package org.nrg.testing.dicom;

public class SiteScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putNonexistenceChecks("(0044,0001)");
        return root;
    }

    @Override
    protected RootDicomObject generateValidationObjectForNonrunningScript() {
        final RootDicomObject root = new RootDicomObject();
        root.putValueEqualCheck("(0044,0001)", "CHOCOLATE");
        return root;
    }

}
