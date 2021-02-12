package org.nrg.testing.xnat.tests;

import com.jayway.restassured.path.json.JsonPath;
import org.hamcrest.Matchers;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.UIDList;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;

public class TestPrearchiveMgmt extends BaseXnatRestTest {

    private final File sessionZip = getDataFile("mr_1.zip");
    private final String subjectName = "SPP_0x220790";
    private final String sessionName = "SPP_0x220790_MR2";
    private final Project project1 = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final Project project2 = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final Project project3 = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final Subject subject1 = new Subject(project1, "1");
    private final Subject subject2 = new Subject(project1, "2");
    private final Subject subject3 = new Subject(project1, "3");
    private final ImagingSession mr1_1 = new MRSession(project1, subject1, "MR11");
    private final ImagingSession mr1_2 = new MRSession(project1, subject1, "MR12");
    private final ImagingSession mr2_1 = new MRSession(project1, subject2, "MR21");
    private final ImagingSession mr3_1 = new MRSession(project1, subject3, "MR31");

    @BeforeClass
    public void addPrearchiveMgmtProjects() {
        mainInterface().createProject(project1);
        mainInterface().createProject(project2);
        mainInterface().createProject(project3);

        for (Subject subject : project1.getSubjects()) {
            for (SubjectAssessor session : subject.getExperiments()) {
                mainCredentials().
                        queryParam("triggerPipelines", false).
                        queryParam("project", project1.getId()).
                        queryParam("subject", subject.getLabel()).
                        queryParam("session", session.getLabel()).
                        queryParam("overwrite", "append").
                        queryParam("prearchive", true).
                        multiPart(sessionZip).
                        post(formatRestUrl("services/import")).
                        then().assertThat().statusCode(200);
            }
        }
    }

    @AfterClass(alwaysRun = true)
    public void removePrearchiveMgmtProjects() {
        for (Project project : new Project[]{project1, project2, project3}) {
            restDriver.deleteProjectSilently(mainUser, project);
        }
        mainAdminCredentials().put(formatRestUrl("prearchive"));
    }

    @Test
    public void testGradualCommitUnassignedMove() {
        restDriver.clearUnassignedPrearchiveSessions(mainAdminUser, UIDList.uids);

        final String sessionUri = mainCredentials().
                queryParam("import-handler", "gradual-DICOM").
                queryParam("triggerPipelines", false).
                queryParam("dest", "/prearchive").
                multiPart(getDataFile("mr_1/1.dcm")).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200).
                and().extract().response().asString().trim();

        final String sessionUrl = restDriver.formatXnatUrl(sessionUri);

        mainAdminCredentials().queryParam("action", "commit").post(sessionUrl).then().assertThat().statusCode(200);

        mainAdminCredentials().get(sessionUrl).then().assertThat().statusCode(200);

        mainAdminCredentials().
                queryParam("src", sessionUri).
                queryParam("newProject", project3.getId()).
                queryParam("async", false).
                post(formatRestUrl("services/prearchive/move")).
                then().assertThat().statusCode(200);

        TimeUtils.sleep(3000);

        final String newUri = getSessionPrearcUri(project3, subjectName, sessionName);

        mainAdminCredentials().
                queryParam("src", newUri).
                queryParam("newProject", project2.getId()).
                queryParam("async", false).
                post(formatRestUrl("services/prearchive/move")).
                then().assertThat().statusCode(200);

        TimeUtils.sleep(3000);

        final String finalUri = getSessionPrearcUri(project2, subjectName, sessionName);

        mainCredentials().delete(restDriver.formatRestUrl(finalUri)).then().assertThat().statusCode(200);
    }

    @Test
    public void testPrearchiveListing() {
        final JsonPath project1Sessions = mainCredentials().queryParam("format", "json").get(formatRestUrl("prearchive/projects", project1.getId())).
                then().assertThat().statusCode(200).and().extract().jsonPath().setRoot("ResultSet.Result");

        for (Subject subject : project1.getSubjects()) {
            for (SubjectAssessor session : subject.getExperiments()) {
                final String sessionUri = project1Sessions.
                                param("subj", subject.getLabel()).
                                param("session", session.getLabel()).
                                getString("find { it.subject == subj && it.name == session && it.status == 'READY' }.url");
                mainCredentials().get(restDriver.formatRestUrl(sessionUri)).then().assertThat().statusCode(200);
            }
        }
    }

    @Test
    public void testPrearchiveSessionDelete() {
        final String subject = "SUBJ1";
        final String session = "SUBJ1_MR1";

        assertSessionsInProjectPrearc(project2, 0);

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("project", project2.getId()).
                queryParam("subject", subject).
                queryParam("session", session).
                queryParam("overwrite", "append").
                queryParam("prearchive", true).
                multiPart(sessionZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        assertSessionsInProjectPrearc(project2, 1);

        final String uri = getSessionPrearcUri(project2, subject, session);

        mainCredentials().delete(restDriver.formatRestUrl(uri)).then().assertThat().statusCode(200);

        assertSessionsInProjectPrearc(project2, 0);
    }

    @Test
    public void testPrearchiveWithDestination() {
        final String subject = "SUBJ9";
        final String session = "SUBJ9_MRI";

        final String destination = String.format("/prearchive/projects/%s/20000101_050505/11223344556677", project2);

        assertSessionsInProjectPrearc(project2, 0);

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", destination).
                queryParam("subject", subject).
                queryParam("session", session).
                queryParam("overwrite", "append").
                queryParam("prearchive", true).
                multiPart(sessionZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        assertSessionsInProjectPrearc(project2, 1);

        mainCredentials().delete(restDriver.formatRestUrl(destination)).then().assertThat().statusCode(200);

        assertSessionsInProjectPrearc(project2, 0);
    }

    @Test
    public void testUnassignedMove() {
        final String subject = "subject1";
        final String session = sessionName;

        restDriver.clearUnassignedPrearchiveSessions(mainAdminUser, UIDList.uids);

        mainCredentials().
                queryParam("triggerPipelines", false).
                queryParam("dest", "/prearchive").
                queryParam("subject", subject).
                multiPart(sessionZip).
                post(formatRestUrl("services/import")).
                then().assertThat().statusCode(200);

        final String uri = mainAdminCredentials().given().queryParam("format", "json").get(formatRestUrl("prearchive/projects/Unassigned")).
                jsonPath().getString(String.format("ResultSet.Result.find { it.subject == '%s' && it.name == '%s' && it.status == 'READY' }.url", subject, session));

        mainAdminCredentials().
                queryParam("src", uri).
                queryParam("newProject", project3.getId()).
                post(formatRestUrl("services/prearchive/move")).
                then().assertThat().statusCode(200);

        TimeUtils.sleep(3000);

        assertSessionsInProjectPrearc(project3, 1);

        final String newUri = getSessionPrearcUri(project3, subject, session);

        mainCredentials().
                queryParam("src", newUri).
                queryParam("newProject", project2.getId()).
                post(formatRestUrl("services/prearchive/move")).
                then().assertThat().statusCode(200);

        TimeUtils.sleep(3000);

        assertSessionsInProjectPrearc(project3, 0);
        assertSessionsInProjectPrearc(project2, 1);

        final String finalUri = getSessionPrearcUri(project2, subject, session);
        mainCredentials().delete(restDriver.formatRestUrl(finalUri)).then().assertThat().statusCode(200);
    }

    private void assertSessionsInProjectPrearc(Project project, int numSessions) {
        mainCredentials().queryParam("format", "json").get(formatRestUrl("prearchive/projects", project.getId())).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(numSessions));
    }

    private String getSessionPrearcUri(Project project, String subjectLabel, String sessionLabel) {
        return mainCredentials().given().queryParam("format", "json").get(formatRestUrl("prearchive/projects", project.getId())).
                jsonPath().getString(String.format("ResultSet.Result.find { it.subject == '%s' && it.name == '%s' }.url", subjectLabel, sessionLabel));
    }

}
