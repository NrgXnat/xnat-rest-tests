package org.nrg.testing.xnat.tests;

import lombok.extern.slf4j.Slf4j;
import org.nrg.testing.xnat.BaseXnatTest;
import org.nrg.testing.xnat.containers.ContainerTestUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.*;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.testing.xnat.processing.files.resources.GenericResource;
import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.containers.Backend;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Version;
import org.nrg.xnat.versions.Xnat_1_7_7;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.containers.CommandSummaryForContext;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.extensions.session_assessor.SessionAssessorXMLExtension;
import org.nrg.xnat.pogo.resources.*;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.nrg.xnat.versions.Xnat_1_9_2;
import org.testng.annotations.*;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

import static org.nrg.testing.TestGroups.CONTAINERS;
import static org.nrg.testing.TestGroups.WORKFLOWS;
import static org.testng.AssertJUnit.*;

@Slf4j
@AddedIn(Xnat_1_7_7.class) // Pending CS-600
@Test(groups = {CONTAINERS, WORKFLOWS}, dataProvider = BaseXnatTest.CS_BACKENDS_DATA_PROVIDER)
@TestRequires(plugins = PluginRegistry.CS_PLUGIN_ID)
public class TestContainerService extends BaseContainerTest {

    private Project project;
    private Subject subject;
    private ImagingSession session;
    private Scan scan;
    private SessionAssessor assessor;

    @BeforeClass
    private void setupCommands() {
        containerManagerInterface .deleteAllCommands();
        if (getPluginVersion(PluginRegistry.CS_PLUGIN_ID).lessThan(new Version("3.2"))) {
            ContainerTestUtils.setServerBackend(this, Settings.CS_PREFERRED_BACKEND, containerManagerInterface);
            containerManagerInterface .pullImage(ContainerTestUtils.DEBUG_IMG, false);
        }
        containerManagerInterface .addCommand(getDataFile(
                XnatTestingVersionManager.testedVersionPrecedes(Xnat_1_8_0.class) ? "debug_command_1.5.json" : "debug_command.json"
        ));
    }

    @BeforeMethod
    private void setupContainerServiceTest(Object[] backendHolder) {
        // setup objects
        project = testSpecificProject;
        subject = new Subject(project, "S1").gender(Gender.MALE);
        session = new MRSession(project, subject, "MR1").date(LocalDate.parse("2000-01-01"));
        scan = new MRScan(session, "1").type("T1").seriesDescription("T1").quality("usable");
        // add file to scan so there's something to mount
        final File dcmFile = getDataFile("mr_1/1.dcm");
        new ScanResource(project, subject, session, scan).folder("DICOM")
                .addResourceFile(new ResourceFile().name(dcmFile.getName())
                        .extension(new SimpleResourceFileExtension(dcmFile)));

        mainInterface().createProject(project);
        TimeUtils.sleep(1000); // cache update

        // setup assessor
        assessor = new ManualQC(project, subject, session)
                .extension(new SessionAssessorXMLExtension(getDataFile("test_asst_v1.xml")));

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

        final Backend backend = (Backend) backendHolder[0];
        log.info("Setting backend {}", backend);
        ContainerTestUtils.setServerBackend(this, backend, containerManagerInterface);
    }

    @AfterMethod
    private void removeContainerServiceProjects() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    public void testContainerProject(final Backend backend) {
        new ContainerTest()
                .uri("/archive/projects/" + project.getId())
                .run(mainInterface(), project, DataType.PROJECT, backend);
    }

    public void testContainerSubject(final Backend backend) {
        new ContainerTest()
                .uri("/archive/subjects/" + subject.getAccessionNumber())
                .run(mainInterface(), project, DataType.SUBJECT, backend);
    }


    @AddedIn(Xnat_1_8_0.class)
    public void testContainerSubjectAltUri(final Backend backend) {
        new ContainerTest()
                .uri(String.format("/archive/projects/%s/subjects/%s", project.getId(), subject.getLabel()))
                .run(mainInterface(), project, DataType.SUBJECT, backend);
    }

    public void testContainerSession(final Backend backend) {
        new ContainerTest()
                .uri("/archive/experiments/" + session.getAccessionNumber())
                .run(mainInterface(), project, DataType.MR_SESSION, backend);
    }

    @TestRequires(plugins = "batchLaunchPlugin")
    public void testContainerSessionBulk(final Backend backend) {
        final MRSession session2 = new MRSession(project, subject, "MR2").date(LocalDate.parse("2000-01-02"));
        mainInterface().createSubjectAssessor(session2);
        mainInterface().getAccessionNumber(session2);
        final Map<String, String> uriToId = new HashMap<>();
        uriToId.put(String.format("/archive/experiments/%s", session.getAccessionNumber()), session.getAccessionNumber());
        uriToId.put(String.format("/archive/experiments/%s", session2.getAccessionNumber()), session2.getAccessionNumber());

        new ContainerTest()
                .urisAndIds(uriToId)
                .run(mainInterface(), project, DataType.MR_SESSION, backend);
    }

    public void testContainerSessionAltUri(final Backend backend) {
        new ContainerTest()
                .uri(String.format("/archive/projects/%s/subjects/%s/experiments/%s", project.getId(), subject.getLabel(), session.getLabel()))
                .run(mainInterface(), project, DataType.MR_SESSION, backend);
    }

    public void testContainerAssessor(final Backend backend) {
        new ContainerTest()
                .uri(String.format("/archive/experiments/%s/assessors/%s", session.getAccessionNumber(), assessor.getAccessionNumber()))
                .run(mainInterface(), project, DataType.QC, backend);
    }

    @AddedIn(Xnat_1_8_0.class)
    public void testContainerAssessorAltUri(final Backend backend) {
        new ContainerTest()
                .uri(String.format("/archive/projects/%s/subjects/%s/experiments/%s/assessors/%s",
                        project.getId(), subject.getLabel(), session.getLabel(), assessor.getLabel()))
                .run(mainInterface(), project, DataType.QC, backend);
    }

    @AddedIn(Xnat_1_8_0.class)
    public void testContainerAssessorAltUri2(final Backend backend) {
        new ContainerTest()
                .uri(String.format("/archive/experiments/%s/assessors/%s", session.getAccessionNumber(), assessor.getLabel()))
                .run(mainInterface(), project, DataType.QC, backend);
    }

    @AddedIn(Xnat_1_8_0.class)
    public void testContainerAssessorAltUri3(final Backend backend) {
        new ContainerTest()
                .uri("/archive/experiments/" + assessor.getAccessionNumber())
                .run(mainInterface(), project, DataType.QC, backend);
    }

    public void testContainerScan(final Backend backend) {
        new ContainerTest()
                .uri(String.format("/archive/experiments/%s/scans/%s", session.getAccessionNumber(), scan.getId()))
                .run(mainInterface(), project, DataType.MR_SCAN, backend);
    }

    public void testContainerScanAltUri(final Backend backend) {
        new ContainerTest()
                .uri(String.format("/archive/projects/%s/subjects/%s/experiments/%s/scans/%s",
                        project.getId(), subject.getLabel(), session.getLabel(), scan.getId()))
                .run(mainInterface(), project, DataType.MR_SCAN, backend);
    }



}
