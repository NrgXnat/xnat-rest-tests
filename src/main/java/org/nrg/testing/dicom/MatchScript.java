package org.nrg.testing.dicom;

public class MatchScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0010,1000)", "b");

        return root;
    }

}
