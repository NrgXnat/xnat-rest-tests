package org.nrg.testing.dicom;

public class StringFunctionsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0010,1000)", "mr_PDW_TSE_01204567_102030");

        return root;
    }

}
