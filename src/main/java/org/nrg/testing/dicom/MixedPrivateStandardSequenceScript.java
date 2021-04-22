package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class MixedPrivateStandardSequenceScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        final DicomObject privateSeqItem = new DicomObject();
        privateSeqItem.putNonexistenceChecks("(2001,1032)", "(2005,133e)");
        privateSeqItem.putValueEqualCheck("(2001,1033)", "AP");
        privateSeqItem.putValueEqualCheck("(2005,1390)", "R_A");
        root.putSequenceCheck("(2001,105F)", new DicomSequence(privateSeqItem));

        final DicomObject perFrameFunctionalGroupsSeqItem0 = new DicomObject();
        final DicomObject innerSeqItem0 = new DicomObject();
        innerSeqItem0.putValueEqualCheck("(0018,0021)", "SK");
        perFrameFunctionalGroupsSeqItem0.putSequenceCheck("(2005,140f)", new DicomSequence(innerSeqItem0));
        final DicomObject perFrameFunctionalGroupsSeqItem1 = new DicomObject();
        perFrameFunctionalGroupsSeqItem1.putWildcardedNonexistenceCheck("(2005,14XX)");
        final DicomObject perFrameFunctionalGroupsSeqItem2 = new DicomObject();
        final DicomObject innerSeqItem1 = new DicomObject();
        innerSeqItem1.putValueEqualCheck("(0018,0020)", "SE");
        perFrameFunctionalGroupsSeqItem2.putSequenceCheck("(2005,140f)", new DicomSequence(innerSeqItem1));
        root.putSequenceCheck("(5200,9230)", new DicomSequence(perFrameFunctionalGroupsSeqItem0, perFrameFunctionalGroupsSeqItem1, perFrameFunctionalGroupsSeqItem2));

        return root;
    }

}
