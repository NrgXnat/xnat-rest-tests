package org.nrg.testing.xnat.tests.dicomwebproxy;

import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
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

@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = "dicomwebplugin"))
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

    // ---- Project-scoped: non-member blocked at study, series, and instance level ----

    public void testNonMemberBlockedProjectScoped() {
        Response response = getAs(outsiderUser, projectStudiesUrl(projectA));
        // Non-member should get empty results or 403
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404
                        || (response.getStatusCode() == 200 && response.getBody().asString().equals("[]")),
                "Non-member should not see project data. Got status: " + response.getStatusCode());
    }

    public void testNonMemberBlockedProjectScopedSeries() {
        Response response = getAs(outsiderUser, projectSeriesUrl(projectA, STUDY_UID_A));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404
                        || (response.getStatusCode() == 200 && response.getBody().asString().equals("[]")),
                "Non-member should not see project series. Got status: " + response.getStatusCode());
    }

    public void testNonMemberBlockedProjectScopedInstances() {
        Response response = getAs(outsiderUser, projectInstancesUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404
                        || (response.getStatusCode() == 200 && response.getBody().asString().equals("[]")),
                "Non-member should not see project instances. Got status: " + response.getStatusCode());
    }

    public void testNonMemberBlockedProjectScopedInstanceRetrieve() {
        Response response = getAs(outsiderUser, projectInstanceUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404,
                "Non-member should not retrieve instance directly. Got status: " + response.getStatusCode());
    }

    public void testNonMemberBlockedProjectScopedMetadata() {
        Response response = getAs(outsiderUser, projectMetadataUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404,
                "Non-member should not retrieve instance metadata. Got status: " + response.getStatusCode());
    }

    // ---- Site-wide: non-member blocked at study, series, and instance level ----

    public void testNonMemberCantSeePrivateSiteWide() {
        Response response = getAs(outsiderUser, siteStudiesUrl());
        assertEquals(response.getStatusCode(), 200, "Site-wide should return 200");
        assertFalse(responseContainsStudyUID(response, STUDY_UID_A),
                "Outsider should not see private project A in site-wide");
        assertFalse(responseContainsStudyUID(response, STUDY_UID_B),
                "Outsider should not see private project B in site-wide");
    }

    public void testNonMemberCantSeePrivateSeriesSiteWide() {
        Response response = getAs(outsiderUser, siteSeriesUrl(STUDY_UID_A));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404
                        || (response.getStatusCode() == 200 && response.getBody().asString().equals("[]")),
                "Outsider should not see series of private project A in site-wide. Got status: " + response.getStatusCode());
    }

    public void testNonMemberCantSeePrivateInstancesSiteWide() {
        Response response = getAs(outsiderUser, siteInstancesUrl(STUDY_UID_A, SERIES_UID_A));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404
                        || (response.getStatusCode() == 200 && response.getBody().asString().equals("[]")),
                "Outsider should not see instances of private project A in site-wide. Got status: " + response.getStatusCode());
    }

    public void testNonMemberCantRetrievePrivateInstanceSiteWide() {
        Response response = getAs(outsiderUser, siteInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404,
                "Outsider should not retrieve instance from private project via site-wide. Got status: " + response.getStatusCode());
    }

    public void testNonMemberCantRetrievePrivateMetadataSiteWide() {
        Response response = getAs(outsiderUser, siteMetadataUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404,
                "Outsider should not retrieve metadata from private project via site-wide. Got status: " + response.getStatusCode());
    }

    // ---- ALL_DATA_ADMIN and ALL_DATA_ACCESS at study level ----

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

    // ---- Member: sees own project data, blocked from other project at all levels ----

    public void testMemberSeesOnlyOwnProjectSiteWide() {
        Response response = getAs(memberUser, siteStudiesUrl());
        assertEquals(response.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(response, STUDY_UID_A),
                "Member should see study A (is member of project A)");
        assertFalse(responseContainsStudyUID(response, STUDY_UID_B),
                "Member should NOT see study B (not member of project B)");
    }

    public void testMemberSeesOwnSeriesSiteWide() {
        Response response = getAs(memberUser, siteSeriesUrl(STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(response, SERIES_UID_A),
                "Member should see series A in site-wide (is member of project A)");
    }

    public void testMemberCantSeeOtherSeriesSiteWide() {
        Response response = getAs(memberUser, siteSeriesUrl(STUDY_UID_B));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404
                        || (response.getStatusCode() == 200 && response.getBody().asString().equals("[]")),
                "Member should not see series of project B in site-wide. Got status: " + response.getStatusCode());
    }

    public void testMemberSeesOwnInstancesSiteWide() {
        Response response = getAs(memberUser, siteInstancesUrl(STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(response, SOP_UID_A),
                "Member should see instances of project A in site-wide");
    }

    public void testMemberCantSeeOtherInstancesSiteWide() {
        Response response = getAs(memberUser, siteInstancesUrl(STUDY_UID_B, SERIES_UID_B));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404
                        || (response.getStatusCode() == 200 && response.getBody().asString().equals("[]")),
                "Member should not see instances of project B in site-wide. Got status: " + response.getStatusCode());
    }

    public void testMemberCantRetrieveOtherInstanceSiteWide() {
        Response response = getAs(memberUser, siteInstanceUrl(STUDY_UID_B, SERIES_UID_B, SOP_UID_B));
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404,
                "Member should not retrieve instance from project B via site-wide. Got status: " + response.getStatusCode());
    }
}
