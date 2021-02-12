package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.CommonStringUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.enums.Handedness;
import org.nrg.xnat.pogo.*;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.users.UserGroups;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;

public class TestSharing extends BaseXnatRestTest {

    private Project userProject;
    private Project adminProject;

    @BeforeMethod
    public void setupSharingProjects() {
        userProject = new Project().accessibility(Accessibility.PROTECTED);
        adminProject = new Project().accessibility(Accessibility.PROTECTED);
        mainInterface().createProject(userProject);
        mainAdminInterface().createProject(adminProject);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteSharingProjects() {
        restDriver.deleteProjectSilently(mainAdminUser, userProject);
        restDriver.deleteProjectSilently(mainAdminUser, adminProject);
    }

    @Test
    public void testUnShared() {
        final Subject subject = new Subject(adminProject, "1").
                group("control").
                src("12").
                pi(new Investigator().firstname("Tim").lastname("Olsen")).
                dob(LocalDate.parse("2001-01-01")).
                gender(Gender.MALE).
                handedness(Handedness.LEFT);

        final ImagingSession session = new MRSession(adminProject, subject, "MR1").date(LocalDate.parse("2000-01-01"));

        mainAdminInterface().createSubject(subject);

        final String sharedSubjectLabel = "TOLSEN1";
        final Share subjectShare = new Share(userProject, sharedSubjectLabel);
        final Subject sharedSubject = new Subject(userProject, sharedSubjectLabel);
        final String sharedSessionLabel = "TOLSEN_E1";
        final Share sessionShare = new Share(userProject, sharedSessionLabel);
        final ImagingSession sharedSession = new MRSession(userProject, sharedSubject, sharedSessionLabel);

        mainCredentials().queryParam("format", "xml").get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(404);
        mainCredentials().get(restDriver.subjectUrl(subject)).then().assertThat().statusCode(404);
        mainCredentials().delete(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(404);
        mainCredentials().delete(restDriver.subjectUrl(subject)).then().assertThat().statusCode(404);
        mainAdminInterface().shareSubject(adminProject, subject, subjectShare);

        // check get of original by admin
        mainAdminCredentials().get(restDriver.subjectUrl(subject)).then().assertThat().statusCode(200);
        mainCredentials().get(restDriver.subjectUrl(sharedSubject)).then().assertThat().statusCode(200);

        // still can't read the MR
        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(404);
        mainAdminInterface().shareSubjectAssessor(session, sessionShare);

        mainAdminCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);
        mainCredentials().get(restDriver.subjectAssessorUrl(sharedSession)).then().assertThat().statusCode(200);

        mainCredentials().delete(restDriver.subjectUrl(subject)).then().assertThat().statusCode(Matchers.isOneOf(403, 404));
        mainCredentials().delete(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(Matchers.isOneOf(403, 404));

        // unshares
        mainInterface().deleteSubject(sharedSubject);

        // confirm admin get
        mainAdminCredentials().get(restDriver.subjectUrl(subject)).then().assertThat().statusCode(200);
        mainAdminCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        mainCredentials().get(restDriver.subjectUrl(subject)).then().assertThat().statusCode(404);
        mainCredentials().delete(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(404);

        mainAdminInterface().addUserToProject(mainUser, adminProject, UserGroups.MEMBER);
        TimeUtils.sleep(1000); // let cache update
        mainCredentials().get(restDriver.subjectUrl(subject)).then().assertThat().statusCode(200);
        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        restDriver.interfaceFor(mainUser).logout();
        restDriver.interfaceFor(mainUser).reauthenticate();
        mainInterface().shareSubject(adminProject, subject, subjectShare);
        mainInterface().shareSubjectAssessor(session, sessionShare);

        mainCredentials().get(restDriver.subjectUrl(subject)).then().assertThat().statusCode(200);
        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);
        mainCredentials().get(restDriver.subjectUrl(sharedSubject)).then().assertThat().statusCode(200);
        mainCredentials().get(restDriver.subjectAssessorUrl(sharedSession)).then().assertThat().statusCode(200);

        final SessionAssessor assessor = new ManualQC(userProject, sharedSubject, sharedSession, "QC1");

        mainCredentials().
                queryParam("format", "xml").
                queryParam("req_format", "qs").
                queryParam("xsiType", DataType.MANUAL_QC.getXsiType()).
                queryParam("xnat:qcManualAssessorData/pass", 0).
                put(restDriver.sessionAssessorUrl(assessor));

        mainInterface().deleteSessionAssessor(assessor);

        // unshares
        mainInterface().deleteSubject(sharedSubject);

        mainCredentials().get(restDriver.subjectUrl(subject)).then().assertThat().statusCode(200);
        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        mainCredentials().put(CommonStringUtils.formatUrl(restDriver.subjectAssessorUrl(session), "projects", userProject.getId())).then().assertThat().statusCode(200);
        sharedSubject.setLabel(subject.getLabel());
        sharedSession.setLabel(session.getLabel());

        mainCredentials().delete(restDriver.subjectUrl(subject)).then().assertThat().statusCode(403); // members can't delete
        mainAdminInterface().deleteSubject(subject);

        final Subject subject2 = new Subject(adminProject, "2");
        final Subject sharedSubject2 = new Subject(userProject, "TOLSEN_2");
        subject2.addShare(new Share(userProject, sharedSubject2.getLabel()));
        mainAdminInterface().createSubject(subject2);

        mainCredentials().get(restDriver.subjectUrl(sharedSubject2)).then().assertThat().statusCode(200);
        final ImagingSession session2 = new MRSession(userProject, sharedSubject2, "MR2").date(LocalDate.parse("2000-01-01"));
        mainInterface().createSubjectAssessor(session2);

        mainAdminCredentials().get(restDriver.subjectAssessorUrl(adminProject, subject2, session2)).then().assertThat().statusCode(404); // mr2 is not available in source project

        final Subject subject5 = new Subject(adminProject, "5");
        final ImagingSession mr5 = new MRSession(adminProject, subject5, "MR5_5").date(LocalDate.parse("2000-01-01"));
        final Scan mr5Scan = new MRScan(mr5, "SCAN5");
        final String recon = "RECON5";

        mainInterface().createSubject(subject5);
        mainCredentials().
                queryParam("format", "xml").
                queryParam("req_format", "qs").
                queryParam("xsiType", "xnat:ReconstructedImage").
                put(reconUrl(mr5, recon)).
                then().assertThat().statusCode(200);

        // members can't delete any of this
        mainCredentials().delete(restDriver.subjectUrl(subject5)).then().assertThat().statusCode(403);
        mainCredentials().delete(restDriver.subjectAssessorUrl(mr5)).then().assertThat().statusCode(403);
        mainCredentials().delete(restDriver.scanUrl(mr5Scan)).then().assertThat().statusCode(403);

        mainCredentials().get(reconUrl(mr5, recon)).then().assertThat().statusCode(200);

        mainCredentials().delete(reconUrl(mr5, recon)).then().assertThat().statusCode(403);

        // remove user from shared project
        mainAdminCredentials().delete(restDriver.formatRestUrl("projects", adminProject.getId(), "users/member", mainUser.getUsername())).then().assertThat().statusCode(200);
        TimeUtils.sleep(1000); // let cache update

        // recheck that this user cannot access the project resources
        mainCredentials().queryParam("format", "xml").get(restDriver.subjectAssessorUrl(mr5)).then().assertThat().statusCode(404);
        mainAdminCredentials().queryParam("format", "xml").get(restDriver.subjectAssessorUrl(mr5)).then().assertThat().statusCode(200);
        mainCredentials().get(restDriver.subjectUrl(subject5)).then().assertThat().statusCode(404);
        mainAdminCredentials().get(restDriver.subjectUrl(subject5)).then().assertThat().statusCode(200);

        mainCredentials().delete(restDriver.subjectAssessorUrl(mr5)).then().assertThat().statusCode(404);
        mainCredentials().delete(restDriver.subjectUrl(subject5)).then().assertThat().statusCode(404);
    }

    @Test
    public void testImageAssessorCreation() {
        // in this test the userProject is the aggregator project and the adminProject is a site-project

        // cross share users as collaborators
        mainAdminInterface().addUserToProject(mainUser, adminProject, UserGroups.COLLABORATOR);
        restDriver.interfaceFor(mainUser).logout();
        restDriver.interfaceFor(mainUser).reauthenticate();
        mainInterface().addUserToProject(mainAdminUser, userProject, UserGroups.COLLABORATOR);

        // create subject in user project and share to admin project
        final Subject subject = new Subject(userProject, "6");
        mainInterface().createSubject(subject);

        final Share subjectShare = new Share(adminProject, "SHARE_S6");
        final Subject sharedSubject = new Subject(adminProject, subject.getLabel()); // access subject by original label
        subject.addShare(subjectShare);
        mainAdminInterface().shareSubject(userProject, subject, subjectShare);

        // create session in admin project and share to user project
        final ImagingSession session = new MRSession(adminProject, sharedSubject, "MR6_6").date(LocalDate.parse("2000-01-01"));
        mainAdminInterface().createSubjectAssessor(session);

        final Share sessionShare = new Share(userProject, "SHARE_E6");
        final ImagingSession sharedSession = new MRSession(userProject, subject, sessionShare.getDestinationLabel());
        mainInterface().shareSubjectAssessor(session, sessionShare);

        final SessionAssessor assessor = new ManualQC(userProject, sharedSubject, sharedSession, "QC1");
        // create qc in the user project
        mainCredentials().
                queryParam("req_format", "qs").
                queryParam("xsiType", DataType.MANUAL_QC.getXsiType()).
                queryParam("xnat:qcManualAssessorData/pass", 0).
                put(restDriver.sessionAssessorUrl(assessor)).
                then().assertThat().statusCode(201);

        final String assessorShareUrl = CommonStringUtils.formatUrl(restDriver.sessionAssessorUrl(assessor), "projects", adminProject.getId());

        mainAdminCredentials().
                queryParam("label", "ADMIN_QC1").
                put(assessorShareUrl).
                then().assertThat().statusCode(200);

        // house cleaning
        mainAdminCredentials().delete(assessorShareUrl).then().assertThat().statusCode(200);
        mainInterface().deleteSessionAssessor(assessor);
        mainCredentials().delete(CommonStringUtils.formatUrl(restDriver.subjectAssessorUrl(session), "projects", userProject.getId())).then().assertThat().statusCode(200);
        mainAdminInterface().deleteSubjectAssessor(session);
        mainInterface().deleteSubject(subject);
    }

    private String reconUrl(ImagingSession session, String recon) {
        return CommonStringUtils.formatUrl(restDriver.subjectAssessorUrl(session), "reconstructions", recon);
    }

}
