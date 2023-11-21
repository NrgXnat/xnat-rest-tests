package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.LevelWildcardAssignIfExistsScript;
import org.nrg.testing.dicom.SequenceItemWildcardAssignIfExistsScript;
import org.nrg.testing.dicom.SequenceLevelWildcard;
import org.nrg.testing.dicom.TagDigitWildcardsAssignIfExistsScript;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_7;
import org.nrg.xnat.versions.Xnat_1_8_8;

@TestRequires(admin = true, data = TestData.ANON_2)
@AddedIn(Xnat_1_8_0.class)
public class TestAnonymizationIfExists extends BaseAnonymizationTest {

    private static final LocallyCacheableDicomTransformation ANON_DATA_WITH_EXTRA_PRIVATE_ELEMENTS = defineAnonDataWithExtraPrivateElements();

    public void testStandardAssignIfExists() {
        new BasicAnonymizationTest("standardAssignIfExists.das")
                .withValidation((root) -> {
                    root.putValueEqualCheck("(0008,1070)", "TECH^SCANNING");
                    root.putValueEqualCheck("(0008,1090)", "REMOVED");
                    root.putNonexistenceChecks("(0008,0061)");
                    root.putValueEqualCheck("(0018,1020)", "1.0");
                }).run();
    }

    @ExpectedFailure(jiraIssue = "DE-14")
    public void testPrivateAssignIfExists() {
        new BasicAnonymizationTest("privateAssignIfExists.das")
                .withValidation((root) -> {
                    root.putNonexistenceChecks("(2001,1004)");
                    root.putValueEqualCheck("(2001,100c)", "Y");
                    root.putValueEqualCheck("(2005,1391)", "PERSON^NAME");
                }).run();
    }

    @AddedIn(Xnat_1_8_7.class)
    public void testSequenceAssignIfExists() {
        new BasicAnonymizationTest("sequenceAssignIfExists.das")
                .withValidation((root) -> {
                    root.putNonexistenceChecks("(0008,1115)");
                    root.putSequenceCheck("(0008,1110)", (item) -> {
                        item.putValueEqualCheck("(0008,1155)", "1.3.46.670589.11.5730.5.0.1744.2010043012343685002");
                        item.putNonexistenceChecks("(0020,0013)");
                    });
                    root.putSequenceCheck("(0008,1111)", (item) -> {
                        item.putValueEqualCheck("(0008,0013)", "131758");
                        item.putValueEqualCheck("(0020,0013)", "10");
                    });
                }).run();
    }

    @AddedIn(Xnat_1_8_8.class)
    public void testLevelWildcardAsteriskAssignIfExists() {
        new BasicAnonymizationTest("levelWildcardAsteriskAssignIfExists.das")
                .withData(ANON_DATA_WITH_EXTRA_PRIVATE_ELEMENTS)
                .withValidation(new LevelWildcardAssignIfExistsScript(SequenceLevelWildcard.ASTERISK))
                .run();
    }

    @AddedIn(Xnat_1_8_8.class)
    public void testLevelWildcardDotAssignIfExists() {
        new BasicAnonymizationTest("levelWildcardDotAssignIfExists.das")
                .withData(ANON_DATA_WITH_EXTRA_PRIVATE_ELEMENTS)
                .withValidation(new LevelWildcardAssignIfExistsScript(SequenceLevelWildcard.DOT))
                .run();
    }

    @AddedIn(Xnat_1_8_8.class)
    public void testLevelWildcardPlusAssignIfExists() {
        new BasicAnonymizationTest("levelWildcardPlusAssignIfExists.das")
                .withData(ANON_DATA_WITH_EXTRA_PRIVATE_ELEMENTS)
                .withValidation(new LevelWildcardAssignIfExistsScript(SequenceLevelWildcard.PLUS))
                .run();
    }

    @AddedIn(Xnat_1_8_8.class)
    public void testTagDigitWildcardAssignIfExists() {
        new BasicAnonymizationTest("tagDigitWildcardAssignIfExists.das")
                .withValidation(new TagDigitWildcardsAssignIfExistsScript())
                .run();
    }

    @AddedIn(Xnat_1_8_8.class)
    public void testSequenceItemWildcardAssignIfExists() {
        new BasicAnonymizationTest("sequenceItemWildcardAssignIfExists.das")
                .withValidation(new SequenceItemWildcardAssignIfExistsScript())
                .run();
    }

    private static LocallyCacheableDicomTransformation defineAnonDataWithExtraPrivateElements() {
        return new LocallyCacheableDicomTransformation("anon2_2")
                .createZip()
                .data(TestData.ANON_2)
                .simpleTransform(
                        (dicom) -> {
                            final String creatorId = "REST tests";
                            dicom.setString(creatorId, 0x00991050, VR.LO, "ROOT VALUE");
                            final Sequence sequence = dicom.newSequence(creatorId, 0x00991051, 1);
                            final Attributes sequenceItem = new Attributes();
                            sequenceItem.setString(creatorId, 0x00991050, VR.LO, "LEVEL ONE");
                            sequence.add(sequenceItem);
                            final Sequence nestedSequence = sequenceItem.newSequence(creatorId, 0x00991051, 1);
                            final Attributes nestedSequenceItem = new Attributes();
                            nestedSequenceItem.setString(creatorId, 0x00991050, VR.LO, "LEVEL TWO");
                            nestedSequence.add(nestedSequenceItem);
                        }
                );
    }

}
