package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.ChainedPutMap;
import org.nrg.testing.file.FileIO;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseRestTest;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.session_assessor.SessionAssessorXMLExtension;
import org.nrg.xnat.pogo.extensions.subject.SubjectXMLPutExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SubjectAssessorXMLExtension;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

public class TestInvalidUserAccess extends BaseRestTest {

    Project testProject;
    Subject testSubject;
    ImagingSession testSession;
    SessionAssessor testSessionAssessor;

    @BeforeMethod
    public void setupInvalidUserAccessTest() {
        testProject = new Project();
        testSubject = new Subject(testProject).extension(new SubjectXMLPutExtension(restDriver.interfaceFor(mainAdminUser), FileIO.getDataFile("test_subject_v1.xml")));
        testSession = new MRSession(testProject, testSubject).extension(new SubjectAssessorXMLExtension(restDriver.interfaceFor(mainAdminUser), FileIO.getDataFile("test_expt_v1.xml")));
        testSessionAssessor = new ManualQC(testProject, testSubject, testSession).extension(new SessionAssessorXMLExtension(restDriver.interfaceFor(mainAdminUser), FileIO.getDataFile("test_asst_v1.xml")));

        restDriver.createProject(mainAdminUser, testProject);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownInvalidUserAccessTest() {
        restDriver.deleteProjectSilently(mainAdminUser, testProject);
    }

    @Test
    public void testProtectedXMLCRUD() {
        restDriver.updateAccessibility(mainAdminUser, testProject, Accessibility.PROTECTED);
        restDriver.assertProjectAccessibility(mainAdminUser, testProject, Accessibility.PROTECTED);

        restDriver.invalidCredentials().given().queryParam("format", "xml").expect().statusCode(401).get(restDriver.accessibilityRestUrl(testProject));
        restDriver.assertProjectAccessibility(mainUser, testProject, Accessibility.PROTECTED); // foreign user should be able to read protected project's accessibility

        restDriver.invalidCredentials().given().queryParam("format", "xml").expect().statusCode(401).
                put(restDriver.accessibilityRestUrl(testProject, Accessibility.PUBLIC)).then().assertThat().body(Matchers.containsString("This request requires HTTP authentication."));
        mainCredentials().given().queryParam("format", "xml").expect().statusCode(403).
                put(restDriver.accessibilityRestUrl(testProject, Accessibility.PUBLIC));
        restDriver.assertProjectAccessibility(mainAdminUser, testProject, Accessibility.PROTECTED);

        restDriver.invalidCredentials().given().queryParam("format", "xml").queryParam("removeFiles", true).expect().statusCode(401).
                delete(restDriver.projectUrl(testProject));
        mainCredentials().given().queryParam("format", "xml").queryParam("removeFiles", true).expect().statusCode(403).
                delete(restDriver.projectUrl(testProject));

        final String alias = RandomHelper.randomID();
        restDriver.invalidCredentials().given().queryParam("format", "xml").queryParam("alias", alias).expect().statusCode(401).
                put(formatRestUrl("projects", testProject.getId()));
        mainCredentials().given().queryParam("format", "xml").queryParam("alias", alias).expect().statusCode(403).
                put(formatRestUrl("projects", testProject.getId()));

        final String subjectXml = FileIO.readDataFile("iu_subject_v1.xml");
        restDriver.invalidCredentials().given().queryParam("format", "xml").body(subjectXml).expect().statusCode(401).
                post(formatRestUrl("projects", testProject.getId(), "subjects"));
        restDriver.invalidCredentials().given().queryParam("format", "xml").body(subjectXml).expect().statusCode(401).
                put(formatRestUrl("projects", testProject.getId(), "subjects", "2"));
        mainCredentials().given().queryParam("format", "xml").body(subjectXml).expect().statusCode(403).
                post(formatRestUrl("projects", testProject.getId(), "subjects"));
        mainCredentials().given().queryParam("format", "xml").body(subjectXml).expect().statusCode(403).
                put(formatRestUrl("projects", testProject.getId(), "subjects", "2"));

        final Map<String, String> queryParams = new ChainedPutMap<String, String>().chainedPut("format", "xml").chainedPut("req_format", "qs").chainedPut("gender", "female");
        restDriver.invalidCredentials().given().queryParams(queryParams).expect().statusCode(401).put(formatRestUrl("projects", testProject.getId(), "subjects", "1"));
        mainCredentials().given().queryParams(queryParams).expect().statusCode(403).put(formatRestUrl("projects", testProject.getId(), "subjects", "1"));
    }

}
