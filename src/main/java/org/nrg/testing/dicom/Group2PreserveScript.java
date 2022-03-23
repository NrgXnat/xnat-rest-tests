package org.nrg.testing.dicom;

public class Group2PreserveScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0002,0016)", "DicomBrowser");

        return root;
    }

}
