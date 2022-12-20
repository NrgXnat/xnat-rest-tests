package org.nrg.testing.dicom;

import org.nrg.testing.enums.TestData;

public class AlterPixelsOverflowScript extends GenericAlterPixelsScript {

    public AlterPixelsOverflowScript() {
        super(TestData.SAMPLE_1);
    }

    @Override
    protected boolean pixelIsWithinBlackoutRegion(int x, int y) {
        if (x < 20 && y < 20) {
            return true;
        }
        if (x >= 250 && y < 20) {
            return true;
        }
        if (x >= 100 && x < 150 && y >= 100 && y < 150) {
            return true;
        }
        return x >= 120 && x < 140 && y >= 80 && y < 200;
    }

}
