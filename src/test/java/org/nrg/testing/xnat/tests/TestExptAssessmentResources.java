package org.nrg.testing.xnat.tests;

import com.jayway.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.nrg.testing.CommonStringUtils;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.LegacyComparison;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xdat.bean.XnatQcmanualassessordataBean;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.project.ProjectXMLPutExtension;
import org.nrg.xnat.pogo.extensions.session_assessor.SessionAssessorXMLExtension;
import org.nrg.xnat.pogo.extensions.subject.SubjectXMLPutExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SubjectAssessorXMLExtension;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collections;

public class TestExptAssessmentResources extends BaseXnatRestTest {

    final Project project1 = new Project();
    final Project project2 = new Project();
    final Subject subject = new Subject(project2);
    final ImagingSession session = new MRSession(project2, subject);

    @BeforeClass
    public void setExtensions() {
        project2.extension(new ProjectXMLPutExtension(restDriver.interfaceFor(mainUser), getDataFile("test_project_v1.xml")));
        subject.extension(new SubjectXMLPutExtension(restDriver.interfaceFor(mainUser), getDataFile("test_subject_v1.xml")));
        session.extension(new SubjectAssessorXMLExtension(restDriver.interfaceFor(mainUser), getDataFile("test_expt_v1.xml")));
    }

    @BeforeMethod
    public void setupExperimentAssessmentResourcesTest() {
        restDriver.createProject(mainUser, project1);
        restDriver.createProject(mainUser, project2);
    }

    @AfterMethod(alwaysRun = true)
    public void removeExperimentAssessmentResourceProjects() {
        restDriver.deleteProjectSilently(mainUser, project1);
        restDriver.deleteProjectSilently(mainUser, project2);
    }

    @Test
    public void testExptAssessmentXmlCrud() {
        final File assessorV1 = getDataFile("test_asst_v1.xml");
        final File assessorV2 = getDataFile("test_asst_v2.xml");

        final SessionAssessor assessor = new ManualQC(project2, subject, session).extension(new SessionAssessorXMLExtension(restDriver.interfaceFor(mainUser), assessorV1));
        restDriver.createSessionAssessor(mainUser, assessor);

        LegacyComparison.compareBeanXML(
                assessorV1,
                restDriver.saveBinaryResponseToFile(mainAdminCredentials().given().queryParam("format", "xml").get(restDriver.sessionAssessorUrl(assessor))),
                Collections.singletonMap(XnatQcmanualassessordataBean.class, Collections.singletonList("project"))
        );

        mainCredentials().given().queryParam("format", "html").get(restDriver.assessorsUrl(project2, subject, session)).then().assertThat().statusCode(200);

        // modify assessment
        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(FileIOUtils.readFile(assessorV2)).
                put(restDriver.sessionAssessorUrl(assessor)).then().assertThat().statusCode(200);

        // confirm listing still works
        mainCredentials().given().queryParam("format", "html").get(restDriver.assessorsUrl(project2, subject, session)).then().assertThat().statusCode(200);

        LegacyComparison.compareBeanXML(
                assessorV2,
                restDriver.saveBinaryResponseToFile(mainAdminCredentials().given().queryParam("format", "xml").get(restDriver.sessionAssessorUrl(assessor))),
                Collections.singletonMap(XnatQcmanualassessordataBean.class, Collections.singletonList("project"))
        );

        mainCredentials().given().queryParam("format", "json").get(CommonStringUtils.formatUrl(restDriver.sessionAssessorUrl(assessor), "projects")).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        restDriver.deleteSessionAssessor(mainUser, assessor);

        mainCredentials().given().queryParam("format", "json").get(restDriver.assessorsUrl(project2, subject, session)).then().assertThat().body("ResultSet.Result", Matchers.hasSize(0));
        mainCredentials().given().queryParam("format", "json").get(restDriver.assessorsByAccessionNumber(session)).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(0));

        mainCredentials().given().queryParam("format", "xml").get(restDriver.sessionAssessorUrl(assessor)).then().assertThat().statusCode(404);

        restDriver.createSessionAssessor(mainUser, assessor); // reupload

        mainCredentials().given().queryParam("format", "json").get(restDriver.assessorsByAccessionNumber(session)).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        mainCredentials().given().queryParam("format", "xml").get(restDriver.assessorByAccessionNumber(session, assessor)).then().assertThat().statusCode(200);

        // check no sharing
        mainCredentials().given().queryParam("format", "json").get(formatRestUrl("experiments", session.getAccessionNumber(), "assessors", assessor.getAccessionNumber(), "projects")).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        // share assessor into project1
        mainCredentials().given().queryParam("format", "xml").
                put(formatRestUrl("experiments", session.getAccessionNumber(), "assessors", assessor.getAccessionNumber(), "projects", project1.getId())).
                then().assertThat().statusCode(200);

        mainCredentials().given().queryParam("format", "json").get(formatRestUrl("experiments", session.getAccessionNumber(), "assessors", assessor.getAccessionNumber(), "projects")).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(2));

        // delete from project1
        mainCredentials().given().queryParam("format", "json").delete(formatRestUrl("experiments", session.getAccessionNumber(), "assessors", assessor.getAccessionNumber(), "projects", project1.getId())).
                then().assertThat().statusCode(200);

        // check no sharing
        mainCredentials().given().queryParam("format", "json").get(formatRestUrl("experiments", session.getAccessionNumber(), "assessors", assessor.getAccessionNumber(), "projects")).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        mainCredentials().given().queryParam("format", "xml").get(restDriver.assessorByAccessionNumber(session, assessor)).then().assertThat().statusCode(200);

        mainCredentials().given().queryParam("format", "json").get(restDriver.assessorsByAccessionNumber(session)).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        mainCredentials().given().queryParam("format", "xml").delete(restDriver.assessorByAccessionNumber(session, assessor)).then().assertThat().statusCode(200);

        mainCredentials().given().queryParam("format", "json").get(restDriver.assessorsByAccessionNumber(session)).
                then().assertThat().body("ResultSet.Result", Matchers.hasSize(0));

        // deleted session assessor should 404
        mainCredentials().given().queryParam("format", "xml").delete(restDriver.assessorByAccessionNumber(session, assessor)).then().assertThat().statusCode(404);
    }

}
