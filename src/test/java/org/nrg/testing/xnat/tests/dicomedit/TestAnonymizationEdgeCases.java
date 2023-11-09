package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.DuplicatedInvalidPrivateTagRemoval;
import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.values.DicomSequence;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_7_7;

import java.util.function.Consumer;

import static org.nrg.xnat.enums.DicomEditVersion.DE_4;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationEdgeCases extends BaseAnonymizationTest {

    private static final Consumer<RootDicomObject> ASSIGN_TO_NULL_VALIDATOR = (root) -> {
        root.putValueEqualCheck("(0010,1005)", "");
        root.putValueEqualCheck("(0010,1050)", "");
    };
    private static final Consumer<RootDicomObject> TRAILING_COMMENT_VALIDATION = (root) ->
            root.putNonexistenceChecks("(0028,0004)");

    /**
     * Tests DE-21
     */
    @TestRequires(admin = true, data = TestData.ANON_DUPLICATE_PRIVATE_TAG)
    @AddedIn(Xnat_1_7_7.class) // this technically could probably be earlier, but this is fine
    public void testInvalidDuplicatedPrivateTagRemovalDE6() {
        new BasicAnonymizationTest("de21.das")
                .withData(TestData.ANON_DUPLICATE_PRIVATE_TAG)
                .withValidation(new DuplicatedInvalidPrivateTagRemoval())
                .run();
    }

    public void testAssignToNullDE4() {
        new BasicAnonymizationTest("assignToNull.das")
                .withDicomEditVersion(DE_4)
                .withValidation(ASSIGN_TO_NULL_VALIDATOR)
                .run();
    }

    @ExpectedFailure(jiraIssue = "DE-47")
    public void testAssignToNullDE6() {
        new BasicAnonymizationTest("assignToNull.das")
                .withValidation(ASSIGN_TO_NULL_VALIDATOR)
                .run();
    }

    public void testTrailingCommentDE4() {
        new BasicAnonymizationTest("trailingComment.das")
                .withDicomEditVersion(DE_4)
                .withValidation(TRAILING_COMMENT_VALIDATION)
                .run();
    }

    public void testTrailingCommentDE6() {
        new BasicAnonymizationTest("trailingComment.das")
                .withValidation(TRAILING_COMMENT_VALIDATION)
                .run();
    }

    @ExpectedFailure(jiraIssue = "DE-91")
    public void testDeleteLastItemInSequence() {
        new BasicAnonymizationTest("standardSequenceDeleteLastItem.das")
                .withValidation((root) -> root.putSequenceCheck("(0008,1110)", new DicomSequence()))
                .run();
    }

}
