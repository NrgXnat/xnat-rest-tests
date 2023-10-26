package org.nrg.testing.dicom;

import java.util.function.Consumer;

public class BlankKnownPhiWildcards extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
        root.putEmptyChecks("(0008,9205)", "(0008,9206)", "(0008,9207)", "(0008,9208)", "(0008,9209)");
        root.putValueEqualCheck("(0018,9012)", "NO");
        root.putEmptyChecks("(0018,9016)", "(0018,9017)", "(0018,9020)", "(0018,9025)", "(0018,9027)",
                "(0018,9028)", "(0018,9029)", "(0018,9037)", "(0018,9170)", "(0018,9172)");
        root.putValueEqualCheck("(0028,0004)", "MONOCHROME2");

        root.putSequenceCheck("(5200,9229)", (sharedFunctionalGroupsSequenceItem) -> {
            sharedFunctionalGroupsSequenceItem.putSequenceCheck("(0018,9006)", (mrImagingModifierSequenceItem) -> {
                mrImagingModifierSequenceItem.putValueEqualCheck("(0018,9022)", "NO");
                mrImagingModifierSequenceItem.putEmptyChecks("(0018,9020)", "(0018,9028)");
            });

            sharedFunctionalGroupsSequenceItem.putSequenceCheck("(0018,9042)", (mrReceiveCoilSequenceItem) -> {
                mrReceiveCoilSequenceItem.putValueEqualCheck("(0018,1250)", "SENSE-Head-8");
                mrReceiveCoilSequenceItem.putEmptyChecks("(0018,9041)");
                mrReceiveCoilSequenceItem.putValueEqualCheck("(0018,9043)", "MULTICOIL");
                mrReceiveCoilSequenceItem.putValueEqualCheck("(0018,9044)", "NO");
                mrReceiveCoilSequenceItem.putSequenceCheck("(0018,9045)", (multiCoilDefinitionSequenceItem) -> {
                    multiCoilDefinitionSequenceItem.putValueEqualCheck("(0018,9047)", "SENSE");
                    multiCoilDefinitionSequenceItem.putValueEqualCheck("(0018,9048)", "YES");
                });
            });

            sharedFunctionalGroupsSequenceItem.putSequenceCheck("(0018,9115)", (mrModifierSequenceItem) -> {
                mrModifierSequenceItem.putEmptyChecks("(0018,9010)", "(0018,9027)");
                mrModifierSequenceItem.putValueEqualCheck("(0018,9009)", "NO");
                mrModifierSequenceItem.putValueEqualCheck("(0018,9021)", "NO");
                mrModifierSequenceItem.putValueEqualCheck("(0018,9026)", "WATER");
                mrModifierSequenceItem.putValueEqualCheck("(0018,9077)", "NO");
                mrModifierSequenceItem.putValueEqualCheck("(0018,9081)", "NO");
            });

            sharedFunctionalGroupsSequenceItem.putSequenceCheck("(2005,140e)", (privateSequenceItem) -> {
                privateSequenceItem.putValueEqualCheck("(0008,0014)", "1.3.46.670589.11.5730.5");
                privateSequenceItem.putEmptyChecks("(0018,9016)");
                privateSequenceItem.putValueEqualCheck("(0018,9081)", "NO");
            });
        });

        final Consumer<DicomObject> commonMrImageFrameTypeSequenceItem = (sequenceItem) -> {
            sequenceItem.putValueEqualCheck("(0008,9007)", AnonConstants.ANON2_IMAGE_TYPE);
            sequenceItem.putEmptyChecks("(0008,9205)", "(0008,9206)", "(0008,9207)", "(0008,9208)", "(0008,9209)");
        };
        final Consumer<DicomObject> commonPrivateSequenceItem = (sequenceItem) -> {
            sequenceItem.putEmptyChecks("(2005,1004)");
            sequenceItem.putValueEqualCheck("(2005,1061)", "NO");
        };

        root.putSequenceCheck(
                "(5200,9230)",
                (firstItem) -> {
                    firstItem.putEmptyChecks("(0008,9208)");
                    firstItem.putValueEqualCheck("(0008,9209)", "FLOW_ENCODED");
                    firstItem.putSequenceCheck("(0018,9226)", commonMrImageFrameTypeSequenceItem);
                    firstItem.putSequenceCheck("(0028,9132)", (innerSequenceItem) -> {
                        innerSequenceItem.putValueEqualCheck("(0028,1050)", "1070");
                        innerSequenceItem.putValueEqualCheck("(0028,1051)", "1860");
                    });
                    firstItem.putSequenceCheck("(2005,140f)", commonPrivateSequenceItem);
                }, (secondItem) -> {
                    secondItem.putSequenceCheck("(0018,9226)", commonMrImageFrameTypeSequenceItem);
                    secondItem.putSequenceCheck("(2005,140f)", commonPrivateSequenceItem);
                }, (thirdItem) -> {
                    thirdItem.putSequenceCheck("(0018,9226)", commonMrImageFrameTypeSequenceItem);
                    thirdItem.putSequenceCheck("(0028,9132)", (innerSequenceItem) -> {
                        innerSequenceItem.putValueEqualCheck("(0028,1050)", "1087");
                        innerSequenceItem.putValueEqualCheck("(0028,1051)", "1890");
                    });
                    thirdItem.putSequenceCheck("(2005,140f)", commonPrivateSequenceItem);
                }
        );
        return root;
    }

}
