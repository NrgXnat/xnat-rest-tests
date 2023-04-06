package org.nrg.testing.xnat.tests.customforms;

import org.nrg.testing.xnat.customforms.pojo.CustomFormPojo;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.CustomFieldScope;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.rest.SerializationUtils;
import org.testng.annotations.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

public class TestHasData extends BaseCustomFormRestTest {

    /*
     Tests:
     If a form has data associated with it, "has data" API Endpoint should return true else false
     Empty form data is treated as no form data
     */

    @BeforeClass
    private void createFormForProject()  {
        project = new Project();
        mainInterface().createProject(project);
        String rawTestString = CustomFormConstants.EXCLUSIVE_TO_PROJECT_SUBJECT_FORM.replaceAll(CustomFormConstants.APPEND_FORM_NUMBER, fieldId);
        rawTestString = rawTestString.replaceAll("PROJECT_ID_HERE", project.getId());
        generatedFormUUIDs.add(saveFormAndAssert(mainAdminInterface(), rawTestString, 201));
    }

    @AfterClass(alwaysRun = true)
    public void deleteProject() {
        mainAdminInterface().deleteProject(project);
    }


    @Test
    public void testHasData() throws IOException {
        String formUUID = generatedFormUUIDs.iterator().next();
        final XnatInterface mainAdminInterface = mainAdminInterface();

        List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
        //Has data should be true for Subject1
        final Subject subject1 = new Subject(project, "S1");
        mainAdminInterface().createSubject(project, subject1);
        CustomFieldScope scope = new CustomFieldScope().project(project).subject(subject1);
        mainInterface().setCustomFields(scope, getFormData(formUUID));


        formsOnSite.stream().filter(form -> form.getFormUUID().equals(formUUID))
                .forEach(form -> {
                    Boolean hasData =  sendRequest(mainAdminInterface, hasDataUrl + form.getAppliesToList().get(0).getIdCustomVariableFormAppliesTo(), 200);
                    assertTrue(hasData);
                });

        mainInterface().deleteSubject(subject1);
        formsOnSite.stream().filter(form -> form.getFormUUID().equals(formUUID))
                .forEach(form -> {
                    Boolean hasData =  sendRequest(mainAdminInterface, hasDataUrl + form.getAppliesToList().get(0).getIdCustomVariableFormAppliesTo(), 200);
                    assertFalse(hasData);
                });


        final Subject subject2 = new Subject(project, "S2");
        mainAdminInterface().createSubject(project, subject2);
        scope = new CustomFieldScope().project(project).subject(subject2);
        mainInterface().setCustomFields(scope, getEmptyFormData(formUUID));

        formsOnSite.stream().filter(form -> form.getFormUUID().equals(formUUID))
                .forEach(form -> {
                    Boolean hasData =  sendRequest(mainAdminInterface, hasDataUrl + form.getAppliesToList().get(0).getIdCustomVariableFormAppliesTo(), 200);
                    assertFalse(hasData);
                });
        mainInterface().deleteSubject(subject2);
        formsOnSite.stream().filter(form -> form.getFormUUID().equals(formUUID))
                .forEach(form -> {
                    Boolean hasData =  sendRequest(mainAdminInterface, hasDataUrl + form.getAppliesToList().get(0).getIdCustomVariableFormAppliesTo(), 200);
                    assertFalse(hasData);
                });


        //Has Data should be false for subject3
        final Subject subject3 = new Subject(project, "S3");
        mainAdminInterface().createSubject(project, subject3);
        scope = new CustomFieldScope().project(project).subject(subject3);
        mainInterface().setCustomFields(scope, getNonFormData());

        formsOnSite.stream().filter(form -> form.getFormUUID().equals(formUUID))
                .forEach(form -> {
                    Boolean hasData =  sendRequest(mainAdminInterface, hasDataUrl + form.getAppliesToList().get(0).getIdCustomVariableFormAppliesTo(), 200);
                    assertFalse(hasData);
                });
        mainInterface().deleteSubject(subject3);
    }

    protected Boolean sendRequest(final XnatInterface xnatInterface, final String url, int statusCode)  {
        return xnatInterface
                .requestWithCsrfToken()
                .get(formatXapiUrl(url))
                .then()
                .assertThat()
                .statusCode(statusCode)
                .and()
                .extract()
                .response()
                .as(Boolean.class);
    }


    private Map<String, Object> getFormData(final String formUUID) {
        return Collections.singletonMap(formUUID, Collections.singletonMap("textField" + fieldId, "This test works"));
    }

    private Map<String, Object> getEmptyFormData(final String formUUID) {
        return Collections.singletonMap(formUUID, new HashMap<>());
    }

    private Map<String, Object> getNonFormData() {
        return Collections.singletonMap("abc", "abc123");

    }

    private final String hasDataUrl = "customforms/hasdata/";
    private Project project;
    private Set<String> generatedFormUUIDs = new HashSet<>();
    private final String fieldId = String.valueOf(ThreadLocalRandom.current().nextInt());

}
