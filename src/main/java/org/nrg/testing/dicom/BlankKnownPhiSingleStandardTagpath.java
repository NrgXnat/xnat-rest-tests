package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class BlankKnownPhiSingleStandardTagpath extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0018,0034)", "SOMETHING");
        root.putValueEqualCheck("(0044,0001)", "");
        root.putValueEqualCheck("(0044,0002)", "YES");

        final DicomObject interventionDrugCodeSequenceItem = new DicomObject();
        interventionDrugCodeSequenceItem.putValueEqualCheck("(0008,0100)", "C-21047");
        interventionDrugCodeSequenceItem.putValueEqualCheck("(0008,0104)", "Ethanol");

        final DicomObject interventionDrugInformationSequenceItem1 = new DicomObject();
        interventionDrugInformationSequenceItem1.putValueEqualCheck("(0018,0028)", "150000");
        interventionDrugInformationSequenceItem1.putSequenceCheck("(0018,0029)", new DicomSequence(interventionDrugCodeSequenceItem));
        interventionDrugInformationSequenceItem1.putValueEqualCheck("(0018,0034)", "");

        final DicomObject interventionDrugInformationSequenceItem2 = new DicomObject();
        interventionDrugInformationSequenceItem2.putValueEqualCheck("(0018,0028)", "");
        interventionDrugInformationSequenceItem2.putValueEqualCheck("(0018,0034)", "");
        root.putSequenceCheck("(0018,0026)", new DicomSequence(interventionDrugInformationSequenceItem1, interventionDrugInformationSequenceItem2));

        return root;
    }

}
