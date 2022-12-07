package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.components.ComponentizedTest;
import org.nrg.testing.xnat.components.TestComponent;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.importer.importers.SessionImporterRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.prearchive.PrearchiveQuery;
import org.nrg.xnat.prearchive.PrearchiveQueryScope;
import org.nrg.xnat.prearchive.SessionData;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

import static org.nrg.testing.TestGroups.ANONYMIZATION;

@TestRequires(admin = true, data = {
        TestData.ANON_2,
})
@Test(groups = ANONYMIZATION)
public class TestDicomAnonymizationScriptVariables extends BaseDicomAnonSessionLabelTest {

    private final TestComponent REBUILD = new RebuildOnlySessionInPrearc();
    private final TestComponent ARCHIVE = new ArchiveSession();
    private final TestComponent VALIDATE = new ValidateStep();

    @Test
    public void testXnatVariablesCStore() {
        run( new ScriptVariablesTest(new CstoreStep(TestData.ANON_2)));
    }

    @Test
    public void testXnatVariablesSessionImporter() {
        run( new ScriptVariablesTest(new SessionImporterStep(TestData.ANON_2)));
    }

    @Test
    public void testXnatVariablesDicomZip() {
        run( new ScriptVariablesTest(new DicomZipStep(TestData.ANON_2, false)));
    }

    protected class ScriptVariablesTest  extends ComponentizedTest {
        TestComponent uploadStep;
        public ScriptVariablesTest( TestComponent uploadStep) {
            this.uploadStep = uploadStep;
        }
        @Override
        public void run(BaseXnatRestTest xnatRestTest) {
            Project project = createTestProject(xnatRestTest);
            DISABLE_SITE_ANON_SCRIPT.perform(xnatRestTest, project);
            uploadStep.perform(xnatRestTest, project);
            SET_PROJECT_ANON_SCRIPT.perform(xnatRestTest, project);
            REBUILD.perform(xnatRestTest, project);
            ARCHIVE.perform(xnatRestTest, project);
            VALIDATE.perform(xnatRestTest, project);
        }
        @Override
        public Project createTestProject(BaseXnatRestTest xnatRestTest) {
            final Project project = registerTempProject().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
            mainInterface().createProject(project);
            return project;
        }
    }

    private class CstoreStep implements TestComponent {
        private final File data;

        CstoreStep(TestData data) {
            this.data = data.toDirectory();
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            new XnatCStore().data(data).sendDICOMToProject(project);
        }
    }

    private class SessionImporterStep implements TestComponent {
        private final TestData data;

        SessionImporterStep(TestData data) {
            this.data = data;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            mainInterface().callImporter(new SessionImporterRequest().destPrearchive(project).file(data.toFile()));
        }
    }

    private class DicomZipStep implements TestComponent {
        private final TestData data;
        private final boolean rename;

        DicomZipStep(TestData data, boolean rename) {
            this.data = data;
            this.rename = rename;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            mainInterface().callImporter(
                    new DicomZipRequest()
                            .file(data.toFile())
                            .project(project)
                            .rename(rename)
            );
        }
    }

    private class RebuildOnlySessionInPrearc implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            final SessionData prearcSession = expectSinglePrearchiveResultForProject(project);
            mainInterface().rebuildSession(prearcSession, false);
        }
    }

    private SessionData expectSinglePrearchiveResultForProject(Project project) {
        return mainInterface().queryPrearchiveForSingularResult(new PrearchiveQuery().scope(PrearchiveQueryScope.forProject(project)));
    }

    private class ArchiveSession implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            mainInterface().archiveSession(expectSinglePrearchiveResultForProject(project));
        }
    }

    private class ValidateStep implements TestComponent {

        @Override
        public void perform(BaseXnatRestTest xnatRestTest,Project project) {
            // I don't know why session.getSubject() would return null here.
            // also session.getPrimaryProject() returns null.
//            ImagingSession session = mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0);
//            final List<File> dicom = restDriver.downloadAllDicomFromSession(mainUser, project, session.getSubject(), session);
//            new ScriptVariablesValidation( session).validateScriptRan( dicom);
            // Work around
            Subject subject = mainInterface().readProject(project.getId()).getSubjects().get(0);
            ImagingSession session = subject.getSessions().get(0);
            final List<File> dicom = restDriver.downloadAllDicomFromSession(mainUser, project, subject, session);
            new ScriptVariablesValidation(project.getId(), subject.getLabel(), session.getLabel()).validateScriptRan(dicom);
        }
    }

}
