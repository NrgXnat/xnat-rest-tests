package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.Anon2NoOp;
import org.nrg.testing.dicom.AnonConstants;
import org.nrg.testing.dicom.DicomObject;
import org.nrg.testing.dicom.MapReferencedUIDsScript;
import org.nrg.testing.dicom.PrivateMapReferencedUIDsScript;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.TransferSyntaxScript;
import org.nrg.testing.dicom.UIDModScript;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_1;
import org.nrg.xnat.versions.Xnat_1_8_10;
import org.nrg.xnat.versions.Xnat_1_8_4;

import java.util.function.Consumer;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationUids extends BaseAnonymizationTest {

    private static final String UID_12345 = "1.2.3.4.5";
    private static final String UID_123456 = "1.2.3.4.5.6";
    private static final String HASH_12345 = "2.25.281097975800109040096884226991818420711";
    private static final String HASH_123456 = "2.25.262228503153619312954819897114011402300";
    private static final LocallyCacheableDicomTransformation ANON2_ADDITIONAL_UIDS = new LocallyCacheableDicomTransformation("anon2_additional_uids")
            .data(TestData.ANON_2)
            .createZip()
            .simpleTransform((dicom) -> {
                dicom.setString(Tag.ContextUID, VR.UI, UID_12345);
                dicom.setString(Tag.MappingResourceUID, VR.UI, UID_123456);
            });
    private static final Consumer<RootDicomObject> ANON2_COMPLEX_VALIDATION = (root) -> {
        root.putValueEqualCheck("(0002,0003)", AnonConstants.ANON2_HASHED_SOP_INSTANCE_UID);
        root.putValueEqualCheck("(0008,0018)", AnonConstants.ANON2_HASHED_SOP_INSTANCE_UID);

        root.putValueEqualCheck("(0008,0060)", "MR");

        root.putSequenceCheck(
                "(5200,9230)",
                itemWithInnerPrivateItemWithExpectedInstance("2.25.218702659752879586881118089470122189067"),
                itemWithInnerPrivateItemWithExpectedInstance("2.25.125971472755950709005956275458938056756"),
                itemWithInnerPrivateItemWithExpectedInstance("2.25.113490037064677013839255572017229303454")
        );

        root.putValueEqualCheck("(0008,0117)", HASH_12345);
        root.putValueEqualCheck("(0008,0118)", HASH_123456);
        root.putNonexistenceChecks("(0008,0110)", "(0008,0115)");

        root.putSequenceCheck(
                "(0020,9222)",
                (item0) -> item0.putValueEqualCheck("(0020,9164)", "1.3.46.670589.11.5730.5.0.3224.2010071913175879000"),
                (item1) -> item1.putValueEqualCheck("(0020,9164)", "2.25.332186669190258916436704332443158107660")
        );
    };

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
                .withValidation(new TransferSyntaxScript(UID.JPEGLosslessSV1))
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


    /**
     * Tests basic usage of hashUIDList function to hash a UID tag to modify it in place.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListBasic() {
        new BasicAnonymizationTest("hashUIDList.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0002,0003)", AnonConstants.ANON2_HASHED_SOP_INSTANCE_UID, VR.UI);
                    root.putValueEqualCheck("(0008,0018)", AnonConstants.ANON2_HASHED_SOP_INSTANCE_UID, VR.UI);

                    root.putSequenceCheck(
                            "(5200,9230)",
                            itemWithInnerPrivateItemWithExpectedInstance("1.3.46.670589.11.5730.5.0.3144.2010043013074510002"),
                            itemWithInnerPrivateItemWithExpectedInstance("1.3.46.670589.11.5730.5.0.3144.2010043013074550011"),
                            itemWithInnerPrivateItemWithExpectedInstance("1.3.46.670589.11.5730.5.0.3144.2010043013074531010")
                    );
                }).run();
    }

    /**
     * Tests usage of hashUIDList function to hash UID tags in place that correspond to wildcarded and/or sequence
     * tagpaths.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListComplexTagpaths() {
        new BasicAnonymizationTest("hashUIDListComplexTagpaths.das")
                .withData(ANON2_ADDITIONAL_UIDS)
                .withValidation(ANON2_COMPLEX_VALIDATION)
                .run();
    }

    /**
     * Tests usage of hashUIDList function to hash private UID tags in place.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListPrivateElements() {
        final int privateCreatorTag = 0x00490010;
        final int privateElement1 = 0x00491010;
        final int privateElement2 = 0x00491011;
        final String privateCreatorId = "REST";
        new BasicAnonymizationTest("hashUIDListPrivateUids.das")
                .withData(
                        new LocallyCacheableDicomTransformation("anon2_additional_private_uids")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> {
                                    dicom.setString(privateCreatorTag, VR.LO, privateCreatorId);
                                    dicom.setString(privateCreatorId, privateElement1, VR.UI, UID_12345);
                                    dicom.setString(privateCreatorId, privateElement2, VR.UI, UID_123456);
                                })
                ).withValidation((root) -> {
                    root.putValueEqualCheck(privateCreatorTag, privateCreatorId, VR.LO);
                    root.putValueEqualCheck(privateElement1, HASH_12345, VR.UI);
                    root.putValueEqualCheck(privateElement2, HASH_123456, VR.UI);
                }).run();
    }

    /**
     * Tests usage of hashUIDList function by specifying a non-existent UID element. There is expected to be no change
     * to the DICOM instance, including specifically the element not being inserted.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListNonexistent() {
        new BasicAnonymizationTest("hashUIDListNonexistent.das")
                .withValidation(new Anon2NoOp())
                .run();
    }

    /**
     * Tests usage of hashUIDList function to modify a file meta element (group 2) in place.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListGroup2() {
        new BasicAnonymizationTest("hashUIDListGroup2.das")
                .withValidation((root) -> root.putValueEqualCheck(Tag.ImplementationClassUID, "2.25.125633612215841525931434398546456782529", VR.UI))
                .run();
    }

    /**
     * Tests usage of hashUIDList function to hash a DICOM element with multiple UIDs stored (VM > 1)
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListMultivalued() {
        final int privateCreatorTag = 0x00350010;
        final int privateElementTag = 0x00351050;
        final String privateCreatorId = "UIDs";
        final String junkUid = "99.99999.99999999999.999999999999999.999999999";
        final String expectedHashedUids = String.join("\\", HASH_12345, HASH_123456, "2.25.25035822876724830630813723844719420300");

        new BasicAnonymizationTest("hashUIDListMultivalued.das")
                .withData(
                        new LocallyCacheableDicomTransformation("anon2_multivalued_uid")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((root) -> {
                                    root.setString(Tag.RelatedGeneralSOPClassUID, VR.UI, UID_12345, UID_123456, junkUid);
                                    root.setString(privateCreatorTag, VR.LO, privateCreatorId);
                                    root.setString(privateElementTag, VR.UI, UID_12345, UID_123456, junkUid);
                                })
                ).withValidation((root) -> {
                    root.putValueEqualCheck(Tag.RelatedGeneralSOPClassUID, expectedHashedUids, VR.UI);
                    root.putValueEqualCheck(privateCreatorTag, privateCreatorId, VR.LO);
                    root.putValueEqualCheck(privateElementTag, expectedHashedUids, VR.UI);
                }).run();
    }

    /**
     * Tests usage of hashUIDList function to hash UID tags in place that correspond to wildcarded and/or sequence
     * tagpaths, passed in with a DicomEdit list construct.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListComplexTagpathsTrueList() {
        new BasicAnonymizationTest("hashUIDListComplexTagpathsList.das")
                .withData(ANON2_ADDITIONAL_UIDS)
                .withValidation(ANON2_COMPLEX_VALIDATION)
                .run();
    }

    /**
     * Tests usage of hashUIDList function to hash UID tags in place that correspond to wildcarded and/or sequence
     * tagpaths, passed in with two DicomEdit list constructs.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListComplexTagpathsTwoLists() {
        new BasicAnonymizationTest("hashUIDListComplexTagpathsLists.das")
                .withData(ANON2_ADDITIONAL_UIDS)
                .withValidation(ANON2_COMPLEX_VALIDATION)
                .run();
    }

    /**
     * Tests usage of hashUIDList function to hash UID tags in place that correspond to wildcarded and/or sequence
     * tagpaths, passed in with a DicomEdit list of list constructs.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListComplexTagpathsListOfLists() {
        new BasicAnonymizationTest("hashUIDListComplexTagpathsListOfLists.das")
                .withData(ANON2_ADDITIONAL_UIDS)
                .withValidation(ANON2_COMPLEX_VALIDATION)
                .run();
    }

    /**
     * Tests usage of hashUIDList function to hash UID tags in place that correspond to wildcarded and/or sequence
     * tagpaths, passed in with a DicomEdit list of list constructs whose inner lists have been assigned in
     * the different possible conditional blocks.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListComplexTagpathsListOfListsFromConditionalBlocks() {
        new BasicAnonymizationTest("hashUIDListConditionalBlocks.das")
                .withData(ANON2_ADDITIONAL_UIDS)
                .withValidation(ANON2_COMPLEX_VALIDATION)
                .run();
    }

    /**
     * Tests usage of hashUIDList function to hash UID tags in place that correspond to wildcarded and/or sequence
     * tagpaths, passed in with a DicomEdit list of list constructs whose inner lists have been assigned in
     * the different possible inline conditionals.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListComplexTagpathsListOfListsFromInlineConditionals() {
        new BasicAnonymizationTest("hashUIDListConditionalInline.das")
                .withData(ANON2_ADDITIONAL_UIDS)
                .withValidation(ANON2_COMPLEX_VALIDATION)
                .run();
    }

    /**
     * Tests usage of hashUIDList function to hash UID tags in place that correspond to wildcarded and/or sequence
     * tagpaths, passed in with two DicomEdit list constructs stored in variables.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListComplexTagpathsTrueListVariables() {
        new BasicAnonymizationTest("hashUIDListComplexTagpathsListVariables.das")
                .withData(ANON2_ADDITIONAL_UIDS)
                .withValidation(ANON2_COMPLEX_VALIDATION)
                .run();
    }

    /**
     * Tests usage of hashUIDList function to hash UID tags in place that correspond to DICOM tags that are made up. In
     * practice, made up standard tags shouldn't exist, but we can use this as a proxy for a UID tag that has been
     * added to the standard without having made it into our version of dcm4che.
     */
    @AddedIn(Xnat_1_8_10.class)
    public void testHashUidListUnknownElement() {
        final int tag = AnonConstants.FAKE_DICOM_TAG;
        new BasicAnonymizationTest("hashUIDListUnknownElement.das")
                .withData(
                        new LocallyCacheableDicomTransformation("anon2_made_up_standard_uid")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> dicom.setString(tag, VR.UI, UID_12345))
                ).withValidation((root) -> root.putValueEqualCheck(tag, HASH_12345, VR.UI))
                .run();
    }

    private static Consumer<DicomObject> itemWithInnerPrivateItemWithExpectedInstance(String expectedUid) {
        return (item) -> item.putSequenceCheck("(2005,140f)", (innerItem) -> innerItem.putValueEqualCheck("(0008,0018)", expectedUid, VR.UI));
    }

}
