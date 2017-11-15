package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.*;
import org.nrg.testing.dicom.ScriptValidation;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.file.FileIO;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

import static org.nrg.xnat.enums.DicomEditVersion.*;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestDicomAnonymization extends BaseXnatRestTest {

    private final Project anonProject = new Project();
    private final File anonData = FileIO.getDataFile(TestData.ANON_2.getZipName());
    private final AnonScript projectAnonDE4 = XnatObjectUtils.anonScriptFromFile(DE_4, "projectAnon.das");
    private final AnonScript projectAnonDE6 = XnatObjectUtils.anonScriptFromFile(DE_6, "projectAnon.das");
    private final AnonScript siteAnonDE4 = XnatObjectUtils.anonScriptFromFile(DE_4, "siteAnon.das");
    private final AnonScript siteAnonDE6 = XnatObjectUtils.anonScriptFromFile(DE_6, "siteAnon.das");
    private final Map<AnonScript, ScriptValidation> scriptValidationMap = new HashMap<>();

    @BeforeClass
    public void createProject() {
        restDriver.createProject(mainUser, anonProject);
        restDriver.interfaceFor(mainUser).invalidateCachedUserSession(); // hack for XNAT-5187
        scriptValidationMap.put(projectAnonDE4, new ProjectDE4Script());
        scriptValidationMap.put(projectAnonDE6, new ProjectDE6Script());
        scriptValidationMap.put(siteAnonDE4, new SiteDE4Script());
        scriptValidationMap.put(siteAnonDE6, new SiteDE6Script());
    }

    @AfterMethod(alwaysRun = true)
    public void clean() {
        restDriver.clearPrearchiveSessions(mainUser, anonProject);
        restDriver.clearProject(mainUser, anonProject);
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

    private void performSubjectRelabelAnonTest(AnonScript projectScript, AnonScript siteScript) {
        restDriver.setSiteAnonScript(mainAdminUser, siteScript);
        restDriver.setProjectAnonScript(mainUser, anonProject, projectScript);
        restDriver.disableProjectAnonScript(mainUser, anonProject);
        restDriver.disableSiteAnonScript(mainAdminUser);

        final ImagingSession session = importAnonSession();
        validateAnon(session, null, Arrays.asList(projectScript, siteScript));
        restDriver.waitForAutoRun(session);

        restDriver.enableProjectAnonScript(mainUser, anonProject);

        restDriver.relabelSubject(mainUser, session.getSubject(), "NEWLABEL");
        validateAnon(session, Collections.singletonList(projectScript), Collections.singletonList(siteScript));
    }

    private void performSessionRelabelAnonTest(AnonScript projectScript, AnonScript siteScript) {
        restDriver.setSiteAnonScript(mainAdminUser, siteScript);
        restDriver.setProjectAnonScript(mainUser, anonProject, projectScript);
        restDriver.disableProjectAnonScript(mainUser, anonProject);
        restDriver.disableSiteAnonScript(mainAdminUser);

        final ImagingSession session = importAnonSession();
        validateAnon(session, null, Arrays.asList(projectScript, siteScript));
        restDriver.waitForAutoRun(session);

        restDriver.enableProjectAnonScript(mainUser, anonProject);

        restDriver.relabelSubjectAssessor(mainUser, session, "NEWLABEL");
        validateAnon(session, Collections.singletonList(projectScript), Collections.singletonList(siteScript));
    }

    private ImagingSession importAnonSession() {
        final Subject subject = new Subject(anonProject);
        final ImagingSession session = new MRSession(anonProject, subject);
        session.extension(new SessionImportExtension(restDriver.interfaceFor(mainUser), session, anonData));
        restDriver.createSubject(mainUser, subject);
        return session;
    }

    private List<File> downloadResourceFiles(ImagingSession session) {
        final List<File> dicomFiles = new ArrayList<>();
        final List<Scan> scans = restDriver.readScans(mainUser, anonProject, session.getSubject(), session);
        for (Scan scan : scans) {
            final Resource dicom = restDriver.findResource(scan.getScanResources(), "DICOM");
            for (ResourceFile file : dicom.getResourceFiles()) {
                dicomFiles.add(restDriver.saveBinaryResponseToFile(restDriver.interfaceFor(mainUser).queryBase().get(restDriver.resourceFileUrl(dicom, file))));
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
