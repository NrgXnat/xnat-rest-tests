package org.nrg.testing.dicom;

import org.dcm4che3.data.UID;
import org.nrg.testing.dicom.values.DicomSequence;

import java.util.Arrays;
import java.util.List;

public class TagDigitWildcardsAssignIfExistsScript extends SimpleInjectibleDicomValidation {

    @Override
    protected void validation(RootDicomObject root) {
        addGeneralNonexistenceCheckWithExceptions(root, "(0008,000X)", Arrays.asList("5", "8"));
        root.putValueEqualCheck("(0008,0005)", "ISO_IR 100");
        root.putValueEqualCheck("(0008,0008)", "FANTASTIC");

        addGeneralNonexistenceCheckWithExceptions(root, "(0008,001X)", Arrays.asList("2", "3", "4", "6", "8"));
        root.putValueEqualCheck("(0008,0012)", "20100719");
        root.putValueEqualCheck("(0008,0013)", "1000");
        root.putValueEqualCheck("(0008,0014)", "1.3.46.670589.11.5730.5");
        root.putValueEqualCheck("(0008,0016)", UID.EnhancedMRImageStorage);

        addFixedValueInRangeCheck(root, "(0008,002X)", Arrays.asList("0", "1", "3", "a"), "19960227");
        addFixedValueInRangeCheck(root, "(2001,100X)", Arrays.asList("c", "e", "f"), "1");

        // above is primarily validating the changes specified were made properly. Below is just sanity checks that
        // the same tags referenced in sequences remain undisturbed

        final DicomObject referencedPerformedProcedureStepSequenceItem = new DicomObject();
        addGeneralNonexistenceCheckWithExceptions(referencedPerformedProcedureStepSequenceItem, "(0008,001X)", Arrays.asList("2", "3", "4"));
        referencedPerformedProcedureStepSequenceItem.putValueEqualCheck("(0008,0012)", "20100719");
        referencedPerformedProcedureStepSequenceItem.putValueEqualCheck("(0008,0013)", "131758");
        referencedPerformedProcedureStepSequenceItem.putValueEqualCheck("(0008,0014)", "1.3.46.670589.11.5730.5");
        referencedPerformedProcedureStepSequenceItem.putValueEqualCheck("(0008,1150)", UID.ModalityPerformedProcedureStepSOPClass);
        root.putSequenceCheck("(0008,1111)", new DicomSequence(referencedPerformedProcedureStepSequenceItem));
    }

    // only supports a single X wildcard
    private void addGeneralNonexistenceCheckWithExceptions(DicomObject dicomObject, String wildcardString, List<String> exclusions) {
        for (String digit : DicomEditUtils.HEX_DIGITS) {
            if (!exclusions.contains(digit)) {
                dicomObject.putNonexistenceChecks(wildcardString.replace("X", digit));
            }
        }
    }

    // only supports a single X wildcard
    private void addFixedValueInRangeCheck(DicomObject dicomObject, String wildcardString, List<String> digits, String expectedVal) {
        addGeneralNonexistenceCheckWithExceptions(dicomObject, wildcardString, digits);
        for (String digit : digits) {
            dicomObject.putValueEqualCheck(wildcardString.replace("X", digit), expectedVal);
        }
    }

}
