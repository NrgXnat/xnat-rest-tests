package org.nrg.testing.dicom;

import org.nrg.testing.enums.TestData;

public class AlterPixelsSimpleOverflowScript extends GenericAlterPixelsScript {

    public AlterPixelsSimpleOverflowScript() {
        super(TestData.SAMPLE_1);
    }

    /**
     * This is for alterPixels["rectangle", "l=20, t=20, r=270, b=120", "solid", "v=0"] on a 256x256 image
     */
    @Override
    protected boolean pixelIsWithinBlackoutRegion(int x, int y, int z) {
        return x >= 20 && y >= 20 && y < 120;
    }

}
