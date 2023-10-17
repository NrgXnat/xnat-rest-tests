package org.nrg.testing.dicom;

public class LateAnonAllPrivateElements extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
        root.putNonexistenceChecks("(0008,9205)", "(0015,1020)", "(0015,1021)", "(0044,0001)");
        root.putValueEqualCheck("(0044,0002)", "YES");
        root.putWildcardedNonexistenceCheck("(2001,XXXX)");
        root.putWildcardedNonexistenceCheck("(2005,XXXX)");
        root.putValueEqualCheck("(2050,0020)", "IDENTITY");

        root.putSequenceCheck(
                "(5200,9229)",
                (item) -> {
                    item.putValueEqualCheck("(0018,9180)", "ELECTRIC_FIELD");
                    item.putWildcardedNonexistenceCheck("(2005,XXXX)");
                }
        );

        root.putNonexistenceChecks(0x97531050, 0x97531051); // specified like this because we need integer overflow...

        root.putSequenceCheck(0xFFFAFFFA, (singleItem) -> {
            singleItem.putValueEqualCheck("(0400,0005)", "1000");
            singleItem.putNonexistenceChecks("(0400,0110)");
            singleItem.putValueEqualCheck("(0400,0305)", "CMS_TSP");
        });

        return root;
    }

}
