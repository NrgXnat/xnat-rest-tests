package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.LocalTestDicom;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.Anon2NoOp;
import org.nrg.testing.dicom.DicomObject;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_10;

import java.util.function.Consumer;

@AddedIn(Xnat_1_8_10.class)
@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationRemoveTags extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> COMMON_VALIDATION = (rootDicomObject) -> {
        rootDicomObject.putNonexistenceChecks("(0008,0020)", "(0008,0021)", "(0008,0022)", "(0008,1140)");
        rootDicomObject.putValueEqualCheck("(0010,0010)", "Sample Patient");
    };

    /**
     * Tests using the new removeTags function with simple varargs parameters to delete DICOM tags.
     */
    public void testRemoveTagsStandard() {
        new BasicAnonymizationTest("removeTags.das")
                .withData(LocalTestDicom.SAMPLE1_SMALL_SUBSET)
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    /**
     * Tests using the new removeTags function with simple varargs parameters spread across multiple lines
     * with the line breaks escaped.
     */
    public void testRemoveTagsMultiline() {
        new BasicAnonymizationTest("removeTagsMultiline.das")
                .withData(LocalTestDicom.SAMPLE1_SMALL_SUBSET)
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    /**
     * Tests using the new removeTags function with a DicomEdit 6.6 list construct passed as a parameter.
     */
    public void testRemoveTagsList() {
        new BasicAnonymizationTest("removeTagsList.das")
                .withData(LocalTestDicom.SAMPLE1_SMALL_SUBSET)
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    /**
     * Tests using the new removeTags function with a DicomEdit 6.6 list construct passed as a parameter. There are
     * various flavors of comments within the list to ensure the syntax works as expected.
     */
    public void testRemoveTagsListComments() {
        new BasicAnonymizationTest(SYNTAX_MISCELLANEA, "removeTagsList.das")
                .withData(LocalTestDicom.SAMPLE1_SMALL_SUBSET)
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    /**
     * Tests using the new removeTags function with a DicomEdit 6.6 list construct passed as a parameter. The list is
     * split across multiple lines without comments or explicit escaping of the new lines to ensure that the syntax
     * works as expected.
     */
    public void testRemoveTagsListUnescaped() {
        new BasicAnonymizationTest(SYNTAX_MISCELLANEA, "removeTagsListUnescaped.das")
                .withData(LocalTestDicom.SAMPLE1_SMALL_SUBSET)
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    /**
     * Tests using the new resolveTags function with a DicomEdit 6.6 list construct passed in as a variable.
     */
    public void testRemoveTagsListVariable() {
        new BasicAnonymizationTest("removeTagsListVariable.das")
                .withData(LocalTestDicom.SAMPLE1_SMALL_SUBSET)
                .withValidation(COMMON_VALIDATION)
                .run();
    }

    /**
     * Tests using the new resolveTags function on various specifications of DICOM elements that do not exist in the tested
     * dataset. The goal is to make sure that this doesn't throw an exception, and also that it doesn't improperly
     * disturb other elements. Scenarios included are:
     *   1. removal of a standard DICOM element not present
     *   2. removal of an entire sequence item which is not present
     *   3. removal of a specific DICOM element which is not present in the specified sequence item
     *   4. removal of a specific DICOM element within a non-existent sequence item
     *   5. removal of a private element which does not exist
     */
    public void testRemoveTagsVariousNonexistent() {
        new BasicAnonymizationTest("removeTagsNonexistent.das")
                .withData(TestData.ANON_2)
                .withValidation(new Anon2NoOp())
                .run();
    }

    /**
     * Tests using the new resolveTags function to delete private tags from a DICOM dataset.
     */
    public void testRemoveTagsPrivate() {
        new BasicAnonymizationTest("removeTagsPrivate.das")
                .withData(TestData.ANON_2)
                .withValidation((root) -> {
                    root.putValueEqualCheck("(2005,1027)", "MINIMUM");
                    root.putNonexistenceChecks("(2005,1327)", "(2005,1330)");
                    root.putValueEqualCheck("(2005,1328)", "ORIGINAL");
                }).run();
    }

    /**
     * Tests using the new resolveTags function to delete a sequence item rather than DICOM elements. The behavior seems
     * to be consistent with the behavior when using the "-" deletion operator, although I would argue the behavior is a
     * bug rather than a feature. The current behavior is to remove all of the elements from the item, but to leave
     * the item itself. More details can be found in ticket DE-91.
     */
    public void testRemoveTagsSequenceItem() {
        new BasicAnonymizationTest("removeTagsSequenceItem.das")
                .withData(TestData.ANON_2)
                .withValidation((root) -> {
                    root.putSequenceCheck(
                            "(0010,1002)",
                            (item0) -> {
                                item0.putNonexistenceChecks("(0010,0010)", "(0010,0020)", "(0010,0021)");
                            }, (item1) -> {
                                item1.putValueEqualCheck("(0010,0010)", "WATERMELON^FRUIT");
                                item1.putValueEqualCheck("(0010,0020)", "PERSON_0001");
                                item1.putValueEqualCheck("(0010,0021)", "Hospital_0002");
                            }, (item2) -> {
                                item2.putSequenceCheck("(0010,1002)", (nestedItem) -> {
                                    nestedItem.putValueEqualCheck("(0010,0010)", "WATERMELON^FRUIT");
                                    nestedItem.putValueEqualCheck("(0010,0020)", "PATIENT_0003");
                                    nestedItem.putValueEqualCheck("(0010,0021)", "Hospital_0003");
                                });
                            }, (item3) -> {
                                item3.putValueEqualCheck("(0010,0010)", "WATERMELON^FRUIT");
                                item3.putValueEqualCheck("(0010,0020)", "PERSON_0004");
                                item3.putValueEqualCheck("(0010,0021)", "Hospital_0004");
                            }
                    );
                }).run();
    }

    /**
     * Tests using the new resolveTags function to remove DICOM tags specified with the various wildcards that can
     * stand-in for individual digits in the tag.
     */
    public void testRemoveTagsTagDigitWildcards() {
        new BasicAnonymizationTest("removeTagsTagDigitWildcards.das")
                .withData(TestData.ANON_2)
                .withValidation((root) -> {
                    root.putWildcardedNonexistenceCheck("(0008,000X)");
                    root.putWildcardedNonexistenceCheck("(0008,003#)");
                    root.putWildcardedNonexistenceCheck("(0010,10@@)");
                    root.putValueEqualCheck("(0008,0030)", "123437");
                    root.putValueEqualCheck("(0010,1005)", "NAME^NAME");
                }).run();
    }

    /**
     * Tests using the new resolveTags function to remove elements with the various item-level wildcards (., *, +),
     * sequence index wildcard (%), and some combinations with tag digit wildcards.
     */
    public void testRemoveTagsSequenceLevelWildcards() {
        final String group2001PrivateCreator = "Philips Imaging DD 001";
        new BasicAnonymizationTest("removeTagsSequenceWildcards.das")
                .withData(TestData.ANON_2)
                .withValidation((root) -> {
                    root.putNonexistenceChecks("(0020,0013)");
                    root.putValueEqualCheck("(0010,0020)", "Watermelon_MR1");
                    root.putValueEqualCheck("(0008,0100)", "");

                    root.putSequenceCheck(
                            "(0018,0026)",
                            (item0) -> {
                                item0.putNonexistenceChecks("(0018,0028)", "(0018,0034)");
                                item0.putSequenceCheck("(0018,0029)", (innerItem) -> {
                                    innerItem.putNonexistenceChecks("(0008,0100)");
                                    innerItem.putValueEqualCheck("(0008,0104)", "Ethanol");
                                });
                            }, (item1) -> item1.putNonexistenceChecks("(0018,0028)", "(0018,0034)")
                    );

                    root.putValueEqualCheck("(2001,0010)", group2001PrivateCreator);
                    root.putValueEqualCheck("(2001,101f)", "NO");
                    root.putWildcardedNonexistenceCheck("(2001,10@X)");

                    root.putSequenceCheck("(2001,105f)", (item) -> {
                       item.putValueEqualCheck("(2001,0010)", group2001PrivateCreator);
                       item.putWildcardedNonexistenceCheck("(2001,10@X)");
                       item.putValueEqualCheck("(2001,1033)", "AP");
                    });

                    final Consumer<DicomObject> innerItemSupplier = (item) -> {
                        item.putSequenceCheck("(2005,140f)", (innerItem) -> {
                            innerItem.putValueEqualCheck("(2001,0010)", group2001PrivateCreator);
                            innerItem.putWildcardedNonexistenceCheck("(2001,10@X)");
                            innerItem.putValueEqualCheck("(2001,10f1)", "0\\0\\0\\0\\0\\0");
                        });
                    };
                    root.putSequenceCheck(
                            "(5200,9230)",
                            innerItemSupplier,
                            innerItemSupplier,
                            innerItemSupplier
                    );
                }).run();
    }

}
