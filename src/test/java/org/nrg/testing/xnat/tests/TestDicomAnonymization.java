package org.nrg.testing.xnat.tests;

import org.dcm4che3.data.*;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.*;
import org.nrg.testing.dicom.ScriptValidation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.enums.DicomEditVersion;
import org.nrg.xnat.pogo.DicomDataSet;
import org.nrg.xnat.versions.*;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

import static org.nrg.testing.TestGroups.ANONYMIZATION;
import static org.nrg.testing.TestGroups.SMOKE;
import static org.nrg.xnat.enums.DicomEditVersion.*;

@TestRequires(admin = true, data = {
        TestData.ANON_2,
        TestData.ANON_DUPLICATE_PRIVATE_TAG,
        TestData.DICOM_WEB_PETMR2_PT,
        TestData.JPEGLOSSLESS_2000
})
@Test(groups = ANONYMIZATION)
public class TestDicomAnonymization extends BaseXnatRestTest {

    private final Project anonProject = new Project();
    private final File anonData = TestData.ANON_2.toFile();
    private final File evleData = anonData;
    private final File ivleData = TestData.DICOM_WEB_PETMR2_PT.toFile();
    private final File jpglosslessData = TestData.JPEGLOSSLESS_2000.toFile();
    private final AnonScript projectAnonDE4 = XnatObjectUtils.anonScriptFromFile(DE_4, "projectAnon.das");
    private final AnonScript projectAnonDE6 = XnatObjectUtils.anonScriptFromFile(DE_6, "projectAnon.das");
    private final AnonScript siteAnonDE4 = XnatObjectUtils.anonScriptFromFile(DE_4, "siteAnon.das");
    private final AnonScript siteAnonDE6 = XnatObjectUtils.anonScriptFromFile(DE_6, "siteAnon.das");
    private final Map<AnonScript, ScriptValidation> scriptValidationMap = new HashMap<>();
    boolean projectCreated = false;

    @BeforeClass
    private void createProject() {
        mainInterface().createProject(anonProject);
        projectCreated = true;
        mainInterface().regenerateUserSession(); // hack for XNAT-5187
        scriptValidationMap.put(projectAnonDE4, new ProjectScript());
        scriptValidationMap.put(projectAnonDE6, new ProjectScript());
        scriptValidationMap.put(siteAnonDE4, new SiteScript());
        scriptValidationMap.put(siteAnonDE6, new SiteScript());
    }

    @AfterMethod(alwaysRun = true)
    private void clean() {
        restDriver.clearPrearchiveSessions(mainUser, anonProject);
        if (projectCreated) {
            mainInterface().deleteAllProjectData(anonProject);
        }
    }

    @AfterClass(alwaysRun = true)
    private void resetAnon() {
        mainAdminInterface().enableSiteAnonScript();
        mainAdminInterface().setSiteAnonScript(restDriver.getDefaultXnatAnonScript());
    }

    @Test(groups = SMOKE)
    @Basic
    public void testSubjectRelabel_4P_4S() {
        performSubjectRelabelAnonTest(projectAnonDE4, siteAnonDE4);
    }

    @Test
    public void testSubjectRelabel_4P_6S() {
        performSubjectRelabelAnonTest(projectAnonDE4, siteAnonDE6);
    }

    @Test
    public void testSubjectRelabel_6P_4S() {
        performSubjectRelabelAnonTest(projectAnonDE6, siteAnonDE4);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testSubjectRelabel_6P_6S() {
        performSubjectRelabelAnonTest(projectAnonDE6, siteAnonDE6);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testSessionRelabel_4P_4S() {
        performSessionRelabelAnonTest(projectAnonDE4, siteAnonDE4);
    }

    @Test
    public void testSessionRelabel_4P_6S() {
        performSessionRelabelAnonTest(projectAnonDE4, siteAnonDE6);
    }

    @Test
    public void testSessionRelabel_6P_4S() {
        performSessionRelabelAnonTest(projectAnonDE6, siteAnonDE4);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testSessionRelabel_6P_6S() {
        performSessionRelabelAnonTest(projectAnonDE6, siteAnonDE6);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testStandardElementRemovalDE4() {
        performBasicScriptTest(DE_4, "standardDelete.das", new StandardDeleteScript());
    }

    @Test(groups = SMOKE)
    @Basic
    public void testStandardElementRemovalDE6() {
        performBasicScriptTest(DE_6, "standardDelete.das", new StandardDeleteScript());
    }

    @Test
    public void testStandardAssignmentDE4() {
        performBasicScriptTest(DE_4, "standardAssignment.das", new StandardAssignmentScript());
    }

    @Test
    public void testStandardAssignmentDE6() {
        performBasicScriptTest(DE_6, "standardAssignment.das", new StandardAssignmentScript());
    }

    @Test
    public void testAssignToNullDE4() {
        performBasicScriptTest(DE_4, "assignToNull.das", new AssignToNullScript());
    }

    @Test
    @ExpectedFailure(jiraIssue = "DE-47")
    public void testAssignToNullDE6() {
        performBasicScriptTest(DE_6, "assignToNull.das", new AssignToNullScript());
    }

    @Test
    public void testStringFunctionsDE4() {
        performBasicScriptTest(DE_4, "stringFunctions.das", new StringFunctionsScript());
    }

    @Test
    public void testStringFunctionsDE6() {
        performBasicScriptTest(DE_6, "stringFunctions.das", new StringFunctionsScript());
    }

    @Test
    public void testUrlEncodeDE4() {
        performBasicScriptTest(DE_4, "urlEncode.das", new UrlEncodeScript());
    }

    @Test
    @ExpectedFailure(jiraIssue = "DE-7")
    public void testUrlEncodeDE6() {
        performBasicScriptTest(DE_6, "urlEncode.das", new UrlEncodeScript());
    }

    @Test
    @AddedIn(Xnat_1_7_7.class) // this technically could probably be earlier, but this is fine
    public void testRemoveAllPrivateTagsDE6() {
        performBasicScriptTest(DE_6, "removeAllPrivateTags.das", new RemoveAllPrivateTags());
    }

    @Test // Tests DE-21
    @AddedIn(Xnat_1_7_7.class) // this technically could probably be earlier, but this is fine
    public void testInvalidDuplicatedPrivateTagRemovalDE6() {
        performBasicScriptTest(DE_6, "de21.das", new DuplicatedInvalidPrivateTagRemoval(), TestData.ANON_DUPLICATE_PRIVATE_TAG.toFile());
    }

    @Test
    public void testGetURLDE4() {
        performBasicScriptTest(DE_4, "getURL.das", new GetURLScript());
    }

    @Test
    public void testGetURLDE6() {
        performBasicScriptTest(DE_6, "getURL.das", new GetURLScript());
    }

    @Test
    public void testMatchDE4() {
        performBasicScriptTest(DE_4, "match.das", new MatchScript());
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testMatchDE6() {
        performBasicScriptTest(DE_6, "match.das", new MatchScript());
    }

    @Test
    public void testUIDModsDE6() {
        performBasicScriptTest(DE_6, "uidMod.das", new UIDModScript());
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testGroup2SyncDE6() {
        performBasicScriptTest(DE_6, "group2Sync.das", new Group2SyncScript());
    }

    @Test
    public void testStandardWildcardedDeleteDE4() {
        performBasicScriptTest(DE_4, "standardWildcardedDelete.das", new StandardWildcardedDeleteScript());
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testStandardWildcardedDeleteDE6() {
        performBasicScriptTest(DE_6, "standardWildcardedDelete.das", new StandardWildcardedDeleteScript());
    }

    @Test
    public void testStandardConditionalsDE4() {
        performBasicScriptTest(DE_4, "standardConditionals.das", new StandardConditionalsScript());
    }

    @Test
    public void testStandardConditionalsDE6() {
        performBasicScriptTest(DE_6, "standardConditionals.das", new StandardConditionalsScript());
    }

    @Test
    public void testPrivateConditionalsDE4() {
        performBasicScriptTest(DE_4, "privateConditionals.das", new PrivateConditionalsScript());
    }

    @Test
    public void testPrivateConditionalsDE6() {
        performBasicScriptTest(DE_6, "privateConditionals.das", new PrivateConditionalsScript());
    }

    @Test
    public void testPrivateDeleteDE4() {
        performBasicScriptTest(DE_4, "privateDelete.das", new PrivateDeleteScript());
    }

    @Test
    public void testPrivateDeleteDE6() {
        performBasicScriptTest(DE_6, "privateDelete.das", new PrivateDeleteScript());
    }

    @Test
    @ExpectedFailure(jiraIssue = "DE-14")
    public void testPrivateAssignmentDE6() {
        performBasicScriptTest(DE_6, "privateAssignment.das", new PrivateAssignmentScript());
    }

    @Test
    public void testStandardSequenceAssignmentDE6() {
        performBasicScriptTest(DE_6, "standardSequenceAssignment.das", new StandardSequenceAssignmentScript());
    }

    @Test
    public void testStandardSequenceDeleteDE6() {
        performBasicScriptTest(DE_6, "standardSequenceDelete.das", new StandardSequenceDeleteScript());
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testLevelWildcardsDE6() {
        performBasicScriptTest(DE_6, "levelWildcard.das", new LevelWildcardScript());
    }

    @Test
    public void testSequenceItemWildcard() {
        performBasicScriptTest(DE_6, "sequenceItemWildcard.das", new SequenceItemWildcardScript());
    }

    @Test
    public void testMixedPrivateStandardSequence() {
        performBasicScriptTest(DE_6, "mixedPrivateStandardSequence.das", new MixedPrivateStandardSequenceScript());
    }

    @Test
    public void testTrailingCommentDE4() {
        performBasicScriptTest(DE_4, "trailingComment.das", new TrailingCommentScript());
    }

    @Test
    public void testTrailingCommentDE6() {
        performBasicScriptTest(DE_6, "trailingComment.das", new TrailingCommentScript());
    }

    @Test
    public void testXnatVariablesDE4() {
        performXnatVariableScriptTest(DE_4);
    }

    @Test
    public void testXnatVariablesDE6() {
        performXnatVariableScriptTest(DE_6);
    }

    @Test
    public void testAssignFloatDE4() {
        performBasicScriptTest(DE_4, "assignFloat.das", new AssignFloatScript());
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testAssignFloatDE6() {
        performBasicScriptTest(DE_6, "assignFloat.das", new AssignFloatScript());
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testStandardAssignIfExists() {
        performBasicScriptTest(DE_6, "standardAssignIfExists.das", new StandardAssignIfExistsScript());
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    @ExpectedFailure(jiraIssue = "DE-14")
    public void testPrivateAssignIfExists() {
        performBasicScriptTest(DE_6, "privateAssignIfExists.das", new PrivateAssignIfExistsScript());
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testSequenceAssignIfExists() {
        performBasicScriptTest(DE_6, "sequenceAssignIfExists.das", new SequenceAssignIfExistsScript());
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testShiftDateByIncrement() {
        performBasicScriptTest(DE_6, "shiftDate.das", new ShiftDateScript());
    }

    @Test
    @AddedIn(Xnat_1_8_1.class)
    public void testShiftDateTimeByIncrement() {
        performBasicScriptTest(DE_6, "shiftDateTime.das", new ShiftDateTimeScript());
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testShiftDateTimeByIncrementWithTimezone() {
        performBasicScriptTest(DE_6, "shiftDateTimeWithTimezone.das", new ShiftDateTimeWithTimezoneScript());
    }

    @Test
    @AddedIn(Xnat_1_8_1.class)
    public void testShiftDateTimeSequenceByIncrement() {
        performBasicScriptTest(DE_6, "shiftDateTimeSequence.das", new ShiftDateTimeSequenceScript());
    }

    /*
      Shift should not change the precision of the value.
     */
    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testShiftDateTimePrecision() {
        final LocallyCacheableDicomTransformation dicomGenerator = new LocallyCacheableDicomTransformation("dtPrecision")
                .createZip()
                .simpleTransform(TransformFunction.generateFromScratch(() -> {
                    DicomDataSet dicomDataSet = new DicomDataSet();
                    dicomDataSet.setTag( Tag.PatientName, "DTPrecision")
                            .setTag( new int[]{Tag.ReferencedStudySequence, 0, Tag.ProductExpirationDateTime}, "1960" )
                            .setTag( new int[]{Tag.ReferencedStudySequence, 1, Tag.ProductExpirationDateTime}, "196005" )
                            .setTag( new int[]{Tag.ReferencedStudySequence, 2, Tag.ProductExpirationDateTime}, "19600519" )
                            .setTag( new int[]{Tag.ReferencedStudySequence, 3, Tag.ProductExpirationDateTime}, "1960051913" )
                            .setTag( new int[]{Tag.ReferencedStudySequence, 4, Tag.ProductExpirationDateTime}, "196005191324" )
                            .setTag( new int[]{Tag.ReferencedStudySequence, 5, Tag.ProductExpirationDateTime}, "19600519132435" );
                    return Collections.singletonList( dicomDataSet.getDataset());
                }));
        dicomGenerator.build();
        File dtPrecisionData = dicomGenerator.locateOverallZip().toFile();
        performBasicScriptTest(DE_6, "shiftDateTimePrecision.das", new ShiftDateTimePrecisionScript(), dtPrecisionData);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testMapReferencedUIDs() {
        performBasicScriptTest(DE_6, "mapReferencedUIDs.das", new MapReferencedUIDsScript());
    }

    /*
      Also relies on the set function to fix missing private creator ID
     */
    @Test
    @AddedIn(Xnat_1_8_1.class)
    @TestRequires(data = TestData.SIMPLE_PET)
    public void testPrivateMapReferencedUIDs() {
        performBasicScriptTest(DE_6, "privateMapReferencedUIDs.das", new PrivateMapReferencedUIDsScript(), TestData.SIMPLE_PET.toFile());
    }

    /*
      This test is rather complex, so even if DE-51 is fixed, I'm not sure this test will work. Unfortunately,
      DE-51 is preventing me from really testing it. Another problem that may arise is whether the blackout regions
      have value=0 in every single image. There may be some W/L considerations that make the blackout regions a solid,
      but non-zero value region, so I would need to modify the check to use a possibly different value for each image.
     */
    @Test
    @AddedIn(Xnat_1_8_1.class)
    @ExpectedFailure(jiraIssue = "DE-51")
    public void testAlterPixels() {
        performBasicScriptTest(DE_6, "alterPixels.das", new AlterPixelsScript(), TestData.SAMPLE_1.toFile());
    }

    @Test
    @AddedIn(Xnat_1_8_1.class)
    public void testIsMatch() {
        performBasicScriptTest(DE_6, "isMatch.das", new IsMatchScript());
    }

    @Test
    @ExpectedFailure(jiraIssue = "DE-52")
    @AddedIn(Xnat_1_8_1.class)
    public void testSet() {
        performBasicScriptTest(DE_6, "set.das", new SetScript());
    }

    @Test
    @AddedIn(Xnat_1_8_1.class)
    public void testDeleteFunction() {
        performBasicScriptTest(DE_6, "deleteFunction.das", new DeleteFunctionScript());
    }

    /**
     * Capture the effect of anon on transfer syntax.
     * 1. Anon without pixel edits retains the original xfer syntax.
     * 2. Anon with pixel edits always results in EVLE data.
     * This test makes due with pre-existing data sets but flings about many images when a single image would do, thus taking much longer than necessary to run.
     */
    @Test
    @AddedIn(Xnat_1_8_4.class)
    public void testTransferSyntax() {
        performBasicScriptTest(DE_6, "deleteFunction.das", new TransferSyntaxScript(UID.ExplicitVRLittleEndian), evleData);
        performBasicScriptTest(DE_6, "deleteFunction.das", new TransferSyntaxScript(UID.ImplicitVRLittleEndian), ivleData);
        performBasicScriptTest(DE_6, "deleteFunction.das", new TransferSyntaxScript(UID.JPEGLossless), jpglosslessData);
        performBasicScriptTest(DE_6, "alterPixelsXferSyntax.das", new TransferSyntaxScript(UID.ExplicitVRLittleEndian), anonData);
        performBasicScriptTest(DE_6, "alterPixelsXferSyntax.das", new TransferSyntaxScript(UID.ExplicitVRLittleEndian), ivleData);
        performBasicScriptTest(DE_6, "alterPixelsXferSyntax.das", new TransferSyntaxScript(UID.ExplicitVRLittleEndian), jpglosslessData);
    }

    @Test
    @AddedIn(Xnat_1_8_5.class)
    public void testGroup2PreserveDE4() {
        performBasicScriptTest(DE_4, "standardDelete.das", new Group2PreserveScript());
    }

    @Test
    @AddedIn(Xnat_1_8_5.class)
    public void testGroup2PreserveDE6() {
        performBasicScriptTest(DE_6, "standardDelete.das", new Group2PreserveScript());
    }

    @Test
    @AddedIn(Xnat_1_8_5.class)
    public void testGroup2DeleteDE4() {
        performBasicScriptTest(DE_4, "group2Delete.das", new Group2DeleteScript());
    }

    @Test
    @AddedIn(Xnat_1_8_5.class)
    public void testGroup2DeleteDE6() {
        performBasicScriptTest(DE_6, "group2Delete.das", new Group2DeleteScript());
    }

    @Test  // Tests DE-58
    @AddedIn(Xnat_1_8_7.class)
    public void testStandardConditionalsWithFunctionDE6() {
        performBasicScriptTest(DE_6, "standardConditionalWithFunction.das", new StandardConditionalWithFunctionScript());
    }

    @Test  // Tests DE-45
    @AddedIn(Xnat_1_8_7.class)
    public void testRetainPrivateTagsDE6() {
        performBasicScriptTest(DE_6, "retainPrivateTags.das", new RetainPrivateTagsScript());
    }

    @Test  // Tests DE-45
    @AddedIn(Xnat_1_8_7.class)
    public void testIfElseBlocks() {
        performBasicScriptTest(DE_6, "ifElseBlocks.das", new IfElseBlockScript());
    }

    @Test //Tests DE-65
    @AddedIn(Xnat_1_8_7.class)
    public void testIfElse() {
        performBasicScriptTest(DE_6, "ifElse.das", new IfElseScript());
    }

    private void performSubjectRelabelAnonTest(AnonScript projectScript, AnonScript siteScript) {
        mainAdminInterface().setSiteAnonScript(siteScript);
        mainInterface().setProjectAnonScript(anonProject, projectScript);
        mainInterface().disableProjectAnonScript(anonProject);
        mainAdminInterface().disableSiteAnonScript();

        final ImagingSession session = importAnonSession();
        validateAnon(session, null, Arrays.asList(projectScript, siteScript));
        mainInterface().waitForAutoRun(session);

        mainInterface().enableProjectAnonScript(anonProject);

        mainInterface().relabelSubject(session.getSubject(), "NEWLABEL");
        validateAnon(session, Collections.singletonList(projectScript), Collections.singletonList(siteScript));
    }

    private void performSessionRelabelAnonTest(AnonScript projectScript, AnonScript siteScript) {
        mainAdminInterface().setSiteAnonScript(siteScript);
        mainInterface().setProjectAnonScript(anonProject, projectScript);
        mainInterface().disableProjectAnonScript(anonProject);
        mainAdminInterface().disableSiteAnonScript();

        final ImagingSession session = importAnonSession();
        validateAnon(session, null, Arrays.asList(projectScript, siteScript));
        mainInterface().waitForAutoRun(session);

        mainInterface().enableProjectAnonScript(anonProject);

        mainInterface().relabelSubjectAssessor(session, "NEWLABEL");
        validateAnon(session, Collections.singletonList(projectScript), Collections.singletonList(siteScript));
    }

    private void performBasicScriptTest(DicomEditVersion deVersion, String scriptName, ScriptValidation scriptValidation, File testData) {
        final AnonScript script = XnatObjectUtils.anonScriptFromFile(deVersion, scriptName);
        scriptValidationMap.put(script, scriptValidation);
        mainInterface().setProjectAnonScript(anonProject, script);
        mainAdminInterface().disableSiteAnonScript();

        validateAnon(importSession(testData), Collections.singletonList(script), null);
    }

    private void performBasicScriptTest(DicomEditVersion deVersion, String scriptName, ScriptValidation scriptValidation) {
        performBasicScriptTest(deVersion, scriptName, scriptValidation, anonData);
    }

    private void performXnatVariableScriptTest(DicomEditVersion deVersion) {
        final AnonScript script = XnatObjectUtils.anonScriptFromFile(deVersion, "xnatVariables.das");
        final XnatVariablesScript scriptValidation = new XnatVariablesScript();
        scriptValidationMap.put(script, scriptValidation);
        mainInterface().setProjectAnonScript(anonProject, script);
        mainAdminInterface().disableSiteAnonScript();
        final ImagingSession session = importSession(anonData);
        scriptValidation.session(session);
        validateAnon(session, Collections.singletonList(script), null);
    }

    private ImagingSession importAnonSession() {
        return importSession(anonData);
    }

    private ImagingSession importSession(File testData) {
        final Subject subject = new Subject(anonProject);
        final ImagingSession session = new ImagingSession(anonProject, subject);
        session.extension(new SessionImportExtension(session, testData));
        mainInterface().createSubject(subject);
        return session;
    }

    private void validateAnon(ImagingSession session, List<AnonScript> enabledScripts, List<AnonScript> disabledScripts) {
        final List<File> dicom = restDriver.downloadAllDicomFromSession(mainUser, anonProject, session.getSubject(), session);
        if (enabledScripts != null) {
            for (AnonScript script : enabledScripts) {
                scriptValidationMap.get(script).validateScriptRan(dicom);
            }
        }
        if (disabledScripts != null) {
            for (AnonScript script : disabledScripts) {
                scriptValidationMap.get(script).validateScriptDidntRun(dicom);
            }
        }
    }

}
