package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.enums.DicomEditVersion;
import org.nrg.xnat.versions.Xnat_1_8_10;

import java.util.function.Consumer;

@TestRequires(admin = true, data = TestData.ANON_2)
@AddedIn(Xnat_1_8_10.class)
public class TestAnonymizationVrConsiderations extends BaseAnonymizationTest {

    private static final LocallyCacheableDicomTransformation DATA_WITH_NEW_VRS = new LocallyCacheableDicomTransformation("anon2_plus_new_vrs")
            .createZip()
            .data(TestData.ANON_2)
            .simpleTransform((dicom) -> {
                dicom.setLong(Tag.FileOffsetInContainer, VR.UV, 1125899906842624L);
                dicom.setBytes(Tag.SelectorOVValue, VR.OV, new byte[]{ 0x10, 0x04, 0x4A, 0x4B, 0x4C, 0x5A, 0x11, 0x12, 0x16, 0x19, 0x46, 0x11, 0x31, 0x33, 0x34, 0x35 });
                dicom.setLong(Tag.SelectorSVValue, VR.SV, -1125899906842624L);
                dicom.setString(Tag.URNCodeValue, VR.UR, "https://xnat.org");
                dicom.setString(Tag.GPSMapDatum, VR.UT, "https://xnat.org");
            });
    private static final Consumer<RootDicomObject> NEW_VR_DATA_VALIDATION = (root) -> {
        root.putValueEqualCheck(Tag.FileOffsetInContainer, "1125899906842624", VR.UV);
        root.putValueEqualCheck(Tag.SelectorOVValue, "1301921051013940240\\3833745468635355414", VR.OV);
        root.putValueEqualCheck(Tag.SelectorSVValue, "-1125899906842624", VR.SV);
        root.putValueEqualCheck(Tag.URNCodeValue, "https://xnat.org", VR.UR);
        root.putValueEqualCheck(Tag.GPSMapDatum, "https://xnat.org", VR.UT);
    };
    private static final Consumer<RootDicomObject> NEW_VR_MOD_VALIDATION = (root) -> {
        root.putValueEqualCheck(Tag.FileOffsetInContainer, "52147483647", VR.UV);
        root.putValueEqualCheck(Tag.SelectorSVValue, "52147483647", VR.SV);
        root.putValueEqualCheck(Tag.URNCodeValue, "https://xnat.org/download", VR.UR);
        root.putValueEqualCheck(Tag.GPSMapDatum, "https://xnat.org/download", VR.UT);
    };

    /**
     * Tests a no-operation DicomEdit 4 script to make sure that elements with modern VRs are preserved correctly,
     * both in terms of value and VR.
     */
    public void testNoopAnonVrPreservationDE4() {
        new BasicAnonymizationTest("noop.das")
                .withData(DATA_WITH_NEW_VRS)
                .withDicomEditVersion(DicomEditVersion.DE_4)
                .withValidation(NEW_VR_DATA_VALIDATION)
                .run();
    }

    /**
     * Tests a no-operation DicomEdit 6 script to make sure that elements with modern VRs are preserved correctly,
     * both in terms of value and VR.
     */
    public void testNoopAnonVrPreservationDE6() {
        new BasicAnonymizationTest("noop.das")
                .withData(DATA_WITH_NEW_VRS)
                .withValidation(NEW_VR_DATA_VALIDATION)
                .run();
    }

    /**
     * Tests assigning elements that are defined in PS 3.6 to be expected to have some very recent VRs. DicomEdit is
     * still on an old version of dcm4che at the time this test is being written, so it cannot properly assign the
     * VR. When dcm4che is updated, this test should be revisited because it may start working. It is also possible
     * that the test will have bugs as we have not had a version of XNAT that can support it in order to properly
     * "test" the test.
     */
    @ExpectedFailure(jiraIssue = "DE-119")
    public void testAnonRecentVrModificationDE4() {
        new BasicAnonymizationTest("recentVrMod.das")
                .withData(DATA_WITH_NEW_VRS)
                .withDicomEditVersion(DicomEditVersion.DE_4)
                .withValidation(NEW_VR_MOD_VALIDATION)
                .run();
    }

    /**
     * Tests assigning elements that are defined in PS 3.6 to be expected to have some very recent VRs. DicomEdit is
     * still on an old version of dcm4che at the time this test is being written, so it cannot properly assign the
     * VR. When dcm4che is updated, this test should be revisited because it may start working. It is also possible
     * that the test will have bugs as we have not had a version of XNAT that can support it in order to properly
     * "test" the test.
     */
    @ExpectedFailure(jiraIssue = "DE-119")
    public void testAnonRecentVrModificationDE6() {
        new BasicAnonymizationTest("recentVrMod.das")
                .withData(DATA_WITH_NEW_VRS)
                .withValidation(NEW_VR_MOD_VALIDATION)
                .run();
    }

}
