package org.nrg.testing.dicom;

import org.dcm4che3.data.Tag;
import org.nrg.testing.dicom.values.DicomSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MapReferencedUIDsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final String uidRoot = "9.99.999.99.9";
        final String dimensionOrganizationUID = "(0020,9164)";
        final RootDicomObject root = new RootDicomObject();

        root.putValueStartsWithCheck(Tag.StudyInstanceUID, uidRoot);
        final DicomObject dimensionOrganizationSeqItem = new DicomObject();
        dimensionOrganizationSeqItem.putValueStartsWithCheck(dimensionOrganizationUID, uidRoot);
        root.putSequenceCheck("(0020,9221)", new DicomSequence(dimensionOrganizationSeqItem));

        final DicomObject dimensionIndexSeqItem0 = new DicomObject();
        final DicomObject dimensionIndexSeqItem1 = new DicomObject();
        for (DicomObject seqItem : Arrays.asList(dimensionIndexSeqItem0, dimensionIndexSeqItem1)) {
            seqItem.putValueEqualCheck(dimensionOrganizationUID, dimensionOrganizationSeqItem.getTagElement(dimensionOrganizationUID));
        }
        root.putSequenceCheck("(0020,9222)", new DicomSequence(dimensionIndexSeqItem0, dimensionIndexSeqItem1));

        return root;
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return (scriptRan) ? Collections.singletonList(new FixedTagValue(Tag.StudyInstanceUID)) : new ArrayList<>();
    }

}
