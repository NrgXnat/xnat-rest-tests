package org.nrg.testing.dicom;

public class ShiftDateScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0012)", "20100729");
        root.putValueEqualCheck("(0008,0020)", "20200301");
        root.putValueEqualCheck("(0008,0021)", "20210302");
        root.putValueEqualCheck("(0008,0022)", "20200223");
        root.putValueEqualCheck("(0008,0024)", "20191231");

        return root;
    }

}
