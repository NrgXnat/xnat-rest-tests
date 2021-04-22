package org.nrg.testing.dicom;

import org.dcm4che3.data.UID;

// Tests DE-30
public class Group2SyncScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        /*
          Yes, there's 2 instances in the test data, so they shouldn't really be getting the same SOP Instance UIDs assigned. It's happening
          here because I'm assigning SOP Instance UID from a hash of the previous value. I overlooked giving the 2 test instances different
          instance UIDs when originally preparing the data.
         */
        final String expectedSOPInstanceUID = "2.25.18971457369541893112737536732177973003";
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0002,0002)", UID.MRImageStorage);
        root.putValueEqualCheck("(0008,0016)", UID.MRImageStorage);
        root.putValueEqualCheck("(0002,0003)", expectedSOPInstanceUID);
        root.putValueEqualCheck("(0008,0018)", expectedSOPInstanceUID);

        return root;
    }

}
