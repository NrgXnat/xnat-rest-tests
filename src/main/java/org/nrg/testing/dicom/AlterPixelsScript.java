package org.nrg.testing.dicom;

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

public class AlterPixelsScript extends ScriptValidation {

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        assertEquals(528, dicomFiles.size());
        final Map<String, File> sourceDicom = new HashMap<>();
        final Map<String, String> tempNameMap = new HashMap<>();
        for (File sourceDicomFile : FileIOUtils.listFilesRecursively(TestData.SAMPLE_1.toDirectory())) {
            sourceDicom.put(DicomUtils.readDicom(sourceDicomFile).getDataset().getString(Tag.SOPInstanceUID), sourceDicomFile);
            tempNameMap.put(DicomUtils.readDicom(sourceDicomFile).getDataset().getString(Tag.SOPInstanceUID), sourceDicomFile.getName());
        }
        for (File dicomFile : dicomFiles) {
            final String sopInstanceUID = DicomUtils.readDicom(dicomFile).getDataset().getString(Tag.SOPInstanceUID);
            System.out.println("Checking " + tempNameMap.get(sopInstanceUID));
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

    private static class BlackedOutDiffedImage extends DiffedImage {
        BlackedOutDiffedImage(File original, File generated) throws ImageProcessingException {
            super(original, generated, ImageType.DICOM);
        }

        @Override
        protected ComparisonPixel readComparisonPixel(int x, int y, int[] original, int[] generated) {
            return super.readComparisonPixel(x, y, insideBlackoutRegion(x, y) ? new int[]{0} : original, generated);
        }

        private boolean insideBlackoutRegion(int x, int y) {
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

}
