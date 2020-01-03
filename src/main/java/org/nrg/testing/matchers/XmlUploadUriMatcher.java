package org.nrg.testing.matchers;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;

public class XmlUploadUriMatcher extends BaseMatcher<String> {

    private Project project;
    private Subject subject;
    private SubjectAssessor subjectAssessor;
    private Scan scan;
    private SessionAssessor sessionAssessor;
    private Level level;

    public XmlUploadUriMatcher(Project project) {
        this.project = project;
        level = Level.PROJECT;
    }

    public XmlUploadUriMatcher(Subject subject) {
        project = subject.getProject();
        this.subject = subject;
        level = Level.SUBJECT;
    }

    @Override
    public boolean matches(Object o) {
        return getUri().equals(o);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(getUri());
    }

    private String getUri() {
        switch (level) {
            case PROJECT :
                return "/archive/projects/" + project.getId();
            case SUBJECT :
                return "/archive/subjects/" + subject.getAccessionNumber();
            default:
                return "";
        }
    }

    private enum Level { PROJECT, SUBJECT, SUBJECT_ASSESSOR, SCAN, SESSION_ASSESSOR }
}
