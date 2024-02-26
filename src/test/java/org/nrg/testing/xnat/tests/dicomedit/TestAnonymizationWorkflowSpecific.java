package org.nrg.testing.xnat.tests.dicomedit;

import org.nrg.testing.CommonStringUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.ProjectScript;
import org.nrg.testing.dicom.ScriptValidation;
import org.nrg.testing.dicom.SiteScript;
import org.nrg.testing.dicom.XnatVariablesScript;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Share;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.versions.Xnat_1_8_10;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        new SubjectRelabelAnonTest(projectAnonDE4, siteAnonDE4).run();
    }

    public void testSubjectRelabel_4P_6S() {
        new SubjectRelabelAnonTest(projectAnonDE4, siteAnonDE6).run();
    }

    public void testSubjectRelabel_6P_4S() {
        new SubjectRelabelAnonTest(projectAnonDE6, siteAnonDE4).run();
    }

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testSubjectRelabel_6P_6S() {
        new SubjectRelabelAnonTest(projectAnonDE6, siteAnonDE6).run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testSubjectRelabelAnonOff() {
        new SubjectRelabelAnonTest(projectAnonDE6, siteAnonDE6, false).run();
    }

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testSessionRelabel_4P_4S() {
        new SessionRelabelAnonTest(projectAnonDE4, siteAnonDE4).run();
    }

    public void testSessionRelabel_4P_6S() {
        new SessionRelabelAnonTest(projectAnonDE4, siteAnonDE6).run();
    }

    public void testSessionRelabel_6P_4S() {
        new SessionRelabelAnonTest(projectAnonDE6, siteAnonDE4).run();
    }

    @Test(groups = {SMOKE, ANONYMIZATION})
    @Basic
    public void testSessionRelabel_6P_6S() {
        new SessionRelabelAnonTest(projectAnonDE6, siteAnonDE6).run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testSessionRelabelAnonOff() {
        new SessionRelabelAnonTest(projectAnonDE6, siteAnonDE6, false).run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testSessionReassignAnonOff() {
        new SessionReassignmentAnonTest(projectAnonDE6, siteAnonDE6, false).run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testSessionMoveProjectAnonOff() {
        new SessionMoveProjectAnonTest(projectAnonDE6, siteAnonDE6, false).run();
    }

    public void testXnatVariablesDE4() {
        new VariableScriptTest().withDicomEditVersion(DE_4).run();
    }

    public void testXnatVariablesDE6() {
        new VariableScriptTest().run();
    }

    private class SubjectRelabelAnonTest extends PostArchiveAnonTest {
        private SubjectRelabelAnonTest(AnonScript projectScript, AnonScript siteScript, boolean enablePostArchiveAnon) {
            super(projectScript, siteScript, enablePostArchiveAnon);
        }

        private SubjectRelabelAnonTest(AnonScript projectScript, AnonScript siteScript) {
            this(projectScript, siteScript, true);
        }

        @Override
        protected void postArchiveAction(ImagingSession session) {
            mainInterface().relabelSubject(session.getSubject(), "NEWLABEL");
        }
    }

    private class SessionRelabelAnonTest extends PostArchiveAnonTest {
        private SessionRelabelAnonTest(AnonScript projectScript, AnonScript siteScript, boolean enablePostArchiveAnon) {
            super(projectScript, siteScript, enablePostArchiveAnon);
        }

        private SessionRelabelAnonTest(AnonScript projectScript, AnonScript siteScript) {
            this(projectScript, siteScript, true);
        }

        @Override
        protected void postArchiveAction(ImagingSession session) {
            mainInterface().relabelSubjectAssessor(session, "NEWLABEL");
        }
    }

    private class SessionReassignmentAnonTest extends PostArchiveAnonTest {
        private SessionReassignmentAnonTest(AnonScript projectScript, AnonScript siteScript, boolean enablePostArchiveAnon) {
            super(projectScript, siteScript, enablePostArchiveAnon);
        }

        @Override
        protected void postArchiveAction(ImagingSession session) {
            final Subject destinationSubject = new Subject(anonProject);
            mainInterface().createSubject(destinationSubject);
            mainInterface().moveSubjectAssessorToOtherSubject(session, destinationSubject);
        }
    }

    private class SessionMoveProjectAnonTest extends PostArchiveAnonTest {
        private AnonScript projectScript;

        private SessionMoveProjectAnonTest(AnonScript projectScript, AnonScript siteScript, boolean enablePostArchiveAnon) {
            super(projectScript, siteScript, enablePostArchiveAnon);
            this.projectScript = projectScript;
        }

        @Override
        protected void postArchiveAction(ImagingSession session) {
            final Project otherProject = registerTempProject();
            mainInterface().createProject(otherProject);
            mainInterface().setProjectAnonScript(otherProject, projectScript);
            mainQueryBase()
                    .queryParam("primary", true)
                    .put(CommonStringUtils.formatUrl(mainInterface().subjectAssessorUrl(session), "projects", otherProject.getId()))
                    .then()
                    .assertThat()
                    .statusCode(200);
            mainInterface().shareSubject(session.getPrimaryProject(), session.getSubject(), new Share(otherProject));
            session.setPrimaryProject(otherProject);
            anonProject = otherProject;
        }
    }

    private abstract class PostArchiveAnonTest extends GenericAnonymizationTest<PostArchiveAnonTest> {

        private ImagingSession session;

        private PostArchiveAnonTest(AnonScript projectScript, AnonScript siteScript, boolean enablePostArchiveAnon) {
            withSetup(() -> {
                session = new GenericAnonymizationTest<>()
                        .withSetup(() -> {
                            mainAdminInterface().setSiteAnonScript(siteScript);
                            mainInterface().setProjectAnonScript(anonProject, projectScript);
                            mainInterface().disableProjectAnonScript(anonProject);
                            mainAdminInterface().disableSiteAnonScript();
                        })
                        .withDisabledScripts(Arrays.asList(COMMON_PROJECT_VALIDATION, COMMON_SITE_VALIDATION))
                        .run();
                mainAdminInterface().setPostArchiveAnonStatus(enablePostArchiveAnon);
                mainInterface().waitForAutoRun(session);
                mainInterface().enableProjectAnonScript(anonProject);
                postArchiveAction(session);
            });

            final List<ScriptValidation> enabledScripts = new ArrayList<>();
            final List<ScriptValidation> disabledScripts = new ArrayList<>();
            (enablePostArchiveAnon ? enabledScripts : disabledScripts).add(COMMON_PROJECT_VALIDATION);
            disabledScripts.add(COMMON_SITE_VALIDATION);

            withEnabledScripts(enabledScripts);
            withDisabledScripts(disabledScripts);

            withUpload(() -> session);
        }

        protected abstract void postArchiveAction(ImagingSession session);

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
