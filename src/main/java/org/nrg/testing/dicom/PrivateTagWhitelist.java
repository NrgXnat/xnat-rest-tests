package org.nrg.testing.dicom;

public class PrivateTagWhitelist extends RemoveAllPrivateTags { // don't extend (already DE6)

    @Override
    protected void addSimpleRanChecks(DicomObject root) {
        super.addSimpleRanChecks(root);
        root.putValueEqualCheck("(2005,1035)", "PIXEL");
        root.putValueEqualCheck("(2005,1327)", "REAL");
    }

}
