package org.nrg.testing.dicom;

import org.dcm4che3.data.Tag;

import java.util.Collections;
import java.util.List;

public class StandardConditionalsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putNonexistenceChecks("(0028,3003)");
        root.putValueEqualCheck("(0028,9002)", "0");
        root.putValueEqualCheck("(0012,0020)", "Hello");
        root.putNonexistenceChecks("(0012,0030)", "(0012,0040)", "(0012,0050)", "(0008,2122)", "(0008,2124)");
        root.putValueEqualCheck("(0020,1204)", "100");
        root.putValueEqualCheck("(0020,1206)", "50");
        root.putValueEqualCheck("(2010,0040)", "GOOD");
        root.putValueEqualCheck("(2010,0050)", "GOOD");
        root.putValueEqualCheck("(2010,0060)", "GOOD");
        root.putValueEqualCheck("(2010,0080)", "GOOD");
        root.putValueEqualCheck("(2010,00A8)", "GOOD");
        root.putNonexistenceChecks("(2010,00A9)");
        root.putValueEqualCheck("(2010,0140)", "GOOD");
        root.putValueEqualCheck("(2010,0150)", "GOOD");
        root.putValueEqualCheck("(2200,0005)", "GOOD");
        root.putNonexistenceChecks("(2200,0006)");

        return root;
    }

}
