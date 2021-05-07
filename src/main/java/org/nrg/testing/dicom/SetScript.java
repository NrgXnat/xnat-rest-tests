package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class SetScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0045,0010)", "PCI");
        root.putValueEqualCheck("(0045,1045)", "PRIVATEVALUE");
        root.putValueEqualCheck("(0028,0008)", "10");

        final DicomObject dimensionIndexSeqItem0 = new DicomObject();
        dimensionIndexSeqItem0.putValueEqualCheck("(0020,9164)", "1.2.3.4");
        dimensionIndexSeqItem0.putValueEqualCheck("(0045,0010)", "PCI");
        final DicomObject dimensionIndexSeqItem1 = new DicomObject();
        dimensionIndexSeqItem1.putValueEqualCheck("(0020,9164)", "1.3.46.670589.11.5730.5.0.3224.2010071913175879000");
        dimensionIndexSeqItem1.putNonexistenceChecks("(0045,0010)");

        root.putSequenceCheck("(0020,9222)", new DicomSequence(dimensionIndexSeqItem0, dimensionIndexSeqItem1));

        return root;
    }

}
