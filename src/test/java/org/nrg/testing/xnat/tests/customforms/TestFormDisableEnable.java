package org.nrg.testing.xnat.tests.customforms;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.customforms.pojo.CustomFormPojo;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_8_8;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static org.nrg.testing.TestGroups.CUSTOM_FORMS;
import static org.nrg.testing.TestGroups.PERMISSIONS;
import org.nrg.testing.annotations.MutatesServerState;

@AddedIn(Xnat_1_8_8.class)
@MutatesServerState
public class TestFormDisableEnable extends BaseCustomFormRestTest {

    /*
        Tests:
        Admin can disable and enable all forms
        Custom Form Manager can enable and disable all forms
        Project Owner can only disable/enable forms exclusive to the project
        No other user can disable/enable forms
     */

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    public void testFormDisableEnableByAdmin() throws IOException {
        Set<String> generatedFormUUIDs = new HashSet<>();
        final XnatInterface mainAdminInterface = mainAdminInterface();

        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));
        Project project = registerTempProject();
        generatedFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project, 201));

        List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
        formsOnSite.forEach(form -> sendRequest(mainAdminInterface, "customforms/disable", form.getAppliesToList(), 200));
        formsOnSite.forEach(form -> sendRequest(mainAdminInterface, "customforms/enable", form.getAppliesToList(), 200));

        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testFormDisableEnableByCustomFormManager() throws IOException {
        final User customFormManagerUser = getGenericUser();
        Set<String> generatedFormUUIDs = new HashSet<>();
        mainAdminInterface().assignUserToRoles(customFormManagerUser, "CustomFormManager");
        XnatInterface customFormManagerInterface = interfaceFor(customFormManagerUser);

        generatedFormUUIDs.addAll(createSiteSpecificForms(customFormManagerInterface, 201));
        Project project = registerTempProject();
        mainAdminInterface().createProject(project);
        generatedFormUUIDs.addAll(createProjectSpecificForms(customFormManagerInterface, project, 201));

        List<CustomFormPojo> formsOnSite = fetchForms(customFormManagerInterface);
        formsOnSite.forEach(form ->
                sendRequest(customFormManagerInterface, "customforms/disable", form.getAppliesToList(), 200));
        formsOnSite.forEach(form ->
                sendRequest(customFormManagerInterface, "customforms/enable", form.getAppliesToList(), 200));

        deleteForms(customFormManagerInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testFormDisableEnableByProjectOwnerForExclusiveForms() throws IOException {
        final User projectOwner = getGenericUser();
        Set<String> generatedFormUUIDs = new HashSet<>();
        XnatInterface mainAdminInterface = mainAdminInterface();

        XnatInterface projectOwnerInterface = interfaceFor(projectOwner);
        Project project = registerTempProject();
        project.addOwner(projectOwner);
        mainAdminInterface.createProject(project);
        generatedFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project, 201));

        List<CustomFormPojo> formsOnSite = fetchForms(projectOwnerInterface);
        formsOnSite.forEach(form ->
                sendRequest(projectOwnerInterface, "customforms/disable", form.getAppliesToList(), 403));
        formsOnSite.forEach(form ->
                sendRequest(projectOwnerInterface, "customforms/enable", form.getAppliesToList(), 403));

        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 2)
    public void testFormDisableEnableByProjectOwnerForSharedForms() throws IOException {
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
        List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
        XnatInterface projectOwnerUserInterface = interfaceFor(projectOwnerUser);
        Predicate<CustomFormPojo> isTheGeneratedForm = f -> f.getFormUUID().equals(formUUID);
        formsOnSite.stream()
                   .filter(isTheGeneratedForm)
                           .forEach(form -> {
                               sendRequest(projectOwnerUserInterface, "customforms/disable", form.getAppliesToList(), 403);
                               sendRequest(projectOwnerUserInterface, "customforms/disable", form.getAppliesToList(), 403);
                           });
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testFormDisableEnableByOtherUser() throws IOException {
        XnatInterface mainAdminInterface = mainAdminInterface();
        final User genericUser = getGenericUser();
        Set<String> generatedFormUUIDs = new HashSet<>();
        XnatInterface genericUserInterface = interfaceFor(genericUser);

        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));
        Project project = registerTempProject();
        mainAdminInterface().createProject(project);
        generatedFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project, 201));

        List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
        formsOnSite.forEach(form ->
                sendRequest(genericUserInterface, "customforms/disable", form.getAppliesToList(), 403));
        formsOnSite.forEach(form ->
                sendRequest(genericUserInterface, "customforms/enable", form.getAppliesToList(), 403));

        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }
}
