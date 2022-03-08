package org.nrg.testing.xnat.tests;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.iod.module.macro.Code;
import org.hamcrest.Matchers;
import org.nrg.testing.CommonStringUtils;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.UIDList;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.enums.MergeBehavior;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.importers.DefaultImporterRequest;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.ScanResource;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.rest.Credentials;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

public class TestAnonymizerLegacy extends BaseXnatRestTest {
    private final File anonScript1File = getDataFile("anon1.das");
    private final AnonScript anonScript1 = XnatObjectUtils.anonScriptFromFile(null, anonScript1File);
    private final File anonScript2File = getDataFile("anon2.das");
    private final AnonScript anonScript2 = XnatObjectUtils.anonScriptFromFile(null, anonScript2File);
    private final File anonScript3File = getDataFile("projectsubjectsession.das");
    private final AnonScript anonScript3 = XnatObjectUtils.anonScriptFromFile(null, anonScript3File);
    private final File testZip = getDataFile("mr_1.zip");
    private final File dicomFile = getDataFile("mr_1/1.dcm");
    private final String dicomPatName = "SPP_0x220790";
    private final String dicomPatId = "SPP_0x220790_MR2";

    private Project currentProject;
    private Subject subject;
    private ImagingSession session;
    private Project otherProject;

    @BeforeClass
    public void setSiteScript() {
        mainAdminInterface().setSiteAnonScript(restDriver.getDefaultXnatAnonScript());
    }

    @BeforeMethod
    public void enableSiteScript() {
        mainAdminInterface().enableSiteAnonScript();
    }

    @BeforeMethod
    public void setUpAnonProject() {
        currentProject = new Project().prearchiveCode(PrearchiveCode.MANUAL);
        subject = new Subject(currentProject, "ANON_SUBJ_1").gender(Gender.MALE);
        session = new MRSession(currentProject, subject, "MR1").date(LocalDate.parse("2000-01-01"));
        // only currentProject is used by all projects, so no reason to re create all of them each time

        mainInterface().createProject(currentProject);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownAnonTest() {
        restDriver.deleteProjectSilently(mainAdminUser, currentProject);
        restDriver.deleteProjectSilently(mainAdminUser, otherProject);
    }

    /**
     * Test uploading and downloading multiple scripts to a project.
     */
    @Test
    @Basic
    public void testUploadScriptToProject() {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);
        assertEquals(FileIOUtils.readFile(anonScript1File), mainInterface().readProjectAnonScript(currentProject).getContents().trim());

        mainInterface().setProjectAnonScript(currentProject, anonScript2);
        assertEquals(FileIOUtils.readFile(anonScript2File), mainInterface().readProjectAnonScript(currentProject).getContents().trim());
    }

    /**
     * Test to see if a file uploaded via the Zip importer to the prearchive and then archived to a project
     * has had both the site-wide and project specific anon scripts applied to it.
     */
    @Test
    @Basic
    public void testPrearchiveProjectZipUpload() throws IOException {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);

        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        destArchive(currentProject).
                        file(testZip)
        );

        final Subject newSubject = new Subject(currentProject, dicomPatName);
        final MRSession newSession = new MRSession(currentProject, newSubject, dicomPatId);
        final String expectedDest = mainInterface().subjectAssessorUrl(newSession);

        mainQueryBase().get(expectedDest).then().assertThat().statusCode(200);

        final File downloadedDicom = saveDicomFile(mainUser, expectedDest);

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(2, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
        assertEquals(getScriptId(mainUser, currentProject), codes[1].getCodeValue());
    }

    /**
     * Test that uploading to a project via the zip uploader applies both
     * the site-wide and project specific scripts to the DICOM files.
     */
    @Test
    @Basic
    public void testProjectZipUpload() throws IOException {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);

        // upload DICOM to the experiment
        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        dest(session.getUri()).
                        overwrite(MergeBehavior.TRUE). // legacy
                        file(testZip)
        );

        final String expectedDest = mainInterface().subjectAssessorUrl(session);

        mainQueryBase().get(expectedDest).then().assertThat().statusCode(200);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(session));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(2, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
        assertEquals(getScriptId(mainUser, currentProject), codes[1].getCodeValue());
    }

    /**
     * Test that applying a project specific script adds the name
     * of the project to the DICOM header
     */
    @Test
    public void testSessionLabelInHeader() throws IOException {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);
        
        // upload DICOM to the experiment
        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        overwrite(MergeBehavior.TRUE). // legacy
                        dest(session.getUri()).
                        file(testZip)
        );

        final String expectedDest = mainInterface().subjectAssessorUrl(session);

        mainQueryBase().get(expectedDest).then().assertThat().statusCode(200);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(session));

        final DicomInputStream dis = new DicomInputStream(downloadedDicom);
        final DicomObject dicomObject = dis.readDicomObject();
        assertEquals(session.getLabel(), dicomObject.getString(Tag.PatientID));
    }

    /**
     * Test that uploading a script to a project and then disabling it
     * keeps the edit script from being applied
     */
    @Test
    public void testDisabledProjectScript() throws IOException {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);
        mainInterface().disableProjectAnonScript(currentProject);

        // upload DICOM to the experiment
        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        overwrite(MergeBehavior.TRUE).
                        dest(session.getUri()).
                        file(testZip)
        );

        final String expectedDest = mainInterface().subjectAssessorUrl(session);

        mainQueryBase().get(expectedDest).then().assertThat().statusCode(200);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(session));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(1, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
    }

    /**
     * Test that disabling the site-wide script and only applies
     * the project-specific script to the DICOM files.
     */
    @Test
    public void testDisabledSiteWideScript() throws IOException {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);
        mainAdminInterface().disableSiteAnonScript();

        // upload DICOM to the experiment
        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        overwrite(MergeBehavior.TRUE).
                        dest(session.getUri()).
                        file(testZip)
        );

        final String expectedDest = mainInterface().subjectAssessorUrl(session);

        mainQueryBase().get(expectedDest).then().assertThat().statusCode(200);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(session));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(1, codes.length);
        assertEquals(getScriptId(mainUser, currentProject), codes[0].getCodeValue());
    }

    /**
     * Test that uploading via the zip uploader to the prearchive applies the
     * site-wide script.
     */
    @Test
    @Basic
    public void testDefaultAnonZipUploadSiteWide() throws IOException {
        restDriver.clearUnassignedPrearchiveSessions(mainAdminUser, UIDList.uids);

        final String sessionUri = mainInterface().callImporter(
                new DefaultImporterRequest().triggerPipelines(false).file(testZip)
        );

        final File downloadedDicom = saveDicomFile(mainAdminUser, formatXnatUrl(sessionUri));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(1, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
    }

    /**
     * Test that uploading an image directly to the resource section of an experiment
     * does *not* trigger the site-wide or project-specific edit script application.
     */
    @Test
    public void testResourceUpload() throws IOException {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);
        mainInterface().setProjectAnonScript(currentProject, anonScript2);

        final Scan scan = new MRScan(session, "1");
        final Resource dicomResource = new ScanResource(currentProject, subject, session, scan).folder("DICOM");
        dicomResource.addResourceFile(new ResourceFile().name(dicomFile.getName()).extension(new SimpleResourceFileExtension(dicomFile)));
        mainInterface().createScan(currentProject, subject, session, scan);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(session));

        // There shouldn't be any anonymization done because it's not going through the
        // the upload assistant or zip uploader or prearchive
        final Code[] codes = getCodes(downloadedDicom);
        assertNull(codes);
    }

    /**
     * Test to see if a file uploaded via the Gradual DICOM importer is properly anonymized in reference to the site-wide script
     */
    @Test
    @Basic
    public void testDefaultAnonGradualDicomUpload() throws IOException {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);

        final String uri = mainInterface().callImporter(
                new DicomZipRequest().destPrearchive(currentProject).file(testZip)
        );

        assertEquals(uri, mainQueryBase().queryParam("action", "commit").post(formatXnatUrl(uri)).then().extract().response().asString().trim());

        final File downloadedDicom = saveDicomFile(mainUser, formatXnatUrl(uri));

        // Since the files are sitting in the prearchive the project specific script should not have applied
        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(1, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
    }

    @Test
    public void testNoProjectScript() throws IOException {
        // upload DICOM to the experiment
        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        overwrite(MergeBehavior.TRUE). // legacy
                        dest(session.getUri()).
                        file(testZip)
        );

        final String expectedDest = mainInterface().subjectAssessorUrl(session);

        mainQueryBase().get(expectedDest).then().assertThat().statusCode(200);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(session));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(1, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
    }

    /**
     * Test that uploading via the gradual DICOM importer into archiving into a project
     * applies the site-wide and project specific edit scripts to the DICOM files.
     */
    @Test
    @Basic
    public void testDefaultProjectGradualDicomUpload() throws IOException {
        mainInterface().setProjectAnonScript(currentProject, anonScript1);

        final String uri = mainInterface().callImporter(
                new DicomZipRequest().destPrearchive(currentProject).file(testZip)
        );

        // commit it
        final String uri2 = mainQueryBase().queryParam("action", "commit").queryParam("auto-archive", true).post(formatXnatUrl(uri)).
                then().assertThat().statusCode(Matchers.isOneOf(200, 301)).and().extract().response().asString();

        final File downloadedDicom = saveDicomFile(mainUser, formatXnatUrl(uri2));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(2, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
        assertEquals(getScriptId(mainUser, currentProject), codes[1].getCodeValue());
    }

    /**
     * Test that changing the subject name of a session triggers a re-application of the
     * project specific script.
     *
     * After the change the session should have the following script application history:
     * 1. Site-wide
     * 2. Project specific
     * 3. Project specific
     */
    @Test
    @Basic
    public void testSubjectChange() throws IOException {
        final Subject testSubject = new Subject(currentProject, "00001");
        final Subject destinationSubject = new Subject(currentProject, "subj_mod");
        final ImagingSession testSession = new MRSession(currentProject, testSubject, "case01");

        mainInterface().setProjectAnonScript(currentProject, anonScript1);

        restDriver.uploadToSessionZipImporter(testZip, testSession);
        mainInterface().waitForAutoRun(testSession);

        mainInterface().moveSubjectAssessorToOtherSubject(testSession, destinationSubject);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(testSession));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(3, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
        assertEquals(getScriptId(mainUser, currentProject), codes[1].getCodeValue());
        assertEquals(getScriptId(mainUser, currentProject), codes[2].getCodeValue());
    }

    /**
     * Test that changing the label of an experiment reapplies the edit script associated
     * with that experiment's project.
     *
     * Also test that the anon script writes the DICOM header with the name of the new session.
     *
     * After the rename a DICOM file should have the following history:
     *   - a site-wide edit script application
     *   - a project-specific edit script application
     *   - another project-specific script application
     */
    @Test
    @Basic
    public void testLabelChange() throws IOException {
        final Subject testSubject = new Subject(currentProject, "00001");
        final ImagingSession testSession = new MRSession(currentProject, testSubject, "case01");

        mainInterface().setProjectAnonScript(currentProject, anonScript3);

        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        dest(testSession.getUri()).
                        file(testZip)
        );

        final String newSessionLabel = "new_MR1";
        mainInterface().relabelSubjectAssessor(testSession, newSessionLabel);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(testSession));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(3, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
        assertEquals(getScriptId(mainUser, currentProject), codes[1].getCodeValue());
        assertEquals(getScriptId(mainUser, currentProject), codes[2].getCodeValue());

        assertEquals(newSessionLabel, new DicomInputStream(downloadedDicom).readDicomObject().getString(Tag.PatientID));
    }


    /**
     * Test that moving a subject and session from an old project to a new project applies the
     * new project's anon script.
     *
     * The script application history on this experiment should be:
     * 1. Site wide
     * 2. old project script
     * 3. new project script
     */
    @Test
    @Basic
    public void testProjectChange() throws IOException {
        otherProject = new Project();
        final Subject otherSubject = new Subject(otherProject, "filler");
        new MRSession(otherProject, otherSubject, "filler_MR1").date(LocalDate.parse("2000-01-01"));
        mainInterface().createProject(otherProject);

        mainInterface().setProjectAnonScript(currentProject, anonScript1);
        mainInterface().setProjectAnonScript(otherProject, anonScript1);

        final Subject testSubject = new Subject(currentProject, "00001");
        final ImagingSession testSession = new MRSession(currentProject, testSubject, "case01");

        restDriver.uploadToSessionZipImporter(testZip, testSession);
        mainInterface().waitForAutoRun(testSession);

        // move the subject to the new project
        mainQueryBase().queryParam("primary", true).put(CommonStringUtils.formatUrl(mainInterface().subjectUrl(testSubject), "projects", otherProject.getId())).
                then().assertThat().statusCode(200);

        // move the session too
        mainQueryBase().queryParam("primary", true).put(CommonStringUtils.formatUrl(mainInterface().subjectAssessorUrl(testSession), "projects", otherProject.getId())).
                then().assertThat().statusCode(200);

        testSubject.setProject(otherProject);
        testSession.setPrimaryProject(otherProject);

        final File downloadedDicom = saveDicomFile(mainUser, mainInterface().subjectAssessorUrl(testSession));

        final Code[] codes = getCodes(downloadedDicom);
        assertEquals(3, codes.length);
        assertEquals(getSiteScriptId(mainAdminUser), codes[0].getCodeValue());
        assertEquals(getScriptId(mainUser, currentProject), codes[1].getCodeValue());
        assertEquals(getScriptId(mainUser, otherProject), codes[2].getCodeValue());
    }

    private File saveDicomFile(User authUser, String partialUrl) {
        return restDriver.saveBinaryResponseToFile(
                Credentials.build(authUser).get(CommonStringUtils.formatUrl(partialUrl, "scans/1/resources/DICOM/files/1.dcm")).
                        then().assertThat().statusCode(200).and().extract().response()
        );
    }

    /**
     * Return the deidentification elements in the given DICOM file
     */
    private Code[] getCodes(File dcmFile) throws IOException {
        final DicomInputStream sample_dis = new DicomInputStream(dcmFile);
        final DicomObject sample = sample_dis.readDicomObject();
        Code[] codes = Code.toCodes(sample.get(Tag.DeidentificationMethodCodeSequence));
        sample_dis.close();
        return codes;
    }

    private String getSiteScriptId(User authUser) {
        return Credentials.build(authUser).queryParam("format", "json").get(restDriver.siteAnonScriptUrl()).jsonPath().getString("ResultSet.Result.get(0).id");
    }

    private String getScriptId(User authUser, Project project) {
        return Credentials.build(authUser).queryParam("format", "json").get(mainInterface().projectAnonScriptUrl(project)).jsonPath().getString("ResultSet.Result.get(0).id");
    }

}
