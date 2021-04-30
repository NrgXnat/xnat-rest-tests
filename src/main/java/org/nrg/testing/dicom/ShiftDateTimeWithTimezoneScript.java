package org.nrg.testing.dicom;

public class ShiftDateTimeWithTimezoneScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0018,9516)", "20200101120010+0500");
        root.putValueEqualCheck("(0018,9517)", "20200101120010-0730");

        return root;
    }

}
