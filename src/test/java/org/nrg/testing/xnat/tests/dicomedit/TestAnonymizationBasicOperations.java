package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_7;
import org.testng.annotations.Test;

import java.util.function.Consumer;

import static org.nrg.testing.TestGroups.ANONYMIZATION;
import static org.nrg.testing.TestGroups.SMOKE;
import static org.nrg.xnat.enums.DicomEditVersion.DE_4;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationBasicOperations extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> STANDARD_REMOVAL_VALIDATION = (root) ->
            root.putNonexistenceChecks("(0008,0105)", "(0008,0033)", "(0008,002A)", "(0008,1110)");
    private static final Consumer<RootDicomObject> STANDARD_ASSIGNMENT_VALIDATION = (root) -> {
        root.putValueEqualCheck("(0008,0064)", "WSD");
        root.putValueEqualCheck("(0008,0050)", "REMOVED");
        root.putValueEqualCheck("(0008,1040)", "Dept A");
        root.putValueEqualCheck("(0018,0022)", "1\\2\\3\\4");
        root.putValueEqualCheck("(0008,0061)", "MR");
        root.putValueEqualCheck("(0010,1010)", "");
        root.putValueEqualCheck("(0018,1003)", "MR");
        root.putValueEqualCheck("(0018,1004)", "MR");
    };
    private static final Consumer<RootDicomObject> ASSIGN_FLOAT_VALIDATION = (root) ->
            root.putValueEqualCheck("(0018,9182)", "30");
    private static final Consumer<RootDicomObject> STANDARD_WILDCARD_DELETE_VALIDATION = (root) -> {
        for (String wildcardCheck : new String[]{"(0018,901#)", "(0018,905@)", "(0018,93XX)", "(0044,00XX)"}) {
            root.putWildcardedNonexistenceCheck(wildcardCheck);
        }
        root.putValueEqualCheck("(0018,9012)", "NO");
        root.putValueEqualCheck("(0018,9051)", "BODY");

        // drill down into sequences to make sure the wildcarded elements were *not* removed

        root.putSequenceCheck("(5200,9229)", (sharedFunctionalGroupsSeqItem) -> {
            sharedFunctionalGroupsSeqItem.putSequenceCheck("(0018,9049)", (innerMRTransmitCoilSeqItem) -> {
                innerMRTransmitCoilSeqItem.putValueEqualCheck("(0018,9050)", "");
                innerMRTransmitCoilSeqItem.putValueEqualCheck("(0018,9051)", "BODY");
            });
        });
    };

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testStandardElementRemovalDE4() {
        new BasicAnonymizationTest("standardDelete.das")
                .withDicomEditVersion(DE_4)
                .withValidation(STANDARD_REMOVAL_VALIDATION)
                .run();
    }

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testStandardElementRemovalDE6() {
        new BasicAnonymizationTest("standardDelete.das")
                .withValidation(STANDARD_REMOVAL_VALIDATION)
                .run();
    }

    public void testStandardAssignmentDE4() {
        new BasicAnonymizationTest("standardAssignment.das")
                .withDicomEditVersion(DE_4)
                .withValidation(STANDARD_ASSIGNMENT_VALIDATION)
                .run();
    }

    public void testStandardAssignmentDE6() {
        new BasicAnonymizationTest("standardAssignment.das")
                .withValidation(STANDARD_ASSIGNMENT_VALIDATION)
                .run();
    }

    public void testAssignFloatDE4() {
        new BasicAnonymizationTest("assignFloat.das")
                .withDicomEditVersion(DE_4)
                .withValidation(ASSIGN_FLOAT_VALIDATION)
                .run();
    }

    @AddedIn(Xnat_1_8_7.class)
    public void testAssignFloatDE6() {
        new BasicAnonymizationTest("assignFloat.das")
                .withValidation(ASSIGN_FLOAT_VALIDATION)
                .run();
    }

    public void testStandardWildcardedDeleteDE4() {
        new BasicAnonymizationTest("standardWildcardedDelete.das")
                .withDicomEditVersion(DE_4)
                .withValidation(STANDARD_WILDCARD_DELETE_VALIDATION)
                .run();
    }

    @AddedIn(Xnat_1_8_0.class)
    public void testStandardWildcardedDeleteDE6() {
        new BasicAnonymizationTest("standardWildcardedDelete.das")
                .withValidation(STANDARD_WILDCARD_DELETE_VALIDATION)
                .run();
    }

}
