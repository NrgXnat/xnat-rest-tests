package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.DicomObject;
import org.nrg.testing.dicom.LevelWildcardScript;
import org.nrg.testing.dicom.StandardSequenceDeleteScript;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_0;

import java.util.function.Consumer;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationSequenceOperations extends BaseAnonymizationTest {

    public void testStandardSequenceAssignmentDE6() {
        new BasicAnonymizationTest("standardSequenceAssignment.das")
                .withValidation((root) -> {
                    root.putSequenceCheck("(0008,1072)", (opIdSeqItem) -> opIdSeqItem.putValueEqualCheck("(0008,0080)", "WUSTL"));

                    root.putSequenceCheck(
                            "(5200,9229)",
                            (sharedFunctionalGroupsSeqItem0) -> {
                                sharedFunctionalGroupsSeqItem0.putNonexistenceChecks("(0008,0100)");
                                sharedFunctionalGroupsSeqItem0.putValueEqualCheck("(0018,9180)", "ELECTRIC_FIELD");
                            }, (sharedFunctionalGroupsSeqItem1) -> {
                                sharedFunctionalGroupsSeqItem1.putValueEqualCheck("(0008,0100)", "NICE");
                                sharedFunctionalGroupsSeqItem1.putValueEqualCheck("(0018,9180)", "DB_DT");
                                sharedFunctionalGroupsSeqItem1.putNonexistenceChecks("(0018,9182)");
                            });

                    root.putValueEqualCheck("(0040,1002)", "00209056");
                }).run();
    }

    public void testStandardSequenceDeleteDE6() {
        new BasicAnonymizationTest("standardSequenceDelete.das")
                .withValidation(new StandardSequenceDeleteScript())
                .run();
    }

    @AddedIn(Xnat_1_8_0.class)
    public void testLevelWildcardsDE6() {
        new BasicAnonymizationTest("levelWildcard.das")
                .withValidation(new LevelWildcardScript())
                .run();
    }

    public void testSequenceItemWildcard() {
        new BasicAnonymizationTest("sequenceItemWildcard.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,1030)", "Fruit_Struct");
                    final Consumer<DicomObject> assertElementsMissing = (item) -> item.putNonexistenceChecks("(0018,9152)", "(0008,1030)");

                    root.putSequenceCheck(
                            "(5200,9230)",
                            (perFrameFunctionalGroupsSeqItem0) -> {
                                assertElementsMissing.accept(perFrameFunctionalGroupsSeqItem0);
                                perFrameFunctionalGroupsSeqItem0.putValueEqualCheck("(0008,9208)", "MAGNITUDE");
                            },
                            assertElementsMissing,
                            assertElementsMissing
                    );
                }).run();
    }

}
