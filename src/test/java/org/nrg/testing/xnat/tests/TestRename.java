package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
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

import static org.nrg.testing.TestGroups.PERMISSIONS;

public class TestRename extends BaseXnatRestTest {

    private final Project renameProject1 = new Project();
    private final Project renameProject2 = new Project();
    private final File louieFile = getDataFile("louie.jpg");
    private final File dicomFile = getDataFile("mr_1/1.dcm");


    @BeforeClass
    public void addRenameProjects() {
        mainAdminInterface().createProject(renameProject1);
        mainAdminInterface().createProject(renameProject2);
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
        final ResourceFile sessionResourceFile = new ResourceFile().extension(new SimpleResourceFileExtension(louieFile));
        final Resource sessionResource = new SubjectAssessorResource(renameProject1, subject, session, "TESTRESOURCE").addResourceFile(sessionResourceFile);

        final Scan scan = new MRScan(session, "1").seriesDescription("FLAIR").type("FLAIR");
        final ResourceFile scanResourceFile = new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile));
        final Resource scanResource = new ScanResource(renameProject1, subject, session, scan).folder("TESTRESOURCE2").addResourceFile(scanResourceFile);

        final String newLabel = "MOD1";

        mainAdminInterface().createSubject(subject);
        mainAdminCredentials().given().queryParam("label", newLabel).put(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);
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
        final ResourceFile sessionResourceFile = new ResourceFile().extension(new SimpleResourceFileExtension(louieFile));
        final Resource sessionResource = new SubjectAssessorResource(renameProject1, subject, session, "TESTRESOURCE").addResourceFile(sessionResourceFile);

        new Scan(session, "1").xsiType(DataType.MR_SCAN.getXsiType()).seriesDescription("FLAIR").type("FLAIR");

        final String newLabel = "MOD2";

        mainAdminInterface().createSubject(subject);
        mainAdminCredentials().given().queryParam("label", newLabel).put(mainInterface().subjectUrl(subject));
        subject.setLabel(newLabel);

        restDriver.validateResource(mainAdminUser, subjectResource);
        restDriver.validateResource(mainAdminUser, sessionResource);
    }

    @Test(groups = PERMISSIONS)
    public void testExptWAssessRename() {
        final Subject subject = new Subject(renameProject1, "3").addShare(new Share(renameProject2));
        final ImagingSession session = new MRSession(renameProject1, subject, "MR3").addShare(new Share(renameProject2));
        new MRScan(session, "3");

        final String newLabel = "MOD3";

        mainAdminInterface().addUserToProject(mainUser, renameProject2, UserGroups.MEMBER);
        mainAdminInterface().createSubject(subject);

        final SessionAssessor assessor = new QC(renameProject2, subject, session, "QC1");
        mainAdminInterface().createSessionAssessor(renameProject2, subject, session, assessor);

        final Resource sessionResource = new SubjectAssessorResource(renameProject1, subject, session, "TEST").addResourceFile(
                new ResourceFile().extension(new SimpleResourceFileExtension(dicomFile))
        );
        final Resource assessorResource = new SessionAssessorResource(renameProject2, subject, session, assessor).folder("ASSESSOR_RESOURCE").addResourceFile(new ResourceFile().extension(new SimpleResourceFileExtension(louieFile)));

        mainAdminInterface().uploadResource(sessionResource);
        mainAdminInterface().uploadResource(assessorResource);

        // user with no access to source project should not be able to relabel
        mainCredentials().given().queryParam("label", newLabel).
                put(mainInterface().subjectAssessorUrl(renameProject1, subject, session)).
                then().assertThat().statusCode(404);

        mainAdminInterface().addUserToProject(mainUser, renameProject1, UserGroups.COLLABORATOR);

        // user with collaborator to source project should not be able to relabel
        mainCredentials().given().queryParam("label", newLabel).put(mainInterface().subjectAssessorUrl(renameProject1, subject, session)).then().assertThat().statusCode(Matchers.isOneOf(403, 417));
        restDriver.validateResource(mainUser, sessionResource);

        mainAdminInterface().addUserToProject(mainUser, renameProject1, UserGroups.MEMBER);
        TimeUtils.sleep(1000); // let cache update

        mainCredentials().given().queryParam("label", newLabel).put(mainInterface().subjectAssessorUrl(renameProject1, subject, session)).then().assertThat().statusCode(200);
        session.setLabel(newLabel);

        restDriver.validateResource(mainUser, sessionResource);
        restDriver.validateResource(mainUser, assessorResource.project(renameProject1));
    }

}
