package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class DeleteFunctionScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putNonexistenceChecks("(0008,0080)", "(0008,0081)", "(0008,1110)");
        final DicomObject mrTimingSeqItem = new DicomObject();
        mrTimingSeqItem.putNonexistenceChecks("(0018,0080)");
        mrTimingSeqItem.putNonexistenceChecks("(0018,9176)");
        final DicomObject specificAbsorptionItem0 = new DicomObject();
        specificAbsorptionItem0.putValueEqualCheck("(0018,9179)", "IEC_LOCAL");
        specificAbsorptionItem0.putValueEqualCheck("(0018,9181)", "10");
        final DicomObject specificAbsorptionItem1 = new DicomObject();
        specificAbsorptionItem1.putValueEqualCheck("(0018,9179)", "IEC_WHOLE_BODY");
        specificAbsorptionItem1.putNonexistenceChecks("(0018,9181)");
        mrTimingSeqItem.putSequenceCheck("(0018,9239)", new DicomSequence(specificAbsorptionItem0, specificAbsorptionItem1));
        root.putSequenceCheck("(0018,9112)", new DicomSequence(mrTimingSeqItem));
        root.putNonexistenceChecks("(2001,1019)", "(2001,101f)");
        final DicomObject privateSeqItem0 = new DicomObject();
        privateSeqItem0.putValueEqualCheck("(2001,1033)", "AP");
        privateSeqItem0.putValueEqualCheck("(2005,1081)", "FH");
        privateSeqItem0.putNonexistenceChecks("(2005,10a3)");
        root.putSequenceCheck("(2001,105f)", new DicomSequence(privateSeqItem0));
        return root;
    }

}
