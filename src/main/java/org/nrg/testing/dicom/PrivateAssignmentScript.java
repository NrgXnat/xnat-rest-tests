package org.nrg.testing.dicom;

public class PrivateAssignmentScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(300A,000E)", "TSE");
        root.putValueEqualCheck("(2001,1006)", "YAH");
        root.putValueEqualCheck("(2001,1024)", "Y");
        root.putValueEqualCheck("(0039,0010)", "INSERTED PRIVATE CREATOR ID");
        root.putValueEqualCheck("(0039,1001)", "COOL");
        root.putValueEqualCheck("(0039,0011)", "ANOTHER");
        root.putValueEqualCheck("(0039,1199)", "Meow");
        root.putValueEqualCheck("(0039,11ee)", "Much Meow");

        return root;
    }

}
