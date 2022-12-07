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
import java.util.Optional;

import static org.nrg.testing.TestGroups.ANONYMIZATION;

/**
 * Verify that anon-script variables are used properly when anon is triggered by assigning a Session to a different Subject or Project.
 */
@TestRequires(admin = true, data = {
        TestData.ANON_2
})
@Test(groups = ANONYMIZATION)
public class TestDicomAnonymizationAssignSession extends BaseDicomAnonSessionLabelTest {
    private static final String ORIG_SUBJECT_LABEL = "Watermelon";
    private static final String NEW_SUBJECT_LABEL = "newSubjectLabel";

    private final TestComponent UPLOAD = new SessionImporterStep(TestData.ANON_2);

    @Test
    public void testAssignToSubject() {
        run (new AsssignToSubjectTest());
    }

    @Test
    public void testAssignToProject() {
        run( new AsssignToProjectTest());
    }

    private class AsssignToSubjectTest extends ComponentizedTest {
        public void run(BaseXnatRestTest xnatRestTest) {

            // create the project
            final Project project = createTestProject(xnatRestTest);
            DISABLE_SITE_ANON_SCRIPT.perform(xnatRestTest, project);
            // Import without any variable assignment
            UPLOAD.perform(xnatRestTest, project);
            // get the original subject and session
            Subject subject = getSubject(project, ORIG_SUBJECT_LABEL).get();
            ImagingSession session = subject.getSessions().get(0);
            session.setSubject(subject);
            session.setPrimaryProject(project);
            // Add a new subject to the project
            new AddSubjectStep(NEW_SUBJECT_LABEL).perform(xnatRestTest, project);

            // Add the project anon script before the move
            SET_PROJECT_ANON_SCRIPT.perform(xnatRestTest, project);
            // Move the session from the original to new subject.
            new AssignSessionToSubjectStep(ORIG_SUBJECT_LABEL, NEW_SUBJECT_LABEL).perform(xnatRestTest, project);
            // Validate anon runs and uses 'subject' variable with new value.
            new ValidateStep(NEW_SUBJECT_LABEL).perform(xnatRestTest, project);
        }

        @Override
        public Project createTestProject(BaseXnatRestTest xnatRestTest) {
            return createProject();
        }
    }

    private class AsssignToProjectTest extends ComponentizedTest {
        public void run(BaseXnatRestTest xnatRestTest) {
            // create the original project
            final Project origProject = createTestProject(xnatRestTest);
            DISABLE_SITE_ANON_SCRIPT.perform(xnatRestTest, origProject);
            // upload data to original project without variable assignment
            UPLOAD.perform(xnatRestTest, origProject);
            // create the destination project
            final Project destinationProject = createTestProject(xnatRestTest);
            // set the anon script on the destination project
            SET_PROJECT_ANON_SCRIPT.perform(xnatRestTest, destinationProject);
            // get the original subject and session
            Subject origSubject = getSubject(origProject, ORIG_SUBJECT_LABEL).get();
            ImagingSession session = origSubject.getSessions().get(0);
            session.setSubject(origSubject);
            session.setPrimaryProject(origProject);

            // add the session to destination project
            // This will share the original subject into the destination project with its original subject label.
            // The session will be removed from the original project and will be owned by the destination project.
            mainInterface().moveSubjectAssessorToOtherProject(session, destinationProject);
            // Validate that anon runs and uses 'project' variable with new value.
            new ValidateStep(ORIG_SUBJECT_LABEL).perform(xnatRestTest, destinationProject);
        }
        @Override
        public Project createTestProject(BaseXnatRestTest xnatRestTest) {
            final Project project = registerTempProject().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
            mainInterface().createProject(project);
            return project;
        }
    }

    private class AddSubjectStep implements TestComponent {
        private final String subjectLabel;

        public AddSubjectStep(String subjectLabel) {
            this.subjectLabel = subjectLabel;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            Subject subject = new Subject().label(subjectLabel);
            mainInterface().createSubject(project, subject);
        }

    }

    private class AssignSessionToSubjectStep implements TestComponent {
        private final String subjectLabel, destinationLabel;

        public AssignSessionToSubjectStep(String subjectLabel, String destinationLabel) {
            this.subjectLabel = subjectLabel;
            this.destinationLabel = destinationLabel;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            Subject srcSubject = getSubject(project, subjectLabel).get();
            ImagingSession session = srcSubject.getSessions().get(0);
            session.setSubject(srcSubject);
            session.setPrimaryProject(project);
            Subject destinationSubject = getSubject(project, destinationLabel).get();
            mainInterface().moveSubjectAssessorToOtherSubject(session, destinationSubject);
        }
    }

    private Optional<Subject> getSubject(Project project, String subjectLabel) {
        return mainInterface().readPrimaryAndSecondarySubjects(project).stream()
                .filter(subject -> subject.getLabel().equals(subjectLabel))
                .findAny();
    }

    private class SessionImporterStep implements TestComponent {
        private final TestData data;

        SessionImporterStep(TestData data) {
            this.data = data;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainInterface().callImporter(new SessionImporterRequest().destArchive(project).file(data.toFile()));
        }
    }


    private class ValidateStep implements TestComponent {
        final String subjectLabel;

        public ValidateStep(String subjectLabel) {
            this.subjectLabel = subjectLabel;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            Subject subject = getSubject(project, subjectLabel).get();
            ImagingSession session = subject.getSessions().get(0);
            final List<File> dicom = restDriver.downloadAllDicomFromSession(mainUser, project, subject, session);
            new ScriptVariablesValidation(project.getId(), subjectLabel, session.getLabel()).validateScriptRan(dicom);
        }
    }

}
