package org.nrg.testing.dicom;

public class TransferSyntaxScript extends SimplestDicomScriptValidation {

    private String transferSyntaxUID;

    public TransferSyntaxScript(String transferSyntaxUID) {
        this.transferSyntaxUID = transferSyntaxUID;
    }

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0002,0010)", transferSyntaxUID);

        return root;
    }

}
