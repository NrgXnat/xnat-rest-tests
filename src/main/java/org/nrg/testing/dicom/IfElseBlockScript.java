package org.nrg.testing.dicom;

public class IfElseBlockScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck( "(0008,0060)", "if");
        root.putValueEqualCheck( "(0008,1080)", "this");
        root.putValueEqualCheck( "(0008,0050)", "elseif");
        root.putValueEqualCheck( "(0008,103E)", "thisone");
        root.putValueEqualCheck( "(0008,1030)", "else");
        root.putValueEqualCheck( "(0008,1090)", "nowthis");

        return root;
    }

}
