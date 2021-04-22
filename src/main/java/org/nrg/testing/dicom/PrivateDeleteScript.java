package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class PrivateDeleteScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putNonexistenceChecks("(0029,0010)", "(0029,1019)", "(2001,1031)");
        root.putNonexistenceChecks("(2001,100f)");
        root.putWildcardedNonexistenceCheck("(2001,108@)");
        root.putValueEqualCheck("(2001,1081)", "1");
        root.putValueEqualCheck("(2001,1083)", "127.787174999999");
        root.putValueEqualCheck("(2001,1085)", "3");
        root.putValueEqualCheck("(2001,1087)", "1H");
        root.putValueEqualCheck("(2001,1089)", "0");
        root.putValueEqualCheck("(2001,108b)", "B");
        root.putNonexistenceChecks("(2005,1402)");

        final DicomObject privateSeqItem = new DicomObject();
        privateSeqItem.putValueEqualCheck("(2005,1072)", "0");
        root.putSequenceCheck("(2001,105f)", new DicomSequence(privateSeqItem));

        return root;
    }

}
