package org.nrg.testing.dicom;

import org.dcm4che3.data.Tag;

import java.util.Collections;
import java.util.List;

public class UIDModScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,1150)", "2.25.76380769527968176043226179709817176787");
        root.putValueNotEqualCheck("(0008,0018)", "1.3.46.670589.11.5730.5.20.1.1.3144.2010043013044142001");

        return root;
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return Collections.singletonList(new DistinctTagValues(Tag.SOPInstanceUID));
    }

}
