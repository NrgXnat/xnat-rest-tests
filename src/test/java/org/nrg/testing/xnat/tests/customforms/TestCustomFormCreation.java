package org.nrg.testing.xnat.tests.customforms;

import org.apache.commons.io.FileUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.customforms.pojo.CustomFormPojo;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_8_8;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

import static org.nrg.testing.TestGroups.CUSTOM_FORMS;
import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;


@AddedIn(Xnat_1_8_8.class)
public class TestCustomFormCreation extends BaseCustomFormRestTest {

    /* Tests:
     1. Admin should be able to create a site wide form
        a. For Project
        b. For Subject
        c. For an experiment - MR Session Datatype
     2. Admin should be able to create a project specific form
        a. For Project
        b. For Subject
        c. For an experiment - MR Session Datatype
     3. Custom Form Manager should be able to create a site wide form
        a. For Project
        b. For Subject
        c. For an experiment - MR Session Datatype
     4. Custom Form Manager should be able to create a project specific form
        a. For Project
        b. For Subject
        c. For an experiment - MR Session Datatype
     5. Project Owner should not be able to (as of vanilla XNAT 1.8.8) create a project specific form for a project owned by them
        a. For Project owned or not owned
        b. For Subject
        c. For an experiment - MR Session Datatype
     6. No other user should be able to create a form - site or project for any datatype
     7. No other user should be able to delete a form - site or project for any datatype
    */

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    public void testFormCreationAndDeletionByAdmin() throws IOException {
        Set<String> generatedFormUUIDs = new HashSet<>();
        XnatInterface mainAdminInterface = mainAdminInterface();
        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));
        final Project project1 = registerTempProject();
        mainAdminInterface.createProject(project1);
        generatedFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project1, 201));
        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS})
    public void testFormCreationWithBadTitle() throws IOException {
        XnatInterface mainAdminInterface = mainAdminInterface();
        String rawTestString = FileUtils.readFileToString(getDataFile("custom_forms/" + CustomFormConstants.SITE_FORM_TEMPLATES.get(0)), "UTF-8");
        rawTestString = rawTestString.replaceAll(CustomFormConstants.APPEND_FORM_NUMBER, "<img &gt;");
        saveFormAndAssert(mainAdminInterface, rawTestString, 400);
    }

    @Test(groups = {CUSTOM_FORMS})
    public void testFormStateOnDeletionOfProject() throws IOException {
        Set<String> generatedSiteFormUUIDs = new HashSet<>();
        Set<String> generatedProjectFormUUIDs = new HashSet<>();
        XnatInterface mainAdminInterface = mainAdminInterface();

        generatedSiteFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface,201));
        final Project project1 = registerTempProject();
        mainAdminInterface.createProject(project1);
        generatedProjectFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project1, 201));
        mainAdminInterface.deleteProject(project1);
        //Only the Site forms should exist
        List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
        for (CustomFormPojo form : formsOnSite) {
            assertFalse(generatedProjectFormUUIDs.contains(form.getFormUUID()));
        }
        deleteForms(mainAdminInterface(), generatedSiteFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testFormCreationByCustomFormManager() throws IOException {
        final User customFormManagerUser = getGenericUser();
        Set<String> generatedFormUUIDs = new HashSet<>();
        mainAdminInterface().assignUserToRoles(customFormManagerUser, "CustomFormManager");
        XnatInterface customFormManagerInterface = interfaceFor(customFormManagerUser);
        generatedFormUUIDs.addAll(createSiteSpecificForms(customFormManagerInterface, 201));

        final Project project1 = registerTempProject();
        mainAdminInterface().createProject(project1);
        generatedFormUUIDs.addAll(createProjectSpecificForms(customFormManagerInterface, project1, 201));
        deleteForms(customFormManagerInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS})
    @TestRequires(users = 1)
    public void testFormCanNotBeCreatedByOtherUser() throws IOException {
        final User genericUser = getGenericUser();
        for (String file : CustomFormConstants.SITE_FORM_TEMPLATES) {
            createForm(interfaceFor(genericUser), file, null, 403);
        }
        final Project projectNotOwned = registerTempProject();
        mainAdminInterface().createProject(projectNotOwned);
        for (String file : CustomFormConstants.PROJECT_FORM_TEMPLATES) {
           createForm(interfaceFor(genericUser), file, projectNotOwned.getId(), 403);
        }
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 3)
    public void testFormCreationByProjectOwner() throws IOException {
        final User owner = getGenericUser();
        final User member = getGenericUser();
        final User collaborator = getGenericUser();
        final XnatInterface projectOwnerInterface = interfaceFor(owner);
        final XnatInterface projectMemberInterface = interfaceFor(owner);
        final XnatInterface projectCollaboratorInterface = interfaceFor(owner);

        final Project project = registerTempProject();
        project.addOwner(owner);
        project.addMember(member);
        project.addCollaborator(collaborator);
        mainAdminInterface().createProject(project);

        final Project projectNotOwned = registerTempProject();
        mainAdminInterface().createProject(projectNotOwned);

        for (String file : CustomFormConstants.SITE_FORM_TEMPLATES) {
            createForm(projectOwnerInterface, file, null, 403);
        }
        for (String file : CustomFormConstants.PROJECT_FORM_TEMPLATES) {
            createForm(projectOwnerInterface, file, projectNotOwned.getId(), 403);
        }
        for (String file : CustomFormConstants.PROJECT_FORM_TEMPLATES) {
            createForm(projectMemberInterface, file, project.getId(), 403);
        }
        for (String file : CustomFormConstants.PROJECT_FORM_TEMPLATES) {
            createForm(projectCollaboratorInterface, file, project.getId(), 403);
        }
        Set<String> generatedFormUUIDs = createProjectSpecificForms(projectOwnerInterface, project, 403);
        assertEquals(generatedFormUUIDs.size(), 0);
    }


    @Test(groups = {CUSTOM_FORMS})
    @TestRequires(users = 1)
    public void testFormCanNotBeDeletedByOtherUser() throws IOException {
        final User genericUser = getGenericUser();
        XnatInterface genericUserInterface = interfaceFor(genericUser);
        XnatInterface mainAdminInterface = mainAdminInterface();
        Set<String> generatedFormUUIDs = new HashSet<>();
        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));
        final Project project1 = registerTempProject();
        mainAdminInterface.createProject(project1);
        generatedFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project1, 201));
        deleteForms(genericUserInterface, generatedFormUUIDs, 403);
        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }

}
