package org.nrg.testing.xnat.tests;

import org.nrg.testing.file.FileIO;
import org.nrg.testing.xnat.BaseRestTest;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Share;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.*;
import org.nrg.xnat.pogo.users.UserGroups;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;

public class TestRename extends BaseRestTest {

    private final Project renameProject1 = new Project();
    private final Project renameProject2 = new Project();
    private final String louie = "louie.jpg";
    private final File dicomFile = FileIO.getDataFile("mr_1/1.dcm");


    @BeforeClass
    public void addRenameProjects() {
        restDriver.createProject(mainAdminUser, renameProject1);
        restDriver.createProject(mainAdminUser, renameProject2);
    }

    @AfterClass(alwaysRun = true)
    public void deleteRenameProjects() {
        restDriver.deleteProjectSilently(mainAdminUser, renameProject1);
        restDriver.deleteProjectSilently(mainAdminUser, renameProject2);
    }

    @Test
    public void testExptRename() {
        final Subject subject = new Subject(renameProject1);

        final ImagingSession session = new MRSession(renameProject1, subject);
        final ResourceFile sessionResourceFile = new ResourceFile().name(louie);
        final Resource sessionResource = new SubjectAssessorResource(renameProject1, subject, session, "TESTRESOURCE").addResourceFile(sessionResourceFile);

        final Scan scan = new MRScan(session, "1").seriesDescription("FLAIR").type("FLAIR");
        final ResourceFile scanResourceFile = new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile));
        final Resource scanResource = new ScanResource(renameProject1, subject, session, scan).folder("TESTRESOURCE2").addResourceFile(scanResourceFile);

        final String newLabel = "MOD1";

        restDriver.createSubject(mainAdminUser, subject);
        mainAdminCredentials().given().queryParam("label", newLabel).put(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);
        session.setLabel(newLabel);

        restDriver.validateResource(mainAdminUser, sessionResource);
        restDriver.validateResource(mainAdminUser, scanResource);
    }

    @Test
    public void testSubjRename() {
        final Subject subject = new Subject(renameProject1, "2");
        final ResourceFile subjectResourceFile = new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile));
        final Resource subjectResource = new SubjectResource(subject.getProject(), subject, "TESTRESOURCE2").addResourceFile(subjectResourceFile);

        final ImagingSession session = new MRSession(renameProject1, subject);
        final ResourceFile sessionResourceFile = new ResourceFile().name(louie);
        final Resource sessionResource = new SubjectAssessorResource(renameProject1, subject, session, "TESTRESOURCE").addResourceFile(sessionResourceFile);

        new Scan(session, "1").xsiType(DataType.MR_SCAN.getXsiType()).seriesDescription("FLAIR").type("FLAIR");

        final String newLabel = "MOD2";

        restDriver.createSubject(mainAdminUser, subject);
        mainAdminCredentials().given().queryParam("label", newLabel).put(restDriver.subjectUrl(subject));
        subject.setLabel(newLabel);

        restDriver.validateResource(mainAdminUser, subjectResource);
        restDriver.validateResource(mainAdminUser, sessionResource);
    }

    @Test
    public void testExptWAssessRename() {
        final Subject subject = new Subject(renameProject1, "3").addShare(new Share(renameProject2));
        final ImagingSession session = new MRSession(renameProject1, subject, "MR3").addShare(new Share(renameProject2));
        new MRScan(session, "3");

        final String newLabel = "MOD3";

        restDriver.addUserToProject(mainAdminUser, mainUser, renameProject2, UserGroups.MEMBER);
        restDriver.createSubject(mainAdminUser, subject);

        final SessionAssessor assessor = new QC(renameProject2, subject, session, "QC1");
        restDriver.createSessionAssessor(mainAdminUser, renameProject2, subject, session, assessor);

        final Resource sessionResource = new SubjectAssessorResource(renameProject1, subject, session, "TEST").addResourceFile(
                new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile))
        );
        final Resource assessorResource = new SessionAssessorResource(renameProject2, subject, session, assessor).addResourceFile(new ResourceFile().name(louie));

        restDriver.uploadResource(mainAdminUser, sessionResource);
        restDriver.uploadResource(mainAdminUser, assessorResource);

        // user with no access to source project should not be able to relabel
        mainCredentials().given().queryParam("label", newLabel).put(restDriver.subjectAssessorUrl(renameProject1, subject, session)).then().assertThat().statusCode(404);

        restDriver.addUserToProject(mainAdminUser, mainUser, renameProject1, UserGroups.COLLABORATOR);

        // user with collaborator to source project should not be able to relabel
        mainCredentials().given().queryParam("label", newLabel).put(restDriver.subjectAssessorUrl(renameProject1, subject, session)).then().assertThat().statusCode(403);

        restDriver.addUserToProject(mainAdminUser, mainUser, renameProject1, UserGroups.MEMBER);

        mainCredentials().given().queryParam("label", newLabel).put(restDriver.subjectAssessorUrl(renameProject1, subject, session)).then().assertThat().statusCode(200);
        session.setLabel(newLabel);

        restDriver.validateResource(mainUser, sessionResource);
        restDriver.validateResource(mainUser, assessorResource.project(renameProject1));
    }

}
