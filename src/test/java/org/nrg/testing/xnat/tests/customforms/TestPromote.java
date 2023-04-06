package org.nrg.testing.xnat.tests.customforms;

import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.customforms.pojo.CustomFormPojo;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.nrg.testing.TestGroups.CUSTOM_FORMS;
import static org.nrg.testing.TestGroups.PERMISSIONS;

public class TestPromote extends BaseCustomFormRestTest {
    /*
    Tests:
    Only Admins or Custom form manager can promote a form from project to site
     */

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    public void testPromoteByAdmin() throws IOException {
        Set<String> generatedFormUUIDs = new HashSet<>();
        final XnatInterface mainAdminInterface = mainAdminInterface();

        Project project = registerTempProject();
        generatedFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface, project, 201));
        generatedFormUUIDs.addAll(createSiteSpecificForms(mainAdminInterface, 201));

        List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface);
        formsOnSite.forEach(form -> {
                    if (form.getScope().equalsIgnoreCase(SITE_FORM)) {
                        sendRequest(mainAdminInterface, PROMOTE_URL, form.getAppliesToList(), 400);
                    }else {
                        sendRequest(mainAdminInterface, PROMOTE_URL, form.getAppliesToList(), 200);
                    }
                });
        deleteForms(mainAdminInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testPromoteByCustomFormManager() throws IOException {
        Set<String> generatedFormUUIDs = new HashSet<>();
        final User customFormManagerUser = getGenericUser();
        mainAdminInterface().assignUserToRoles(customFormManagerUser, CustomFormConstants.CUSTOM_FORM_MANAGER_ROLE_NAME);
        XnatInterface customFormManagerInterface = interfaceFor(customFormManagerUser);

        Project project = registerTempProject();
        generatedFormUUIDs.addAll(createProjectSpecificForms(customFormManagerInterface, project, 201));
        generatedFormUUIDs.addAll(createSiteSpecificForms(customFormManagerInterface, 201));

        List<CustomFormPojo> formsOnSite = fetchForms(customFormManagerInterface);
        formsOnSite.forEach(form -> {
            if (form.getScope().equalsIgnoreCase(SITE_FORM)) {
                sendRequest(customFormManagerInterface, PROMOTE_URL, form.getAppliesToList(), 400);
            }else {
                sendRequest(customFormManagerInterface, PROMOTE_URL, form.getAppliesToList(), 200);
            }
        });
        deleteForms(customFormManagerInterface, generatedFormUUIDs, 200);
    }

    @Test(groups = {CUSTOM_FORMS, PERMISSIONS})
    @TestRequires(users = 1)
    public void testPromoteByOtherUser() throws IOException {
        Set<String> generatedFormUUIDs = new HashSet<>();
        final User genericUser = getGenericUser();
        XnatInterface genericUserInterface = interfaceFor(genericUser);

        Project project = registerTempProject();
        generatedFormUUIDs.addAll(createProjectSpecificForms(mainAdminInterface(), project, 201));

        List<CustomFormPojo> formsOnSite = fetchForms(mainAdminInterface());
        formsOnSite.forEach(form ->
                sendRequest(genericUserInterface, PROMOTE_URL, form.getAppliesToList(), 403));

        deleteForms(mainAdminInterface(), generatedFormUUIDs, 200);
    }

    private final String PROMOTE_URL = "customforms/promote";
    private final String SITE_FORM = "SITE";

}
