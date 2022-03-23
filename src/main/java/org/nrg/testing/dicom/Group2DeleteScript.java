package org.nrg.testing.dicom;

public class Group2DeleteScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putNonexistenceChecks("(0002,0016)");

        return root;
    }

}
