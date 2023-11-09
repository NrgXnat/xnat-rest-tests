package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.DicomObject;
import org.nrg.testing.dicom.RemoveAllPrivateTags;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_7_7;
import org.nrg.xnat.versions.Xnat_1_8_7;

import java.util.function.Consumer;

import static org.nrg.xnat.enums.DicomEditVersion.DE_4;

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
    public void testRetainPrivateTagsDE6() {
        new BasicAnonymizationTest("retainPrivateTags.das")
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

}
