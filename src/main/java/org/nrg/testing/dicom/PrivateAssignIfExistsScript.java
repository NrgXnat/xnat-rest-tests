package org.nrg.testing.dicom;

public class PrivateAssignIfExistsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putNonexistenceChecks("(2001,1004)");
        root.putValueEqualCheck("(2001,100c)", "Y");
        root.putValueEqualCheck("(2005,1391)", "PERSON^NAME");

        return root;
    }

}