package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
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

import java.util.HashMap;
import java.util.Map;

import static org.nrg.testing.TestGroups.PERMISSIONS;

public class TestInvalidUserAccess extends BaseXnatRestTest {

    Project testProject;
    Subject testSubject;
    ImagingSession testSession;
    SessionAssessor testSessionAssessor;

    @BeforeMethod
    public void setupInvalidUserAccessTest() {
        testProject = new Project();
        testSubject = new Subject(testProject).extension(new SubjectXMLPutExtension(getDataFile("test_subject_v1.xml")));
        testSession = new MRSession(testProject, testSubject).extension(new SubjectAssessorXMLExtension(getDataFile("test_expt_v1.xml")));
        testSessionAssessor = new ManualQC(testProject, testSubject, testSession).extension(new SessionAssessorXMLExtension(getDataFile("test_asst_v1.xml")));

        mainAdminInterface().createProject(testProject);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownInvalidUserAccessTest() {
        restDriver.deleteProjectSilently(mainAdminUser, testProject);
    }

    @Test(groups = PERMISSIONS)
    @TestRequires(closedXnat = true)
    public void testProtectedXMLCRUD() {
        mainAdminInterface().updateAccessibility(testProject, Accessibility.PROTECTED);
        restDriver.assertProjectAccessibility(mainAdminUser, testProject, Accessibility.PROTECTED);
        
        restDriver.invalidCredentials().queryParam("format", "xml").get(mainInterface().accessibilityRestUrl(testProject)).then().assertThat().statusCode(401);
        restDriver.assertProjectAccessibility(mainUser, testProject, Accessibility.PROTECTED); // foreign user should be able to read protected project's accessibility

        restDriver.invalidCredentials().queryParam("format", "xml").
                put(mainInterface().accessibilityRestUrl(testProject, Accessibility.PUBLIC)).then().assertThat()
                .body(Matchers.anyOf(
                        Matchers.containsString("This request requires HTTP authentication."),
                        Matchers.containsString("The request has not been applied because it lacks valid authentication credentials for the target resource")
                )).assertThat().statusCode(401);
        
        mainQueryBase().queryParam("format", "xml").put(mainInterface().accessibilityRestUrl(testProject, Accessibility.PUBLIC)).then().assertThat().statusCode(403);
        restDriver.assertProjectAccessibility(mainAdminUser, testProject, Accessibility.PROTECTED);

        restDriver.invalidCredentials().queryParam("format", "xml").queryParam("removeFiles", true).
                delete(mainInterface().projectUrl(testProject)).then().assertThat().statusCode(401);
        mainQueryBase().queryParam("format", "xml").queryParam("removeFiles", true).
                delete(mainInterface().projectUrl(testProject)).then().assertThat().statusCode(403);

        final String alias = RandomHelper.randomID();
        restDriver.invalidCredentials().queryParam("format", "xml").queryParam("alias", alias).
                put(formatRestUrl("projects", testProject.getId())).then().assertThat().statusCode(401);
        mainQueryBase().queryParam("format", "xml").queryParam("alias", alias).
                put(formatRestUrl("projects", testProject.getId())).then().assertThat().statusCode(403);

        final String subjectXml = readDataFile("iu_subject_v1.xml");
        restDriver.invalidCredentials().given().queryParam("format", "xml").body(subjectXml).
                post(formatRestUrl("projects", testProject.getId(), "subjects")).then().assertThat().statusCode(401);
        restDriver.invalidCredentials().given().queryParam("format", "xml").body(subjectXml).
                put(formatRestUrl("projects", testProject.getId(), "subjects", "2")).then().assertThat().statusCode(401);
        mainQueryBase().queryParam("format", "xml").body(subjectXml).
                post(formatRestUrl("projects", testProject.getId(), "subjects")).then().assertThat().statusCode(403);
        mainQueryBase().queryParam("format", "xml").body(subjectXml).
                put(formatRestUrl("projects", testProject.getId(), "subjects", "2")).then().assertThat().statusCode(403);

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("format", "xml");
        queryParams.put("req_format", "qs");
        queryParams.put("gender", "female");
        restDriver.invalidCredentials().queryParams(queryParams).put(formatRestUrl("projects", testProject.getId(), "subjects", "1")).then().assertThat().statusCode(401);
        mainQueryBase().queryParams(queryParams).put(formatRestUrl("projects", testProject.getId(), "subjects", "1")).then().assertThat().statusCode(403);
    }

}
