package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.dicom.DicomObjectIdentifier;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.versions.Xnat_1_10_1;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.restassured.http.ContentType.JSON;
import static org.nrg.testing.TestGroups.*;
import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.AssertJUnit.*;


/**
 * Tests for XNAT-6990, per-receiver routing rules.
 * <p>
 * (0014,0046) is MaterialNotes, VR=LT. Used for per-receiver routing rule.
 */
@AddedIn(Xnat_1_8_5.class)
@TestRequires(admin = true, data = TestData.SIMPLE_PET)
@Test(groups = {DICOM_SCP, DICOM_ROUTING, IMPORTER})
public class TestDicomSCPRoutingExpressions extends BaseXnatRestTest {
    private Project project;
    private static final Logger LOG = Logger.getLogger(TestDicomSCPRoutingExpressions.class);

    private final DicomScpReceiver allRouteReceiver
            = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
            .host(Settings.DICOM_HOST)
            .port(Settings.DICOM_PORT)
            .identifier(DicomObjectIdentifier.DEFAULT.getId())
            .enabled(true)
            .customProcessing(false)
            .directArchive(true)
            .anonymizationEnabled(true)
            .whitelistEnabled(false)
            .routingExpressionsEnabled(false)
            .projectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):1")
            .subjectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):2")
            .sessionRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):3");

    private final DicomScpReceiver simpleRouteReceiver
            = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
            .host(Settings.DICOM_HOST)
            .port(Settings.DICOM_PORT)
            .identifier("dicomObjectIdentifier")
            .enabled(true)
            .customProcessing(false)
            .directArchive(true)
            .anonymizationEnabled(true)
            .whitelistEnabled(false)
            .routingExpressionsEnabled(false)
            .projectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):1")
            .subjectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):2")
            .sessionRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):3");

    private final DicomScpReceiver someRoutesReceiver
            = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
            .host(Settings.DICOM_HOST)
            .port(Settings.DICOM_PORT)
            .identifier("dicomObjectIdentifier")
            .enabled(true)
            .customProcessing(false)
            .directArchive(true)
            .anonymizationEnabled(true)
            .whitelistEnabled(false)
            .routingExpressionsEnabled(false)
            .projectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):1")
            .subjectRoutingExpression(null)
            .sessionRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):3");

    private final DicomScpReceiver unknownIdentifierReceiver
            = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
            .host(Settings.DICOM_HOST)
            .port(Settings.DICOM_PORT)
            .identifier("unknownIdentifier")
            .enabled(true)
            .customProcessing(false)
            .directArchive(true)
            .anonymizationEnabled(true)
            .whitelistEnabled(false)
            .routingExpressionsEnabled(false)
            .projectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):1")
            .subjectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):2")
            .sessionRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):3");

    private final DicomScpReceiver highGroupRouteReceiver
            = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
            .host(Settings.DICOM_HOST)
            .port(Settings.DICOM_PORT)
            .identifier(DicomObjectIdentifier.DEFAULT.getId())
            .enabled(true)
            .customProcessing(false)
            .directArchive(true)
            .anonymizationEnabled(true)
            .whitelistEnabled(false)
            .routingExpressionsEnabled(false)
            .projectRoutingExpression("(F215,1050):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):1")
            .subjectRoutingExpression("(F215,1050):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):2")
            .sessionRoutingExpression("(F215,1050):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):3");

    private final DicomScpReceiver dqrReceiver
            = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
            .host(Settings.DICOM_HOST)
            .port(Settings.DICOM_PORT)
            .identifier("dqrObjectIdentifier")
            .enabled(true)
            .customProcessing(false)
            .directArchive(true)
            .anonymizationEnabled(true)
            .whitelistEnabled(false)
            .routingExpressionsEnabled(false)
            .projectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):1")
            .subjectRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):2")
            .sessionRoutingExpression("(0014,0046):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):3");

    @BeforeClass
    private void setup() {
        mainAdminInterface().createDicomScpReceiver(allRouteReceiver);
        mainAdminInterface().createDicomScpReceiver(someRoutesReceiver);
        mainAdminInterface().createDicomScpReceiver(simpleRouteReceiver);
        mainAdminInterface().createDicomScpReceiver(highGroupRouteReceiver);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 10000);
    }

    @BeforeMethod(alwaysRun = true)
    @AfterClass(alwaysRun = true)
    private void clearPrearchive() {
        try {
            restDriver.clearPrearchiveSessions(mainUser, project);
        } catch (Throwable throwable) {
            LOG.warn(throwable);
        }
    }

    @AfterClass(alwaysRun = true)
    private void tearDown() {
        if (project != null) {
            mainAdminInterface().deleteProject(project);
        }
        mainAdminInterface().deleteDicomScpReceiver(allRouteReceiver);
        mainAdminInterface().deleteDicomScpReceiver(someRoutesReceiver);
        mainAdminInterface().deleteDicomScpReceiver(simpleRouteReceiver);
        mainAdminInterface().deleteDicomScpReceiver(highGroupRouteReceiver);
    }

    /**
     * It is an error to create a Receiver with an unknown DICOM Object Identifier.
     */
    @Test(groups = VALIDATION)
    public void testUnknownIdentifier() {
        project = null;
        String responseString = mainAdminInterface().queryBase().contentType(JSON).body(unknownIdentifierReceiver).post(formatXapiUrl("dicomscp"))
                .then().assertThat().statusCode(400).extract().asString();
        assertTrue(responseString.contains("unknown DICOM Object Identifier 'unknownIdentifier'"));
    }

    /**
     * A DICOM tag is a single unsigned 32-bit number, group in the high half and element in the low
     * half, so the tag (F215,1050) is the number 0xF2151050 -- larger than Integer.MAX_VALUE. Saving a
     * receiver whose routing expression is keyed on such a tag used to be rejected, because the
     * expression is parsed during validation and the tag was read with a signed parse.
     *
     * routingExpressionsEnabled(true) is required rather than incidental. DicomSCPInstanceService
     * .validate() returns immediately when routing expressions are disabled, so with that flag off the
     * expression is never parsed and this test would pass whether the tag parses or not.
     *
     * This covers saving the receiver, and only that. Routing on such a tag does not yet work end to
     * end: the tag reaches CompositeDicomObjectIdentifier.getTags(), which builds a naturally ordered
     * set, so a tag above 0x7FFFFFFF sorts first rather than last. GradualDicomImporter then takes
     * .last() of that set to decide how much of the object to read, stops short of the tag, and the
     * routing never fires. That is XNAT-7933; until it is fixed, do not read this test as evidence
     * that high-group routing works.
     */
    @Test(groups = VALIDATION)
    @AddedIn(Xnat_1_10_1.class)
    public void testHighGroupRoutingExpressionSaves() {
        project = null;
        final DicomScpReceiver highGroupReceiver
                = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
                .host(Settings.DICOM_HOST)
                .port(Settings.DICOM_PORT)
                .identifier(DicomObjectIdentifier.DEFAULT.getId())
                .enabled(true)
                .customProcessing(false)
                .directArchive(true)
                .anonymizationEnabled(true)
                .whitelistEnabled(false)
                .routingExpressionsEnabled(true)
                .projectRoutingExpression("(F215,1050):Project:(\\w+):1");
        // createDicomScpReceiver asserts the POST returned 200, which is the assertion under test, and sets
        // the id on the receiver so it can be deleted afterwards. If it throws, nothing was created to clean up.
        mainAdminInterface().createDicomScpReceiver(highGroupReceiver);
        mainAdminInterface().deleteDicomScpReceiver(highGroupReceiver);
    }

    /**
     * XNAT-7933. Routing on a tag above 0x7FFFFFFF has to actually route, not merely save.
     *
     * lastTag in GradualDicomImporter decides how much of an incoming object is read before the
     * identifier runs, and it was derived from getTags().last(). That set is naturally ordered, so such
     * a tag sorted first rather than last, the read stopped short of it, and the extractor found
     * nothing -- the receiver accepted the expression and then quietly never routed on it.
     *
     * Unverified against a live server: this sets (F215,0010) and (F215,1050) through
     * overwrittenHeaders, and the VR that path picks for a private tag is not something I could
     * determine from the sources. If this fails on the tag's value rather than on the routing, that is
     * the first thing to check.
     */
    @Test
    @AddedIn(Xnat_1_10_1.class)
    public void testHighGroupRoutingExpressionRoutes() {
        highGroupRouteReceiver.routingExpressionsEnabled(true);
        mainAdminInterface().updateDicomScpReceiver(highGroupRouteReceiver);

        project = new Project();
        final String projectIdPost = project.getId() + "_hg";
        project.setId(projectIdPost);
        mainInterface().createProject(project);

        final Map<Integer, String> hdr = Stream.of(new Object[][]{
                {0xF2150010, "XNAT QA HIGH GROUP"},
                {0xF2151050, String.format("Project:%s Subject:subj_hg Session:sess_hg", projectIdPost)},
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> (String) data[1]));
        uploadViaDicomScp(highGroupRouteReceiver, hdr);
        waitForDicomRecieve(project);

        final Project expectedProject = new Project(projectIdPost);
        new PETSession(expectedProject, new Subject(expectedProject, "subj_hg"), "sess_hg");
        compareProjects(expectedProject, mainAdminInterface().readProject(projectIdPost));
    }

    /**
     * Save a receiver with routing-expression--incapable DOI but routing expressions disabled.
     */
    @Test(groups = VALIDATION)
    @TestRequires(plugins = "dicom-query-retrieve")
    public void testDqrIdentifier() {
        project = null;
        dqrReceiver.routingExpressionsEnabled(false);
        mainAdminInterface().queryBase().contentType(JSON).body(dqrReceiver).post(formatXapiUrl("dicomscp"))
                .then().assertThat().statusCode(200);
    }

    /**
     * Fail to save a receiver with routing-expression--incapable DOI but routing expressions enabled.
     */
    @Test(groups = VALIDATION)
    @TestRequires(plugins = "dicom-query-retrieve")
    public void testDqrIdentifierPrevented() {
        project = null;
        dqrReceiver.routingExpressionsEnabled(true);
        String responseString = mainAdminInterface().queryBase().contentType(JSON).body(dqrReceiver).post(formatXapiUrl("dicomscp"))
                .then().assertThat().statusCode(400).extract().asString();
        assertTrue(responseString.contains("does not support custom routing but custom routing is enabled"));
    }

    @Test
    public void testRoutingExpressionsEnabled() {
        allRouteReceiver.routingExpressionsEnabled(true);
        mainAdminInterface().updateDicomScpReceiver(allRouteReceiver);

        project = new Project();
        String projectIdPre = project.getId();
        String projectIdPost = String.format("%s_rtd", projectIdPre);
        project.setId(projectIdPost);
        mainInterface().createProject(project);

        Map<Integer, String> hdr = Stream.of(new Object[][]{
                {0x00140046, String.format("Project:%s Subject:subj_rtd Session:sess_rtd", projectIdPost)},
                {0x00104000, String.format("Project:%s Subject:subj Session:sess", projectIdPre)},
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> (String) data[1]));
        uploadViaDicomScp(allRouteReceiver, hdr);
        waitForDicomRecieve(project);

        Project expectedProject = new Project(projectIdPost);
        Subject subject = new Subject(expectedProject, "subj_rtd");
        PETSession session = new PETSession(expectedProject, subject, "sess_rtd");
        Project actualProject = mainAdminInterface().readProject(projectIdPost);

        compareProjects(expectedProject, actualProject);
    }

    @Test
    public void testRoutingExpressionsDisabled() {
        allRouteReceiver.routingExpressionsEnabled(false);
        mainAdminInterface().updateDicomScpReceiver(allRouteReceiver);

        project = new Project();
        String projectIdPre = project.getId();
        String projectIdPost = String.format("%s_rtd", projectIdPre);
        project.setId(projectIdPre);
        mainInterface().createProject(project);
        // the subject and session will be created by auto archiving.

        Map<Integer, String> hdr = Stream.of(new Object[][]{
                {0x00140046, String.format("Project:%s Subject:subj_rtd Session:sess_rtd", projectIdPost)},
                {0x00104000, String.format("Project:%s Subject:subj Session:sess", projectIdPre)},
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> (String) data[1]));
        uploadViaDicomScp(allRouteReceiver, hdr);
        waitForDicomRecieve(project);

        Project expectedProject = new Project(projectIdPre);
        Subject subject = new Subject(expectedProject, "subj");
        PETSession session = new PETSession(expectedProject, subject, "sess");
        Project actualProject = mainAdminInterface().readProject(projectIdPre);

        compareProjects(expectedProject, actualProject);
    }

    /**
     * Missing routes fall back to classic routing.
     */
    @Test
    public void testSomeRoutingExpressionsBlank() {
        someRoutesReceiver.routingExpressionsEnabled(true);
        mainAdminInterface().updateDicomScpReceiver(someRoutesReceiver);

        project = new Project();
        String projectIdPre = project.getId();
        String projectIdPost = projectIdPre + "_rtd";
        project.setId(projectIdPost);
        mainInterface().createProject(project);

        Map<Integer, String> hdr = Stream.of(new Object[][]{
                {0x00140046, String.format("Project:%s Subject:subj_rtd Session:sess_rtd", projectIdPost)},
                {0x00104000, String.format("Project:%s Subject:subj Session:sess", projectIdPre)},
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> (String) data[1]));
        uploadViaDicomScp(someRoutesReceiver, hdr);
        waitForDicomRecieve(project);

        Project expectedProject = new Project(projectIdPost);
        Subject subject = new Subject(expectedProject, "subj");
        PETSession session = new PETSession(expectedProject, subject, "sess_rtd");
        Project actualProject = mainAdminInterface().readProject(projectIdPost);

        compareProjects(expectedProject, actualProject);
    }

    /**
     * Per-receiver routing overrides any site-wide routing.
     */
    @Test
    public void testSiteAndReceiverRoutingExpressionsEnabled() {
        mainAdminInterface().setSubjectDicomRoutingConfig("(0010,4000):Project:(\\w+)\\s*Subject:(\\w+)\\s*Session:(\\w+):2 t:.+ r:sitewide\"");
        simpleRouteReceiver.routingExpressionsEnabled(true);
        mainAdminInterface().updateDicomScpReceiver(simpleRouteReceiver);

        project = new Project();
        String projectIdPre = project.getId();
        String projectIdPost = projectIdPre + "_rtd";
        project.setId(projectIdPost);
        mainInterface().createProject(project);

        Map<Integer, String> hdr = Stream.of(new Object[][]{
                {0x00140046, String.format("Project:%s Subject:subj_rtd Session:sess_rtd", projectIdPost)},
                {0x00104000, String.format("Project:%s Subject:subj Session:sess", projectIdPre)},
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> (String) data[1]));
        uploadViaDicomScp(simpleRouteReceiver, hdr);
        waitForDicomRecieve(project);

        Project expectedProject = new Project(projectIdPost);
        Subject subject = new Subject(expectedProject, "subj_rtd");
        PETSession session = new PETSession(expectedProject, subject, "sess_rtd");
        Project actualProject = mainAdminInterface().readProject(projectIdPost);

        compareProjects(expectedProject, actualProject);
        mainAdminInterface().disableSubjectDicomRoutingConfig();
    }

    /**
     * Test the ability to route any DICOM traffic to a single project.
     * <p>
     * Series Instance UID should always exist so there should always be content to match on. Take that content
     * and completely replace it with the ID of the project.
     */
    @Test
    public void testRouteAllTraficToSpecifiedProject() {
        project = new Project();
        String projectId = project.getId();
        mainInterface().createProject(project);

        String projectRoutingExpression = String.format("(0020,000E):(.*):1 t:.+ r:%s", projectId);
        someRoutesReceiver.routingExpressionsEnabled(true);
        someRoutesReceiver.setProjectRoutingExpression(projectRoutingExpression);
        someRoutesReceiver.setSubjectRoutingExpression(null);
        someRoutesReceiver.setSessionRoutingExpression(null);
        mainAdminInterface().updateDicomScpReceiver(someRoutesReceiver);

        new XnatCStore(someRoutesReceiver).data(TestData.SIMPLE_PET).sendDICOM();
        waitForDicomRecieve(project);

        Project actualProject = mainAdminInterface().readProject(projectId);

        Subject subject = new Subject(project, "GSU001");
        PETSession session = new PETSession(project, subject, "GSU001_v00_pet");

        compareProjects(project, actualProject);
    }

    /**
     * The subject routing contains two expressions. The second should take effect.
     */
    @Test
    public void testMultiLineExpressions() {
        project = new Project();
        String projectId = project.getId();
        mainInterface().createProject(project);

        String projectRoutingExpression = String.format("(0020,000E):(.*):1 t:.+ r:%s", projectId);
        String subjectRoutingExpression = "(0010,4001):(.*):1 t:.+ r:subj_rtd1\n(0020,000E):(.*):1 t:.+ r:subj_rtd2";
        someRoutesReceiver.routingExpressionsEnabled(true);
        someRoutesReceiver.setProjectRoutingExpression(projectRoutingExpression);
        someRoutesReceiver.setSubjectRoutingExpression(subjectRoutingExpression);
        someRoutesReceiver.setSessionRoutingExpression(null);
        mainAdminInterface().updateDicomScpReceiver(someRoutesReceiver);

        new XnatCStore(someRoutesReceiver).data(TestData.SIMPLE_PET).sendDICOM();
        waitForDicomRecieve(project);

        Project actualProject = mainAdminInterface().readProject(projectId);

        Subject subject = new Subject(project, "subj_rtd2");
        PETSession session = new PETSession(project, subject, "GSU001_v00_pet");

        compareProjects(project, actualProject);
    }

    private void uploadViaDicomScp(DicomScpReceiver receiver, Map<Integer, String> hdr) {
        new XnatCStore(receiver).data(TestData.SIMPLE_PET).overwrittenHeaders(hdr).sendDICOM();
    }

    private void waitForDicomRecieve(Project project) {
        TimeUtils.sleep(10000);
        restDriver.waitForDirectArchiveEmpty(mainUser, project, 300);
    }

    private void verifyImport(SubjectAssessor session) {
        // if session can be retrieved at this URL, then project, subject, session are all labelled properly
        TimeUtils.sleep(1000); // sleep for 1s to accommodate a little gap between prearchive being empty and session being accessible
        mainQueryBase().get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);
    }

    private void compareProjects(Project expectedProject, Project actualProject) {
        assertNotNull(actualProject);
        assertEquals(expectedProject.getId(), actualProject.getId());
        assertEquals(expectedProject.getSubjects().size(), actualProject.getSubjects().size());
        for (Subject expectedSubject : expectedProject.getSubjects()) {
            Optional<Subject> actualSubject = getSubject(expectedSubject, actualProject);
            if (actualSubject.isPresent()) {
                compareSubjects(expectedSubject, actualSubject.get());
            } else {
                fail(String.format("Project %s is missing subject %s", actualProject.getId(), expectedSubject.getLabel()));
            }
        }
    }

    private Optional<Subject> getSubject(Subject expectedSubject, Project actualProject) {
        return actualProject.getSubjects().stream().filter(as -> as.getLabel().equals(expectedSubject.getLabel())).findAny();
    }

    private void compareSubjects(Subject expectedSubject, Subject actualSubject) {
        assertEquals(expectedSubject.getLabel(), actualSubject.getLabel());
        for (SubjectAssessor expectedExperiment : expectedSubject.getExperiments()) {
            Optional<SubjectAssessor> actualExperiment = getExperiment(expectedExperiment, actualSubject);
            if (actualExperiment.isPresent()) {
                compareExperiments(expectedExperiment, actualExperiment.get());
            } else {
                fail(String.format("Subject %s is missing expected Experiment %s", actualSubject.getLabel(), expectedExperiment.getLabel()));
            }
        }
    }

    private Optional<SubjectAssessor> getExperiment(SubjectAssessor expectedExperiment, Subject actualSubject) {
        return actualSubject.getExperiments().stream().filter(e -> e.getLabel().equals(expectedExperiment.getLabel())).findAny();
    }

    private void compareExperiments(SubjectAssessor expectedExperiment, SubjectAssessor actualExperiment) {
        assertEquals(expectedExperiment.getLabel(), actualExperiment.getLabel());
    }

}
