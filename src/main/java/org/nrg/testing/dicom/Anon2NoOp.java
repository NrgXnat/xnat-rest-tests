package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class Anon2NoOp extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0008)", "ORIGINAL\\PRIMARY\\PROTON_DENSITY\\NONE");
        root.putValueEqualCheck("(0008,0060)", "MR");
        root.putValueEqualCheck("(0008,9208)", "MAGNITUDE");

        final DicomObject performedProtocolCodeSequenceItem = new DicomObject();
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,0100)", "UNDEFINED");
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,0102)", "UNDEFINED");
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,0104)", "UNDEFINED");
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,010b)", "N");

        root.putSequenceCheck("(0040,0260)", new DicomSequence(performedProtocolCodeSequenceItem));

        return root;
    }

}
