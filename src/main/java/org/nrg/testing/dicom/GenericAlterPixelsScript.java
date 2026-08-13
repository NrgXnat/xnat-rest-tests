package org.nrg.testing.dicom;

import org.apache.log4j.Logger;
import org.dcm4che3.data.Tag;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.enums.ImageType;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.processing.exceptions.ImageProcessingException;
import org.nrg.testing.xnat.processing.files.comparators.imaging.ComparisonPixel;
import org.nrg.testing.xnat.processing.files.comparators.imaging.DiffedImage;
import org.nrg.testing.xnat.processing.files.comparators.imaging.ImageComparator;
import org.nrg.testing.xnat.processing.files.comparators.imaging.PixelValue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public abstract class GenericAlterPixelsScript extends ScriptValidation {

    protected TestData testedDataset;
    private final int fillValue;
    private static final Logger LOGGER = Logger.getLogger(GenericAlterPixelsScript.class);

    GenericAlterPixelsScript(TestData testedDataset) {
        this(testedDataset, 0);
    }

    /**
     * @param fillValue the stored value alterPixels was asked to write, from the script's
     *                  {@code "solid", "v=<n>"} argument. It used to be ignored: the redaction ran
     *                  through pixelmed's blackout, which always writes zero whatever the script
     *                  says. A script asking for 100 now gets 100, so the expected value has to come
     *                  from the script rather than being assumed.
     */
    GenericAlterPixelsScript(TestData testedDataset, int fillValue) {
        this.testedDataset = testedDataset;
        this.fillValue = fillValue;
    }

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        final List<File> expectedDicomInstances = FileIOUtils.listFilesRecursively(testedDataset.toDirectory());
        assertEquals(expectedDicomInstances.size(), dicomFiles.size());
        final Map<String, File> sourceDicom = new HashMap<>();
        final Map<String, String> tempNameMap = new HashMap<>();
        for (File sourceDicomFile : expectedDicomInstances) {
            final String sopInstanceUid = DicomUtils.readDicom(sourceDicomFile).getString(Tag.SOPInstanceUID);
            sourceDicom.put(sopInstanceUid, sourceDicomFile);
            tempNameMap.put(sopInstanceUid, sourceDicomFile.getName());
        }
        for (File dicomFile : dicomFiles) {
            final String sopInstanceUID = DicomUtils.readDicom(dicomFile).getString(Tag.SOPInstanceUID);
            LOGGER.info("Checking PixelData for DICOM instance " + tempNameMap.get(sopInstanceUID));
            try {
                DiffedImage diffedImage = new DiffedImage(sourceDicom.get(sopInstanceUID), dicomFile, ImageType.DICOM, true);
                new WholeImageComparator().checkDiffedImage(diffedImage);
            } catch (ImageProcessingException ipe) {
                throw new RuntimeException(ipe);
            }
        }
    }

    @Override
    public void validateScriptDidntRun(List<File> dicomFiles) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return new ArrayList<>();
    }

    private class WholeImageComparator extends ImageComparator {

        @Override
        public void checkDiffedImage(DiffedImage diffedImage) throws ImageProcessingException {
            if (diffedImage.ifAnyFail(this::pixelValueTest)) {
                throw new ImageProcessingException("");
            }
        }

        protected boolean pixelValueTest(ComparisonPixel comparisonPixel) {
            int x = comparisonPixel.getX();
            int y = comparisonPixel.getY();
            int z = comparisonPixel.getZ();
            try {
                if (pixelIsWithinBlackoutRegion(x, y, z)) {
                    return comparisonPixel.getGeneratedPixelValue().equals(getExpectedBlackoutPixelValue(x, y, z));
                } else {
                    return comparisonPixel.isTrivial();
                }
            } catch (ImageProcessingException e) {
                return false;
            }
        }
    }


    protected abstract boolean pixelIsWithinBlackoutRegion(int x, int y, int z);

    protected PixelValue getExpectedBlackoutPixelValue(int x, int y, int z) throws ImageProcessingException {
        if (pixelIsWithinBlackoutRegion(x, y, z)) {
            return new PixelValue(fillValue);
        } else {
            throw new ImageProcessingException(String.format("Requesting redacted pixel value in non-redacted region %d,%d,%d (x,y,z)", x, y, z));
        }
    }

}
