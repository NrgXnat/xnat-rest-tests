package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class ShiftDateTimeSequenceScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        final DicomObject perFrameFunctionalSeqItem0 = new DicomObject();
        final DicomObject perFrameFunctionalSeqItem1 = new DicomObject();
        final DicomObject perFrameFunctionalSeqItem2 = new DicomObject();
        for (DicomObject seqItem : new DicomObject[]{perFrameFunctionalSeqItem0, perFrameFunctionalSeqItem1, perFrameFunctionalSeqItem2}) {
            final DicomObject frameContentSeqItem = new DicomObject();
            frameContentSeqItem.putValueEqualCheck("(0018,9151)", "20100501130441.40");
            frameContentSeqItem.putValueEqualCheck("(0018,9074)", "20100429130441.40");
            seqItem.putSequenceCheck("(0020,9111)", new DicomSequence(frameContentSeqItem));
            if (seqItem == perFrameFunctionalSeqItem0) {
                frameContentSeqItem.putValueEqualCheck("(0008,002A)", "20200101100010");
            } else {
                frameContentSeqItem.putNonexistenceChecks("(0008,002A)");
            }
        }
        root.putSequenceCheck("(5200,9230)", new DicomSequence(perFrameFunctionalSeqItem0, perFrameFunctionalSeqItem1, perFrameFunctionalSeqItem2));

        return root;
    }

}
