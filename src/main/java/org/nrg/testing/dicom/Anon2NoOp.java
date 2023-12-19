package org.nrg.testing.dicom;

import org.dcm4che3.data.UID;
import org.nrg.testing.dicom.values.DicomSequence;

public class Anon2NoOp extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
        root.putNonexistenceChecks("(0008,0019)");
        root.putValueEqualCheck("(0008,0060)", "MR");
        root.putValueEqualCheck("(0008,9208)", "MAGNITUDE");
        root.putNonexistenceChecks("(0012,0040)");

        root.putSequenceCheck("(0008,1110)", (item) -> {
            item.putValueEqualCheck("(0008,1150)", UID.DetachedStudyManagement);
            item.putValueEqualCheck("(0008,1155)", "1.3.46.670589.11.5730.5.0.1744.2010043012343685002");
            item.putNonexistenceChecks("(0010,0010)");
        });

        final DicomObject performedProtocolCodeSequenceItem = new DicomObject();
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,0100)", "UNDEFINED");
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,0102)", "UNDEFINED");
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,0104)", "UNDEFINED");
        performedProtocolCodeSequenceItem.putValueEqualCheck("(0008,010b)", "N");

        root.putSequenceCheck("(0040,0260)", new DicomSequence(performedProtocolCodeSequenceItem));

        root.putNonexistenceChecks("(0099,0010)", "(0099,1010)");

        return root;
    }

}
