package org.nrg.testing.dicom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PrivateMapReferencedUIDsScript extends SimplestDicomScriptValidation {

    private final int PRIVATE_UID_TAG = 0x00e110c2;

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();
        root.putValueStartsWithCheck(PRIVATE_UID_TAG, "1.2.826.0.1.3680043.10.9");

        return root;
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return (scriptRan) ? Collections.singletonList(new FixedTagValue(PRIVATE_UID_TAG)) : new ArrayList<>();
    }

}
