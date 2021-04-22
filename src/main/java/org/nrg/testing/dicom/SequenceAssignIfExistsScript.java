package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class SequenceAssignIfExistsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putNonexistenceChecks("(0008,1115)");
        final DicomObject referencedStudySeqItem = new DicomObject();
        referencedStudySeqItem.putValueEqualCheck("(0008,1155)", "1.3.46.670589.11.5730.5.0.1744.2010043012343685002");
        referencedStudySeqItem.putNonexistenceChecks("(0020,0013)");
        root.putSequenceCheck("(0008,1110)", new DicomSequence(referencedStudySeqItem));
        final DicomObject referencedPerformedProcedureStepSeqItem = new DicomObject();
        referencedPerformedProcedureStepSeqItem.putValueEqualCheck("(0008,0013)", "131758");
        referencedPerformedProcedureStepSeqItem.putValueEqualCheck("(0020,0013)", "10");
        root.putSequenceCheck("(0008,1111)", new DicomSequence(referencedPerformedProcedureStepSeqItem));

        return root;
    }

}