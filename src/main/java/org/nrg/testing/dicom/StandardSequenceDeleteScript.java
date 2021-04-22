package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class StandardSequenceDeleteScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        final DicomObject interventionDrugInfoSeqItem = new DicomObject();
        interventionDrugInfoSeqItem.putValueEqualCheck("(0018,0028)", "150000");
        interventionDrugInfoSeqItem.putValueEqualCheck("(0018,0034)", "CHOCOLATE");
        final DicomObject interventionDrugCodeSeqItem = new DicomObject();
        interventionDrugCodeSeqItem.putValueEqualCheck("(0008,0100)", "C-21047");
        interventionDrugCodeSeqItem.putValueEqualCheck("(0008,0104)", "Ethanol");
        interventionDrugInfoSeqItem.putSequenceCheck("(0018,0029)", new DicomSequence(interventionDrugCodeSeqItem));
        root.putSequenceCheck("(0018,0026)", new DicomSequence(interventionDrugInfoSeqItem));

        root.putNonexistenceChecks("(0040,100A)");

        final DicomObject dimensionIndexSeqItem0 = new DicomObject();
        dimensionIndexSeqItem0.putValueEqualCheck("(0020,9421)", "Stack ID");
        final DicomObject dimensionIndexSeqItem1 = new DicomObject();
        dimensionIndexSeqItem1.putValueEqualCheck("(0020,9421)", "In-Stack Position Number");
        root.putSequenceCheck("(0020,9222)", new DicomSequence(dimensionIndexSeqItem0, dimensionIndexSeqItem1));

        final DicomObject performedProtocolCodeSeqItem = new DicomObject();
        performedProtocolCodeSeqItem.putValueEqualCheck("(0008,0100)", "UNDEFINED");
        performedProtocolCodeSeqItem.putNonexistenceChecks("(0008,010b)");
        root.putSequenceCheck("(0040,0260)", new DicomSequence(performedProtocolCodeSeqItem));

        return root;
    }

}
