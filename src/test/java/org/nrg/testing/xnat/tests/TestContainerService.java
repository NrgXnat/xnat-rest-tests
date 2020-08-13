package org.nrg.testing.xnat.tests;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;
import org.apache.commons.io.IOUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.HardDependency;
import org.nrg.testing.annotations.SoftDependency;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xdat.om.*;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
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
public class TestContainerService extends BaseXnatRestTest {
    private static final String OUTPUT_CONTENT = "hello world";
    private static final String OUTPUT_FILENAME = "out.txt";
    private static final String DEBUG_IMG = "xnat/debug-command:latest";
    private static final String OUTPUT_RESOURCE = "DEBUG_OUTPUT";

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

        restDriver.createProject(mainUser, project);
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

        restDriver.createSessionAssessor(mainUser, assessor);

        // Sets accession number on subject, session, and assessor
        restDriver.interfaceFor(mainUser).getAccessionNumber(subject);
        restDriver.interfaceFor(mainUser).getAccessionNumber(session);
        assessor.setAccessionNumber(mainAdminCredentials()
                .get(restDriver.formatRestUrl("experiments", session.getAccessionNumber(), "assessors"))
                        .then().extract().jsonPath()
                        .getString("ResultSet.Result.find {it.label == '" + assessor.getLabel() + "' }.ID"));
    }

    @AfterMethod
    public void removeContainerServiceProjects() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @Test
    public void testDeleteAllImages() {
        final List<String> imageIds = mainAdminCredentials()
                .get(restDriver.formatXapiUrl("docker", "images"))
                .then().assertThat().statusCode(200).and().extract().jsonPath().getList("image-id");

        for (String id : imageIds) {
            mainAdminCredentials()
                    .delete(restDriver.formatXapiUrl("docker", "images", id))
                    .then().assertThat().statusCode(204);
        }

        final JsonPath commands = mainAdminCredentials()
                .queryParam("image", DEBUG_IMG)
                .get(restDriver.formatXapiUrl("commands"))
                .then().assertThat().statusCode(200).and().extract().jsonPath();

        assertEquals(0, commands.getList("$").size());
    }

    @Test
    @HardDependency("testDeleteAllImages")
    public void testPullImageWithCommand() {
        // Add new image with commands
        mainAdminCredentials().queryParam("image", DEBUG_IMG)
                .post(restDriver.formatXapiUrl("docker", "pull"))
                .then().assertThat().statusCode(200);

        final JsonPath commands = mainAdminCredentials()
                .queryParam("image", DEBUG_IMG)
                .get(restDriver.formatXapiUrl("commands"))
                .then().assertThat().statusCode(200).and().extract().jsonPath();

        assertEquals(1, commands.getList("$").size());
    }

    @Test
    @SoftDependency({"testDisableSwarmMode", "testContainerProject","testContainerSubject","testContainerSubjectAltUri","testContainerSession","testContainerSessionAltUri","testContainerAssessor","testContainerAssessorAltUri","testContainerAssessorAltUri2","testContainerScan","testContainerScanAltUri"})
    public void testDeleteImage() {
        final JsonPath images = mainAdminCredentials()
                .get(restDriver.formatXapiUrl("docker", "images"))
                .then().assertThat().statusCode(200).and().extract().jsonPath();

        assertEquals(1, images.getList("$").size());

        String id = images.getString("image-id.get(0)");

        mainAdminCredentials()
                .delete(restDriver.formatXapiUrl("docker", "images", id))
                .then().assertThat().statusCode(204);

        final JsonPath commands = mainAdminCredentials()
                .queryParam("image", DEBUG_IMG)
                .get(restDriver.formatXapiUrl("commands"))
                .then().assertThat().statusCode(200).and().extract().jsonPath();

        assertEquals(0, commands.getList("$").size());
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerProject() {
        enableAndRunContainerThenCheckOutputs(XnatProjectdata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/projects/%s", project.getId()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSubject() {
        enableAndRunContainerThenCheckOutputs(XnatSubjectdata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/subjects/%s", subject.getAccessionNumber()));
    }

    @Test(enabled = false)
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSubjectAltUri() {
        enableAndRunContainerThenCheckOutputs(XnatSubjectdata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/projects/%s/subjects/%s", project.getId(), subject.getLabel()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSession() {
        enableAndRunContainerThenCheckOutputs(XnatMrsessiondata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/experiments/%s", session.getAccessionNumber()));
    }

    @Test
    @TestRequires(plugins = "batchLaunchPlugin")
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSessionBulk() {
        MRSession session2 = new MRSession(project, subject, "MR2").date(LocalDate.parse("2000-01-02"));
        restDriver.createSubjectAssessor(mainUser, session2);
        restDriver.interfaceFor(mainUser).getAccessionNumber(session2);
        Map<String, String> uriToId = new HashMap<>();
        uriToId.put(String.format("/archive/experiments/%s", session.getAccessionNumber()), session.getAccessionNumber());
        uriToId.put(String.format("/archive/experiments/%s", session2.getAccessionNumber()), session2.getAccessionNumber());
        enableAndRunContainerThenCheckOutputs(XnatMrsessiondata.SCHEMA_ELEMENT_NAME, uriToId);
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerSessionAltUri() {
        enableAndRunContainerThenCheckOutputs(XnatMrsessiondata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/projects/%s/subjects/%s/experiments/%s",
                        project.getId(), subject.getLabel(), session.getLabel()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerAssessor() {
        enableAndRunContainerThenCheckOutputs(XnatQcassessmentdata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/experiments/%s/assessors/%s",
                        session.getAccessionNumber(), assessor.getAccessionNumber()));
    }

    @Test(enabled = false)
    @SoftDependency("testDisableSwarmMode")
    public void testContainerAssessorAltUri() {
        enableAndRunContainerThenCheckOutputs(XnatQcassessmentdata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/projects/%s/subjects/%s/experiments/%s/assessors/%s",
                        project.getId(), subject.getLabel(), session.getLabel(), assessor.getLabel()));
    }

    @Test(enabled = false)
    @SoftDependency("testDisableSwarmMode")
    public void testContainerAssessorAltUri2() {
        enableAndRunContainerThenCheckOutputs(XnatQcassessmentdata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/experiments/%s/assessors/%s",
                        session.getAccessionNumber(), assessor.getLabel()));
    }

    @Test(enabled = false)
    @SoftDependency("testDisableSwarmMode")
    public void testContainerAssessorAltUri3() {
        enableAndRunContainerThenCheckOutputs(XnatQcassessmentdata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/experiments/%s", assessor.getAccessionNumber()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerScan() {
        enableAndRunContainerThenCheckOutputs(XnatMrscandata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/experiments/%s/scans/%s", session.getAccessionNumber(), scan.getId()));
    }

    @Test
    @SoftDependency("testDisableSwarmMode")
    public void testContainerScanAltUri() {
        enableAndRunContainerThenCheckOutputs(XnatMrscandata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/projects/%s/subjects/%s/experiments/%s/scans/%s",
                        project.getId(), subject.getLabel(), session.getLabel(), scan.getId()));
    }

    @Test
    @TestRequires(csSwarmCanEnable = true)
    @HardDependency("testPullImageWithCommand")
    public void testEnableSwarmMode() {
        JsonPath server = toggleSwarmMode(true);
        assertTrue(server.getBoolean("swarm-mode"));
    }

    @Test
    @TestRequires(csSwarmCanEnable = true)
    @HardDependency("testEnableSwarmMode")
    public void testContainerSessionSwarm() {
        enableAndRunContainerThenCheckOutputs(XnatMrsessiondata.SCHEMA_ELEMENT_NAME,
                String.format("/archive/experiments/%s", session.getAccessionNumber()));
    }

    @Test
    @TestRequires(csSwarmCanEnable = true)
    @SoftDependency("testContainerSessionSwarm")
    public void testDisableSwarmMode() {
        JsonPath server = toggleSwarmMode(false);
        assertFalse(server.getBoolean("swarm-mode"));
    }

    private void enableAndRunContainerThenCheckOutputs(String xsiType, String uri) {
        WrapperInfo wi = getWrapperInfo(xsiType);

        enableOnProject(wi.wrapperId);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(wi.csType, uri);
        queryParams.put("command", "echo " + OUTPUT_CONTENT);
        queryParams.put("output-file", OUTPUT_FILENAME);

        String workflowId = mainQueryBase().body(queryParams)
                .contentType(ContentType.JSON)
                .post(restDriver.formatXapiUrl("projects", project.getId(), "wrappers",
                        wi.wrapperId, "root", wi.csType, "launch"))
                .then().assertThat().statusCode(200).and().extract().jsonPath().getString("workflow-id");

        waitForWorkflowComplete(workflowId);

        verifyOutputs(uri);
    }

    private void enableAndRunContainerThenCheckOutputs(String xsiType, Map<String, String> urisAndIds) {
        WrapperInfo wi = getWrapperInfo(xsiType);

        enableOnProject(wi.wrapperId);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(wi.csType,  "[\"" + String.join("\",\"", urisAndIds.keySet()) + "\"]");
        queryParams.put("command", "echo " + OUTPUT_CONTENT);
        queryParams.put("output-file", OUTPUT_FILENAME);

        JsonPath result = mainQueryBase().body(queryParams)
                .contentType(ContentType.JSON)
                .post(restDriver.formatXapiUrl("projects", project.getId(), "wrappers",
                        wi.wrapperId, "root", wi.csType, "bulklaunch"))
                .then().assertThat().statusCode(200).and().extract().jsonPath();

        // assert that these were successfully queued
        assertEquals(result.getList("successes").size(), urisAndIds.size());

        for (String uri : urisAndIds.keySet()) {
            String id = urisAndIds.get(uri);
            Map<String, Object> params = new HashMap<>();
            params.put("data_type", xsiType);
            params.put("id", id);
            params.put("sort_col", "launchTime");
            params.put("sort_dir", "desc");
            params.put("page", "1");
            params.put("filters", makeFilterMap(wi.wrapperName));

            final long start = System.currentTimeMillis();
            String workflowId;
            do {
                TimeUtils.sleep(1000); // give the thread time to submit these
                workflowId = mainQueryBase().body(params)
                        .contentType(ContentType.JSON)
                        .post(restDriver.formatXapiUrl("workflows"))
                        .then().assertThat().statusCode(200).and().extract().jsonPath().getString("wfid.get(0)");
            } while (workflowId == null && System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(5));

            assertNotNull(workflowId);
            waitForWorkflowComplete(workflowId);
        }

        // check output
        for (String uri : urisAndIds.keySet()) {
            verifyOutputs(uri);
        }
    }

    private Map<String, Object> makeFilterMap(String wrapperName) {
        Map<String, String> filterVals = new HashMap<>();
        filterVals.put("like", wrapperName);
        filterVals.put("type", "string");
        Map<String, Object> filters = new HashMap<>();
        filters.put("pipelineName", filterVals);
        return filters;
    }

    private WrapperInfo getWrapperInfo(String xsiType) {
        JsonPath json = mainQueryBase().queryParam("project", project.getId())
                .queryParam("xsiType", xsiType)
                .get(restDriver.formatXapiUrl("commands", "available"))
                .then().assertThat().statusCode(200).and().extract().jsonPath();

        String wrapperId = json.getString("wrapper-id.get(0)");
        String wrapperName = json.getString("wrapper-name.get(0)");
        String csType = json.getString("root-element-name.get(0)");
        return new WrapperInfo(wrapperId, wrapperName, csType);
    }

    private void enableOnProject(String wrapperId) {
        mainQueryBase().put(restDriver.formatXapiUrl("projects", project.getId(), "wrappers", wrapperId, "enabled"))
                .then().assertThat().statusCode(200);
    }

    private void waitForWorkflowComplete(String workflowId) {
        final long start = System.currentTimeMillis();
        String status;
        do {
            TimeUtils.sleep(1000);
            status = mainQueryBase().given().queryParams("format", "json")
                    .get(restDriver.formatRestUrl("workflows", workflowId))
                    .jsonPath().getString("items.get(0).data_fields.status");
        } while (!(PersistentWorkflowUtils.COMPLETE.equals(status) || status.startsWith(PersistentWorkflowUtils.FAILED)) &&
                System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(15));

        assertEquals(PersistentWorkflowUtils.COMPLETE, status);
    }

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

    private JsonPath toggleSwarmMode(boolean enable) {
        String serverJson = mainAdminCredentials().get(restDriver.formatXapiUrl("docker", "server"))
                .then().assertThat().statusCode(200).and().extract().asString();

        //TODO this is a hack, we should deserialize as POJO but then we add a CS dep
        String serverJsonSwarm = serverJson.replace("\"swarm-mode\":" + !enable,
                "\"swarm-mode\":" + enable)
                .replaceAll("\"id\":[0-9]*,", "");

        return mainAdminCredentials().content(serverJsonSwarm).contentType(ContentType.JSON)
                .post(restDriver.formatXapiUrl("docker", "server"))
                .then().assertThat().statusCode(201).and().extract().jsonPath();
    }

    public static class WrapperInfo {
        String wrapperId;
        String wrapperName;
        String csType;

        public WrapperInfo(String wrapperId, String wrapperName, String csType) {
            this.wrapperId = wrapperId;
            this.wrapperName = wrapperName;
            this.csType = csType;
        }
    }
}
