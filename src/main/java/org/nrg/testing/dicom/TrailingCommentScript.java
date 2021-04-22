package org.nrg.testing.dicom;

public class TrailingCommentScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putNonexistenceChecks("(0028,0004)");
        return root;
    }

}
