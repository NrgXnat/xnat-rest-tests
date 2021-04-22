package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class StandardWildcardedDeleteScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        for (String wildcardCheck : new String[]{"(0018,901#)", "(0018,905@)", "(0018,93XX)", "(0044,00XX)"}) {
            root.putWildcardedNonexistenceCheck(wildcardCheck);
        }
        root.putValueEqualCheck("(0018,9012)", "NO");
        root.putValueEqualCheck("(0018,9051)", "BODY");

        // drill down into sequences to make sure the wildcarded elements were *not* removed

        final DicomObject sharedFunctionalGroupsSeqItem = new DicomObject();
        final DicomObject innerMRTransmitCoilSeqItem = new DicomObject();
        innerMRTransmitCoilSeqItem.putValueEqualCheck("(0018,9050)", "");
        innerMRTransmitCoilSeqItem.putValueEqualCheck("(0018,9051)", "BODY");
        sharedFunctionalGroupsSeqItem.putSequenceCheck("(0018,9049)", new DicomSequence(innerMRTransmitCoilSeqItem));
        root.putSequenceCheck("(5200,9229)", new DicomSequence(sharedFunctionalGroupsSeqItem));

        return root;
    }

}
