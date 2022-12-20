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
import org.nrg.testing.xnat.processing.files.comparators.imaging.ImageDeviationComparator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public abstract class GenericAlterPixelsScript extends ScriptValidation {

    protected TestData testedDataset;
    private static final Logger LOGGER = Logger.getLogger(GenericAlterPixelsScript.class);

    GenericAlterPixelsScript(TestData testedDataset) {
        this.testedDataset = testedDataset;
    }

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        final List<File> expectedDicomInstances = FileIOUtils.listFilesRecursively(testedDataset.toDirectory());
        assertEquals(expectedDicomInstances.size(), dicomFiles.size());
        final Map<String, File> sourceDicom = new HashMap<>();
        final Map<String, String> tempNameMap = new HashMap<>();
        for (File sourceDicomFile : expectedDicomInstances) {
            final String sopInstanceUid = DicomUtils.readDicom(sourceDicomFile).getDataset().getString(Tag.SOPInstanceUID);
            sourceDicom.put(sopInstanceUid, sourceDicomFile);
            tempNameMap.put(sopInstanceUid, sourceDicomFile.getName());
        }
        for (File dicomFile : dicomFiles) {
            final String sopInstanceUID = DicomUtils.readDicom(dicomFile).getDataset().getString(Tag.SOPInstanceUID);
            LOGGER.info("Checking PixelData for DICOM instance " + tempNameMap.get(sopInstanceUID));
            try {
                new ImageDeviationComparator().checkDiffedImage(new BlackedOutDiffedImage(sourceDicom.get(sopInstanceUID), dicomFile));
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

    protected abstract boolean pixelIsWithinBlackoutRegion(int x, int y);

    private class BlackedOutDiffedImage extends DiffedImage {
        BlackedOutDiffedImage(File original, File generated) throws ImageProcessingException {
            super(original, generated, ImageType.DICOM);
        }

        @Override
        protected ComparisonPixel readComparisonPixel(int x, int y, int[] original, int[] generated) {
            return super.readComparisonPixel(x, y, pixelIsWithinBlackoutRegion(x, y) ? new int[]{0} : original, generated);
        }
    }

}
