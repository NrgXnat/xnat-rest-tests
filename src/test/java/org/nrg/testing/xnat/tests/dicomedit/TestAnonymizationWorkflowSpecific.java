package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.ProjectScript;
import org.nrg.testing.dicom.ScriptValidation;
import org.nrg.testing.dicom.SiteScript;
import org.nrg.testing.dicom.XnatVariablesScript;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.nrg.testing.TestGroups.ANONYMIZATION;
import static org.nrg.testing.TestGroups.SMOKE;
import static org.nrg.xnat.enums.DicomEditVersion.DE_4;
import static org.nrg.xnat.enums.DicomEditVersion.DE_6;

@TestRequires(admin = true, data = TestData.ANON_2)
public class TestAnonymizationWorkflowSpecific extends BaseAnonymizationTest {

    private static final ScriptValidation COMMON_PROJECT_VALIDATION = new ProjectScript();
    private static final ScriptValidation COMMON_SITE_VALIDATION = new SiteScript();
    private final AnonScript projectAnonDE4 = XnatObjectUtils.anonScriptFromFile(DE_4, "projectAnon.das");
    private final AnonScript projectAnonDE6 = XnatObjectUtils.anonScriptFromFile(DE_6, "projectAnon.das");
    private final AnonScript siteAnonDE4 = XnatObjectUtils.anonScriptFromFile(DE_4, "siteAnon.das");
    private final AnonScript siteAnonDE6 = XnatObjectUtils.anonScriptFromFile(DE_6, "siteAnon.das");

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testSubjectRelabel_4P_4S() {
        performSubjectRelabelAnonTest(projectAnonDE4, siteAnonDE4);
    }

    public void testSubjectRelabel_4P_6S() {
        performSubjectRelabelAnonTest(projectAnonDE4, siteAnonDE6);
    }

    public void testSubjectRelabel_6P_4S() {
        performSubjectRelabelAnonTest(projectAnonDE6, siteAnonDE4);
    }

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testSubjectRelabel_6P_6S() {
        performSubjectRelabelAnonTest(projectAnonDE6, siteAnonDE6);
    }

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testSessionRelabel_4P_4S() {
        performSessionRelabelAnonTest(projectAnonDE4, siteAnonDE4);
    }

    public void testSessionRelabel_4P_6S() {
        performSessionRelabelAnonTest(projectAnonDE4, siteAnonDE6);
    }

    public void testSessionRelabel_6P_4S() {
        performSessionRelabelAnonTest(projectAnonDE6, siteAnonDE4);
    }

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testSessionRelabel_6P_6S() {
        performSessionRelabelAnonTest(projectAnonDE6, siteAnonDE6);
    }

    public void testXnatVariablesDE4() {
        new VariableScriptTest().withDicomEditVersion(DE_4).run();
    }

    public void testXnatVariablesDE6() {
        new VariableScriptTest().run();
    }

    private void performSubjectRelabelAnonTest(AnonScript projectScript, AnonScript siteScript) {
        final ImagingSession session = new GenericAnonymizationTest<>()
                .withSetup(setupUpDisabledScripts(projectScript, siteScript))
                .withDisabledScripts(Arrays.asList(COMMON_PROJECT_VALIDATION, COMMON_SITE_VALIDATION))
                .run();

        new GenericAnonymizationTest<>()
                .withSetup(() -> {
                    mainInterface().waitForAutoRun(session);
                    mainInterface().enableProjectAnonScript(anonProject);
                    mainInterface().relabelSubject(session.getSubject(), "NEWLABEL");
                }).withUpload(() -> session)
                .withEnabledScripts(Collections.singletonList(COMMON_PROJECT_VALIDATION))
                .withDisabledScripts(Collections.singletonList(COMMON_SITE_VALIDATION))
                .run();
    }

    private void performSessionRelabelAnonTest(AnonScript projectScript, AnonScript siteScript) {
        mainAdminInterface().setSiteAnonScript(siteScript);
        mainInterface().setProjectAnonScript(anonProject, projectScript);
        mainInterface().disableProjectAnonScript(anonProject);
        mainAdminInterface().disableSiteAnonScript();

        final ImagingSession session = new GenericAnonymizationTest<>()
                .withSetup(setupUpDisabledScripts(projectScript, siteScript))
                .withDisabledScripts(Arrays.asList(COMMON_PROJECT_VALIDATION, COMMON_SITE_VALIDATION))
                .run();

        new GenericAnonymizationTest<>()
                .withSetup(() -> {
                    mainInterface().waitForAutoRun(session);
                    mainInterface().enableProjectAnonScript(anonProject);
                    mainInterface().relabelSubjectAssessor(session, "NEWLABEL");
                }).withUpload(() -> session)
                .withEnabledScripts(Collections.singletonList(COMMON_PROJECT_VALIDATION))
                .withDisabledScripts(Collections.singletonList(COMMON_SITE_VALIDATION))
                .run();
    }

    private Runnable setupUpDisabledScripts(AnonScript projectScript, AnonScript siteScript) {
        return () -> {
            mainAdminInterface().setSiteAnonScript(siteScript);
            mainInterface().setProjectAnonScript(anonProject, projectScript);
            mainInterface().disableProjectAnonScript(anonProject);
            mainAdminInterface().disableSiteAnonScript();
        };
    }

    private class VariableScriptTest extends BasicAnonymizationTest {
        VariableScriptTest() {
            super("xnatVariables.das");
            withValidation(new XnatVariablesScript());
            withUpload(() -> {
                final ImagingSession session = defaultUploadStep().get();
                ((XnatVariablesScript) scriptContainer.get(0)).session(session);
                return session;
            });
        }
    }

}
