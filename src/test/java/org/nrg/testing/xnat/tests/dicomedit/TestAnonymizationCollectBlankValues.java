package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.Anon2NoOp;
import org.nrg.testing.dicom.AnonConstants;
import org.nrg.testing.dicom.BlankValuesPrivateElements;
import org.nrg.testing.dicom.BlankValuesSeveralElements;
import org.nrg.testing.dicom.BlankValuesSingleStandardTag;
import org.nrg.testing.dicom.BlankValuesSingleStandardTagpath;
import org.nrg.testing.dicom.BlankValuesWildcards;
import org.nrg.testing.dicom.DicomObject;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_10;

import java.util.function.Consumer;

@TestRequires(admin = true, data = TestData.ANON_2)
@AddedIn(Xnat_1_8_10.class)
public class TestAnonymizationCollectBlankValues extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> VARIABLE_SCRIPT_VALIDATION = (root) -> {
        root.putEmptyChecks("(0018,9020)", "(0044,0001)", "(0018,9011)", "(0018,9100)");
        root.putValueEqualCheck("(0018,9004)", "RESEARCH");
        root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
        root.putEmptyChecks("(0018,9016)", "(0018,9017)", "(0018,9037)", "(0018,9170)", "(0018,9172)");

        root.putSequenceCheck(
                "(0018,0026)",
                (item1) -> {
                    item1.putValueEqualCheck("(0018,0028)", "150000");
                    item1.putSequenceCheck("(0018,0029)", (innerItem) -> {
                        innerItem.putValueEqualCheck("(0008,0100)", "C-21047");
                        innerItem.putValueEqualCheck("(0008,0104)", "Ethanol");
                    });
                    item1.putEmptyChecks("(0018,0034)");
                }, (item2) -> {
                    item2.putValueEqualCheck("(0018,0028)", "100000");
                    item2.putEmptyChecks("(0018,0034)");
                }
        );

        root.putEmptyChecks("(0018,980b)");

        root.putSequenceCheck("(5200,9229)", (item) -> {
            item.putSequenceCheck("(0018,9006)", (innerItem) -> {
                innerItem.putValueEqualCheck("(0018,9022)", "NO");
                innerItem.putEmptyChecks("(0018,9020)", "(0018,9028)");
            });

            item.putSequenceCheck("(0018,9042)", (innerItem) -> {
                innerItem.putValueEqualCheck("(0018,9044)", "NO");
                innerItem.putSequenceCheck("(0018,9045)", (coilItem) -> {
                    coilItem.putValueEqualCheck("(0018,9047)", "SENSE");
                    coilItem.putEmptyChecks("(0018,9048)");
                });
            });
        });

        root.putEmptyChecks("(2001,1087)");
        root.putValueEqualCheck("(2001,1088)", "1");
    };

    private static final LocallyCacheableDicomTransformation ANON2_PLUS_RECENT_ELEMENT = new LocallyCacheableDicomTransformation("anon2-blank-known-add")
            .createZip()
            .data(TestData.ANON_2)
            .simpleTransform((dicom) -> dicom.setString(0x00180034, VR.LO, "SOMETHING"));

    public void testBlankValuesSingleStandardTag() {
        new CollectBlankValuesTest("singleStandardTag.das")
                .withValidation(new BlankValuesSingleStandardTag())
                .run();
    }

    public void testBlankValuesSingleStandardTagDirect() {
        new CollectBlankValuesTest("singleStandardTagDirect.das")
                .withValidation(new BlankValuesSingleStandardTag())
                .run();
    }

    public void testBlankValuesSingleStandardTagList() {
        new CollectBlankValuesTest("singleStandardTagList.das")
                .withValidation(new BlankValuesSingleStandardTag())
                .run();
    }

    public void testBlankValuesTagsVariable() {
        new CollectBlankValuesTest("tagsVariable.das")
                .withValidation(VARIABLE_SCRIPT_VALIDATION)
                .run();
    }

    public void testBlankValuesValuesVariable() {
        new CollectBlankValuesTest("valuesVariable.das")
                .withValidation(VARIABLE_SCRIPT_VALIDATION)
                .run();
    }

    public void testBlankValuesMultipleVariables() {
        new CollectBlankValuesTest("multipleVariables.das")
                .withValidation(VARIABLE_SCRIPT_VALIDATION)
                .run();
    }

    public void testBlankValuesNestedLists() {
        new CollectBlankValuesTest("nestedLists.das")
                .withValidation(VARIABLE_SCRIPT_VALIDATION)
                .run();
    }

    public void testBlankValuesNestedWithOverlap() {
        new CollectBlankValuesTest("nestedWithOverlap.das")
                .withValidation(VARIABLE_SCRIPT_VALIDATION)
                .run();
    }

    public void testBlankValuesMultilineListDirect() {
        new CollectBlankValuesTest("multilineListDirect.das")
                .withValidation(VARIABLE_SCRIPT_VALIDATION)
                .run();
    }

    public void testBlankValuesNoArguments() {
        new CollectBlankValuesTest("noArguments.das")
                .withValidation(new Anon2NoOp())
                .run();
    }

    public void testBlankValuesSingleStandardTagpath() {
        new CollectBlankValuesTest("singleStandardTagpath.das")
                .withValidation(new BlankValuesSingleStandardTagpath())
                .withData(ANON2_PLUS_RECENT_ELEMENT)
                .run();
    }

    public void testBlankValuesMixedArguments() {
        new CollectBlankValuesTest("mixedArgs.das")
                .withValidation(new BlankValuesSeveralElements())
                .withData(ANON2_PLUS_RECENT_ELEMENT)
                .run();
    }

    public void testBlankValuesMixedArgumentsList() {
        new CollectBlankValuesTest("mixedArgsList.das")
                .withValidation(new BlankValuesSeveralElements())
                .withData(ANON2_PLUS_RECENT_ELEMENT)
                .run();
    }

    public void testBlankValuesWildcards() {
        new CollectBlankValuesTest("wildcards.das")
                .withValidation(new BlankValuesWildcards())
                .run();
    }

    public void testBlankValuesPrivateElements() {
        new CollectBlankValuesTest("privateElements.das")
                .withValidation(new BlankValuesPrivateElements())
                .run();
    }

    public void testBlankValuesVrPreservation() {
        new CollectBlankValuesTest("vrPreservation.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0020)", "", VR.DA);
                    root.putValueEqualCheck("(0008,0021)", "", VR.DA);
                    root.putValueEqualCheck("(0018,1204)", "", VR.DA);
                    root.putValueEqualCheck("(0010,1030)", "10", VR.DS);
                    root.putValueEqualCheck("(0020,0013)", "", VR.IS);
                    root.putValueEqualCheck("(0028,0002)", "", VR.US);
                    root.putValueEqualCheck("(0028,9001)", "", VR.UL);
                    root.putValueEqualCheck("(2001,1013)", "", VR.SL);
                    root.putValueEqualCheck("(2001,1014)", "", VR.SL);
                    root.putValueEqualCheck("(2001,1015)", "", VR.SS);
                    root.putValueEqualCheck("(2001,1018)", "3", VR.SL);
                    root.putValueEqualCheck("(2001,101d)", "", VR.IS);
                    root.putSequenceCheck("(2001,105f)", (item) -> {
                        item.putValueEqualCheck("(2001,102d)", "3", VR.SS);
                        item.putValueEqualCheck("(2001,1035)", "", VR.SS);
                    });
                    root.putValueEqualCheck("(2001,1088)", "", VR.DS);
                }).withData(
                        new LocallyCacheableDicomTransformation("anon2_vr_data")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> {
                                    dicom.setInt(Tag.InstanceNumber, VR.IS, 1);
                                    dicom.setString(0x00181204, VR.DA, "20100430");
                                })
                ).run();
    }

    /**
     * Tests the ability for a DicomEdit script to capture a value, derive a value from it, and then blank the original
     * value while maintaining the derived value.
     */
    public void testBlankValuesValuePreservation() {
        new CollectBlankValuesTest("valuePreservation.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
                    root.putEmptyChecks("(0008,9207)");
                    root.putValueEqualCheck("(0008,9208)", "WAS NONE");
                    root.putEmptyChecks("(0018,9016)", "(0018,9017)", "(2005,144f)");
                }).run();
    }

    /**
     * Tests the ability for a DicomEdit script to appropriately blank a multivalued (VM > 1) DICOM element from the dataset.
     */
    public void testBlankValuesMultivaluedMatching() {
        new CollectBlankValuesTest("multivaluedMatching.das")
                .withValidation((root) -> {
                    root.putEmptyChecks("(0008,0008)");
                    final Consumer<DicomObject> sequenceItemSpecifier = (dicomObject) -> {
                        dicomObject.putSequenceCheck("(0018,9226)", (innerItem) -> {
                            innerItem.putEmptyChecks("(0008,9007)");
                            innerItem.putValueEqualCheck("(0008,9206)", "DISTORTED");
                        });
                    };
                    root.putSequenceCheck("(5200,9230)", sequenceItemSpecifier, sequenceItemSpecifier, sequenceItemSpecifier);
                }).run();
    }

    /**
     * Tests the ability for a DicomEdit script to blank a DICOM element in group 0002
     * (a file meta element), or to blank a value found therein for the entire dataset
     */
    public void testBlankValuesFmiMod() {
        new CollectBlankValuesTest("fmi.das")
                .withValidation((root) -> {
                    root.putEmptyChecks("(0002,0016)", "(0002,0018)", "(0010,1040)", "(0044,0001)");
                    root.putValueEqualCheck("(0002,0017)", "UNAFFECTED");
                    root.putValueEqualCheck("(0044,0002)", "YES");
                }).withData(
                        new LocallyCacheableDicomTransformation("anon2_group2_test")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> {
                                    dicom.setString(Tag.ReceivingApplicationEntityTitle, VR.AE, "CHOCOLATE");
                                    dicom.setString(Tag.SendingApplicationEntityTitle, VR.AE, "UNAFFECTED");
                                    dicom.setString(Tag.PatientAddress, VR.LO, "DicomBrowser");
                                })
                ).run();
    }

    /**
     * Tests the behavior for a DicomEdit script to attempt to blank a private element that contains ASCII bytes
     * with a VR of UN. This could conceivably be supported in the future, but is not for now, so the expected behavior
     * is that these are not considered matches.
     */
    public void testBlankValuesImplicitAscii() {
        new CollectBlankValuesTest("implicitAscii.das")
                .withValidation((root) -> {
                    root.putEmptyChecks("(0044,0001)");

                    root.putSequenceCheck(
                            "(0018,0026)",
                            (item1) -> {
                                item1.putValueEqualCheck("(0018,0028)", "150000");
                                item1.putEmptyChecks("(0018,0034)");
                            }, (item2) -> {
                                item2.putValueEqualCheck("(0018,0028)", "100000");
                                item2.putEmptyChecks("(0018,0034)");
                            }
                    );

                    root.putValueEqualCheck("(0049,1010)", "67\\72\\79\\67\\79\\76\\65\\84\\69\\0"); // CHOCOLATE (plus null) bytes to decimal
                    root.putValueEqualCheck("(0049,1011)", "67\\72\\79\\67\\79\\76\\65\\84\\84\\69"); // CHOCOLATTE bytes to decimal
                }).withData(
                        new LocallyCacheableDicomTransformation("anon2_un_ascii")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> {
                                    dicom.setString(0x00490010, VR.LO, "Ascii test");
                                    dicom.setBytes(0x00491010, VR.UN, new byte[]{ 0x43, 0x48, 0x4f, 0x43, 0x4f, 0x4c, 0x41, 0x54, 0x45, 0x00 }); // ASCII for "CHOCOLATE", plus a null byte for padding
                                    dicom.setBytes(0x00491011, VR.UN, new byte[]{ 0x43, 0x48, 0x4f, 0x43, 0x4f, 0x4c, 0x41, 0x54, 0x54, 0x45 }); // ASCII for "CHOCOLATTE"
                                })
                ).run();
    }

    /**
     * Tests the scenario where blankValues is called on an element that does not exist in the dataset. No changes to
     * the dataset are expected, including especially that the specified element should NOT be inserted. However, other
     * elements specified in the function invocation should still be processed as normal.
     */
    public void testBlankValuesNonexistentElement() {
        new CollectBlankValuesTest("nonexistent.das")
                .withValidation(new BlankValuesSingleStandardTag())
                .run();
    }

    /**
     * Tests the scenarios where values differ by whitespace.
     * 1) Leading/trailing spaces defined in PS3.5 sec 6.2 to be insignificant
     *    would still be considered matches for "blanking matches". These are the AE values.
     * 2) A whitespace difference internal to the value, which is the LO value, is NOT a match.
     */
    public void testBlankValuesWhitespace() {
        final String rwvMapping = "Philips Real WorldValue Mapping";

        new CollectBlankValuesTest("whitespace.das")
                .withValidation((root) -> {
                    root.putEmptyChecks("(0008,0054)", "(0008,0055)", "(0028,3003)");
                    root.putValueEqualCheck("(0028,3004)", rwvMapping);
                }).withData(
                        new LocallyCacheableDicomTransformation("anon2_ae_whitespace")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> {
                                    dicom.setString(Tag.RetrieveAETitle, VR.AE, "DEVICE");
                                    dicom.setString(Tag.StationAETitle, VR.AE, " DEVICE ");
                                    dicom.setString(Tag.ModalityLUTType, VR.LO, rwvMapping);
                                })
                ).run();
    }

    /**
     * Tests the scenario where values differ by only capitalization. For the blankValues function, these are
     * expected to NOT be considered matches.
     */
    public void testBlankValuesCapitalization() {
        final String institutionNameVal = "PhilipS MeDicAl SyStems";
        final String institutionAddressVal = "philips medical systems";

        new CollectBlankValuesTest("capitalization.das")
                .withValidation((root) -> {
                    root.putEmptyChecks(Tag.Manufacturer);
                    root.putValueEqualCheck(Tag.InstitutionName, institutionNameVal);
                    root.putValueEqualCheck(Tag.InstitutionAddress, institutionAddressVal);
                }).withData(
                        new LocallyCacheableDicomTransformation("anon2_capitalization")
                                .data(TestData.ANON_2)
                                .createZip()
                                .simpleTransform((dicom) -> {
                                    dicom.setString(Tag.InstitutionName, VR.LO, institutionNameVal);
                                    dicom.setString(Tag.InstitutionAddress, VR.ST, institutionAddressVal);
                                })
                ).run();
    }

    private class CollectBlankValuesTest extends BasicAnonymizationTest {

        CollectBlankValuesTest(String scriptName) {
            super("collectAndBlankValues", scriptName);
        }

    }

}
