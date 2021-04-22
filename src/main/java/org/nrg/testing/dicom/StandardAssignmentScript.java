package org.nrg.testing.dicom;

public class StandardAssignmentScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0064)", "WSD");
        root.putValueEqualCheck("(0008,0050)", "REMOVED");
        root.putValueEqualCheck("(0008,1040)", "Dept A");
        root.putValueEqualCheck("(0018,0022)", "1\\2\\3\\4");
        root.putValueEqualCheck("(0008,0061)", "MR");
        root.putValueEqualCheck("(0010,1010)", "");
        root.putValueEqualCheck("(0018,1003)", "MR");
        root.putValueEqualCheck("(0018,1004)", "MR");

        return root;
    }

}
