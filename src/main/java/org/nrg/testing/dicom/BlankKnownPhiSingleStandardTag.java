package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class BlankKnownPhiSingleStandardTag extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0018,9020)", "");
        root.putValueEqualCheck("(0008,9207)", "");
        root.putValueEqualCheck("(0018,9028)", "");
        root.putValueEqualCheck("(0018,9029)", "");
        root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);

        final DicomObject mrImagingModifierSequenceItem = new DicomObject();
        mrImagingModifierSequenceItem.putValueEqualCheck("(0018,9022)", "NO");
        mrImagingModifierSequenceItem.putValueEqualCheck("(0018,9020)", "");
        mrImagingModifierSequenceItem.putValueEqualCheck("(0018,9028)", "");

        final DicomObject sharedFunctionalGroupsSequenceItem = new DicomObject();
        sharedFunctionalGroupsSequenceItem.putSequenceCheck("(0018,9006)", new DicomSequence(mrImagingModifierSequenceItem));
        root.putSequenceCheck("(5200,9229)", new DicomSequence(sharedFunctionalGroupsSequenceItem));
        root.putNonexistenceChecks("(0010,0217)"); // for testBlankKnownPhiNonexistentElement, which reuses this class

        return root;
    }

}
