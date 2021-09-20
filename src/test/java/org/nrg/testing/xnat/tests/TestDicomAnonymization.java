package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.*;
import org.nrg.testing.dicom.ScriptValidation;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.enums.DicomEditVersion;
import org.nrg.xnat.versions.Xnat_1_7_7;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_8_1;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

import static org.nrg.xnat.enums.DicomEditVersion.*;

@TestRequires(admin = true, data = {TestData.ANON_2, TestData.ANON_DUPLICATE_PRIVATE_TAG})
public class TestDicomAnonymization extends BaseXnatRestTest {

    private final Project anonProject = new Project();
    private final File anonData = getDataFile(TestData.ANON_2.getZipName());
    private final AnonScript projectAnonDE4 = XnatObjectUtils.anonScriptFromFile(DE_4, "projectAnon.das");
    private final AnonScript projectAnonDE6 = XnatObjectUtils.anonScriptFromFile(DE_6, "projectAnon.das");
    private final AnonScript siteAnonDE4 = XnatObjectUtils.anonScriptFromFile(DE_4, "siteAnon.das");
    private final AnonScript siteAnonDE6 = XnatObjectUtils.anonScriptFromFile(DE_6, "siteAnon.das");
    private final Map<AnonScript, ScriptValidation> scriptValidationMap = new HashMap<>();
    boolean projectCreated = false;

    @BeforeClass
    public void createProject() {
        mainInterface().createProject(anonProject);
        projectCreated = true;
        mainInterface().regenerateUserSession(); // hack for XNAT-5187
        scriptValidationMap.put(projectAnonDE4, new ProjectScript());
        scriptValidationMap.put(projectAnonDE6, new ProjectScript());
        scriptValidationMap.put(siteAnonDE4, new SiteScript());
        scriptValidationMap.put(siteAnonDE6, new SiteScript());
    }

    @AfterMethod(alwaysRun = true)
    public void clean() {
        restDriver.clearPrearchiveSessions(mainUser, anonProject);
        if (projectCreated) {
            mainInterface().deleteAllProjectData(anonProject);
        }
    }

    @AfterClass(alwaysRun = true)
    public void resetAnon() {
        mainAdminInterface().enableSiteAnonScript();
        mainAdminInterface().setSiteAnonScript(restDriver.getDefaultXnatAnonScript());
    }

    @Test
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

    @Test
    public void testSubjectRelabel_6P_6S() {
        performSubjectRelabelAnonTest(projectAnonDE6, siteAnonDE6);
    }

    @Test
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

    @Test
    public void testSessionRelabel_6P_6S() {
        performSessionRelabelAnonTest(projectAnonDE6, siteAnonDE6);
    }

    @Test
    public void testStandardElementRemovalDE4() {
        performBasicScriptTest(DE_4, "standardDelete.das", new StandardDeleteScript());
    }

    @Test
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
    @ExpectedFailure(jiraIssue = "DE-46")
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
    @AddedIn(Xnat_1_8_0.class)
    @ExpectedFailure(jiraIssue = "DE-49") // (and DE-48)
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
    @ExpectedFailure(jiraIssue = "DE-42")
    public void testShiftDateTimeByIncrementWithTimezone() {
        performBasicScriptTest(DE_6, "shiftDateTimeWithTimezone.das", new ShiftDateTimeWithTimezoneScript());
    }

    @Test
    @AddedIn(Xnat_1_8_1.class)
    public void testShiftDateTimeSequenceByIncrement() {
        performBasicScriptTest(DE_6, "shiftDateTimeSequence.das", new ShiftDateTimeSequenceScript());
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

    private List<File> downloadResourceFiles(ImagingSession session) {
        final List<File> dicomFiles = new ArrayList<>();
        final List<Scan> scans = mainInterface().readScans(anonProject, session.getSubject(), session);
        for (Scan scan : scans) {
            final Resource dicom = mainInterface().findResource(scan.getScanResources(), "DICOM");
            for (ResourceFile file : dicom.getResourceFiles()) {
                dicomFiles.add(restDriver.saveBinaryResponseToFile(restDriver.interfaceFor(mainUser).queryBase().get(mainInterface().resourceFileUrl(dicom, file))));
            }
        }
        return dicomFiles;
    }

    private void validateAnon(ImagingSession session, List<AnonScript> enabledScripts, List<AnonScript> disabledScripts) {
        final List<File> dicom = downloadResourceFiles(session);
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
