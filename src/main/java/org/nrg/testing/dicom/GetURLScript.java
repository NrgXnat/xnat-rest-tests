package org.nrg.testing.dicom;

public class GetURLScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0010,1000)", "text_from_URL");

        return root;
    }

}
