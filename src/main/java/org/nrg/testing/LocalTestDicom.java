package org.nrg.testing;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class LocalTestDicom {

    public static final String BAD_UID_ID = "bad-study-instance-uid";
    public static final LocallyCacheableDicomTransformation BAD_STUDY_INSTANCE_UID = new LocallyCacheableDicomTransformation(BAD_UID_ID)
            .data(TestData.SAMPLE_1)
            .createZip()
            .transformations(
                    new DicomTransformation(BAD_UID_ID)
                            .produceZip()
                            .prefilter(DicomFilters.ONLY_ONE_FILE)
                            .transformFunction(
                                    TransformFunction.simple((dicom) -> {
                                        dicom.setString(Tag.StudyInstanceUID, VR.UI, "1.3.12.2.1107.5.2.32.05177.3.2006121409370267005218149"); // invalid per PS 3.5, section 9.1
                                    })
                            )
            );
    public static final String SAMPLE1_SMALL_SUBSET_ID = "sample1-subset-middle";
    public static final Function<List<Attributes>, List<Attributes>> SAMPLE1_MIDDLEISH_INSTANCES = DicomFilters.subsetWithInstanceNumber(Arrays.asList(80, 81, 82));
    public static final LocallyCacheableDicomTransformation SAMPLE1_SMALL_SUBSET = new LocallyCacheableDicomTransformation(SAMPLE1_SMALL_SUBSET_ID)
            .data(TestData.SAMPLE_1)
            .createZip()
            .transformations(
                    new DicomTransformation(SAMPLE1_SMALL_SUBSET_ID)
                            .produceZip()
                            .prefilter(SAMPLE1_MIDDLEISH_INSTANCES)
            );

}
