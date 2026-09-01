package org.nrg.testing.dicom;

import org.nrg.testing.enums.TestData;

public class IfElseAlterPixelsScript extends GenericAlterPixelsScript {

    public IfElseAlterPixelsScript() {
        // ifElseAlterPixels.das asks for "solid", "v=100" in every one of its branches.
        super(TestData.ANON_2, 100);
    }

    @Override
    protected boolean pixelIsWithinBlackoutRegion(int x, int y, int z) {
        if (x < 20 && x >= 10 && y < 100 && y >= 10) {
            return true;
        }
        if (x < 100 && x >= 90 && y < 100 && y >= 10){
            return true;
        }
        return x >= 25 && x < 75 && y >= 10 && y < 40;
    }

}
