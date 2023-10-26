package org.nrg.testing.dicom;

import org.dcm4che3.data.VR;

public class BlankKnownPhiVrPreservation extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0020)", "", VR.DA);
        root.putValueEqualCheck("(0008,0021)", "", VR.DA);
        root.putValueEqualCheck("(0018,1204)", "", VR.DA);
        root.putValueEqualCheck("(0010,1030)", "10", VR.DS);
        root.putValueEqualCheck("(0020,0013)", "", VR.IS);
        root.putValueEqualCheck("(0028,0002)", "", VR.US);
        root.putValueEqualCheck("(0028,9001)", "", VR.UL);
        root.putValueEqualCheck("(2001,1013)", "", VR.SL);
        root.putValueEqualCheck("(2001,1014)", "", VR.SL);
        root.putValueEqualCheck("(2001,1015)", "", VR.SL);
        root.putValueEqualCheck("(2001,1018)", "3", VR.SL);
        root.putValueEqualCheck("(2001,101d)", "", VR.IS);
        root.putSequenceCheck("(2001,105f)", (item) -> {
            item.putValueEqualCheck("(2001,102d)", "3", VR.SS);
            item.putValueEqualCheck("(2001,1035)", "", VR.SS);
        });
        root.putValueEqualCheck("(2001,1088)", "", VR.DS);

        return root;
    }

}
