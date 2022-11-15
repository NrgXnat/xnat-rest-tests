package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class ShiftDateTimePrecisionScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        final DicomObject seqItem0 = new DicomObject();
        seqItem0.putValueEqualCheck( 0x0044000B, "1960");
        final DicomObject seqItem1 = new DicomObject();
        seqItem1.putValueEqualCheck( 0x0044000B, "196005");
        final DicomObject seqItem2 = new DicomObject();
        seqItem2.putValueEqualCheck( 0x0044000B, "19600519");
        final DicomObject seqItem3 = new DicomObject();
        seqItem3.putValueEqualCheck( 0x0044000B, "1960051913");
        final DicomObject seqItem4 = new DicomObject();
        seqItem4.putValueEqualCheck( 0x0044000B, "196005191324");
        final DicomObject seqItem5 = new DicomObject();
        seqItem5.putValueEqualCheck( 0x0044000B, "19600519132435");

        root.putSequenceCheck("(0008,1110)", new DicomSequence(seqItem0, seqItem1, seqItem2, seqItem3, seqItem4, seqItem5));

        return root;
    }

}
