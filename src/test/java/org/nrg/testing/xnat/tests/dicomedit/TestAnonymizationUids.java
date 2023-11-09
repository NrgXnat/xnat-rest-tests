package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.UID;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.MapReferencedUIDsScript;
import org.nrg.testing.dicom.PrivateMapReferencedUIDsScript;
import org.nrg.testing.dicom.TransferSyntaxScript;
import org.nrg.testing.dicom.UIDModScript;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_1;
import org.nrg.xnat.versions.Xnat_1_8_4;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationUids extends BaseAnonymizationTest {

    public void testUIDModsDE6() {
        new BasicAnonymizationTest("uidMod.das")
                .withValidation(new UIDModScript())
                .run();
    }

    @AddedIn(Xnat_1_8_0.class)
    public void testMapReferencedUIDs() {
        new BasicAnonymizationTest("mapReferencedUIDs.das")
                .withValidation(new MapReferencedUIDsScript())
                .run();
    }

    /**
      Also relies on the set function to fix missing private creator ID
     */
    @AddedIn(Xnat_1_8_1.class)
    @TestRequires(data = TestData.SIMPLE_PET)
    public void testPrivateMapReferencedUIDs() {
        new BasicAnonymizationTest("privateMapReferencedUIDs.das")
                .withData(TestData.SIMPLE_PET)
                .withValidation(new PrivateMapReferencedUIDsScript())
                .run();
    }

    /**
     * Capture the effect of anon on transfer syntax.
     * 1. Anon without pixel edits retains the original xfer syntax.
     * 2. Anon with pixel edits always results in EVLE data.
     * This test makes due with pre-existing data sets but flings about many images when a single image would do, thus taking much longer than necessary to run.
     */
    @AddedIn(Xnat_1_8_4.class)
    @TestRequires(data = {
            TestData.DICOM_WEB_PETMR2_PT,
            TestData.JPEGLOSSLESS_2000
    })
    public void testTransferSyntax() {
        final TestData evleData = TestData.ANON_2;
        final TestData ivleData = TestData.DICOM_WEB_PETMR2_PT;
        final TestData jpglosslessData = TestData.JPEGLOSSLESS_2000;

        new BasicAnonymizationTest("deleteFunction.das")
                .withData(evleData)
                .withValidation(new TransferSyntaxScript(UID.ExplicitVRLittleEndian))
                .run();

        new BasicAnonymizationTest("deleteFunction.das")
                .withData(ivleData)
                .withValidation(new TransferSyntaxScript(UID.ImplicitVRLittleEndian))
                .run();

        new BasicAnonymizationTest("deleteFunction.das")
                .withData(jpglosslessData)
                .withValidation(new TransferSyntaxScript(UID.JPEGLossless))
                .run();

        new BasicAnonymizationTest("alterPixelsXferSyntax.das")
                .withValidation(new TransferSyntaxScript(UID.ExplicitVRLittleEndian))
                .run();

        new BasicAnonymizationTest("alterPixelsXferSyntax.das")
                .withData(ivleData)
                .withValidation(new TransferSyntaxScript(UID.ExplicitVRLittleEndian))
                .run();

        new BasicAnonymizationTest("alterPixelsXferSyntax.das")
                .withData(jpglosslessData)
                .withValidation(new TransferSyntaxScript(UID.ExplicitVRLittleEndian))
                .run();
    }

}
