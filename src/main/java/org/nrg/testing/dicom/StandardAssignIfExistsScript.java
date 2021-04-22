package org.nrg.testing.dicom;

public class StandardAssignIfExistsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,1070)", "TECH^SCANNING");
        root.putValueEqualCheck("(0008,1090)", "REMOVED");
        root.putNonexistenceChecks("(0008,0061)");
        root.putValueEqualCheck("(0018,1020)", "1.0");

        return root;
    }

}