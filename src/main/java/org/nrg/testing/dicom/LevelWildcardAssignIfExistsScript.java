package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

import java.util.Arrays;

public class LevelWildcardAssignIfExistsScript extends SimpleInjectibleDicomValidation {

    private final SequenceLevelWildcard wildcard;

    public LevelWildcardAssignIfExistsScript(SequenceLevelWildcard wildcard) {
        this.wildcard = wildcard;
    }

    @Override
    protected void validation(RootDicomObject root) {
        root.putNonexistenceChecks("(0008,0068)", "(0008,1150)", "(0008,010b)");
        root.putValueEqualCheck("(0008,0080)", wildcard.includesRootLevel ? "only in root" : "BU SCHOOL OF MEDICINE");
        root.putValueEqualCheck("(0010,0010)", wildcard.includesRootLevel ? "renamed" : "Watermelon");
        root.putValueEqualCheck("(0099,1050)", wildcard.includesRootLevel ? "CUSTOM_VAL" : "ROOT VALUE");

        final DicomObject referencedStudySequenceItem = new DicomObject();
        referencedStudySequenceItem.putValueEqualCheck("(0008,1150)", "1.2.3.4");
        referencedStudySequenceItem.putNonexistenceChecks("(0008,0068)", "(0008,0080)", "(0010,0010)", "(0008,010b)", "(0099,1050)");
        referencedStudySequenceItem.putValueEqualCheck("(0008,1155)", "1.3.46.670589.11.5730.5.0.1744.2010043012343685002");
        root.putSequenceCheck("(0008,1110)", new DicomSequence(referencedStudySequenceItem));

        final DicomObject referencedPerformedProcedureStepSequenceItem = new DicomObject();
        referencedPerformedProcedureStepSequenceItem.putValueEqualCheck("(0008,1150)", "1.2.3.4");
        referencedPerformedProcedureStepSequenceItem.putNonexistenceChecks("(0008,0068)", "(0008,0080)", "(0010,0010)", "(0008,010b)");
        referencedPerformedProcedureStepSequenceItem.putValueEqualCheck("(0008,1155)", "1.3.46.670589.11.5730.5.0.1744.2010043012343685003");
        root.putSequenceCheck("(0008,1111)", new DicomSequence(referencedPerformedProcedureStepSequenceItem));

        final DicomObject otherPatientIdsSequenceItem0 = new DicomObject();
        final DicomObject otherPatientIdsSequenceItem1 = new DicomObject();
        final DicomObject otherPatientIdsSequenceItem2 = new DicomObject();
        final DicomObject otherPatientIdsSequenceItem3 = new DicomObject();
        final DicomObject otherPatientIdsSequenceNestedItem = new DicomObject();
        otherPatientIdsSequenceItem2.putSequenceCheck("(0010,1002)", new DicomSequence(otherPatientIdsSequenceNestedItem));
        for (DicomObject sequenceItem : Arrays.asList(
                otherPatientIdsSequenceItem0,
                otherPatientIdsSequenceItem1,
                otherPatientIdsSequenceItem2,
                otherPatientIdsSequenceItem3,
                otherPatientIdsSequenceNestedItem)) {
            sequenceItem.putNonexistenceChecks("(0008,0068)", "(0008,0080)", "(0008,1150)", "(0008,010b)", "(0099,1050)");
        }
        otherPatientIdsSequenceItem2.putNonexistenceChecks("(0010,0010)", "(0010,0020)");
        for (DicomObject sequenceItem : Arrays.asList(otherPatientIdsSequenceItem0, otherPatientIdsSequenceItem1, otherPatientIdsSequenceItem3)) {
            sequenceItem.putValueEqualCheck("(0010,0010)", "renamed");
        }
        otherPatientIdsSequenceNestedItem.putValueEqualCheck("(0010,0010)", wildcard.extendsBeyondOneSequenceLevel ? "renamed" : "WATERMELON^FRUIT");
        otherPatientIdsSequenceItem0.putValueEqualCheck("(0010,0020)", "PATIENT_0001");
        otherPatientIdsSequenceItem1.putValueEqualCheck("(0010,0020)", "PERSON_0001");
        otherPatientIdsSequenceNestedItem.putValueEqualCheck("(0010,0020)", "PATIENT_0003");
        otherPatientIdsSequenceItem3.putValueEqualCheck("(0010,0020)", "PERSON_0004");
        root.putSequenceCheck("(0010,1002)", new DicomSequence(otherPatientIdsSequenceItem0, otherPatientIdsSequenceItem1, otherPatientIdsSequenceItem2, otherPatientIdsSequenceItem3));

        final DicomObject performedProtocolCodeSequenceItem = new DicomObject();
        performedProtocolCodeSequenceItem.putNonexistenceChecks("(0008,0068)", "(0008,0080)", "(0008,1150)", "(0010,0010)", "(0099,1050)");
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,010b)", "MAYBE");
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,0100)", "UNDEFINED");
        root.putSequenceCheck("(0040,0260)", new DicomSequence(performedProtocolCodeSequenceItem));

        final DicomObject privateSequenceItem = new DicomObject();
        privateSequenceItem.putNonexistenceChecks("(0008,0068)", "(0008,0080)", "(0008,1150)", "(0010,0010)", "(0099,1050)");
        privateSequenceItem.putValueEqualCheck("(0008,010b)", "MAYBE");
        privateSequenceItem.putValueEqualCheck("(0008,0100)", "");
        root.putSequenceCheck("(2005,1402)", new DicomSequence(privateSequenceItem));

        final DicomObject customPrivateSequenceItem = new DicomObject();
        customPrivateSequenceItem.putValueEqualCheck("(0099,1050)", "CUSTOM_VAL");
        customPrivateSequenceItem.putNonexistenceChecks("(0008,0068)", "(0008,0080)", "(0008,1150)", "(0010,0010)", "(0008,010b)");
        final DicomObject customPrivateNestedSequenceItem = new DicomObject();
        customPrivateNestedSequenceItem.putValueEqualCheck("(0099,1050)", wildcard.extendsBeyondOneSequenceLevel ? "CUSTOM_VAL" : "LEVEL TWO");
        customPrivateNestedSequenceItem.putNonexistenceChecks("(0008,0068)", "(0008,0080)", "(0008,1150)", "(0010,0010)", "(0008,010b)");
        customPrivateSequenceItem.putSequenceCheck("(0099,1051)", new DicomSequence(customPrivateNestedSequenceItem));
        root.putSequenceCheck("(0099,1051)", new DicomSequence(customPrivateSequenceItem));
    }

}
