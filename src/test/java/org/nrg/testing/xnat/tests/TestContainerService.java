package org.nrg.testing.xnat.tests;

import com.jayway.restassured.http.ContentType;
import org.apache.commons.io.IOUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.*;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.versions.Xnat_1_7_7;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.containers.CommandSummaryForContext;
import org.nrg.xnat.pogo.containers.DockerServer;
import org.nrg.xnat.pogo.containers.Image;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.extensions.session_assessor.SessionAssessorXMLExtension;
import org.nrg.xnat.pogo.resources.*;
import org.testng.annotations.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.testng.AssertJUnit.*;

@TestRequires(plugins = "containers")
@AddedIn(Xnat_1_7_7.class) // Pending CS-600
public class TestContainerService extends BaseXnatRestTest {
    private static final String OUTPUT_CONTENT = "hello world";
    private static final String OUTPUT_FILENAME = "out.txt";
    private static final Image DEBUG_IMG = new Image("xnat", "debug-command", "latest");
    private static final String OUTPUT_RESOURCE = "DEBUG_OUTPUT";
    private static final String IMAGES_WITH_COMMANDS_JSON_PATH = "findAll { it.commands.size() > 0 }";
    private static final Map<String, String> BASE_DEBUG_LAUNCH_PARAMS = makeContainerLaunchReqBody();

    private Project project;
    private Subject subject;
    private ImagingSession session;
    private Scan scan;
    private SessionAssessor assessor;

    @BeforeMethod
    public void setupContainerServiceTest() {
        // setup objects
        project  = testSpecificProject;
        subject  = new Subject(project, "S1").gender(Gender.MALE);
        session  = new MRSession(project, subject, "MR1").date(LocalDate.parse("2000-01-01"));
        scan     = new MRScan(session, "1").type("T1").seriesDescription("T1").quality("usable");
        // add file to scan so there's something to mount
        final File dcmFile = getDataFile("mr_1/1.dcm");
        new ScanResource(project, subject, session, scan).folder("DICOM")
                .addResourceFile(new ResourceFile().name(dcmFile.getName())
                        .extension(new SimpleResourceFileExtension(dcmFile)));

        mainInterface().createProject(project);
        TimeUtils.sleep(1000); // cache update

        // setup assessor
        assessor = new ManualQC(project, subject, session)
                .extension(new SessionAssessorXMLExtension(restDriver.interfaceFor(mainUser),
                        getDataFile("test_asst_v1.xml")));

        // add file to assessor so there's something to mount
        final File dummyFile = getDataFile("dummy.txt");
        new SessionAssessorResource(project, subject, session, assessor, "TEST")
                .addResourceFile(new ResourceFile().name(dummyFile.getName())
                        .extension(new SimpleResourceFileExtension(dummyFile)));

        mainInterface().createSessionAssessor(assessor);

        // Sets accession number on subject, session, and assessor
        mainInterface().getAccessionNumber(subject);
        mainInterface().getAccessionNumber(session);
        assessor.setAccessionNumber(mainInterface().jsonQuery()
                .get(mainInterface().assessorsUrlByAccessionNumber(session))
                .then().assertThat().statusCode(200).and().extract().jsonPath()
                .getString("ResultSet.Result.find {it.label == '" + assessor.getLabel() + "' }.ID"));
    }

    @AfterMethod
    public void removeContainerServiceProjects() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @Test
    public void testDeleteAllImages() {
        final List<Image> imagesWithCommands = mainAdminInterface().readImages(IMAGES_WITH_COMMANDS_JSON_PATH);

        for (Image image : imagesWithCommands) {
            mainAdminInterface().deleteImage(image);
        }

        assertEquals(0, mainAdminInterface().readCommands(DEBUG_IMG).size());
    }

    @Test
    @HardDependency("testDeleteAllImages")
    public void testPullImageWithCommand() {
        // Add new image with commands
        assertEquals(
                1,
                mainAdminInterface().pullImage(DEBUG_IMG).readCommands(DEBUG_IMG).size()
        );
    }

    @Test
    @SoftDependency({"testDisableSwarmMode", "testContainerProject", "testContainerSubject", "testContainerSubjectAltUri", "testContainerSession", "testContainerSessionAltUri", "testContainerAssessor", "testContainerAssessorAltUri", "testContainerAssessorAltUri2", "testContainerScan", "testContainerScanAltUri"})
    public void testDeleteImage() {
        final List<Image> imagesWithCommands = mainAdminInterface().readImages(IMAGES_WITH_COMMANDS_JSON_PATH);
        assertEquals(1, imagesWithCommands.size());
        mainAdminInterface().deleteImage(imagesWithCommands.get(0));
        assertEquals(0, mainAdminInterface().readCommands(DEBUG_IMG).size());
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerProject() {
        enableAndRunContainerThenCheckOutputs(DataType.PROJECT,
                String.format("/archive/projects/%s", project.getId()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSubject() {
        enableAndRunContainerThenCheckOutputs(DataType.SUBJECT,
                String.format("/archive/subjects/%s", subject.getAccessionNumber()));
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSubjectAltUri() {
        enableAndRunContainerThenCheckOutputs(DataType.SUBJECT,
                String.format("/archive/projects/%s/subjects/%s", project.getId(), subject.getLabel()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSession() {
        enableAndRunContainerThenCheckOutputs(DataType.MR_SESSION,
                String.format("/archive/experiments/%s", session.getAccessionNumber()));
    }

    @Test
    @TestRequires(plugins = "batchLaunchPlugin")
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSessionBulk() {
        final MRSession session2 = new MRSession(project, subject, "MR2").date(LocalDate.parse("2000-01-02"));
        mainInterface().createSubjectAssessor(session2);
        mainInterface().getAccessionNumber(session2);
        final Map<String, String> uriToId = new HashMap<>();
        uriToId.put(String.format("/archive/experiments/%s", session.getAccessionNumber()), session.getAccessionNumber());
        uriToId.put(String.format("/archive/experiments/%s", session2.getAccessionNumber()), session2.getAccessionNumber());
        enableAndRunContainerThenCheckOutputs(DataType.MR_SESSION, uriToId);
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSessionAltUri() {
        enableAndRunContainerThenCheckOutputs(DataType.MR_SESSION,
                String.format("/archive/projects/%s/subjects/%s/experiments/%s",
                        project.getId(), subject.getLabel(), session.getLabel()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerAssessor() {
        enableAndRunContainerThenCheckOutputs(DataType.QC,
                String.format("/archive/experiments/%s/assessors/%s",
                        session.getAccessionNumber(), assessor.getAccessionNumber()));
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    @SoftDependency("testDisableSwarmMode")
    public void testContainerAssessorAltUri() {
        enableAndRunContainerThenCheckOutputs(DataType.QC,
                String.format("/archive/projects/%s/subjects/%s/experiments/%s/assessors/%s",
                        project.getId(), subject.getLabel(), session.getLabel(), assessor.getLabel()));
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    @SoftDependency("testDisableSwarmMode")
    public void testContainerAssessorAltUri2() {
        enableAndRunContainerThenCheckOutputs(DataType.QC,
                String.format("/archive/experiments/%s/assessors/%s",
                        session.getAccessionNumber(), assessor.getLabel()));
    }

    @Test
    @AddedIn(Xnat_1_8_0.class)
    @SoftDependency("testDisableSwarmMode")
    public void testContainerAssessorAltUri3() {
        enableAndRunContainerThenCheckOutputs(DataType.QC,
                String.format("/archive/experiments/%s", assessor.getAccessionNumber()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerScan() {
        enableAndRunContainerThenCheckOutputs(DataType.MR_SCAN,
                String.format("/archive/experiments/%s/scans/%s", session.getAccessionNumber(), scan.getId()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerScanAltUri() {
        enableAndRunContainerThenCheckOutputs(DataType.MR_SCAN,
                String.format("/archive/projects/%s/subjects/%s/experiments/%s/scans/%s",
                        project.getId(), subject.getLabel(), session.getLabel(), scan.getId()));
    }

    @Test
    @TestRequires(csSwarmCanEnable = true)
    @HardDependency("testPullImageWithCommand")
    public void testEnableSwarmMode() {
        toggleSwarmMode(true);
        assertTrue(mainInterface().readDockerServer().getSwarmMode());
    }

    @Test
    @TestRequires(csSwarmCanEnable = true)
    @HardDependency("testEnableSwarmMode")
    public void testContainerSessionSwarm() {
        enableAndRunContainerThenCheckOutputs(DataType.MR_SESSION,
                String.format("/archive/experiments/%s", session.getAccessionNumber()), true);
    }

    @Test
    @TestRequires(csSwarmCanEnable = true)
    @SoftDependency("testContainerSessionSwarm")
    public void testDisableSwarmMode() {
        toggleSwarmMode(false);
        assertFalse(mainInterface().readDockerServer().getSwarmMode());
    }

    private void enableAndRunContainerThenCheckOutputs(DataType dataType, String uri) {
        enableAndRunContainerThenCheckOutputs(dataType, uri, false);
    }

    private void enableAndRunContainerThenCheckOutputs(DataType dataType, String uri, boolean swarm) {
        final CommandSummaryForContext wrapper = readWrapper(dataType);
        mainInterface().setWrapperStatusOnProject(wrapper, project, true);
        final int workflowId = mainInterface().launchContainer(project, wrapper, uri, BASE_DEBUG_LAUNCH_PARAMS);
        waitForWorkflowComplete(workflowId, swarm);
        verifyOutputs(uri);
    }

    private void enableAndRunContainerThenCheckOutputs(DataType dataType, Map<String, String> urisAndIds) {
        enableAndRunContainerThenCheckOutputs(dataType, urisAndIds, false);
    }

    private void enableAndRunContainerThenCheckOutputs(DataType dataType, Map<String, String> urisAndIds, boolean swarm) {
        final CommandSummaryForContext wrapper = mainInterface().readAvailableCommands(dataType, project).get(0);
        mainInterface().setWrapperStatusOnProject(wrapper, project, true);
        mainInterface().bulkLaunchContainers(project, wrapper, urisAndIds.keySet(), BASE_DEBUG_LAUNCH_PARAMS);

        // Determine workflow ID, wait for complete, verify outputs
        for (Map.Entry<String, String> uriAndId : urisAndIds.entrySet()) {
            final int workflowId = determineWorkflowId(dataType, uriAndId.getValue(), wrapper);
            waitForWorkflowComplete(workflowId, swarm);
            verifyOutputs(uriAndId.getKey());
        }
    }

    private CommandSummaryForContext readWrapper(DataType dataType) {
        return mainInterface().readAvailableCommands(dataType, project).get(0);
    }

    private int determineWorkflowId(DataType dataType, String id, CommandSummaryForContext wrapper) {
        final Map<String, Object> params = new HashMap<>();
        params.put("data_type", dataType.getXsiType());
        params.put("id", id);
        params.put("sort_col", "launchTime");
        params.put("sort_dir", "desc");
        params.put("page", "1");
        params.put("filters", makeFilterMap(wrapper.getWrapperName()));

        final long start = System.currentTimeMillis();
        int workflowId;
        do {
            TimeUtils.sleep(1000); // give the thread time to submit these
            workflowId = mainQueryBase().body(params)
                    .contentType(ContentType.JSON)
                    .post(restDriver.formatXapiUrl("workflows"))
                    .then().assertThat().statusCode(200).and().extract().jsonPath().getInt("wfid.get(0)");
        } while (workflowId == 0 && System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(5));

        assertTrue(workflowId > 0);
        return workflowId;
    }

    private Map<String, Object> makeFilterMap(String wrapperName) {
        final Map<String, String> filterVals = new HashMap<>();
        filterVals.put("like", wrapperName);
        if (XnatTestingVersionManager.testedVersionPrecedes(Xnat_1_8_0.class)) {
            filterVals.put("type", "string");
        } else {
            filterVals.put("backend", "sql_string");
        }
        return Collections.singletonMap("pipelineName", filterVals);
    }

    private void waitForWorkflowComplete(int workflowId, boolean swarm) {
        mainInterface().waitForWorkflowComplete(workflowId, 60 * (swarm ? Settings.CS_SWARM_TIMEOUT : 5));
    }

    @Deprecated
    private void verifyOutputs(String uri) {
        byte[] zipBytes = mainQueryBase().queryParam("format", "zip")
                .get(restDriver.formatRestUrl(uri, "resources", "DEBUG_OUTPUT", "files"))
                .then().assertThat().statusCode(200).and().extract().asByteArray();

        try {
            int count = 0;
            String name = "";
            String content = "";
            try (ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry zipEntry;
                while ((zipEntry = zi.getNextEntry()) != null) {
                    count++;
                    name = zipEntry.getName();
                    content = IOUtils.toString(zi, StandardCharsets.UTF_8.name());
                }
            }

            assertEquals(1, count);
            assertTrue(Paths.get(name).endsWith(Paths.get("resources", OUTPUT_RESOURCE,
                    "files", OUTPUT_FILENAME)));
            assertEquals(OUTPUT_CONTENT, content.trim());
        } catch (IOException e) {
            fail("Exception thrown trying to unzip and read resource " + OUTPUT_RESOURCE +
                    " of " + uri + ": " + e.getMessage());
        }
    }

    private void toggleSwarmMode(boolean enable) {
        final DockerServer dockerServer = mainAdminInterface().readDockerServer();
        dockerServer.setSwarmMode(enable);
        mainAdminInterface().updateDockerServer(dockerServer);
    }

    private static Map<String, String> makeContainerLaunchReqBody() {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("command", "echo " + OUTPUT_CONTENT);
        queryParams.put("output-file", OUTPUT_FILENAME);
        return queryParams;
    }

}
