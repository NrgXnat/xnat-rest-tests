package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.enums.Handedness;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public class TestCustomVariables extends BaseXnatRestTest {

    private static final String FIELD_NAME = "test01";
    private static final String FIELD_VALUE = "12";
    private static final Map<String, Object> FIELD_MAP = Collections.singletonMap(FIELD_NAME, FIELD_VALUE);

    @BeforeMethod
    public void createTestProject() {
        mainInterface().createProject(testSpecificProject);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteTestProject() {
        restDriver.deleteProjectSilently(mainUser, testSpecificProject);
    }

    @Test
    public void testCustomVariableSubjectCreate() {
        final Project project = new Project().addSubject(new Subject().fields(FIELD_MAP));
        mainInterface().createProject(project);
        assertEquals(FIELD_MAP, mainInterface().readProject(project.getId()).getSubjects().get(0).getFields());
    }

    @Test
    public void testCustomVariableSubjectUpdate() {
        final Project project = new Project();
        final Subject subject = new Subject(project);
        final Map<String, Object> currentExpectedFields = new HashMap<>(FIELD_MAP);
        mainInterface().createProject(project);
        mainInterface().createSubject(subject.fields(FIELD_MAP));
        assertEquals(currentExpectedFields, mainInterface().readProject(project.getId()).getSubjects().get(0).getFields());
        final String newFieldName = "best_cat";
        final String newFieldVal = "Claudine";
        mainInterface().createSubject(subject.fields(Collections.singletonMap(newFieldName, newFieldVal)));
        currentExpectedFields.put(newFieldName, newFieldVal);
        assertEquals(currentExpectedFields, mainInterface().readProject(project.getId()).getSubjects().get(0).getFields());
        final String updatedFieldVal = "20";
        mainInterface().createSubject(subject.fields(Collections.singletonMap(FIELD_NAME, updatedFieldVal)));
        currentExpectedFields.put(FIELD_NAME, updatedFieldVal);
        assertEquals(currentExpectedFields, mainInterface().readProject(project.getId()).getSubjects().get(0).getFields());
    }

    @Test
    public void testCustomVariablePutGet() {
        final String fieldName = "test01";
        final String testField = String.format("xnat:mrSessionData/fields/field[name=%s]/field", fieldName);
        final String originalFieldValue = "12";

        final Project project = testSpecificProject;
        final Subject subject = testSpecificSubject.project(project).group("control").src("12").dob(LocalDate.parse("2001-01-01")).gender(Gender.MALE).handedness(Handedness.LEFT);
        final SubjectAssessor subjectAssessor = new MRSession(project, subject).date(LocalDate.parse("1999-12-31"));
        mainInterface().createProject(project);

        mainCredentials().given().queryParam(testField, originalFieldValue).put(mainInterface().subjectAssessorUrl(subjectAssessor)).then().statusCode(200);
        readCustomVariables(subjectAssessor).then().assertThat().body(customVariableJsonPath(originalFieldValue, fieldName), Matchers.notNullValue());

        final String newValue = "14";
        mainCredentials().given().queryParam(testField, newValue).put(mainInterface().subjectAssessorUrl(subjectAssessor)).then().statusCode(200);
        readCustomVariables(subjectAssessor).then().assertThat().body(customVariableJsonPath(newValue, fieldName), Matchers.notNullValue()); // new value set
        readCustomVariables(subjectAssessor).then().assertThat().body(customVariableJsonPath(originalFieldValue, fieldName), Matchers.nullValue()); // old value gone

        final String finalValue = "15";
        mainCredentials().given().contentType(ContentType.URLENC).formParam(testField, finalValue).put(mainInterface().subjectAssessorUrl(subjectAssessor)).then().statusCode(200); // modify via form post
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
