package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.versions.Xnat_1_8_0;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.nrg.testing.TestGroups.*;
import static org.testng.AssertJUnit.fail;
import org.nrg.testing.annotations.MutatesServerState;

@Test(groups = {DICOM_SCP, DICOM_ROUTING, IMPORTER})
@MutatesServerState
public class TestDicomRouting extends BaseXnatRestTest {

    private final Project project = registerTempProject().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
    private final Path tmpDir = Paths.get(Settings.TEMP_SUBDIR);
    private final File testZip = getDataFile("mr_1.zip");
    private final List<File> filesToRemove = new ArrayList<>();
    private static final String subjectFromTestZip = "SPP_0x220790";
    private static final String sessionFromTestZip = "SPP_0x220790_MR2";
    private static final String accessionNumStripped = "497684894126";
    private static final Logger LOGGER = Logger.getLogger(TestDicomRouting.class);
    private static final String REPLACE_STR = "X";
    private static final String DICOM_ZIP = "DICOM-zip";
    private static final String SI = "SI";
    private static final String IMPORT_PROVIDER = "import-provider";

    /*
        Legacy (1.7.7) tests are only run with DICOM-zip here. Many of the previous @AddedIn(Xnat_1_8_0.class)
        usages are replaced by the parameterization provided by this method.
     */
    @DataProvider(name = IMPORT_PROVIDER)
    private String[][] getZipImportHandlers() {
        final String[] dicomZip = new String[]{ DICOM_ZIP };
        final String[] si = new String[] { SI };
        return XnatTestingVersionManager.testedVersionPrecedes(Xnat_1_8_0.class) ? new String[][]{ dicomZip } : new String[][]{ dicomZip, si };
    }

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
    private void setupImportProject() {
        mainInterface().createProject(project);
        mainAdminInterface().disableSiteAnonScript();
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 10000);
    }

    @BeforeMethod(alwaysRun = true) // clear out prearchive/archive for each test
    @AfterClass(alwaysRun = true) // ... and then clear them out when we're all done
    private void clearArchives() {
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
            mainAdminInterface().disableProjectDicomRoutingConfig();
            mainAdminInterface().disableSubjectDicomRoutingConfig();
            mainAdminInterface().disableSessionDicomRoutingConfig();
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
    }

    @AfterMethod(alwaysRun = true)
    private void removeTmpFiles() {
        for (File f : filesToRemove) {
            f.delete();
        }
    }

    @AfterClass(alwaysRun = true)
    private void tearDownImportTests() {
        mainAdminInterface().enableSiteAnonScript();
    }

    @Test(groups = SMOKE, dataProvider = IMPORT_PROVIDER)
    @AddedIn(Xnat_1_8_0.class)
    @Basic
    public void testProjectRoutingImportApi(String handler) {
        testProjectRouting(this::uploadViaImporter, handler);
    }

    @Test(groups = SMOKE, dataProvider = IMPORT_PROVIDER)
    @AddedIn(Xnat_1_8_0.class)
    @Basic
    public void testSubjectRoutingImportApi(String handler) {
        testSubjectRouting(this::uploadViaImporter, handler);
    }

    @Test(groups = SMOKE, dataProvider = IMPORT_PROVIDER)
    @AddedIn(Xnat_1_8_0.class)
    @Basic
    public void testSessionRoutingImportApi(String handler) {
        testSessionRouting(this::uploadViaImporter, handler);
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
        mainAdminInterface().setProjectDicomRoutingConfig(projectCfg);
        mainAdminInterface().setSubjectDicomRoutingConfig("(0010,0010):Sample.*?([\\w]+)");
        mainAdminInterface().setSessionDicomRoutingConfig("(0008,0020):(.*)");

        // create destination project
        final Project project = new Project(expectedProject).prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
        mainInterface().createProject(project);

        // upload
        uploadViaDicomScp(Collections.emptyMap(), project);

        // check
        final Subject subject = new Subject(project, expectedSub);
        final ImagingSession session = new MRSession(project, subject, expectedSes);
        verifyImport(session);
        mainInterface().waitForAutoRun(session);
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingPatientComments() {
        new XnatDefaultRoutingTest(Tag.PatientComments, true, this::uploadViaDicomScp)
                .run();
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingStudyComments() {
        new XnatDefaultRoutingTest(Tag.StudyComments, true, this::uploadViaDicomScp)
                .tagsToClear(Tag.PatientComments)
                .run();
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingAddlPatHist() {
        new XnatDefaultRoutingTest(Tag.AdditionalPatientHistory, true, this::uploadViaDicomScp)
                .tagsToClear(Tag.StudyComments, Tag.PatientComments)
                .run();
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingStudyDesc() {
        new XnatDefaultRoutingTest(Tag.StudyDescription, false, this::uploadViaDicomScp)
                .tagsToClear(Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments)
                .run();
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingAccession() {
        new XnatDefaultRoutingTest(Tag.AccessionNumber, false, this::uploadViaDicomScp)
                .tagsToClear(Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments)
                .run();
    }

    @Test(dataProvider = IMPORT_PROVIDER)
    public void testXnatDefaultRoutingPatientCommentsImportApi(String handler) {
        new XnatDefaultRoutingTest(Tag.PatientComments, true, this::uploadViaImporter)
                .handler(handler)
                .run();
    }

    @Test(dataProvider = IMPORT_PROVIDER)
    public void testXnatDefaultRoutingStudyCommentsImportApi(String handler) {
        new XnatDefaultRoutingTest(Tag.StudyComments, true, this::uploadViaImporter)
                .handler(handler)
                .tagsToClear(Tag.PatientComments)
                .run();
    }

    @Test(dataProvider = IMPORT_PROVIDER)
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingAddlPatHistImportApi(String handler) {
        new XnatDefaultRoutingTest(Tag.AdditionalPatientHistory, true, this::uploadViaImporter)
                .handler(handler)
                .tagsToClear(Tag.StudyComments, Tag.PatientComments)
                .run();
    }

    @Test(dataProvider = IMPORT_PROVIDER)
    public void testXnatDefaultRoutingStudyDescImportApi(String handler) {
        new XnatDefaultRoutingTest(Tag.StudyDescription, false, this::uploadViaImporter)
                .handler(handler)
                .tagsToClear(Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments)
                .run();
    }

    @Test
    public void testXnatDefaultRoutingAccessionDicomZip() {
        new XnatDefaultRoutingTest(Tag.AccessionNumber, false, this::uploadViaImporter)
                .handler(DICOM_ZIP)
                .tagsToClear(Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments)
                .run();
    }

    @Test
    @ExpectedFailure(jiraIssue = "XNAT-6469")
    public void testXnatDefaultRoutingAccessionSessionImporter() {
        new XnatDefaultRoutingTest(Tag.AccessionNumber, false, this::uploadViaImporter)
                .handler(SI)
                .tagsToClear(Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments)
                .run();
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingAccessionSessionImporterAlt() {
        new XnatDefaultRoutingTest(Tag.AccessionNumber, false, this::uploadViaImporter)
                .handler(SI)
                .replaceVal("")
                .tagsToClear(Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments)
                .run();
    }

    private class XnatDefaultRoutingTest {
        int tag;
        boolean combined;
        UploadFn uploadFn;
        String handler;
        String replaceVal = "notPatternOrProject";
        File zipFile = testZip; // will be ignored for CSTORE uploads
        int[] tagsToClear = new int[]{};

        XnatDefaultRoutingTest(int tag, boolean combined, UploadFn uploadFn) {
            this.tag = tag;
            this.combined = combined;
            this.uploadFn = uploadFn;
        }

        XnatDefaultRoutingTest handler(String handler) {
            this.handler = handler;
            return this;
        }

        XnatDefaultRoutingTest replaceVal(String replaceVal) {
            this.replaceVal = replaceVal;
            return this;
        }

        XnatDefaultRoutingTest tagsToClear(int... tagsToClear) {
            this.tagsToClear = tagsToClear;
            return this;
        }

        void run() {
            final String listener = Long.toString(System.currentTimeMillis());
            final Project project = new Project(listener).prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);
            mainInterface().createProject(project);
            final Subject subject = new Subject(project, "su" + listener);
            final MRSession session = new MRSession(project, subject, "se" + listener);

            // update headers
            final Map<Integer, String> hdr = new HashMap<>();
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
            mainInterface().waitForAutoRun(session);
            restDriver.deleteProjectSilently(mainUser, project);
        }
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

        final Map<String, String> queryParams = new HashMap<>();
        if (handler != null) {
            queryParams.put("import-handler", handler);
        }
        if (sendProjectId) {
            queryParams.put("PROJECT_ID", project.getId());
        }
        mainQueryBase()
                .queryParams(queryParams)
                .multiPart(zip)
                .post(formatRestUrl("services/import"))
                .then().assertThat().statusCode(200);
        
        if (DICOM_ZIP.equals(handler)) {
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
                Attributes dcm;
                try (InputStream is = zf.getInputStream(ze)) {
                    dcm = DicomUtils.readDicom(is);
                }
                for (Integer tag : hdr.keySet()) {
                    VR vr = dcm.getVR(tag);
                    dcm.setString(tag, vr == null ? VR.LT : vr, hdr.get(tag));
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
        mainInterface().waitForAutoRun(session);
        restDriver.deleteProjectSilently(mainUser, p);
    }

    private void verifySubject(String expected) {
        final Subject subject = new Subject(project, expected);
        final ImagingSession session = new MRSession(project, subject, sessionFromTestZip);
        verifyImport(session);
        mainInterface().waitForAutoRun(session);
        mainInterface().deleteSubjectAssessor(session);
    }

    private void verifySession(String expected) {
        final Subject subject = new Subject(project, subjectFromTestZip);
        final ImagingSession session = new MRSession(project, subject, expected);
        verifyImport(session);
        mainInterface().waitForAutoRun(session);
        mainInterface().deleteSubjectAssessor(session);
    }

    private void testProjectRouting(ApiUploadFn uploadFn, String handler) {
        String rand = new SimpleDateFormat("mmssSSS").format(new Date()); // unique project id
        for (String key : cfgMapProject.keySet()) {
            String cfg = cfgMapProject.get(key).replaceAll(REPLACE_STR, rand);
            mainAdminInterface().setProjectDicomRoutingConfig(cfg);
            String expected = key.replaceAll(REPLACE_STR, rand);
            Project p = new Project(expected);
            mainInterface().createProject(p);
            uploadFn.accept(p, handler, false);
            verifyProject(p);
        }
    }

    private void testSubjectRouting(ApiUploadFn uploadFn, String handler) {
        for (String expected : cfgMap.keySet()) {
            mainAdminInterface().setSubjectDicomRoutingConfig(cfgMap.get(expected));
            uploadFn.accept(project, handler, true);
            verifySubject(expected);
        }
    }

    private void testSessionRouting(ApiUploadFn uploadFn, String handler) {
        for (String expected : cfgMap.keySet()) {
            mainAdminInterface().setSessionDicomRoutingConfig(cfgMap.get(expected));
            uploadFn.accept(project, handler, true);
            verifySession(expected);
        }
    }

    private void verifyImport(SubjectAssessor session) {
        // if session can be retrieved at this URL, then project, subject, session are all labelled properly
        TimeUtils.sleep(1000); // sleep for 1s to accommodate a little gap between prearchive being empty and session being accessible
        mainQueryBase().get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);
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