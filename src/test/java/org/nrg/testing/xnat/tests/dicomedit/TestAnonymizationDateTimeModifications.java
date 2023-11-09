package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Tag;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.DicomObject;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.dicom.values.DicomSequence;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.pogo.DicomDataSet;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_1;
import org.nrg.xnat.versions.Xnat_1_8_7;

import java.util.Collections;

@TestRequires(admin = true, data = TestData.ANON_2)
@AddedIn(Xnat_1_8_0.class)
public class TestAnonymizationDateTimeModifications extends BaseAnonymizationTest {

    public void testShiftDateByIncrement() {
        new BasicAnonymizationTest("shiftDate.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0012)", "20100729");
                    root.putValueEqualCheck("(0008,0020)", "20200301");
                    root.putValueEqualCheck("(0008,0021)", "20210302");
                    root.putValueEqualCheck("(0008,0022)", "20200223");
                    root.putValueEqualCheck("(0008,0024)", "20191231");
                }).run();
    }

    @AddedIn(Xnat_1_8_1.class)
    public void testShiftDateTimeByIncrement() {
        new BasicAnonymizationTest("shiftDateTime.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,002A)", "20100430131041.40");
                    root.putValueEqualCheck("(0018,9516)", "20200101120010.000000");
                    root.putValueEqualCheck("(0018,9517)", "20200101120010.800000");
                    root.putValueEqualCheck("(0018,9804)", "20200229000100");
                    root.putValueEqualCheck("(0018,9919)", "19960808135000");
                }).run();
    }

    @AddedIn(Xnat_1_8_7.class)
    public void testShiftDateTimeByIncrementWithTimezone() {
        new BasicAnonymizationTest("shiftDateTimeWithTimezone.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0018,9516)", "20200101120010+0500");
                    root.putValueEqualCheck("(0018,9517)", "20200101120010-0730");
                }).run();
    }

    @AddedIn(Xnat_1_8_1.class)
    public void testShiftDateTimeSequenceByIncrement() {
        new BasicAnonymizationTest("shiftDateTimeSequence.das")
                .withValidation((root) -> {
                    final DicomObject perFrameFunctionalSeqItem0 = new DicomObject();
                    final DicomObject perFrameFunctionalSeqItem1 = new DicomObject();
                    final DicomObject perFrameFunctionalSeqItem2 = new DicomObject();
                    for (DicomObject seqItem : new DicomObject[]{perFrameFunctionalSeqItem0, perFrameFunctionalSeqItem1, perFrameFunctionalSeqItem2}) {
                        final DicomObject frameContentSeqItem = new DicomObject();
                        frameContentSeqItem.putValueEqualCheck("(0018,9151)", "20100501130441.40");
                        frameContentSeqItem.putValueEqualCheck("(0018,9074)", "20100429130441.40");
                        seqItem.putSequenceCheck("(0020,9111)", new DicomSequence(frameContentSeqItem));
                        if (seqItem == perFrameFunctionalSeqItem0) {
                            frameContentSeqItem.putValueEqualCheck("(0008,002A)", "20200101100010");
                        } else {
                            frameContentSeqItem.putNonexistenceChecks("(0008,002A)");
                        }
                    }
                    root.putSequenceCheck("(5200,9230)", new DicomSequence(perFrameFunctionalSeqItem0, perFrameFunctionalSeqItem1, perFrameFunctionalSeqItem2));
                }).run();
    }

    /**
      Shift should not change the precision of the value.
     */
    @AddedIn(Xnat_1_8_7.class)
    public void testShiftDateTimePrecision() {
        new BasicAnonymizationTest("shiftDateTimePrecision.das")
                .withValidation((root) -> root.putSequenceCheck(
                        "(0008,1110)",
                        (seqItem0) -> seqItem0.putValueEqualCheck(0x0044000B, "1960"),
                        (seqItem1) -> seqItem1.putValueEqualCheck(0x0044000B, "196005"),
                        (seqItem2) -> seqItem2.putValueEqualCheck(0x0044000B, "19600519"),
                        (seqItem3) -> seqItem3.putValueEqualCheck(0x0044000B, "1960051913"),
                        (seqItem4) -> seqItem4.putValueEqualCheck(0x0044000B, "196005191324"),
                        (seqItem5) -> seqItem5.putValueEqualCheck(0x0044000B, "19600519132435")
                )).withData(
                        new LocallyCacheableDicomTransformation("dtPrecision")
                                .createZip()
                                .simpleTransform(TransformFunction.generateFromScratch(() -> {
                                    DicomDataSet dicomDataSet = new DicomDataSet();
                                    dicomDataSet.setTag(Tag.PatientName, "DTPrecision")
                                            .setTagArray(new int[]{Tag.ReferencedStudySequence, 0, Tag.ProductExpirationDateTime}, "1960")
                                            .setTagArray(new int[]{Tag.ReferencedStudySequence, 1, Tag.ProductExpirationDateTime}, "196005")
                                            .setTagArray(new int[]{Tag.ReferencedStudySequence, 2, Tag.ProductExpirationDateTime}, "19600519")
                                            .setTagArray(new int[]{Tag.ReferencedStudySequence, 3, Tag.ProductExpirationDateTime}, "1960051913")
                                            .setTagArray(new int[]{Tag.ReferencedStudySequence, 4, Tag.ProductExpirationDateTime}, "196005191324")
                                            .setTagArray(new int[]{Tag.ReferencedStudySequence, 5, Tag.ProductExpirationDateTime}, "19600519132435");
                                    return Collections.singletonList(dicomDataSet.getDataset());
                                }))
                ).run();
    }

}
