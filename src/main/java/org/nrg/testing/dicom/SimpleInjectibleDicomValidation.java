package org.nrg.testing.dicom;

public abstract class SimpleInjectibleDicomValidation extends SimplestDicomScriptValidation {

    protected abstract void validation(RootDicomObject root);

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        validation(root);
        return root;
    }

}
