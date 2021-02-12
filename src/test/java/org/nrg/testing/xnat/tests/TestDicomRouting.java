package org.nrg.testing.xnat.tests;

import com.jayway.restassured.specification.RequestSpecification;
import org.apache.log4j.Logger;
import org.dcm4che2.data.Tag;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DatasetWithFMI;
import org.dcm4che3.data.VR;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.testng.annotations.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.fail;

public class TestDicomRouting extends BaseXnatRestTest {

    private final Project project = new Project().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
    private final Path tmpDir = Paths.get(Settings.TEMP_SUBDIR);
    private final File testZip = getDataFile("mr_1.zip");
    private final List<File> filesToRemove = new ArrayList<>();
    private static final String subjectFromTestZip = "SPP_0x220790";
    private static final String sessionFromTestZip = "SPP_0x220790_MR2";
    private static final String accessionNumStripped = "497684894126";
    private static final Logger LOGGER = Logger.getLogger(TestDicomRouting.class);
    private static final String REPLACE_STR = "X";

    private final Map<String, String> cfgMapProject = Stream.of(new String[][] {
            { accessionNumStripped.replaceAll("6", REPLACE_STR),
                    "(0008,0050):^[0-9]{4}(.*):1 t:6 r:" + REPLACE_STR },
            { subjectFromTestZip.replaceAll("SPP_", "").replaceAll("[A-Za-z]", REPLACE_STR),
                    "(0008,0030):(.*)"  + System.lineSeparator() + "(0010,0010):SPP_([0-9x]*) t:[A-Za-z] r:" + REPLACE_STR }
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

    private final Map<String, String> cfgMap = Stream.of(new String[][] {
            { accessionNumStripped.replaceAll("9", REPLACE_STR), "(0008,0050):^[0-9]{4}(.*):1 t:9 r:" + REPLACE_STR },
            { accessionNumStripped, "(0008,0050):^[0-9]{4}(.*)"  },
            { subjectFromTestZip.replaceAll(".*_", ""),
                    "(0008,0030):(.*)" + System.lineSeparator() + "(0010,0010):.*_([\\w]+)" }
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

    @BeforeClass
    public void setupImportProject() {
        mainInterface().createProject(project);
        mainAdminInterface().disableSiteAnonScript();
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 10000);
    }

    @BeforeMethod(alwaysRun = true) // clear out prearchive/archive for each test
    @AfterClass(alwaysRun = true) // ... and then clear them out when we're all done
    public void clearArchives() {
        try {
            restDriver.clearPrearchiveSessions(mainUser, project);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
        try {
            mainInterface().deleteAllProjectData(project);
            TimeUtils.sleep(1000);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
        try {
            restDriver.disableProjectDicomRoutingConfig();
            restDriver.disableSubjectDicomRoutingConfig();
            restDriver.disableSessionDicomRoutingConfig();
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void removeTmpFiles() {
        for (File f : filesToRemove) {
            f.delete();
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDownImportTests() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
        mainAdminInterface().enableSiteAnonScript();
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testProjectRoutingSessionImporter() {
        testProjectRouting(this::uploadViaImporter, null);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testProjectRoutingDicomZip() {
        testProjectRouting(this::uploadViaImporter, "DICOM-zip");
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSubjectRoutingSessionImporter() {
        testSubjectRouting(this::uploadViaImporter, null);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSubjectRoutingDicomZip() {
        testSubjectRouting(this::uploadViaImporter, "DICOM-zip");
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSessionRoutingSessionImporter() {
        testSessionRouting(this::uploadViaImporter, null);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSessionRoutingDicomZip() {
        testSessionRouting(this::uploadViaImporter, "DICOM-zip");
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    @AddedIn(Xnat_1_8_0.class)
    public void testCombinedDicomScp() {
        String rand = Integer.toString(new Random().nextInt(100));
        String tagVal = "SIEMENS";
        String projectCfg = "(0008,0030):(.*)"  + System.lineSeparator() + "(0008,0070):([^M]+).* t:$ r:" + rand;
        String expectedProject = tagVal.replaceAll("M.*", rand);
        String expectedSub = "Patient";
        String expectedSes = "20061214";

        // set routing cfg
        restDriver.setProjectDicomRoutingConfig(projectCfg);
        restDriver.setSubjectDicomRoutingConfig("(0010,0010):Sample.*?([\\w]+)");
        restDriver.setSessionDicomRoutingConfig("(0008,0020):(.*)");

        // create destination project
        final Project project = new Project(expectedProject).prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
        mainInterface().createProject(project);

        // upload
        uploadViaDicomScp(Collections.emptyMap(), project);

        // check
        final Subject subject = new Subject(project, expectedSub);
        final ImagingSession session = new MRSession(project, subject, expectedSes);
        verifyImport(session);
        restDriver.waitForAutoRun(session);
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingPatientComments() {
        testXnatDefaultRouting(Tag.PatientComments, true, this::uploadViaDicomScp);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingStudyComments() {
        testXnatDefaultRouting(Tag.StudyComments, true, this::uploadViaDicomScp, Tag.PatientComments);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingAddlPatHist() {
        testXnatDefaultRouting(Tag.AdditionalPatientHistory, true, this::uploadViaDicomScp,
                Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingStudyDesc() {
        testXnatDefaultRouting(Tag.StudyDescription, false, this::uploadViaDicomScp,
                Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingAccession() {
        testXnatDefaultRouting(Tag.AccessionNumber, false, this::uploadViaDicomScp,
                Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    public void testXnatDefaultRoutingPatientCommentsDicomZip() {
        testXnatDefaultRouting(Tag.PatientComments, true, this::uploadViaImporter, "DICOM-zip", testZip);
    }

    @Test
    public void testXnatDefaultRoutingStudyCommentsDicomZip() {
        testXnatDefaultRouting(Tag.StudyComments, true, this::uploadViaImporter, "DICOM-zip", testZip, 
                Tag.PatientComments);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingAddlPatHistDicomZip() {
        testXnatDefaultRouting(Tag.AdditionalPatientHistory, true, this::uploadViaImporter, "DICOM-zip", testZip,
                Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    public void testXnatDefaultRoutingStudyDescDicomZip() {
        testXnatDefaultRouting(Tag.StudyDescription, false, this::uploadViaImporter, "DICOM-zip", testZip,
                Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    public void testXnatDefaultRoutingAccessionDicomZip() {
        testXnatDefaultRouting(Tag.AccessionNumber, false, this::uploadViaImporter, "DICOM-zip", testZip,
                Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingPatientCommentsSessionImporter() {
        testXnatDefaultRouting(Tag.PatientComments, true, this::uploadViaImporter, null, testZip);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingStudyCommentsSessionImporter() {
        testXnatDefaultRouting(Tag.StudyComments, true, this::uploadViaImporter, null, testZip,
                Tag.PatientComments);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingAddlPatHistSessionImporter() {
        testXnatDefaultRouting(Tag.AdditionalPatientHistory, true, this::uploadViaImporter, null, testZip,
                Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingStudyDescSessionImporter() {
        testXnatDefaultRouting(Tag.StudyDescription, false, this::uploadViaImporter, null, testZip,
                Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-6469")
    public void testXnatDefaultRoutingAccessionSessionImporter() {
        testXnatDefaultRouting(Tag.AccessionNumber, false, this::uploadViaImporter, null,
                testZip, Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingAccessionSessionImporterAlt() {
        testXnatDefaultRouting(Tag.AccessionNumber, false, this::uploadViaImporter, null, "",
                testZip, Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    private void testXnatDefaultRouting(int tag, boolean combined, UploadFn uploadFn, int... tagsToClear) {
        testXnatDefaultRouting(tag, combined, uploadFn, null, null, tagsToClear);
    }

    private void testXnatDefaultRouting(int tag, boolean combined, UploadFn uploadFn, String handler,
                                        File zipFile, int... tagsToClear) {
        testXnatDefaultRouting(tag, combined, uploadFn, handler, "notPatternOrProject", zipFile, tagsToClear);
    }

    private void testXnatDefaultRouting(int tag, boolean combined, UploadFn uploadFn, String handler, String replaceVal,
                                        File zipFile, int... tagsToClear) {
        final String listener = Long.toString(System.currentTimeMillis());
        final Project project = new Project(listener).prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);
        mainInterface().createProject(project);
        final Subject   subject = new Subject(project, "su" + listener);
        final MRSession session = new MRSession(project, subject, "se" + listener);

        // update headers
        Map<Integer, String> hdr = new HashMap<>();
        if (combined) {
            hdr.put(tag, String.format("Project:%s Subject:%s Session:%s",
                    project.getId(), subject.getLabel(), session.getLabel()));
        } else {
            hdr.put(tag, project.getId());
            hdr.put(Tag.PatientName, subject.getLabel());
            hdr.put(Tag.PatientID, session.getLabel());
        }
        for (int t : tagsToClear) {
            hdr.put(t, replaceVal);
        }

        // upload
        uploadFn.accept(hdr, project, zipFile, handler);

        // verify
        verifyImport(session);

        // cleanup
        restDriver.waitForAutoRun(session);
        restDriver.deleteProjectSilently(mainUser, project);
    }

    private void uploadViaImporter(@Nullable Project project, @Nullable String handler, boolean sendProjectId) {
        uploadViaImporter(null, project, testZip, handler, sendProjectId);
    }

    private void uploadViaImporter(Map<Integer, String> hdr, Project project, File zip, @Nullable String handler) {
        uploadViaImporter(hdr, project, zip, handler, false);
    }

    private void uploadViaImporter(Map<Integer, String> hdr, Project project, File zip, @Nullable String handler, 
                                   boolean sendProjectId) {
        if (hdr != null) {
            try {
                zip = editDicomInZip(zip, hdr);
                filesToRemove.add(zip);
            } catch (IOException e) {
                fail("Issue editing DICOM in " + zip);
            }
        }

        RequestSpecification request = mainCredentials();
        if (handler != null) {
            request.queryParam("import-handler", handler);
        }
        if (sendProjectId) {
            request.queryParam("PROJECT_ID", project.getId());
        }
        request.multiPart(zip)
                .post(formatRestUrl("services/import"))
                .then().assertThat().statusCode(200);
        
        if (handler != null) {
            waitForDicomRecieve(project);
        }
    }

    private File editDicomInZip(File zip, @Nonnull Map<Integer, String> hdr) throws IOException {
        ZipFile zf = new ZipFile(zip);
        File destZipFile = tmpDir.resolve(System.currentTimeMillis() + ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destZipFile))) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.isDirectory()) {
                    continue;
                }
                DatasetWithFMI dcm;
                try (InputStream is = zf.getInputStream(ze)) {
                    dcm = DicomUtils.readDicom(is);
                }
                Attributes attr = dcm.getDataset();
                for (Integer tag : hdr.keySet()) {
                    VR vr = attr.getVR(tag);
                    attr.setString(tag, vr == null ? VR.LT : vr, hdr.get(tag));
                }
                File f = DicomUtils.writeDicomToFile(dcm);
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
                    zos.putNextEntry(new ZipEntry(ze.getName()));
                    byte[] readBuffer = new byte[4096];
                    int amountRead;
                    while ((amountRead = bis.read(readBuffer)) > 0) {
                        zos.write(readBuffer, 0, amountRead);
                    }
                    zos.closeEntry();
                }
                f.delete();
            }
        }
        return destZipFile;
    }

    private void uploadViaDicomScp(Map<Integer, String> hdr, Project project) {
        uploadViaDicomScp(hdr, project,null, null);
    }
    
    private void uploadViaDicomScp(Map<Integer, String> hdr, Project project, File ignored, String ignored2) {
        // ignored arguments here so this upload function matches DICOM-zip and SI
        new XnatCStore().data(TestData.SAMPLE_1_SCAN_4).overwrittenHeaders(hdr).sendDICOM();
        waitForDicomRecieve(project);
    }

    private void waitForDicomRecieve(Project project) {
        TimeUtils.sleep(60000);
        restDriver.waitForPrearchiveEmpty(mainUser, project, 300);
    }

    private void verifyProject(Project p) {
        final Subject subject = new Subject(p, subjectFromTestZip);
        final ImagingSession session = new MRSession(p, subject, sessionFromTestZip);
        verifyImport(session);
        restDriver.waitForAutoRun(session);
        restDriver.deleteProjectSilently(mainUser, p);
    }

    private void verifySubject(String expected) {
        final Subject subject = new Subject(project, expected);
        final ImagingSession session = new MRSession(project, subject, sessionFromTestZip);
        verifyImport(session);
        restDriver.waitForAutoRun(session);
        mainInterface().deleteSubjectAssessor(session);
    }

    private void verifySession(String expected) {
        final Subject subject = new Subject(project, subjectFromTestZip);
        final ImagingSession session = new MRSession(project, subject, expected);
        verifyImport(session);
        restDriver.waitForAutoRun(session);
        mainInterface().deleteSubjectAssessor(session);
    }

    private void testProjectRouting(ApiUploadFn uploadFn, String handler) {
        // Because XNAT cannot delete and readd a project with the same ID, we have to include a random component in
        // the project id. Hopefully 0-99 is sufficient to ensure no conflicts within a given test run/rerun.
        String rand = Integer.toString(new Random().nextInt(100));
        for (String key : cfgMapProject.keySet()) {
            String cfg = cfgMapProject.get(key).replaceAll(REPLACE_STR, rand);
            restDriver.setProjectDicomRoutingConfig(cfg);
            String expected = key.replaceAll(REPLACE_STR, rand);
            Project p = new Project(expected);
            mainInterface().createProject(p);
            uploadFn.accept(p, handler, false);
            verifyProject(p);
        }
    }

    private void testSubjectRouting(ApiUploadFn uploadFn, String handler) {
        for (String expected : cfgMap.keySet()) {
            restDriver.setSubjectDicomRoutingConfig(cfgMap.get(expected));
            uploadFn.accept(project, handler, true);
            verifySubject(expected);
        }
    }

    private void testSessionRouting(ApiUploadFn uploadFn, String handler) {
        for (String expected : cfgMap.keySet()) {
            restDriver.setSessionDicomRoutingConfig(cfgMap.get(expected));
            uploadFn.accept(project, handler, true);
            verifySession(expected);
        }
    }

    private void verifyImport(SubjectAssessor session) {
        // if session can be retrieved at this URL, then project, subject, session are all labelled properly
        TimeUtils.sleep(1000); // sleep for 1s to accommodate a little gap between prearchive being empty and session being accessible
        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);
    }

    @FunctionalInterface
    public interface ApiUploadFn {
        void accept(Project project, String handler, boolean sendProject);
    }
    
    @FunctionalInterface
    public interface UploadFn {
        void accept(Map<Integer, String> hdr, Project project, File zip, String handler);
    }
}