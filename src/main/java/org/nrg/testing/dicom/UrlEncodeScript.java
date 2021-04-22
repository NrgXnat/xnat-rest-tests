package org.nrg.testing.dicom;

public class UrlEncodeScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0010,1000)", "0%3D1");

        return root;
    }

}
