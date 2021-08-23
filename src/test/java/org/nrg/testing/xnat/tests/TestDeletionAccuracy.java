package org.nrg.testing.xnat.tests;

import org.dcm4che3.data.Tag;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.versions.Xnat_1_8_2_2;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * This whole test class is to check various types of deletes to confirm that they don't remove unintended data, such as in
 * XNAT-6852
 */
@TestRequires(data = {TestData.SAMPLE_1})
public class TestDeletionAccuracy extends BaseXnatRestTest {

    private static final int FILES_PER_SCAN = 176;
    private final Project project = new Project();

    @BeforeClass
    public void setupProject() {
        mainInterface().createProject(project);
        mainAdminInterface().disableSiteAnonScript();
    }

    @AfterClass(alwaysRun = true)
    public void tearDownTests() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test
    @AddedIn(Xnat_1_8_2_2.class)
    public void testDeleteResourcelessSessionNoScans() {
        runDeleteResourcelssSessionTest(false);
    }

    @Test
    @AddedIn(Xnat_1_8_2_2.class)
    public void testDeleteResourcelessSessionWithScans() {
        runDeleteResourcelssSessionTest(true);
    }

    @Test
    public void testDeleteEmptySessionAssessor() {
        final Subject subject = new Subject(project);
        final MRSession mainSession = new MRSession(project, subject);
        new SessionImportExtension(mainSession, TestData.SAMPLE_1.toFile());
        final SessionAssessor qc = new QC(project, subject, mainSession);
        mainInterface().createSubject(subject);
        mainInterface().deleteSessionAssessor(qc);
        validateSample1(mainSession);
    }

    @Test
    public void testDeleteNeighboringSession() {
        final Subject subject = new Subject(project);
        final MRSession mainSession = new MRSession(project, subject);
        new SessionImportExtension(mainSession, TestData.SAMPLE_1.toFile());
        final MRSession otherSession = new MRSession(project, subject);
        new SessionImportExtension(otherSession, TestData.SAMPLE_1.toFile());
        mainInterface().createSubject(subject);
        mainInterface().deleteSubjectAssessor(otherSession);
        validateSample1(mainSession);
    }

    @Test
    public void testDeleteNeighboringSubject() {
        final Subject subject = new Subject(project);
        final MRSession mainSession = new MRSession(project, subject);
        new SessionImportExtension(mainSession, TestData.SAMPLE_1.toFile());
        final Subject emptySubject = new Subject(project);
        mainInterface().createSubject(subject);
        mainInterface().createSubject(emptySubject);
        mainInterface().deleteSubject(emptySubject);
        validateSample1(mainSession);
    }

    @Test
    public void testDeleteNeighboringProject() {
        final Subject subject = new Subject(project);
        final MRSession mainSession = new MRSession(project, subject);
        new SessionImportExtension(mainSession, TestData.SAMPLE_1.toFile());
        final Project emptyProject = new Project();
        mainInterface().createSubject(subject);
        mainInterface().createProject(emptyProject);
        mainInterface().deleteProject(emptyProject);
        validateSample1(mainSession);
    }

    /*
        Reparses the scans in the copy of sample1 from XNAT, picks a scan, and checks to make sure the files are reasonable
     */
    private void validateSample1(ImagingSession session) {
        TimeUtils.sleep(1000); // give it a second just to make sure that the bug from XNAT-6852 isn't whirring away in the background
        final Resource dicomResource = mainInterface().findResource(mainInterface().readScans(project, session.getSubject(), session).get(0).getScanResources(), "DICOM");
        assertEquals(FILES_PER_SCAN, dicomResource.getFileCount());
        assertEquals(FILES_PER_SCAN, dicomResource.getResourceFiles().size());
        for (ResourceFile file : dicomResource.getResourceFiles()) {
            assertEquals(
                    TestData.SAMPLE_1.getStudyInstanceUid(),
                    DicomUtils.readDicom(mainInterface().streamResourceFile(dicomResource, file)).getDataset().getString(Tag.StudyInstanceUID));
        }
    }

    private void runDeleteResourcelssSessionTest(boolean createScan) {
        final Subject subject = new Subject(project);
        final MRSession realSession = new MRSession(project, subject);
        new SessionImportExtension(realSession, TestData.SAMPLE_1.toFile());
        final MRSession emptySession = new MRSession(project, subject);
        if (createScan) {
            new MRScan(emptySession, "1").seriesDescription("LOCALIZER");
        }
        mainInterface().createSubject(subject);
        mainInterface().deleteSubjectAssessor(emptySession);
        validateSample1(realSession);
    }

}
