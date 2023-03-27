package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

import java.util.Arrays;

public class SequenceItemWildcardAssignIfExistsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0010,0010)", "Watermelon");
        root.putValueEqualCheck("(0010,0020)", "Watermelon_MR1");

        final DicomObject otherPatientIdsSequenceItem0 = new DicomObject();
        final DicomObject otherPatientIdsSequenceItem1 = new DicomObject();
        final DicomObject otherPatientIdsSequenceItem2 = new DicomObject();
        final DicomObject otherPatientIdsSequenceItem3 = new DicomObject();
        final DicomObject otherPatientIdsSequenceNestedItem = new DicomObject();
        otherPatientIdsSequenceItem2.putSequenceCheck("(0010,1002)", new DicomSequence(otherPatientIdsSequenceNestedItem));
        otherPatientIdsSequenceItem2.putNonexistenceChecks("(0010,0010)", "(0010,0020)");
        for (DicomObject sequenceItem : Arrays.asList(otherPatientIdsSequenceItem0, otherPatientIdsSequenceItem1, otherPatientIdsSequenceItem3)) {
            sequenceItem.putValueEqualCheck("(0010,0010)", "M E L O N");
        }
        otherPatientIdsSequenceNestedItem.putValueEqualCheck("(0010,0010)", "WATERMELON^FRUIT");
        otherPatientIdsSequenceItem0.putValueEqualCheck("(0010,0020)", "PATIENT_0001");
        otherPatientIdsSequenceItem1.putValueEqualCheck("(0010,0020)", "PERSON_0001");
        otherPatientIdsSequenceNestedItem.putValueEqualCheck("(0010,0020)", "NEW ID");
        otherPatientIdsSequenceItem3.putValueEqualCheck("(0010,0020)", "PERSON_0004");
        root.putSequenceCheck("(0010,1002)", new DicomSequence(otherPatientIdsSequenceItem0, otherPatientIdsSequenceItem1, otherPatientIdsSequenceItem2, otherPatientIdsSequenceItem3));

        return root;
    }

}
