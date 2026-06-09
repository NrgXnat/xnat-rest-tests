package org.nrg.testing.xnat.tests.dicomwebproxy;

import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import io.restassured.response.Response;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
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
import org.nrg.testing.annotations.MutatesServerState;

/**
 * Integration tests for STOW-RS (store) endpoints at both project-scoped and site-wide scope.
 * Covers: basic upload, session merging, overwriting instances, new series in existing study,
 * permissions, and site-wide upload.
 */
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = "dicomwebplugin"))
@Test(groups = {PERMISSIONS})
@MutatesServerState
public class TestDicomWebStowRs extends BaseDicomWebProxyTest {

    // Site-wide STOW UIDs (separate from the static DATA_A/B to avoid conflicts)
    private static final String SITE_STOW_STUDY_UID = "2.25.77777777777777777777777777777777777";
    private static final String SITE_STOW_SERIES_UID = "2.25.88888888888888888888888888888888888";
    private static final String SITE_STOW_SOP_UID = "2.25.99999999999999999999999999999999999";

    private User memberUser;
    private User outsiderUser;
    private User allDataAdminUser;
    private Project projectA;
    private Project projectB;

    // DICOM data with StudyDescription matching projectA, for site-wide STOW routing
    private LocallyCacheableDicomTransformation siteWideStowData;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);
        outsiderUser = Users.genericAccount();
        allDataAdminUser = Users.genericAccount().dataRole(SiteDataRole.ALL_DATA_ADMIN);
        mainAdminInterface().createUser(outsiderUser);
        mainAdminInterface().createUser(allDataAdminUser);

        // Project A: memberUser is a member
        projectA = new Project("DWStowA" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(projectA);

        // Project B: no extra members (for permission tests)
        projectB = new Project("DWStowB" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE);
        mainAdminInterface().createProject(projectB);

        // Create DICOM data with StudyDescription = projectA ID for site-wide STOW routing
        siteWideStowData = createDataForProject(
                projectA.getId(), SITE_STOW_STUDY_UID, SITE_STOW_SERIES_UID, SITE_STOW_SOP_UID,
                "dicomweb_proxy_site_stow_" + projectA.getId());

        // Enable direct archive append so STOW-RS can add to existing sessions
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");

        // Enable site-wide for site-wide STOW tests
        enableSiteWide();
        setFilterMode("blacklist");
        setProjectList("");
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        resetSiteWideDefaults();
        restDriver.deleteProjectSilently(mainAdminUser, projectA);
        restDriver.deleteProjectSilently(mainAdminUser, projectB);
    }

    // ==================== Project-Scoped STOW-RS ====================

    /**
     * Basic upload: STOW DICOM data to a project the user has access to.
     * Verifies 200 response and that the study is then queryable via QIDO.
     */
    public void testProjectStowBasicUpload() {
        Response stowResponse = stowAs(memberUser, projectStowUrl(projectA), DATA_A);
        assertEquals(stowResponse.getStatusCode(), 200, "STOW-RS should return 200");
        String body = stowResponse.getBody().asString();
        assertTrue(body.contains(SOP_UID_A), "Response should reference the uploaded SOP UID");

        // Verify data is queryable
        Response qidoResponse = getAs(memberUser, projectStudiesUrl(projectA));
        assertEquals(qidoResponse.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(qidoResponse, STUDY_UID_A),
                "Uploaded study should be queryable via QIDO");
    }

    /**
     * Upload a new instance to an existing series (same study, same series, different SOP).
     * Should merge into the existing session, adding the new instance.
     */
    @Test(dependsOnMethods = "testProjectStowBasicUpload")
    public void testProjectStowAddInstanceToExistingSeries() {
        Response stowResponse = stowAs(memberUser, projectStowUrl(projectA), DATA_A_NEW_INSTANCE);
        assertEquals(stowResponse.getStatusCode(), 200, "STOW-RS merge should return 200");

        // Verify both instances are queryable in the same series
        Response instancesResponse = getAs(memberUser,
                projectInstancesUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertEquals(instancesResponse.getStatusCode(), 200);
        String instancesBody = instancesResponse.getBody().asString();
        assertTrue(instancesBody.contains(SOP_UID_A), "Original instance should still exist");
        assertTrue(instancesBody.contains(SOP_UID_A2), "New instance should be added to same series");
    }

    /**
     * Upload an instance with a new SeriesInstanceUID to an existing study.
     * Should create a new scan/series in the same session.
     */
    @Test(dependsOnMethods = "testProjectStowBasicUpload")
    public void testProjectStowNewSeriesInExistingStudy() {
        Response stowResponse = stowAs(memberUser, projectStowUrl(projectA), DATA_A_NEW_SERIES);
        assertEquals(stowResponse.getStatusCode(), 200, "STOW-RS new series should return 200");

        // Verify the new series exists alongside the original
        Response seriesResponse = getAs(memberUser,
                projectSeriesUrl(projectA, STUDY_UID_A));
        assertEquals(seriesResponse.getStatusCode(), 200);
        String seriesBody = seriesResponse.getBody().asString();
        assertTrue(seriesBody.contains(SERIES_UID_A), "Original series should still exist");
        assertTrue(seriesBody.contains(SERIES_UID_A2), "New series should be created in same study");
    }

    /**
     * Re-upload the same instance (same SOP UID) — should overwrite.
     * Default params have overwrite_files=true.
     */
    @Test(dependsOnMethods = "testProjectStowBasicUpload")
    public void testProjectStowOverwriteExistingInstance() {
        // Upload same data again (same SOP UID)
        Response stowResponse = stowAs(memberUser, projectStowUrl(projectA), DATA_A);
        assertEquals(stowResponse.getStatusCode(), 200, "STOW-RS overwrite should return 200");
        String body = stowResponse.getBody().asString();
        assertTrue(body.contains(SOP_UID_A), "Response should reference the overwritten SOP UID");

        // Verify the instance is still retrievable
        Response retrieveResponse = getAs(memberUser,
                projectInstanceUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(retrieveResponse.getStatusCode(), 200, "Overwritten instance should be retrievable");
    }

    /**
     * Non-member user should not be able to STOW to a private project.
     */
    public void testProjectStowNonMemberBlocked() {
        Response response = stowAs(outsiderUser, projectStowUrl(projectA), DATA_B);
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 401,
                "Non-member STOW should be blocked. Got: " + response.getStatusCode());
    }

    /**
     * ALL_DATA_ADMIN user should be able to STOW to any project.
     */
    public void testProjectStowAllDataAdmin() {
        Response response = stowAs(allDataAdminUser, projectStowUrl(projectB), DATA_B);
        assertEquals(response.getStatusCode(), 200,
                "ALL_DATA_ADMIN should be able to STOW to any project");

        // Verify data arrived
        Response qidoResponse = getAs(allDataAdminUser, projectStudiesUrl(projectB));
        assertEquals(qidoResponse.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(qidoResponse, STUDY_UID_B),
                "Study uploaded by ALL_DATA_ADMIN should be queryable");
    }

    // ==================== Site-Wide STOW-RS ====================

    /**
     * Site-wide STOW: upload without specifying a project.
     * Project is determined from DICOM StudyDescription field matching the project ID.
     * The DICOM data has StudyDescription set to projectA's ID.
     */
    public void testSiteWideStowUpload() {
        Response response = stowAsAdmin(siteStowUrl(), siteWideStowData);
        assertEquals(response.getStatusCode(), 200, "Site-wide STOW should return 200");

        // Verify the data was routed to projectA and is queryable
        Response qidoResponse = getAsAdmin(projectStudiesUrl(projectA));
        assertEquals(qidoResponse.getStatusCode(), 200);
        assertTrue(responseContainsStudyUID(qidoResponse, SITE_STOW_STUDY_UID),
                "Site-wide STOW data should be routed to projectA based on StudyDescription");
    }

    /**
     * Site-wide STOW should return 404 when site-wide is disabled.
     */
    public void testSiteWideStowDisabledReturns404() {
        try {
            disableSiteWide();
            Response response = stowAsAdmin(siteStowUrl(), siteWideStowData);
            assertEquals(response.getStatusCode(), 404,
                    "Site-wide STOW should return 404 when disabled");
        } finally {
            enableSiteWide();
        }
    }

    /**
     * Member user can use site-wide STOW when the DICOM routes to a project they have access to.
     * The StudyDescription routes the data to projectA, where memberUser is a member.
     */
    public void testSiteWideStowMemberAccess() {
        // Create fresh data to avoid UID conflicts with admin upload test
        LocallyCacheableDicomTransformation memberStowData = createDataForProject(
                projectA.getId(),
                "2.25.77777777777777777777777777777777778",
                "2.25.88888888888888888888888888888888889",
                "2.25.99999999999999999999999999999999998",
                "dicomweb_proxy_site_stow_member_" + projectA.getId());

        Response response = stowAs(memberUser, siteStowUrl(), memberStowData);
        assertEquals(response.getStatusCode(), 200,
                "Member should be able to STOW via site-wide when data routes to their project");
    }
}
