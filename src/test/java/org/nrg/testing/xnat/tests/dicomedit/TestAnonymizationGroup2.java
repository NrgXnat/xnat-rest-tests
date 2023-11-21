package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.UID;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.AnonConstants;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_5;

import java.util.function.Consumer;

import static org.nrg.xnat.enums.DicomEditVersion.DE_4;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationGroup2 extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> GROUP_2_PRESERVE_VALIDATION = (root) ->
            root.putValueEqualCheck("(0002,0016)", "DicomBrowser");
    private static final Consumer<RootDicomObject> GROUP_2_DELETE_VALIDATION = (root) ->
            root.putNonexistenceChecks("(0002,0016)");

    /**
     * Tests DE-30
     */
    @AddedIn(Xnat_1_8_0.class)
    public void testGroup2SyncDE6() {
        new BasicAnonymizationTest("group2Sync.das")
                .withValidation((root) -> {
                    /*
                       Yes, there's 2 instances in the test data, so they shouldn't really be getting the same SOP Instance UIDs assigned. It's happening
                       here because I'm assigning SOP Instance UID from a hash of the previous value. I overlooked giving the 2 test instances different
                       instance UIDs when originally preparing the data.
                    */
                    final String expectedSOPInstanceUID = AnonConstants.ANON2_HASHED_SOP_INSTANCE_UID;

                    root.putValueEqualCheck("(0002,0002)", UID.MRImageStorage);
                    root.putValueEqualCheck("(0008,0016)", UID.MRImageStorage);
                    root.putValueEqualCheck("(0002,0003)", expectedSOPInstanceUID);
                    root.putValueEqualCheck("(0008,0018)", expectedSOPInstanceUID);
                }).run();
    }

    @AddedIn(Xnat_1_8_5.class)
    public void testGroup2PreserveDE4() {
        new BasicAnonymizationTest("standardDelete.das")
                .withDicomEditVersion(DE_4)
                .withValidation(GROUP_2_PRESERVE_VALIDATION)
                .run();
    }

    @AddedIn(Xnat_1_8_5.class)
    public void testGroup2PreserveDE6() {
        new BasicAnonymizationTest("standardDelete.das")
                .withValidation(GROUP_2_PRESERVE_VALIDATION)
                .run();
    }

    @AddedIn(Xnat_1_8_5.class)
    public void testGroup2DeleteDE4() {
        new BasicAnonymizationTest("group2Delete.das")
                .withDicomEditVersion(DE_4)
                .withValidation(GROUP_2_DELETE_VALIDATION)
                .run();
    }

    @AddedIn(Xnat_1_8_5.class)
    public void testGroup2DeleteDE6() {
        new BasicAnonymizationTest("group2Delete.das")
                .withValidation(GROUP_2_DELETE_VALIDATION)
                .run();
    }

}
