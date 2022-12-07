package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.components.ComponentizedTest;
import org.nrg.testing.xnat.components.TestComponent;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.importers.SessionImporterRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

import static org.nrg.testing.TestGroups.ANONYMIZATION;

@TestRequires(admin = true, data = {
        TestData.ANON_2
})
@Test(groups = ANONYMIZATION)
public class TestDicomAnonymizationChangeLabel extends BaseDicomAnonSessionLabelTest {

    private static final String NEW_SUBJECT_LABEL = "newSubjectLabel";
    private static final String NEW_SESSION_LABEL = "newSessionLabel";
    private static final int WAIT_FOR_AUTORUN_IN_SECONDS = 60;
    private final TestComponent UPLOAD = new SessionImporterStep(TestData.ANON_2);

    @Test
    public void testChangeSubjectLabel() {
        run( new ChangeLabelTest(new ChangeSubjectLabelStep(), new ValidateSubjectLabelStep(NEW_SUBJECT_LABEL)));
    }

    @Test
    public void testChangeSessionLabel() {
        run( new ChangeLabelTest(new ChangeSessionLabelStep(), new ValidateSessionLabelStep(NEW_SESSION_LABEL)));
    }

    private class ChangeLabelTest extends ComponentizedTest {
        TestComponent changeLabelStep;
        TestComponent validateStep;

        public ChangeLabelTest(TestComponent changeLabelStep, TestComponent validateStep) {
            this.changeLabelStep = changeLabelStep;
            this.validateStep = validateStep;
        }
        @Override
        public void run(BaseXnatRestTest xnatRestTest) {
            Project project = createTestProject(xnatRestTest);
            DISABLE_SITE_ANON_SCRIPT.perform(xnatRestTest, project);
            UPLOAD.perform(xnatRestTest, project);
            SET_PROJECT_ANON_SCRIPT.perform(xnatRestTest, project);
            changeLabelStep.perform(xnatRestTest, project);
            validateStep.perform(xnatRestTest, project);
        }
        @Override
        public Project createTestProject(BaseXnatRestTest xnatRestTest) {
            final Project project = registerTempProject().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
            mainInterface().createProject(project);
            return project;
        }
    }

    private class ChangeSubjectLabelStep implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            Subject subject = mainInterface().readProject(project.getId()).getSubjects().get(0);
            mainInterface().relabelSubject(project, subject, NEW_SUBJECT_LABEL);
        }
    }

    private class ChangeSessionLabelStep implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            Subject subject = mainInterface().readProject(project.getId()).getSubjects().get(0);
            mainInterface().relabelSubjectAssessor(project, subject, subject.getSessions().get(0), NEW_SESSION_LABEL);
        }
    }

    private class SessionImporterStep implements TestComponent {
        private final TestData data;

        SessionImporterStep(TestData data) {
            this.data = data;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            mainInterface().callImporter(new SessionImporterRequest().destArchive(project).file(data.toFile()));
            Subject subject = mainInterface().readSubjects(project).get(0);
            ImagingSession session = subject.getSessions().get(0);
            mainInterface().waitForAutoRun(session, WAIT_FOR_AUTORUN_IN_SECONDS);
        }
    }

    private class ValidateSubjectLabelStep implements TestComponent {
        final String subjectLabel;

        public ValidateSubjectLabelStep(String subjectLabel) {
            this.subjectLabel = subjectLabel;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            Subject subject = mainInterface().readSubjects(project).get(0);
            ImagingSession session = subject.getSessions().get(0);
            final List<File> dicom = restDriver.downloadAllDicomFromSession(mainUser, project, subject, session);
            new ScriptVariablesValidation(project.getId(), subjectLabel, session.getLabel()).validateScriptRan(dicom);
        }
    }

    private class ValidateSessionLabelStep implements TestComponent {
        final String sessionLabel;

        public ValidateSessionLabelStep(String sessionLabel) {
            this.sessionLabel = sessionLabel;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            Subject subject = mainInterface().readSubjects(project).get(0);
            ImagingSession session = subject.getSessions().get(0);
            final List<File> dicom = restDriver.downloadAllDicomFromSession(mainUser, project, subject, session);
            new ScriptVariablesValidation(project.getId(), subject.getLabel(), sessionLabel).validateScriptRan(dicom);
        }
    }

}
