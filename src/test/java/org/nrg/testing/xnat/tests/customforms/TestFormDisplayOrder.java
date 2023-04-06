package org.nrg.testing.xnat.tests.customforms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.customforms.pojo.CustomFormPojo;
import org.nrg.testing.xnat.exceptions.ProcessingException;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_8_8;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.nrg.testing.TestGroups.CUSTOM_FORMS;
import static org.nrg.testing.TestGroups.PERMISSIONS;

@AddedIn(Xnat_1_8_8.class)
public class TestFormDisplayOrder extends BaseCustomFormRestTest {

    /*
     Reference: https://radiologics.atlassian.net/browse/XNAT-7444
     */

    @BeforeClass
    private void createCustomForms() throws IOException {
        List<String> files = CustomFormConstants.SITE_FORM_TEMPLATES.subList(0, 1);
        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface(), files, 201));
    }

    @AfterClass(alwaysRun = true)
    public void deleteCustomForms() throws IOException {
        deleteForms(mainAdminInterface(), generatedFormUUIDs, 200);
    }


    @Test(groups = {CUSTOM_FORMS})
    public void testInValidValueForFormId() {
        sendRequestToModify(mainAdminInterface(), "SOME_NON_UUID_STRING", "100", 400);
    }

    @Test(groups = {CUSTOM_FORMS})
    public void testValidValueForFormId() throws IOException {
        XnatInterface mainAdminInterface = mainAdminInterface();
        String formUUID = generatedFormUUIDs.iterator().next();
        List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
        verifyDisplayOrder(formsOnSite, "10");
        sendRequestToModify(mainAdminInterface, formUUID, "2000", 200);
        formsOnSite = fetchForms(mainAdminInterface);
        verifyDisplayOrder(formsOnSite, "2000");
    }


    @Test(groups = {CUSTOM_FORMS})
    public void testDisplayOrderValues() {
        XnatInterface mainAdminInterface = mainAdminInterface();

        //Skipping tests when valid integer contains comma
        List<String> validOrders = Arrays.asList("10", "1000000", "-1000000");
        List<String> invalidOrders = Arrays.asList("-1000001", "1000001");
        invalidOrders.stream().forEach(displayOrder ->
                generatedFormUUIDs.forEach(fUUID ->
                        sendRequestToModify(mainAdminInterface, fUUID, displayOrder, 400))
        );
        //TODO: fix the status code once the @RequestParam exception is fixed
        List<String> invalidOrders1 = Arrays.asList("STRING_NON_INTEGER", "77.87");
        invalidOrders1.stream().forEach(displayOrder ->
                generatedFormUUIDs.forEach(fUUID ->
                        sendRequestToModify(mainAdminInterface, fUUID, displayOrder, 500))
        );

        validOrders.stream().forEach(displayOrder ->
                generatedFormUUIDs.forEach(fUUID -> {
                        sendRequestToModify(mainAdminInterface, fUUID, displayOrder, 200);
                        try {
                            List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
                            verifyDisplayOrder(formsOnSite, displayOrder);
                        } catch (Exception e) {
                            throw new ProcessingException(e.getMessage());
                        }
                })
        );
    }

    /*
     Permissions tests:
     Admin can set display order
     Custom Form Manager can set display order
     Project owners can set display order ONLY for forms that belong to their project
     Project owners can not set display order if the form is a shared form
     No other user can set the display order
     */

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testCustomFormManagerCanSetDisplayOrder() throws IOException {
        final User customFormManagerUser = getGenericUser();
        XnatInterface mainAdminInterface = mainAdminInterface();
        mainAdminInterface.assignUserToRoles(customFormManagerUser, "CustomFormManager");
        XnatInterface customFormManagerInterface = interfaceFor(customFormManagerUser);
        String formUUID = generatedFormUUIDs.iterator().next();
        sendRequestToModify(customFormManagerInterface, formUUID, "-8000", 200);
        List<CustomFormPojo> formsOnSite = fetchForms(customFormManagerInterface);
        verifyDisplayOrder(formsOnSite, "-8000");
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testProjectOwnerCanNotSetDisplayOrderForSiteForm()  {
        final User projectOwnerUser = getGenericUser();
        final Project project = registerTempProject();
        project.addOwner(projectOwnerUser);
        mainAdminInterface().createProject(project);

        XnatInterface projectOwnerUserInterface = interfaceFor(projectOwnerUser);
        String formUUID = generatedFormUUIDs.iterator().next();
        sendRequestToModify(projectOwnerUserInterface, formUUID, "-9000", 403);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testUserCanNotSetDisplayOrderForSiteForm()  {
        final User genericUser = getGenericUser();
        XnatInterface genericUserInterface = interfaceFor(genericUser);
        String formUUID = generatedFormUUIDs.iterator().next();
        sendRequestToModify(genericUserInterface, formUUID, "-9001", 403);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testProjectOwnerCanNotSetDisplayOrderForFormSharedBetweenProject() throws IOException {
        XnatInterface mainAdminInterface = mainAdminInterface();
        final User projectOwnerUser = getGenericUser();
        final Project project1 = registerTempProject();
        project1.addOwner(projectOwnerUser);
        mainAdminInterface.createProject(project1);
        final Project project2 = registerTempProject();
        mainAdminInterface.createProject(project2);
        String rawTestString = CustomFormConstants.SHARED_SUBJECT_FORM_TEMPLATE.replaceAll(CustomFormConstants.APPEND_FORM_NUMBER, String.valueOf(ThreadLocalRandom.current().nextInt()));
        rawTestString = rawTestString.replaceAll("PROJECT1_ID_HERE", project1.getId());
        rawTestString = rawTestString.replaceAll("PROJECT2_ID_HERE", project2.getId());
        String formUUID = saveFormAndAssert(mainAdminInterface, rawTestString, 201);
        XnatInterface projectOwnerUserInterface = interfaceFor(projectOwnerUser);
        sendRequestToModify(projectOwnerUserInterface, formUUID, "-9002", 403);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testProjectOwnerCanNotSetDisplayOrderForProjectForm() throws IOException {
        XnatInterface mainAdminInterface = mainAdminInterface();
        final User projectOwnerUser = getGenericUser();
        final Project project = registerTempProject();
        project.addOwner(projectOwnerUser);
        mainAdminInterface.createProject(project);

        XnatInterface projectOwnerUserInterface = interfaceFor(projectOwnerUser);
        String rawTestString = CustomFormConstants.EXCLUSIVE_TO_PROJECT_SUBJECT_FORM.replaceAll(CustomFormConstants.APPEND_FORM_NUMBER, String.valueOf(ThreadLocalRandom.current().nextInt()));
        rawTestString = rawTestString.replaceAll("PROJECT_ID_HERE", project.getId());
        String formUUID = saveFormAndAssert(mainAdminInterface, rawTestString, 201);
        sendRequestToModify(projectOwnerUserInterface, formUUID, "-9003", 403);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testProjectMemberCanNotSetDisplayOrderForProjectForm() throws IOException {
        XnatInterface mainAdminInterface = mainAdminInterface();
        final User projectMemberUser = getGenericUser();
        final Project project = registerTempProject();
        project.addMember(projectMemberUser);
        mainAdminInterface.createProject(project);

        XnatInterface projectMemberUserInterface = interfaceFor(projectMemberUser);
        String rawTestString = CustomFormConstants.EXCLUSIVE_TO_PROJECT_SUBJECT_FORM.replaceAll(CustomFormConstants.APPEND_FORM_NUMBER, String.valueOf(ThreadLocalRandom.current().nextInt()));
        rawTestString = rawTestString.replaceAll("PROJECT_ID_HERE", project.getId());
        String formUUID = saveFormAndAssert(mainAdminInterface, rawTestString, 201);
        sendRequestToModify(projectMemberUserInterface, formUUID, "-9004", 403);
    }

    /*
    Display order tie breaker: Two forms at the same level with the same display order
    The latest create time of the form renders the form "back"
     */
    @Test(groups = {CUSTOM_FORMS})
    public void testDisplayOrderTieBreaker() throws IOException {
        XnatInterface mainAdminInterface = mainAdminInterface();
        final Project project = registerTempProject();
        mainAdminInterface.createProject(project);

        String form1RandomNumber = String.valueOf(ThreadLocalRandom.current().nextInt());
        String form1 = CustomFormConstants.EXCLUSIVE_TO_PROJECT_SUBJECT_FORM.replaceAll(CustomFormConstants.APPEND_FORM_NUMBER, form1RandomNumber);
        form1 = form1.replaceAll("PROJECT_ID_HERE", project.getId());
        String formUUID1 = saveFormAndAssert(mainAdminInterface, form1, 201);

        String form2RandomNumber = String.valueOf(ThreadLocalRandom.current().nextInt());
        String form2 = CustomFormConstants.EXCLUSIVE_TO_PROJECT_SUBJECT_FORM.replaceAll(CustomFormConstants.APPEND_FORM_NUMBER, form2RandomNumber);
        form2 = form2.replaceAll("PROJECT_ID_HERE", project.getId());
        String formUUID2 = saveFormAndAssert(mainAdminInterface, form2, 201);

        Map<String, String> queryParams = new HashMap();
        queryParams.put("xsiType", "xnat:subjectData");
        queryParams.put("id", "null");
        queryParams.put("appendPrevNextButtons", "true");
        queryParams.put("projectId", project.getId());
        queryParams.put("visitId", "null");
        queryParams.put("subtype", "null");
        queryParams.put("format", "json");

        String response = getJsonResponse(mainAdminInterface, "customforms/element", queryParams, 200);
        ObjectMapper objectMapper = getObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response);
        assertNotNull(rootNode);
        JsonNode components = rootNode.at("/components");
        if (components instanceof ArrayNode) {
            assertEquals(((ArrayNode)components).size(), 2);
            String firstFormLabel = ((ArrayNode)components).get(0).get("label").asText();
            String secondFormLabel = ((ArrayNode)components).get(1).get("label").asText();
            assertEquals(firstFormLabel, "Form"+form2RandomNumber);
            assertEquals(secondFormLabel, "Form"+form1RandomNumber);
        }
    }

    private void verifyDisplayOrder(final List<CustomFormPojo> formsOnSite,  final String desiredFormDisplayOrder) throws JsonProcessingException, IOException {
        formsOnSite.stream()
                .filter(form -> generatedFormUUIDs.contains(form.getFormUUID()))
                .forEach(form -> assertEquals(new Long(form.getFormDisplayOrder()).longValue(), Long.parseLong(desiredFormDisplayOrder)));
    }

    private String sendRequestToModify(final XnatInterface xnatInterface, final String formId, final String displayOrder, int statusCode) {
        return xnatInterface
                .requestWithCsrfToken()
                .queryParams("displayOrder", displayOrder)
                .post(formatXapiUrl("customforms/formId/" + formId))
                .then()
                .assertThat()
                .statusCode(statusCode)
                .and()
                .extract()
                .response()
                .asString();
    }

    private Set<String> generatedFormUUIDs = new HashSet<>();

}
