package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class LevelWildcardScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0010,0020)", "Watermelon_MR1");
        root.putValueEqualCheck("(0008,0102)", "DCM");
        root.putValueEqualCheck("(0018,0015)", "BRAIN");
        root.putValueEqualCheck("(0018,9034)", "UNKNOWN");

        final DicomObject otherPatientIDsSeqItem0 = new DicomObject();
        otherPatientIDsSeqItem0.putNonexistenceChecks("(0010,0020)");
        otherPatientIDsSeqItem0.putValueEqualCheck("(0010,0021)", "Hospital_0001");
        final DicomObject otherPatientIDsSeqItem1 = new DicomObject();
        otherPatientIDsSeqItem1.putNonexistenceChecks("(0010,0020)");
        otherPatientIDsSeqItem1.putValueEqualCheck("(0010,0021)", "Hospital_0002");
        final DicomObject otherPatientIDsSeqItem2 = new DicomObject();
        otherPatientIDsSeqItem0.putNonexistenceChecks("(0010,0020)");
        final DicomObject nestedOtherPatientIDsSeqItem = new DicomObject();
        nestedOtherPatientIDsSeqItem.putNonexistenceChecks("(0010,0020)");
        nestedOtherPatientIDsSeqItem.putValueEqualCheck("(0010,0021)", "Hospital_0003");
        otherPatientIDsSeqItem2.putSequenceCheck("(0010,1002)", new DicomSequence(nestedOtherPatientIDsSeqItem));
        final DicomObject otherPatientIDsSeqItem3 = new DicomObject();
        otherPatientIDsSeqItem3.putNonexistenceChecks("(0010,0020)");
        otherPatientIDsSeqItem3.putValueEqualCheck("(0010,0021)", "Hospital_0004");
        root.putSequenceCheck("(0010,1002)", new DicomSequence(
                otherPatientIDsSeqItem0,
                otherPatientIDsSeqItem1,
                otherPatientIDsSeqItem2,
                otherPatientIDsSeqItem3
        ));

        root.putNonexistenceChecks("(0010,2160)", "(0018,9035)", "(0018,9180)");

        final DicomObject privateSeqItem = new DicomObject();
        privateSeqItem.putNonexistenceChecks("(0018,9035)");
        privateSeqItem.putValueEqualCheck("(0018,0089)", "319");
        privateSeqItem.putValueEqualCheck("(0018,9034)", "UNKNOWN");
        final DicomObject sharedFunctionalGroupsSeqItem = new DicomObject();
        sharedFunctionalGroupsSeqItem.putSequenceCheck("(2005,140e)", new DicomSequence(privateSeqItem));
        sharedFunctionalGroupsSeqItem.putNonexistenceChecks("(0018,9180)");
        root.putSequenceCheck("(5200,9229)", new DicomSequence(sharedFunctionalGroupsSeqItem));

        final DicomObject dimensionIndexSeqItem0 = new DicomObject();
        dimensionIndexSeqItem0.putValueEqualCheck("(0020,9167)", "00209111");
        dimensionIndexSeqItem0.putNonexistenceChecks("(0020,9421)");
        final DicomObject dimensionIndexSeqItem1 = new DicomObject();
        dimensionIndexSeqItem1.putValueEqualCheck("(0020,9167)", "00209111");
        dimensionIndexSeqItem1.putNonexistenceChecks("(0020,9421)");
        root.putSequenceCheck("(0020,9222)", new DicomSequence(dimensionIndexSeqItem0, dimensionIndexSeqItem1));

        return root;
    }

}
