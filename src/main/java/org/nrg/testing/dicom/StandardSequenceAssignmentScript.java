package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class StandardSequenceAssignmentScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        final DicomObject opIdSeqItem = new DicomObject();
        opIdSeqItem.putValueEqualCheck("(0008,0080)", "WUSTL");
        root.putSequenceCheck("(0008,1072)", new DicomSequence(opIdSeqItem));

        final DicomObject sharedFunctionalGroupsSeqItem0 = new DicomObject();
        sharedFunctionalGroupsSeqItem0.putNonexistenceChecks("(0008,0100)");
        sharedFunctionalGroupsSeqItem0.putValueEqualCheck("(0018,9180)", "ELECTRIC_FIELD");
        final DicomObject sharedFunctionalGroupsSeqItem1 = new DicomObject();
        sharedFunctionalGroupsSeqItem1.putValueEqualCheck("(0008,0100)", "NICE");
        sharedFunctionalGroupsSeqItem1.putValueEqualCheck("(0018,9180)", "DB_DT");
        sharedFunctionalGroupsSeqItem1.putNonexistenceChecks("(0018,9182)");
        root.putSequenceCheck("(5200,9229)", new DicomSequence(sharedFunctionalGroupsSeqItem0, sharedFunctionalGroupsSeqItem1));

        root.putValueEqualCheck("(0040,1002)", "00209056");

        return root;
    }

}