package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class SequenceItemWildcardScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,1030)", "Fruit_Struct");
        final DicomObject perFrameFunctionalGroupsSeqItem0 = new DicomObject();
        final DicomObject perFrameFunctionalGroupsSeqItem1 = new DicomObject();
        final DicomObject perFrameFunctionalGroupsSeqItem2 = new DicomObject();

        for (DicomObject seqItem : new DicomObject[]{perFrameFunctionalGroupsSeqItem0, perFrameFunctionalGroupsSeqItem1, perFrameFunctionalGroupsSeqItem2}) {
            seqItem.putNonexistenceChecks("(0018,9152)", "(0008,1030)");
        }
        perFrameFunctionalGroupsSeqItem0.putValueEqualCheck("(0008,9208)", "MAGNITUDE");

        root.putSequenceCheck("(5200,9230)", new DicomSequence(perFrameFunctionalGroupsSeqItem0, perFrameFunctionalGroupsSeqItem1, perFrameFunctionalGroupsSeqItem2));

        return root;
    }

}
