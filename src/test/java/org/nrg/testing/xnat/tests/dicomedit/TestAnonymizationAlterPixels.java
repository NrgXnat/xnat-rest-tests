package org.nrg.testing.xnat.tests.dicomedit;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.AlterPixelsBasicScript;
import org.nrg.testing.dicom.AlterPixelsNonimageScript;
import org.nrg.testing.dicom.AlterPixelsOverflowScript;
import org.nrg.testing.dicom.AlterPixelsSimpleOverflowScript;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.pogo.DicomDataSet;
import org.nrg.xnat.versions.Xnat_1_8_1;
import org.nrg.xnat.versions.Xnat_1_8_9;

@AddedIn(Xnat_1_8_1.class)
@TestRequires(admin = true, data = TestData.SAMPLE_1)
public class TestAnonymizationAlterPixels extends BaseAnonymizationTest {

    @AddedIn(Xnat_1_8_9.class)
    public void testAlterPixelsOverflow() {
        new BasicAnonymizationTest("alterPixelsOverflow.das")
                .withData(TestData.SAMPLE_1)
                .withValidation(new AlterPixelsOverflowScript())
                .run();
    }

    public void testAlterPixelsBasic() {
        new BasicAnonymizationTest("alterPixelsBasic.das")
                .withData(TestData.SAMPLE_1)
                .withValidation(new AlterPixelsBasicScript())
                .run();
    }

    @AddedIn(Xnat_1_8_9.class)
    public void testAlterPixelsSimpleOverflow() {
        new BasicAnonymizationTest("alterPixelsSimpleOverflow.das")
                .withData(TestData.SAMPLE_1)
                .withValidation(new AlterPixelsSimpleOverflowScript())
                .run();
    }

    /*
    Behavior in existing code is alterPixels being called on non-imaging instances
    skips pixel anon without error. I'm adding this test to maintain regression
    coverage to make sure that even if pixel anon gets skipped, the rest of
    the script does get applied.
     */
    @AddedIn(Xnat_1_8_9.class)
    public void testAlterPixelsNonimage() {
        final LocallyCacheableDicomTransformation data = new LocallyCacheableDicomTransformation("alter-pixels-sr")
            .createZip()
            .simpleTransform(TransformFunction.generateFromScratch(() -> {
                final String studyInstanceUid = UIDUtils.createUID();
                final String seriesInstanceUid = UIDUtils.createUID();

                return IntStream.range(1, 6).mapToObj(instanceNum -> {
                    final Attributes dataset = new Attributes();
                    dataset.setString(Tag.SOPClassUID, VR.UI, UID.BasicTextSRStorage);
                    dataset.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
                    dataset.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
                    dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
                    dataset.setString(Tag.Modality, VR.CS, "SR");
                    dataset.setInt(Tag.SeriesNumber, VR.IS, 1);
                    dataset.setString(Tag.PatientName, VR.PN, "ALTERPIXELS^TEST");
                    dataset.setString(Tag.PatientID, VR.LO, "ALTERPIXELS_TEST_001");
                    dataset.setString(Tag.Manufacturer, VR.LO, "SOMEBODY");
                    dataset.setString(Tag.InstitutionName, VR.LO, "SOME PLACE");
                    dataset.setString(Tag.InstitutionAddress, VR.ST, "SOMEWHERE");

                    dataset.addAll(
                        dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian)
                    );

                    return dataset;
                }).collect(Collectors.toList());
            }));

        new BasicAnonymizationTest("alterPixelsForNonimage.das")
                .withData(data)
                .withValidation(new AlterPixelsNonimageScript())
                .run();
    }

}
