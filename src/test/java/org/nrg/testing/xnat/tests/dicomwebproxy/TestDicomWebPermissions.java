package org.nrg.testing.xnat.tests.dicomwebproxy;

import io.restassured.response.Response;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.Users;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.SiteDataRole;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.Assert.*;

@Test(groups = {PERMISSIONS})
public class TestDicomWebPermissions extends BaseDicomWebProxyTest {

    private User memberUser;
    private User outsiderUser;
    private User allDataAdminUser;
    private User allDataAccessUser;
    private Project projectA;
    private Project projectB;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);
        outsiderUser = Users.genericAccount();
        allDataAdminUser = Users.genericAccount().dataRole(SiteDataRole.ALL_DATA_ADMIN);
        allDataAccessUser = Users.genericAccount().dataRole(SiteDataRole.ALL_DATA_ACCESS);

        mainAdminInterface().createUser(outsiderUser);
        mainAdminInterface().createUser(allDataAdminUser);
        mainAdminInterface().createUser(allDataAccessUser);

        // Project A: memberUser is a member
        projectA = new Project("DWPermA" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        Subject subjectA = new Subject(projectA);
        ImagingSession sessionA = new MRSession(projectA, subjectA);
        new SessionImportExtension(sessionA, DATA_A.locateOverallZip().toFile());

        // Project B: no extra members (only admin/all-data users can see it)
        projectB = new Project("DWPermB" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE);
        Subject subjectB = new Subject(projectB);
        ImagingSession sessionB = new MRSession(projectB, subjectB);
        new SessionImportExtension(sessionB, DATA_B.locateOverallZip().toFile());

        mainAdminInterface().createProject(projectA);
        mainAdminInterface().createProject(projectB);

        enableSiteWide();
        setFilterMode("blacklist");
        setProjectList("");
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        resetSiteWideDefaults();
        restDriver.deleteProjectSilently(mainAdminUser, projectA);
        restDriver.deleteProjectSilently(mainAdminUser, projectB);
    }

    public void testNonMemberBlockedProjectScoped() {
        Response response = getAs(outsiderUser, projectStudiesUrl(projectA));
        // Non-member should get empty results or 403
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404
                        || (response.getStatusCode() == 200 && response.getBody().asString().equals("[]")),
                "Non-member should not see project data. Got status: " + response.getStatusCode());
    }

    public void testNonMemberCantSeePrivateSiteWide() {
        Response response = getAs(outsiderUser, siteStudiesUrl());
        assertEquals(response.getStatusCode(), 200, "Site-wide should return 200");
        assertFalse(responseContainsStudyUID(response, STUDY_UID_A),
                "Outsider should not see private project A in site-wide");
        assertFalse(responseContainsStudyUID(response, STUDY_UID_B),
                "Outsider should not see private project B in site-wide");
    }

    public void testAllDataAdminSeesAllSiteWide() {
        Response response = getAs(allDataAdminUser, siteStudiesUrl());
        assertEquals(response.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(response, STUDY_UID_A),
                "ALL_DATA_ADMIN should see study A in site-wide");
        assertTrue(responseContainsStudyUID(response, STUDY_UID_B),
                "ALL_DATA_ADMIN should see study B in site-wide");
    }

    public void testAllDataAccessSeesAllSiteWide() {
        Response response = getAs(allDataAccessUser, siteStudiesUrl());
        assertEquals(response.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(response, STUDY_UID_A),
                "ALL_DATA_ACCESS should see study A in site-wide");
        assertTrue(responseContainsStudyUID(response, STUDY_UID_B),
                "ALL_DATA_ACCESS should see study B in site-wide");
    }

    public void testAllDataAdminProjectScoped() {
        Response response = getAs(allDataAdminUser, projectStudiesUrl(projectB));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(response, STUDY_UID_B),
                "ALL_DATA_ADMIN should see study B in project-scoped query");
    }

    public void testAllDataAccessProjectScoped() {
        Response response = getAs(allDataAccessUser, projectStudiesUrl(projectB));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(response, STUDY_UID_B),
                "ALL_DATA_ACCESS should see study B in project-scoped query");
    }

    public void testMemberSeesOnlyOwnProjectSiteWide() {
        Response response = getAs(memberUser, siteStudiesUrl());
        assertEquals(response.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(response, STUDY_UID_A),
                "Member should see study A (is member of project A)");
        assertFalse(responseContainsStudyUID(response, STUDY_UID_B),
                "Member should NOT see study B (not member of project B)");
    }
}
