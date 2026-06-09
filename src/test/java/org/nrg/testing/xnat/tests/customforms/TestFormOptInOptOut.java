package org.nrg.testing.xnat.tests.customforms;

import com.gs.collections.impl.factory.Sets;
import io.restassured.http.ContentType;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.customforms.pojo.CustomFormPojo;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_8_8;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.CUSTOM_FORMS;
import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.Assert.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;
import org.nrg.testing.annotations.MutatesServerState;

@AddedIn(Xnat_1_8_8.class)
@MutatesServerState
public class TestFormOptInOptOut extends BaseCustomFormRestTest {

    /* Tests
     * Admin should be able to Opt Out and Opt In a project from a form
     * Custom Form Manager should be able to Opt Out and Opt In a project from a form
     * Project Owner should be able to Opt Out and Opt in a project from a form
     * No other user should be able to Opt Out and Opt in a project from a form
     */

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    public void testFormOptOutAndInByAdmin() throws IOException {
        Set<String> generatedFormUUIDs = new HashSet<>();
        final XnatInterface mainAdminInterface = mainAdminInterface();

        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));
        final Project project = registerTempProject();
        mainAdminInterface.createProject(project);
        validateResponseForNonExistentForm(mainAdminInterface, "customforms/optout/", project.getId());
        validateResponseForNonExistentForm(mainAdminInterface, "customforms/optin/", project.getId());
        optOut(mainAdminInterface, generatedFormUUIDs, 400, "");
        optOut(mainAdminInterface, generatedFormUUIDs, 200, project.getId());
        optOut(mainAdminInterface, generatedFormUUIDs, 404, "PROJECT_DOES_NOT_EXIST");

        optIn(mainAdminInterface, generatedFormUUIDs, 400, "");
        optIn(mainAdminInterface, generatedFormUUIDs, 404, "PROJECT_DOES_NOT_EXIST");
        optIn(mainAdminInterface, generatedFormUUIDs, 200, project.getId());
        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS})
    public void testFormStateOnDeletionOfProjectWhenFormIsShared() throws IOException {
        Set<String> generatedProjectFormUUIDs = new HashSet<>();
        XnatInterface mainAdminInterface = mainAdminInterface();

        final Project project1 = registerTempProject();
        mainAdminInterface.createProject(project1);
        final Project project2 = registerTempProject();
        mainAdminInterface.createProject(project2);

        generatedProjectFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project1, 201));
        optIn(mainAdminInterface, generatedProjectFormUUIDs, 200, project2.getId());
        mainAdminInterface().deleteProject(project1);
        //All the project forms should exist
        final List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
        assertFalse(formsOnSite.isEmpty(), "Appears that all forms have been deleted");
        assertTrue(
                Sets.isSubsetOf(
                        generatedProjectFormUUIDs,
                        formsOnSite.stream().map(CustomFormPojo::getFormUUID).collect(Collectors.toSet())
                )
        );
        deleteForms(mainAdminInterface, generatedProjectFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testFormOptOutAndInByCustomFormManager() throws IOException {
        final User customFormManagerUser = getGenericUser();
        XnatInterface mainAdminInterface = mainAdminInterface();
        mainAdminInterface.assignUserToRoles(customFormManagerUser, "CustomFormManager");
        XnatInterface customFormManagerInterface = interfaceFor(customFormManagerUser);
        final Project project = registerTempProject();
        mainAdminInterface.createProject(project);
        Set<String> generatedFormUUIDs = new HashSet<>();
        generatedFormUUIDs.addAll(createSiteSpecificForms(customFormManagerInterface, 201));
        optOut(customFormManagerInterface, generatedFormUUIDs, 200, project.getId());
        optIn(customFormManagerInterface, generatedFormUUIDs, 200, project.getId());
        deleteForms(customFormManagerInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testFormOptOutAndInByProjectOwner() throws IOException {
        final User projectOwner = getGenericUser();
        XnatInterface projectOwnerInterface = interfaceFor(projectOwner);
        XnatInterface mainAdminInterface = mainAdminInterface();

        final Project project = registerTempProject();
        project.addOwner(projectOwner);
        mainAdminInterface.createProject(project);
        Set<String> generatedFormUUIDs = new HashSet<>();
        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));
        optOut(projectOwnerInterface, generatedFormUUIDs, 200,project.getId());
        optIn(projectOwnerInterface, generatedFormUUIDs, 200,project.getId());
        deleteForms(mainAdminInterface(), generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 2)
    public void testFormOptOutByOtherProjectMembers() throws IOException {
        final User projectMember = getGenericUser();
        XnatInterface projectMemberInterface = interfaceFor(projectMember);
        final User projectCollaborator = getGenericUser();
        XnatInterface projectCollaboratorInterface = interfaceFor(projectCollaborator);
        XnatInterface mainAdminInterface = mainAdminInterface();

        final Project project = registerTempProject();
        project.addMember(projectMember);
        project.addCollaborator(projectCollaborator);
        mainAdminInterface.createProject(project);
        Set<String> generatedFormUUIDs = new HashSet<>();
        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));
        generatedFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project, 201));
        optOut(projectMemberInterface, generatedFormUUIDs, 403, project.getId());
        optIn(projectMemberInterface, generatedFormUUIDs, 403, project.getId());
        optOut(projectCollaboratorInterface, generatedFormUUIDs, 403, project.getId());
        optIn(projectCollaboratorInterface, generatedFormUUIDs, 403, project.getId());
        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testFormOptOutByOtherUsers() throws IOException {
        final User genericUser = getGenericUser();
        XnatInterface genericUserInterface = interfaceFor(genericUser);
        XnatInterface mainAdminInterface = mainAdminInterface();
        final Project project = registerTempProject();
        mainAdminInterface.createProject(project);
        Set<String> generatedFormUUIDs = new HashSet<>();
        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));
        optOut(genericUserInterface, generatedFormUUIDs, 404, project.getId());
        optIn(genericUserInterface, generatedFormUUIDs, 404, project.getId());
        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }


    private void optOut(final XnatInterface xnatInterface, final Set<String> generatedFormUUIDs, final int statusCode, final String... projectIds) throws IOException {
        List<CustomFormPojo> forms = fetchForms(xnatInterface);
        for (String fUUID : generatedFormUUIDs) {
            CustomFormPojo form = null;
            for (CustomFormPojo f: forms) {
                if (f.getFormUUID().equals(fUUID)) {
                    form = f;
                    break;
                }
            }
            assertNotNull(form);
            sendRequest(xnatInterface, form.getAppliesToList().get(0).getIdCustomVariableFormAppliesTo(), "customforms/optout/", statusCode, projectIds);
        }
    }

    private void optIn(final XnatInterface xnatInterface, final Set<String> generatedFormUUIDs, final int statusCode, final String... projectIds) throws IOException {
        List<CustomFormPojo> forms = fetchForms(xnatInterface);
        for (String fUUID : generatedFormUUIDs) {
            CustomFormPojo form = null;
            for (CustomFormPojo f: forms) {
                if (f.getFormUUID().equals(fUUID)) {
                    form = f;
                    break;
                }
            }
            assertNotNull(form);
            sendRequest(xnatInterface, form.getAppliesToList().get(0).getIdCustomVariableFormAppliesTo(), "customforms/optin/", statusCode, projectIds);
        }
    }

    private String validateResponseForNonExistentForm(final XnatInterface xnatInterface, final String url, final String... projectIds) {
        return xnatInterface
                .requestWithCsrfToken()
                .contentType(ContentType.JSON)
                .body(projectIds)
                .post(formatXapiUrl(url + "DONT_EXIST"))
                .then()
                .assertThat()
                .statusCode(400)
                .and()
                .extract()
                .response()
                .asString();
    }


    private String sendRequest(final XnatInterface xnatInterface, final String formId, final String url, int statusCode, final String... projectIds) {
        return xnatInterface
                .requestWithCsrfToken()
                .contentType(ContentType.JSON)
                .body(projectIds)
                .post(formatXapiUrl(url + formId))
                .then()
                .assertThat()
                .statusCode(statusCode)
                .and()
                .extract()
                .response()
                .asString();
    }

}
