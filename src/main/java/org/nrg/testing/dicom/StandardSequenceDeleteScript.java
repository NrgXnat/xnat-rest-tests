package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.versions.Xnat_1_8_7;

public class StandardSequenceDeleteScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        final DicomObject interventionDrugInfoSeqItem1 = new DicomObject();
        interventionDrugInfoSeqItem1.putValueEqualCheck("(0018,0028)", "150000");
        interventionDrugInfoSeqItem1.putValueEqualCheck("(0018,0034)", "CHOCOLATE");
        final DicomObject interventionDrugCodeSeqItem1 = new DicomObject();
        interventionDrugCodeSeqItem1.putValueEqualCheck("(0008,0100)", "C-21047");
        interventionDrugCodeSeqItem1.putValueEqualCheck("(0008,0104)", "Ethanol");
        if (XnatTestingVersionManager.testedVersionPrecedes(Xnat_1_8_7.class)) {
            interventionDrugInfoSeqItem1.putSequenceCheck("(0018,0029)", new DicomSequence(interventionDrugCodeSeqItem1));
            root.putSequenceCheck("(0018,0026)", new DicomSequence(interventionDrugInfoSeqItem1));
        } else {
            // the contents of item 2 are deleted but not the item itself.
            final DicomObject interventionDrugInfoSeqItem2 = new DicomObject();
            interventionDrugInfoSeqItem2.putNonexistenceChecks( "(0018,0028)", "(0018,0034)");
            root.putSequenceCheck( "(0018,0026)", new DicomSequence( interventionDrugInfoSeqItem1, interventionDrugInfoSeqItem2));
            interventionDrugInfoSeqItem1.putSequenceCheck("(0018,0029)", new DicomSequence(interventionDrugCodeSeqItem1));
        }

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
