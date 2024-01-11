package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.DicomObject;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.dicom.values.DicomSequence;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.pogo.DicomDataSet;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_1;
import org.nrg.xnat.versions.Xnat_1_8_10;
import org.nrg.xnat.versions.Xnat_1_8_7;

import java.util.Collections;
import java.util.function.Consumer;

import static org.dcm4che3.data.Tag.PatientAge;
import static org.dcm4che3.data.Tag.PatientBirthDate;
import static org.dcm4che3.data.Tag.StudyDate;

@TestRequires(admin = true, data = TestData.ANON_2)
@AddedIn(Xnat_1_8_0.class)
public class TestAnonymizationDateTimeModifications extends BaseAnonymizationTest {

    private static final int PRIVATE_SEQUENCE = 0x00851010;
    private static final String SCALE_FUNCTION = "scalePatientAgeAndDobFromStudyDate";
    private static final String CUSTOM_SCALE_PLUS_SHIFT = "plusShift.das";
    private static final ScalingTransformation STANDARD_SCALE_BELOW_89_DATA = new ScalingTransformation("scale-patient-age-below-89", "20000131", "19500101", "050Y");
    private static final ScalingTransformation STANDARD_SCALE_ABOVE_89_DATA = new ScalingTransformation("scale-patient-age-above-89", "20000131", "19000101", "100Y");

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

    /**
     * Tests using the new scaling function with (0008,0020) Study Date and (0010,0030) Patient's Birth Date empty and
     * (0010,1010) Patient's Age set to 090Y to check that:
     *   1) Those dates remain empty
     *   2) 090Y is anonymized to 089Y
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testScalePatientAgeNoDates() {
        new ScaleFunctionTest()
                .withValuesAndDefaultValidation("scale-patient-age-no-dates", "", "", "090Y")
                .withValidation((root) -> {
                    root.putEmptyChecks(StudyDate, PatientBirthDate);
                    root.putValueEqualCheck(PatientAge, "089Y", VR.AS);
                }).run();
    }

    /**
     * Tests using the new scaling function with (0008,0020) Study Date, (0010,0030) Patient's Birth Date, and
     * (0010,1010) Patient's Age all set to represent a newborn 99 days old. We're double-checking
     *   1) Those dates remain untouched
     *   2) The age remains untouched
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testScalePatientAgeNewborn() {
        new ScaleFunctionTest()
                .withValuesAndDefaultValidation("scale-patient-age-newborn", "20230410", "20230101", "099D")
                .run();
    }

    /**
     * Tests using the new scaling function with (0008,0020) Study Date blank. The values in
     * (0010,0030) Patient's Birth Date and (0010,1010) Patient's Age all expected to remain
     * untouched.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testScalePatientAgeStudyDateBlank() {
        new ScaleFunctionTest()
                .withValuesAndDefaultValidation("scale-patient-age-study-date-blank", "", "19500101", "500Y")
                .run();
    }

    /**
     * Tests using the new scaling function with (0010,0030) Patient's Birth Date and
     * (0010,1010) Patient's Age not present in the dataset. They should not be inserted.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testScalePatientAgeNoAgeInfo() {
        final String studyDate = "19801010";
        new ScaleFunctionTest()
                .withData(
                        new LocallyCacheableDicomTransformation("scale-patient-age-ageless")
                                .createZip()
                                .data(TestData.ANON_2)
                                .simpleTransform((dicom) -> {
                                    dicom.remove(PatientAge);
                                    dicom.remove(PatientBirthDate);
                                    dicom.setString(StudyDate, VR.DA, studyDate);
                                })
                ).withValidation((root) -> {
                    root.putNonexistenceChecks(PatientAge, PatientBirthDate);
                    root.putValueEqualCheck(StudyDate, studyDate, VR.DA);
                }).run();
    }

    /**
     * Tests using the new scaling function with (0008,0020) Study Date, (0010,0030) Patient's Birth Date, and
     * (0010,1010) Patient's Age set to represent a 50 year-old to check that:
     *   1) Those dates remain unchanged
     *   2) The age remains unchanged
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testScalePatientAgeBelow89() {
        new ScaleFunctionTest()
                .withValuesAndDefaultValidation(STANDARD_SCALE_BELOW_89_DATA)
                .run();
    }

    /**
     * Tests using the new scaling function with (0008,0020) Study Date, (0010,0030) Patient's Birth Date, and
     * (0010,1010) Patient's Age set to represent a 100 year-old to check that:
     *   1) The study date remains unchanged.
     *   2) The DOB is shifted relative to the day of the study such that the patient will "turn 89" the day after
     *     the study (i.e. DOB shifted to 19110201).
     *   3) The age is shifted to 089Y.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testScalePatientAgeAbove89() {
        new ScaleFunctionTest()
                .withData(STANDARD_SCALE_ABOVE_89_DATA)
                .withValidation(validateScaleFields(STANDARD_SCALE_ABOVE_89_DATA.studyDate, "19110201", "089Y"))
                .run();
    }

    /**
     * Tests using a shift in combination with the new scaling function with (0008,0020) Study Date, (0010,0030)
     * Patient's Birth Date, and (0010,1010) Patient's Age set to represent a 50 year-old to check that:
     *   1) The study date remains unchanged.
     *   2) The DOB is unaffected by the scaling function and then shifted 14 days by the secondary shift function.
     *   3) The age remains unchanged.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testScalePatientAgeFullAnonBelow89() {
        new ScaleFunctionTest(CUSTOM_SCALE_PLUS_SHIFT)
                .withData(STANDARD_SCALE_BELOW_89_DATA)
                .withValidation(validateScaleFields(
                        STANDARD_SCALE_BELOW_89_DATA.studyDate,
                        "19500115",
                        STANDARD_SCALE_BELOW_89_DATA.age
                )).run();
    }

    /**
     * Tests using a shift in combination with the new scaling function with (0008,0020) Study Date, (0010,0030)
     * Patient's Birth Date, and (0010,1010) Patient's Age set to represent a 100 year-old to check that:
     *   1) The study date remains unchanged.
     *   2) The DOB is shifted:
     *          a) relative to the day of the study such that the patient will "turn 89" the day after
     *             the study (i.e. DOB shifted 19000101 -> 19110201).
     *          b) then shifted 14 days by the secondary shift function (i.e. from 19110201 -> 19110215).
     *   3) The age is shifted to 089Y.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testScalePatientAgeFullAnonAbove89() {
        new ScaleFunctionTest(CUSTOM_SCALE_PLUS_SHIFT)
                .withData(STANDARD_SCALE_ABOVE_89_DATA)
                .withValidation(validateScaleFields(
                        STANDARD_SCALE_ABOVE_89_DATA.studyDate,
                        "19110215",
                        "089Y"
                )).run();
    }

    private class ScaleFunctionTest extends BasicAnonymizationTest {

        ScaleFunctionTest() {
            super(SCALE_FUNCTION, "standalone.das");
        }

        ScaleFunctionTest(String customScript) {
            super(SCALE_FUNCTION, customScript);
        }

        BasicAnonymizationTest withValuesAndDefaultValidation(String dataId, String studyDate, String dob, String age) {
            return withValuesAndDefaultValidation(new ScalingTransformation(dataId, studyDate, dob, age));
        }

        BasicAnonymizationTest withValuesAndDefaultValidation(ScalingTransformation scalingTransformation) {
            return withData(scalingTransformation).withValidation(scalingTransformation.defaultValidation());
        }

    }

    private static class ScalingTransformation extends LocallyCacheableDicomTransformation {
        final String studyDate;
        final String dob;
        final String age;

        ScalingTransformation(String identifier, String studyDate, String dob, String age) {
            super(identifier);
            this.studyDate = studyDate;
            this.dob = dob;
            this.age = age;
            createZip()
                    .data(TestData.ANON_2)
                    .simpleTransform((dicom) -> {
                        dicom.setString(StudyDate, VR.DA, studyDate);
                        dicom.setString(PatientBirthDate, VR.DA, dob);
                        dicom.setString(PatientAge, VR.AS, age);

                        dicom.setString(0x00850010, VR.LO, "PRIVSEQ");
                        final Attributes sequenceItem = new Attributes();
                        sequenceItem.setString(PatientBirthDate, VR.DA, dob);
                        sequenceItem.setString(PatientAge, VR.AS, age);
                        dicom.newSequence(PRIVATE_SEQUENCE, 0).add(sequenceItem);
                    });
        }

        Consumer<RootDicomObject> defaultValidation() {
            return validateScaleFields(studyDate, dob, age);
        }
    }

    private static Consumer<RootDicomObject> validateScaleFields(String studyDate, String dob, String age) {
        return (root) -> {
            root.putValueEqualCheck(StudyDate, studyDate, VR.DA);
            root.putValueEqualCheck(PatientBirthDate, dob, VR.DA);
            root.putValueEqualCheck(PatientAge, age, VR.AS);

            root.putSequenceCheck(PRIVATE_SEQUENCE, (item) -> {
                item.putValueEqualCheck(PatientBirthDate, dob, VR.DA);
                item.putValueEqualCheck(PatientAge, age, VR.AS);
            });
        };
    }

}
