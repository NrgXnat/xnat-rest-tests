package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class StandardDeleteLastItemScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putSequenceCheck("(0008,1110)", new DicomSequence());
        return root;
    }

}
