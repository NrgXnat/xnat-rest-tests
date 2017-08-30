package org.nrg.testing.xnat.tests;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import org.hamcrest.Matchers;
import org.joda.time.LocalDate;
import org.nrg.testing.xnat.BaseRestTest;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.enums.Handedness;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestCustomVariables extends BaseRestTest {

    @BeforeMethod
    public void createTestProject() {
        restDriver.createProject(mainUser, testSpecificProject);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteTestProject() {
        restDriver.deleteProjectSilently(mainUser, testSpecificProject);
    }

    @Test
    public void testCustomVariablePutGet() {
        final String fieldName = "test01";
        final String testField = String.format("xnat:mrSessionData/fields/field[name=%s]/field", fieldName);
        final String originalFieldValue = "12";

        final Project project = testSpecificProject;
        final Subject subject = testSpecificSubject.project(project).group("control").src("12").dob(new LocalDate("2001-01-01")).gender(Gender.MALE).handedness(Handedness.LEFT);
        final SubjectAssessor subjectAssessor = new MRSession(project, subject).date(new LocalDate("1999-12-31"));
        restDriver.createProject(mainUser, project);

        mainCredentials().given().queryParam(testField, originalFieldValue).put(restDriver.subjectAssessorUrl(subjectAssessor)).then().statusCode(200);
        readCustomVariables(subjectAssessor).then().assertThat().body(customVariableJsonPath(originalFieldValue, fieldName), Matchers.notNullValue());

        final String newValue = "14";
        mainCredentials().given().queryParam(testField, newValue).put(restDriver.subjectAssessorUrl(subjectAssessor)).then().statusCode(200);
        readCustomVariables(subjectAssessor).then().assertThat().body(customVariableJsonPath(newValue, fieldName), Matchers.notNullValue()); // new value set
        readCustomVariables(subjectAssessor).then().assertThat().body(customVariableJsonPath(originalFieldValue, fieldName), Matchers.nullValue()); // old value gone

        final String finalValue = "15";
        mainCredentials().given().contentType(ContentType.URLENC).formParam(testField, finalValue).put(restDriver.subjectAssessorUrl(subjectAssessor)).then().statusCode(200); // modify via form post
        readCustomVariables(subjectAssessor).then().assertThat().body(customVariableJsonPath(finalValue, fieldName), Matchers.notNullValue()); // new value set
        readCustomVariables(subjectAssessor).then().assertThat().body(customVariableJsonPath(newValue, fieldName), Matchers.nullValue()); // old value gone
    }

    private Response readCustomVariables(SubjectAssessor assessor) {
        return mainCredentials().given().
                queryParam("xnat:mrSessionData/label", assessor.getLabel()).
                queryParam("format", "json").
                queryParam("columns", "xnat:mrSessionData/fields/field/field,xnat:mrSessionData/fields/field/name").
                get(formatRestUrl("projects", assessor.getSubject().getProject().getId(), "subjects", assessor.getSubject().getLabel(), "experiments"));
    }

    private String customVariableJsonPath(String fieldValue, String fieldName) {
        return String.format("ResultSet.Result.find { it.'xnat:mrsessiondata/fields/field/field' == '%s' && it.'xnat:mrsessiondata/fields/field/name' == '%s' }", fieldValue, fieldName);
    }

}
