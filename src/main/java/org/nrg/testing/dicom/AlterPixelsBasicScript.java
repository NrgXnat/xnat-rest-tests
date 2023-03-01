package org.nrg.testing.dicom;

import org.nrg.testing.enums.TestData;

public class AlterPixelsBasicScript extends GenericAlterPixelsScript {
    public AlterPixelsBasicScript() {
        super(TestData.SAMPLE_1);
    }

    @Override
    protected boolean pixelIsWithinBlackoutRegion(int x, int y, int z) {
        if (x < 20 && y < 20) {
            return true;
        }
        return x >= 100 && x < 150 && y >= 100 && y < 150;
    }

}
