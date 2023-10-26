package org.nrg.testing.dicom;

public class BlankKnownPhiVariable extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putEmptyChecks("(0018,9020)", "(0044,0001)", "(0018,9011)", "(0018,9100)");
        root.putValueEqualCheck("(0018,9004)", "RESEARCH");
        root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
        root.putEmptyChecks("(0018,9016)", "(0018,9017)", "(0018,9037)", "(0018,9170)", "(0018,9172)");

        root.putSequenceCheck(
                "(0018,0026)",
                (item1) -> {
                    item1.putValueEqualCheck("(0018,0028)", "150000");
                    item1.putSequenceCheck("(0018,0029)", (innerItem) -> {
                        innerItem.putValueEqualCheck("(0008,0100)", "C-21047");
                        innerItem.putValueEqualCheck("(0008,0104)", "Ethanol");
                    });
                    item1.putEmptyChecks("(0018,0034)");
                }, (item2) -> {
                    item2.putValueEqualCheck("(0018,0028)", "100000");
                    item2.putEmptyChecks("(0018,0034)");
                }
        );

        root.putEmptyChecks("(0018,980b)");

        root.putSequenceCheck("(5200,9229)", (item) -> {
            item.putSequenceCheck("(0018,9006)", (innerItem) -> {
                innerItem.putValueEqualCheck("(0018,9022)", "NO");
                innerItem.putEmptyChecks("(0018,9020)", "(0018,9028)");
            });

            item.putSequenceCheck("(0018,9042)", (innerItem) -> {
                innerItem.putValueEqualCheck("(0018,9044)", "NO");
                innerItem.putSequenceCheck("(0018,9045)", (coilItem) -> {
                    coilItem.putValueEqualCheck("(0018,9047)", "SENSE");
                    coilItem.putEmptyChecks("(0018,9048)");
                });
            });
        });

        root.putEmptyChecks("(2001,1087)");
        root.putValueEqualCheck("(2001,1088)", "1");

        return root;
    }

}
