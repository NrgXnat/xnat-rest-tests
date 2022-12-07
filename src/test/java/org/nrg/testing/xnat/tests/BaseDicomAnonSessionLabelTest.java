package org.nrg.testing.xnat.tests;

import org.nrg.testing.dicom.RootDicomObject;
import org.nrg.testing.dicom.SimplestDicomScriptValidation;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.components.TestComponent;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;

public class BaseDicomAnonSessionLabelTest extends BaseXnatRestTest {

    public Project createProject() {
        final Project project = registerTempProject().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
        mainInterface().createProject(project);
        return project;
    }

    public final TestComponent DISABLE_SITE_ANON_SCRIPT = new DisableSiteAnonStep();
    public class DisableSiteAnonStep implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainAdminInterface().disableSiteAnonScript();
        }
    }

    public final TestComponent SET_PROJECT_ANON_SCRIPT = new SetProjectAnonScriptsStep();

    public class SetProjectAnonScriptsStep implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainInterface().setProjectAnonScript(project, getAnonScript());
        }
    }

    protected static AnonScript getAnonScript() {
        return new AnonScript()
                .addStatement("version \"6.1\"")
                .addStatement("(0008,1010) := project")
                .addStatement("(0008,1030) := subject")
                .addStatement("(0008,103e) := session");
    }

    protected class ScriptVariablesValidation extends SimplestDicomScriptValidation {

        private final String project;
        private final String subject;
        private final String session;

        public ScriptVariablesValidation(String projectId, String subjectLabel, String sessionLabel) {
            this.project = projectId;
            this.subject = subjectLabel;
            this.session = sessionLabel;
        }

        @Override
        protected RootDicomObject generateValidationObject() {
            final RootDicomObject root = new RootDicomObject();

            root.putValueEqualCheck("(0008,1010)", project);
            root.putValueEqualCheck("(0008,1030)", subject);
            root.putValueEqualCheck("(0008,103e)", session);

            return root;
        }
    }


}
