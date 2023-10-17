package org.nrg.testing.dicom;

public class LateAnonTargetedPrivateElements extends SimplestDicomScriptValidation {

    private boolean checkSequence;

    public LateAnonTargetedPrivateElements() {
        this(true);
    }

    public LateAnonTargetedPrivateElements(boolean checkSequence) {
        this.checkSequence = checkSequence;
    }

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
        root.putNonexistenceChecks("(0008,9205)", "(0044,0001)");
        root.putValueEqualCheck("(0015,1020)", "ABC");
        root.putValueEqualCheck("(0015,1021)", "XYZ");
        root.putValueEqualCheck("(0044,0002)", "YES");

        root.putValueEqualCheck("(2001,100c)", "N");
        root.putValueEqualCheck("(2001,100e)", "N");
        root.putValueEqualCheck("(2005,1038)", "N");
        root.putNonexistenceChecks("(2005,1039)", "(2005,10c0)");
        root.putValueEqualCheck("(2005,10a9)", "2D");

        root.putValueEqualCheck("(2050,0020)", "IDENTITY");

        root.putValueEqualCheck(0x97531050, "blah blah"); // specified like this because we need integer overflow...
        root.putNonexistenceChecks(0x97531051);

        if (checkSequence) {
            root.putSequenceCheck(0xFFFAFFFA, (singleItem) -> {
                singleItem.putValueEqualCheck("(0400,0005)", "1000");
                singleItem.putNonexistenceChecks("(0400,0110)");
                singleItem.putValueEqualCheck("(0400,0305)", "CMS_TSP");
            });
        }

        return root;
    }

}
