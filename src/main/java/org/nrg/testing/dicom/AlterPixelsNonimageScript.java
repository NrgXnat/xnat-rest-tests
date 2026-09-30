package org.nrg.testing.dicom;

public class AlterPixelsNonimageScript extends SimpleInjectibleDicomValidation {

    @Override
    protected void validation(RootDicomObject root) {
        root.putNonexistenceChecks("(0008,0070)", "(0008,0080)", "(7FE0,0010)");
        root.putValueEqualCheck("(0008,0081)", "SOMEWHERE");
    }

}
