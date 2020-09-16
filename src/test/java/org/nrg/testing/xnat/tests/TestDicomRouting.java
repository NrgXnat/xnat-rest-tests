package org.nrg.testing.xnat.tests;

import com.jayway.restassured.specification.RequestSpecification;
import org.apache.log4j.Logger;
import org.dcm4che2.data.Tag;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.testng.annotations.*;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestDicomRouting extends BaseXnatRestTest {

    private final Project project = new Project().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
    private final File testZip = getDataFile("mr_1.zip");
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
        restDriver.createProject(mainUser, project);
        restDriver.disableSiteAnonScript(mainAdminUser);
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
            restDriver.clearProject(mainUser, project);
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

    @AfterClass(alwaysRun = true)
    public void tearDownImportTests() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
        restDriver.enableSiteAnonScript(mainAdminUser);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testProjectRoutingSessionImporter() {
        testProjectRouting(this::uploadViaSessionImporter);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testProjectRoutingDicomZip() {
        testProjectRouting(this::uploadViaDicomZip);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSubjectRoutingSessionImporter() {
        testSubjectRouting(this::uploadViaSessionImporter);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSubjectRoutingDicomZip() {
        testSubjectRouting(this::uploadViaDicomZip);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSessionRoutingSessionImporter() {
        testSessionRouting(this::uploadViaSessionImporter);
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    public void testSessionRoutingDicomZip() {
        testSessionRouting(this::uploadViaDicomZip);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    @AddedIn(Xnat_1_8_0.class)
    public void testCombinedDicomScp() {
        restDriver.interfaceFor(mainAdminUser).setSessionXmlRebuilderTimes(1, 10000);
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
        restDriver.createProject(mainUser, project);

        // upload
        new XnatCStore().data(TestData.SAMPLE_1_SCAN_4).sendDICOM();
        TimeUtils.sleep(60000);
        restDriver.waitForPrearchiveEmpty(mainUser, project, 120);

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
        testXnatDefaultRouting(Tag.PatientComments, true);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingStudyComments() {
        testXnatDefaultRouting(Tag.StudyComments, true, Tag.PatientComments);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    @AddedIn(Xnat_1_8_0.class)
    public void testXnatDefaultRoutingAddlPatHist() {
        testXnatDefaultRouting(Tag.AdditionalPatientHistory, true, Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingStudyDesc() {
        testXnatDefaultRouting(Tag.StudyDescription, false,
                Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testXnatDefaultRoutingAccession() {
        testXnatDefaultRouting(Tag.AccessionNumber, false,
                Tag.StudyDescription, Tag.AdditionalPatientHistory, Tag.StudyComments, Tag.PatientComments);
    }

    private void testXnatDefaultRouting(int tag, boolean combined, int... tagsToClear) {
        final String listener = Long.toString(System.currentTimeMillis());
        final Project project = new Project("project" + listener).prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);
        restDriver.createProject(mainUser, project);
        final Subject   subject = new Subject(project, "subject" + listener);
        final MRSession session = new MRSession(project, subject, "session" + listener);

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
            hdr.put(t, "junkThatDoesntMatchAProject");
        }

        // upload
        new XnatCStore().data(TestData.SAMPLE_1_SCAN_4).overwrittenHeaders(hdr).sendDICOM();
        TimeUtils.sleep(60000);
        restDriver.waitForPrearchiveEmpty(mainUser, project, 120);

        // verify
        verifyImport(session);
        restDriver.waitForAutoRun(session);
        restDriver.deleteProjectSilently(mainUser, project);
    }

    private void uploadViaSessionImporter(@Nullable String projectId) {
        RequestSpecification request = mainCredentials().
                queryParam("triggerPipelines", false);
        if (projectId != null) {
            request.queryParam("PROJECT_ID", projectId);
        }
        request.multiPart(testZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);
    }

    private void uploadViaDicomZip(@Nullable String projectId) {
        RequestSpecification request = mainCredentials()
                .queryParam("triggerPipelines", false)
                .queryParam("handler", "DICOM-zip");
        if (projectId != null) {
            request.queryParam("PROJECT_ID", projectId);
        }
        request.multiPart(testZip)
                .post(formatRestUrl("services/import"))
                .then().assertThat().statusCode(200);
    }

    private void verifyProject(Project p) {
        final Subject subject = new Subject(p, subjectFromTestZip);
        final ImagingSession session = new MRSession(p, subject, sessionFromTestZip);
        verifyImport(session);
        restDriver.deleteProjectSilently(mainUser, p);
    }

    private void verifySubject(String expected) {
        final Subject subject = new Subject(project, expected);
        final ImagingSession session = new MRSession(project, subject, sessionFromTestZip);
        verifyImport(session);
        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    private void verifySession(String expected) {
        final Subject subject = new Subject(project, subjectFromTestZip);
        final ImagingSession session = new MRSession(project, subject, expected);
        verifyImport(session);
        restDriver.deleteSubjectAssessor(mainUser, session);
    }

    private void testProjectRouting(Consumer<String> uploadFn) {
        // Because XNAT cannot delete and readd a project with the same ID, we have to include a random component in
        // the project id. Hopefully 0-99 is sufficient to ensure no conflicts within a given test run/rerun.
        String rand = Integer.toString(new Random().nextInt(100));
        for (String key : cfgMapProject.keySet()) {
            String cfg = cfgMapProject.get(key).replaceAll(REPLACE_STR, rand);
            restDriver.setProjectDicomRoutingConfig(cfg);
            String expected = key.replaceAll(REPLACE_STR, rand);
            Project p = new Project(expected);
            restDriver.createProject(mainUser, p);
            uploadFn.accept(null);
            verifyProject(p);
        }
    }

    private void testSubjectRouting(Consumer<String> uploadFn) {
        for (String expected : cfgMap.keySet()) {
            restDriver.setSubjectDicomRoutingConfig(cfgMap.get(expected));
            uploadFn.accept(project.getId());
            verifySubject(expected);
        }
    }

    private void testSessionRouting(Consumer<String> uploadFn) {
        for (String expected : cfgMap.keySet()) {
            restDriver.setSessionDicomRoutingConfig(cfgMap.get(expected));
            uploadFn.accept(project.getId());
            verifySession(expected);
        }
    }

    private void verifyImport(SubjectAssessor session) {
        // if session can be retrieved at this URL, then project, subject, session are all labelled properly
        mainCredentials().get(restDriver.subjectAssessorUrl(session)).then().assertThat().statusCode(200);
    }
}