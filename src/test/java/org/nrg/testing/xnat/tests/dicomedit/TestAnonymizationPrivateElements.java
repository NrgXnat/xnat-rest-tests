package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.DicomObject;
import org.nrg.testing.dicom.InterfileDicomValidation;
import org.nrg.testing.dicom.RemoveAllPrivateTags;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.ScriptValidation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_10_1;
import org.nrg.xnat.versions.Xnat_1_10_3;
import org.nrg.xnat.versions.Xnat_1_7_7;
import org.nrg.xnat.versions.Xnat_1_8_10;
import org.nrg.xnat.versions.Xnat_1_8_7;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.nrg.xnat.enums.DicomEditVersion.DE_4;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationPrivateElements extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> PRIVATE_DELETE_VALIDATION = (root) -> {
        root.putNonexistenceChecks("(0029,0010)", "(0029,1019)", "(2001,1031)");
        root.putNonexistenceChecks("(2001,100f)");
        root.putWildcardedNonexistenceCheck("(2001,108@)");
        root.putValueEqualCheck("(2001,1081)", "1");
        root.putValueEqualCheck("(2001,1083)", "127.787174999999");
        root.putValueEqualCheck("(2001,1085)", "3");
        root.putValueEqualCheck("(2001,1087)", "1H");
        root.putValueEqualCheck("(2001,1089)", "0");
        root.putValueEqualCheck("(2001,108b)", "B");
        root.putNonexistenceChecks("(2005,1402)");

        root.putSequenceCheck("(2001,105f)", (privateSeqItem) -> privateSeqItem.putValueEqualCheck("(2005,1072)", "0"));
    };

    private static final String PRIVATE_CREATOR_ID_1 = "AE 1";
    private static final String PRIVATE_CREATOR_ID_2 = "AE 2";
    private static final String INNER_PRIVATE_CREATOR_ID = "AE 3";
    private static final int PRIVATE_ID_1_TAG = 0x00650010;
    private static final int PRIVATE_ID_2_TAG = 0x00650011;
    private static final int INNER_PRIVATE_ID_TAG = 0x00770010;
    private static final int PRIVATE_ID_1_ELEMENT = 0x00651010;
    private static final int PRIVATE_ID_1_SEQUENCE = 0x00651040;
    private static final int PRIVATE_ID_2_ELEMENT = 0x00651120;
    private static final int INNER_PRIVATE_ID_ELEMENT = 0x00771035;
    private static final String HIGH_GROUP_CREATOR_ID = "XNAT QA HIGH GROUP";
    private static final int HIGH_GROUP_ID_TAG = 0xF2150010;
    private static final int HIGH_GROUP_ELEMENT = 0xF2151050;
    private static final String OTHER_HIGH_GROUP_CREATOR_ID = "XNAT QA OTHER GROUP";
    private static final int OTHER_HIGH_GROUP_ID_TAG = 0x97530010;
    private static final int OTHER_HIGH_GROUP_ELEMENT = 0x97531050;
    private static final LocallyCacheableDicomTransformation HIGH_GROUP_PRIVATE_DATA = new LocallyCacheableDicomTransformation("anon2_high_group_private_tags")
            .data(TestData.ANON_2)
            .createZip()
            .simpleTransform(
                    (dicom) -> {
                        dicom.setString(HIGH_GROUP_ID_TAG, VR.LO, HIGH_GROUP_CREATOR_ID);
                        dicom.setString(HIGH_GROUP_ELEMENT, VR.LT, "retained");
                        dicom.setString(OTHER_HIGH_GROUP_ID_TAG, VR.LO, OTHER_HIGH_GROUP_CREATOR_ID);
                        dicom.setString(OTHER_HIGH_GROUP_ELEMENT, VR.LT, "removed");
                    }
            );
    private static final String BINARY_CREATOR_ID = "XNAT QA BINARY";
    private static final int BINARY_ID_TAG = 0x00710010;
    private static final int BINARY_ELEMENT = 0x00711010;
    private static final int BINARY_SEQUENCE = 0x00711020;
    private static final byte[] EVERY_BYTE_VALUE = everyByteValue();
    private static final LocallyCacheableDicomTransformation BINARY_PRIVATE_DATA = new LocallyCacheableDicomTransformation("anon2_binary_private_values")
            .data(TestData.ANON_2)
            .createZip()
            .simpleTransform(
                    (dicom) -> {
                        dicom.setString(BINARY_ID_TAG, VR.LO, BINARY_CREATOR_ID);
                        dicom.setBytes(BINARY_ELEMENT, VR.OB, EVERY_BYTE_VALUE);
                        final Attributes item = new Attributes();
                        item.setString(BINARY_ID_TAG, VR.LO, BINARY_CREATOR_ID);
                        item.setBytes(BINARY_ELEMENT, VR.OB, EVERY_BYTE_VALUE);
                        dicom.newSequence(BINARY_SEQUENCE, 1).add(item);
                    }
            );
    private static final String PARTIAL_CREATOR_ID = "XNAT QA PARTIAL";
    private static final int PARTIAL_ID_TAG = 0x00730010;
    private static final int PARTIAL_UNNAMED_ELEMENT = 0x00731010;
    private static final int PARTIAL_NAMED_ELEMENT = 0x00731020;
    private static final LocallyCacheableDicomTransformation PARTIAL_PRIVATE_BLOCK_DATA = new LocallyCacheableDicomTransformation("anon2_partial_private_block")
            .data(TestData.ANON_2)
            .createZip()
            .simpleTransform(
                    (dicom) -> {
                        dicom.setString(PARTIAL_ID_TAG, VR.LO, PARTIAL_CREATOR_ID);
                        dicom.setString(PARTIAL_UNNAMED_ELEMENT, VR.LT, "not named");
                        dicom.setString(PARTIAL_NAMED_ELEMENT, VR.LT, "named");
                    }
            );
    private static final String MEDCOM_CREATOR_ID = "SIEMENS MEDCOM HEADER";
    private static final int MEDCOM_ID_TAG = 0x00290010;
    private static final int APPLICATION_HEADER_SEQUENCE = 0x00291040;
    private static final int APPLICATION_HEADER_TYPE = 0x00291041;
    private static final int APPLICATION_HEADER_INFO = 0x00291044;
    private static final LocallyCacheableDicomTransformation EMPTY_PRIVATE_BINARY_DATA = new LocallyCacheableDicomTransformation("anon2_empty_private_binary_value")
            .data(TestData.ANON_2)
            .createZip()
            .simpleTransform(
                    (dicom) -> {
                        dicom.setString(MEDCOM_ID_TAG, VR.LO, MEDCOM_CREATOR_ID);
                        final Attributes item = new Attributes();
                        item.setString(MEDCOM_ID_TAG, VR.LO, MEDCOM_CREATOR_ID);
                        item.setString(APPLICATION_HEADER_TYPE, VR.CS, "SOM 5 TIMESTAMPS");
                        item.setNull(APPLICATION_HEADER_INFO, VR.OB);
                        dicom.newSequence(APPLICATION_HEADER_SEQUENCE, 1).add(item);
                    }
            );

    private static final LocallyCacheableDicomTransformation RETAIN_PRIVATE_TAGS_CUSTOM_DATA = new LocallyCacheableDicomTransformation("anon2_retain_custom_private_tags")
            .data(TestData.ANON_2)
            .createZip()
            .simpleTransform(
                    (dicom) -> {
                        dicom.setString(PRIVATE_ID_1_TAG, VR.LO, PRIVATE_CREATOR_ID_1);
                        dicom.setString(PRIVATE_ID_2_TAG, VR.LO, PRIVATE_CREATOR_ID_2);
                        dicom.setString(PRIVATE_ID_1_ELEMENT, VR.LT, "hello there");
                        final Sequence privateSequence = dicom.newSequence(PRIVATE_ID_1_SEQUENCE, 1);
                        final Attributes privateItem = new Attributes();
                        privateItem.setString(INNER_PRIVATE_ID_TAG, VR.LO, INNER_PRIVATE_CREATOR_ID);
                        privateItem.setString(INNER_PRIVATE_ID_ELEMENT, VR.LT, "retained");
                        privateSequence.add(privateItem);
                        dicom.setString(PRIVATE_ID_2_ELEMENT, VR.LT, "hello again");

                        final Sequence sequence = dicom.newSequence(Tag.FiducialIdentifierCodeSequence, 1);
                        final Attributes item1 = new Attributes();
                        item1.setString(Tag.CodeValue, VR.SH, "sticking around");
                        item1.setString(PRIVATE_ID_1_TAG, VR.LO, PRIVATE_CREATOR_ID_1);
                        item1.setString(PRIVATE_ID_2_TAG, VR.LO, PRIVATE_CREATOR_ID_2);
                        item1.setString(PRIVATE_ID_1_ELEMENT, VR.LT, "inner");
                        item1.setString(PRIVATE_ID_2_ELEMENT, VR.LT, "inner again");
                        sequence.add(item1);
                    }
            );

    private static final Consumer<RootDicomObject> RETAIN_PRIVATE_TAGS_CUSTOM_VALIDATION = (root) -> {
        root.putValueEqualCheck("(0008,9205)", "MONOCHROME");

        root.putValueEqualCheck(PRIVATE_ID_1_TAG, PRIVATE_CREATOR_ID_1, VR.LO);
        root.putValueEqualCheck(PRIVATE_ID_2_TAG, PRIVATE_CREATOR_ID_2, VR.LO);
        root.putValueEqualCheck(PRIVATE_ID_1_ELEMENT, "hello there", VR.LT);

        root.putSequenceCheck(PRIVATE_ID_1_SEQUENCE, (item) -> {
            item.putValueEqualCheck(INNER_PRIVATE_ID_TAG, INNER_PRIVATE_CREATOR_ID, VR.LO);
            item.putValueEqualCheck(INNER_PRIVATE_ID_ELEMENT, "retained", VR.LT);
        });

        root.putValueEqualCheck(PRIVATE_ID_2_ELEMENT, "hello again", VR.LT);

        root.putSequenceCheck(Tag.FiducialIdentifierCodeSequence, (item) -> {
            item.putNonexistenceChecks(PRIVATE_ID_1_TAG);
            item.putNonexistenceChecks(PRIVATE_ID_1_ELEMENT);
            item.putValueEqualCheck(PRIVATE_ID_2_TAG, PRIVATE_CREATOR_ID_2, VR.LO);
            item.putValueEqualCheck(PRIVATE_ID_2_ELEMENT, "inner again", VR.LT);
        });

        root.putWildcardedNonexistenceCheck("(2001,XXXX)");
        root.putWildcardedNonexistenceCheck("(2005,10XX)");
        root.putWildcardedNonexistenceCheck("(2005,11XX)");
        root.putWildcardedNonexistenceCheck("(2005,12XX)");
        root.putWildcardedNonexistenceCheck("(2005,13XX)");
        root.putNonexistenceChecks("(2005,1401)", "(2005,1403)");

        root.putSequenceCheck("(2005,1402)", (item) -> {
            item.putExistenceChecks("(0008,0100)", "(0008,0102)", "(0008,0103)", "(0008,0104)", "(0008,010d)");
            item.putValueEqualCheck("(0008,010b)", "N");
        });

        root.putSequenceCheck("(5200,9229)", (item) -> {
            item.putValueEqualCheck("(0018,9180)", "ELECTRIC_FIELD");
            item.putNonexistenceChecks("(2005,140e)");
        });
    };

    @AddedIn(Xnat_1_7_7.class) // this technically could probably be earlier, but this is fine
    public void testRemoveAllPrivateTagsDE6() {
        new BasicAnonymizationTest("removeAllPrivateTags.das")
                .withValidation(new RemoveAllPrivateTags())
                .run();
    }

    public void testPrivateDeleteDE4() {
        new BasicAnonymizationTest("privateDelete.das")
                .withDicomEditVersion(DE_4)
                .withValidation(PRIVATE_DELETE_VALIDATION)
                .run();
    }

    public void testPrivateDeleteDE6() {
        new BasicAnonymizationTest("privateDelete.das")
                .withValidation(PRIVATE_DELETE_VALIDATION)
                .run();
    }

    @ExpectedFailure(jiraIssue = "DE-14")
    public void testPrivateAssignmentDE6() {
        new BasicAnonymizationTest("privateAssignment.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(300A,000E)", "TSE");
                    root.putValueEqualCheck("(2001,1006)", "YAH");
                    root.putValueEqualCheck("(2001,1024)", "Y");
                    root.putValueEqualCheck("(0039,0010)", "INSERTED PRIVATE CREATOR ID");
                    root.putValueEqualCheck("(0039,1001)", "COOL");
                    root.putValueEqualCheck("(0039,0011)", "ANOTHER");
                    root.putValueEqualCheck("(0039,1199)", "Meow");
                    root.putValueEqualCheck("(0039,11ee)", "Much Meow");
                }).run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testPrivateAssignmentPreexisting() {
        new BasicAnonymizationTest("privateAssignmentPreexisting.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(2001,1063)", "HERE", VR.CS);
                }).run();
    }

    public void testMixedPrivateStandardSequence() {
        new BasicAnonymizationTest("mixedPrivateStandardSequence.das")
                .withValidation((root) -> {
                    root.putSequenceCheck("(2001,105F)", (privateSeqItem) -> {
                        privateSeqItem.putNonexistenceChecks("(2001,1032)", "(2005,133e)");
                        privateSeqItem.putValueEqualCheck("(2001,1033)", "AP");
                        privateSeqItem.putValueEqualCheck("(2005,1390)", "R_A");
                    });

                    root.putSequenceCheck(
                            "(5200,9230)",
                            (perFrameFunctionalGroupsSeqItem0) -> {
                                perFrameFunctionalGroupsSeqItem0.putSequenceCheck("(2005,140f)", (innerSeqItem) -> {
                                    innerSeqItem.putValueEqualCheck("(0018,0021)", "SK");
                                });
                            }, (perFrameFunctionalGroupsSeqItem1) -> {
                                perFrameFunctionalGroupsSeqItem1.putWildcardedNonexistenceCheck("(2005,14XX)");
                            }, (perFrameFunctionalGroupsSeqItem2) -> {
                                perFrameFunctionalGroupsSeqItem2.putSequenceCheck("(2005,140f)", (innerSeqItem) -> {
                                    innerSeqItem.putValueEqualCheck("(0018,0020)", "SE");
                                });
                            }
                   );
                }).run();
    }

    /**
     * Tests DE-45
     */
    @AddedIn(Xnat_1_8_7.class)
    public void testRetainPrivateTagsDE6LegacyClassic() {
        new BasicAnonymizationTest(RETAIN_PRIVATE_TAGS, "legacyClassic.das")
                .withValidation((root) -> {
                    root.putExistenceChecks("(2001,0010)", "(2001,100C)");
                    root.putExistenceChecks("(2005,0010)", "(2005,1012)");
                    root.putNonexistenceChecks("(2005,0011)", "(2005,0012)", "(2005,0013)", "(2005,0014)");

                    final Consumer<DicomObject> commonCheck = (item) -> item.putNonexistenceChecks("(2001,0014)", "(2001,140F)");

                    root.putSequenceCheck(
                            "(5200,9230)",
                            commonCheck,
                            commonCheck,
                            commonCheck
                    );
                }).run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testRetainPrivateTagsList() {
        new StandardRetainPrivateTagsTest("list.das").run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testRetainPrivateTagsClassic() {
        new StandardRetainPrivateTagsTest("classic.das").run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testRetainPrivateTagsMultipleLists() {
        new StandardRetainPrivateTagsTest("multipleLists.das").run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testRetainPrivateTagsVariable() {
        new StandardRetainPrivateTagsTest("variable.das").run();
    }

    /**
     * A private group >= 0x8000 puts the group and element together past Integer.MAX_VALUE: (F215,1050)
     * is 0xF2151050. Anonymizing an object containing such a block used to fail outright. Retaining one
     * has to keep its private creator declaration as well as its data, because without the declaration
     * the retained elements cannot be attributed to a vendor block.
     */
    @AddedIn(Xnat_1_10_1.class)
    public void testRetainPrivateTagsHighGroup() {
        new BasicAnonymizationTest(RETAIN_PRIVATE_TAGS, "highGroup.das")
                .withData(HIGH_GROUP_PRIVATE_DATA)
                .withValidation((root) -> {
                    root.putValueEqualCheck(HIGH_GROUP_ID_TAG, HIGH_GROUP_CREATOR_ID, VR.LO);
                    root.putValueEqualCheck(HIGH_GROUP_ELEMENT, "retained", VR.LT);

                    root.putNonexistenceChecks(OTHER_HIGH_GROUP_ID_TAG, OTHER_HIGH_GROUP_ELEMENT);
                }).run();
    }

    /**
     * Retained binary values keep their bytes, at the root and in a private sequence item. The value holds every
     * byte value, so it is not valid UTF-8.
     */
    @AddedIn(Xnat_1_10_3.class)
    public void testRetainPrivateTagsKeepsBinaryValues() {
        new BasicAnonymizationTest(RETAIN_PRIVATE_TAGS, "binaryValues.das")
                .withData(BINARY_PRIVATE_DATA)
                .withValidation(new RetainedBinaryValues())
                .run();
    }

    /**
     * Keeping part of a private block deletes the rest of it. The script names neither (0073,1010) nor (2001,1010):
     * each is the element in block 0x10 whose element number repeats the block number. (2001,1010) is in the block
     * of (2001,105F), the sequence that holds the retained (2005) elements.
     */
    @AddedIn(Xnat_1_10_3.class)
    public void testRetainPrivateTagsKeepsOnlyNamedElements() {
        new BasicAnonymizationTest(RETAIN_PRIVATE_TAGS, "partialBlock.das")
                .withData(PARTIAL_PRIVATE_BLOCK_DATA)
                .withValidation((root) -> {
                    root.putValueEqualCheck(PARTIAL_ID_TAG, PARTIAL_CREATOR_ID, VR.LO);
                    root.putValueEqualCheck(PARTIAL_NAMED_ELEMENT, "named", VR.LT);
                    root.putNonexistenceChecks(PARTIAL_UNNAMED_ELEMENT);

                    root.putValueEqualCheck("(2001,0010)", "Philips Imaging DD 001", VR.LO);
                    root.putSequenceCheck("(2001,105f)", (privateSeqItem) -> {
                        privateSeqItem.putValueEqualCheck("(2005,0010)", "Philips MR Imaging DD 001", VR.LO);
                        privateSeqItem.putValueEqualCheck("(2005,1072)", "0");
                    });
                    root.putNonexistenceChecks("(2001,1010)");
                }).run();
    }

    /**
     * An empty private OB does not stop the script. The item is like a Siemens MEDCOM Application Header Sequence
     * item with an empty Application Header Info.
     */
    @AddedIn(Xnat_1_10_3.class)
    public void testRetainPrivateTagsWithEmptyBinaryValue() {
        new BasicAnonymizationTest(RETAIN_PRIVATE_TAGS, "emptyBinaryValue.das")
                .withData(EMPTY_PRIVATE_BINARY_DATA)
                .withValidation((root) -> {
                    root.putValueEqualCheck(MEDCOM_ID_TAG, MEDCOM_CREATOR_ID, VR.LO);
                    root.putSequenceCheck(APPLICATION_HEADER_SEQUENCE, (item) -> {
                        item.putValueEqualCheck(MEDCOM_ID_TAG, MEDCOM_CREATOR_ID, VR.LO);
                        item.putValueEqualCheck(APPLICATION_HEADER_TYPE, "SOM 5 TIMESTAMPS", VR.CS);
                        item.putValueEqualCheck(APPLICATION_HEADER_INFO, "", VR.OB);
                    });
                }).run();
    }

    private static byte[] everyByteValue() {
        final byte[] value = new byte[256];
        for (int i = 0; i < value.length; i++) {
            value[i] = (byte) i;
        }
        return value;
    }

    private class StandardRetainPrivateTagsTest extends BasicAnonymizationTest {

        StandardRetainPrivateTagsTest(String scriptName) {
            super(RETAIN_PRIVATE_TAGS, scriptName);
            withData(RETAIN_PRIVATE_TAGS_CUSTOM_DATA);
            withValidation(RETAIN_PRIVATE_TAGS_CUSTOM_VALIDATION);
        }

    }

    /**
     * Compares the retained binary values byte for byte. RootDicomObject compares values as strings.
     */
    private static class RetainedBinaryValues extends ScriptValidation {

        @Override
        public void validateScriptRan(List<File> dicomFiles) {
            assertEquals(dicomFiles.size(), 2);
            for (File dicomFile : dicomFiles) {
                final Attributes root = DicomUtils.readDicom(dicomFile);
                checkBinaryValue(root);
                final Attributes item = root.getNestedDataset(BINARY_SEQUENCE);
                assertNotNull(item);
                checkBinaryValue(item);
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

        private void checkBinaryValue(Attributes dataset) {
            assertEquals(dataset.getString(BINARY_ID_TAG), BINARY_CREATOR_ID);
            assertEquals(dataset.getVR(BINARY_ELEMENT), VR.OB);
            try {
                assertEquals(dataset.getBytes(BINARY_ELEMENT), EVERY_BYTE_VALUE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

}
