package org.nrg.testing.dicom;

public class ShiftDateTimeScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,002A)", "20100430131041.40");
        root.putValueEqualCheck("(0018,9516)", "20200101120010.000000");
        root.putValueEqualCheck("(0018,9517)", "20200101120010.800000");
        root.putValueEqualCheck("(0018,9804)", "20200229000100");
        root.putValueEqualCheck("(0018,9919)", "19960808135000");

        return root;
    }

}
