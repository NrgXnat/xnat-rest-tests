package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

public class TestContextlessExperiments extends BaseXnatRestTest {

    private Project testProject;

    @BeforeMethod
    public void assignProjectVar() {
        testProject = new Project();
    }

    @AfterMethod
    public void cleanupProject() {
        restDriver.deleteProjectSilently(mainUser, testProject);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-6687")
    public void testContextlessExptDelete() {
        final Subject subject = new Subject(testProject);
        final ImagingSession session = new MRSession(testProject, subject);
        final SessionAssessor qc = new QC(testProject, subject, session);
        mainInterface().createProject(testProject);
        mainQueryBase().delete(formatRestUrl("experiments", qc.getAccessionNumber())).then().assertThat().statusCode(200);
        assertEquals(0, mainInterface().readSessionAssessors(testProject, subject, session).size());
        mainQueryBase().delete(formatRestUrl("experiments", session.getAccessionNumber())).then().assertThat().statusCode(200);
        assertEquals(0, mainInterface().readSubjectAssessors(testProject, subject).size());
    }

}
