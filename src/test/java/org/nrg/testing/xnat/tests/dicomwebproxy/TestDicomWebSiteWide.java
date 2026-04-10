package org.nrg.testing.xnat.tests.dicomwebproxy;

import io.restassured.response.Response;
import org.nrg.testing.util.RandomHelper;
import org.nrg.xnat.enums.Accessibility;
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
public class TestDicomWebSiteWide extends BaseDicomWebProxyTest {

    private User memberUser;
    private Project projectA;
    private Project projectB;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);

        projectA = new Project("DWSiteA" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        Subject subjectA = new Subject(projectA);
        ImagingSession sessionA = new MRSession(projectA, subjectA);
        new SessionImportExtension(sessionA, DATA_A.locateOverallZip().toFile());

        projectB = new Project("DWSiteB" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        Subject subjectB = new Subject(projectB);
        ImagingSession sessionB = new MRSession(projectB, subjectB);
        new SessionImportExtension(sessionB, DATA_B.locateOverallZip().toFile());

        mainAdminInterface().createProject(projectA);
        mainAdminInterface().createProject(projectB);

        // Enable site-wide with blacklist mode, empty list (all included)
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

    public void testSiteWideSearchStudies() {
        Response response = getAs(memberUser, siteStudiesUrl());
        assertEquals(response.getStatusCode(), 200, "Site-wide search should return 200");
        assertTrue(responseContainsStudyUID(response, STUDY_UID_A), "Should contain study A");
        assertTrue(responseContainsStudyUID(response, STUDY_UID_B), "Should contain study B");
    }

    public void testSiteWideSearchSeries() {
        Response response = getAs(memberUser, siteSeriesUrl(STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide series search should return 200");
        assertTrue(responseContainsStudyUID(response, SERIES_UID_A), "Should contain series A");
    }

    public void testSiteWideSearchInstances() {
        Response response = getAs(memberUser, siteInstancesUrl(STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide instances search should return 200");
        assertTrue(responseContainsStudyUID(response, SOP_UID_A), "Should contain SOP UID A");
    }

    public void testSiteWideRetrieveInstance() {
        Response response = getAs(memberUser, siteInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide instance retrieve should return 200");
    }

    public void testSiteWideRetrieveMetadata() {
        Response response = getAs(memberUser, siteMetadataUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide metadata retrieve should return 200");
    }

    // ---- Study/Series retrieval ----

    public void testSiteWideRetrieveStudy() {
        Response response = getAs(memberUser, siteStudyUrl(STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide study retrieval should return 200");
    }

    public void testSiteWideRetrieveSeries() {
        Response response = getAs(memberUser, siteSeriesRetrieveUrl(STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide series retrieval should return 200");
    }

    // ---- Study/Series metadata ----

    public void testSiteWideRetrieveStudyMetadata() {
        Response response = getAs(memberUser, siteStudyMetadataUrl(STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide study metadata should return 200");
    }

    public void testSiteWideRetrieveSeriesMetadata() {
        Response response = getAs(memberUser, siteSeriesMetadataUrl(STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide series metadata should return 200");
    }

    // ---- Rendered ----

    public void testSiteWideRenderedInstance() {
        Response response = getAs(memberUser, siteRenderedInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide rendered instance should return 200");
    }

    public void testSiteWideRenderedStudy() {
        Response response = getAs(memberUser, siteRenderedStudyUrl(STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide rendered study should return 200");
    }

    public void testSiteWideRenderedSeries() {
        Response response = getAs(memberUser, siteRenderedSeriesUrl(STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide rendered series should return 200");
    }

    // ---- Thumbnails ----

    public void testSiteWideThumbnailStudy() {
        Response response = getAs(memberUser, siteThumbnailStudyUrl(STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide study thumbnail should return 200");
    }

    public void testSiteWideThumbnailSeries() {
        Response response = getAs(memberUser, siteThumbnailSeriesUrl(STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide series thumbnail should return 200");
    }

    public void testSiteWideThumbnailInstance() {
        Response response = getAs(memberUser, siteThumbnailInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide instance thumbnail should return 200");
    }

    // ---- Frames ----

    public void testSiteWideRetrieveFrames() {
        Response response = getAs(memberUser, siteFramesUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "1"));
        assertEquals(response.getStatusCode(), 200, "Site-wide frame retrieval should return 200");
    }

    public void testSiteWideFrameRendered() {
        Response response = getAs(memberUser, siteFrameRenderedUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "1"));
        assertEquals(response.getStatusCode(), 200, "Site-wide frame rendered should return 200");
    }

    public void testSiteWideFrameThumbnail() {
        Response response = getAs(memberUser, siteFrameThumbnailUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "1"));
        assertEquals(response.getStatusCode(), 200, "Site-wide frame thumbnail should return 200");
    }

    // ---- Bulk data ----

    public void testSiteWideBulkDataByTag() {
        Response response = getAs(memberUser, siteBulkDataByTagUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "7FE00010"));
        assertEquals(response.getStatusCode(), 200, "Site-wide bulk data by tag should return 200");
    }

    public void testSiteWideInstanceBulkData() {
        Response response = getAs(memberUser, siteInstanceBulkDataUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide instance bulk data should return 200");
    }

    public void testSiteWideSeriesBulkData() {
        Response response = getAs(memberUser, siteSeriesBulkDataUrl(STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide series bulk data should return 200");
    }

    public void testSiteWideStudyBulkData() {
        Response response = getAs(memberUser, siteStudyBulkDataUrl(STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide study bulk data should return 200");
    }

    // ---- Pixel data ----

    public void testSiteWideInstancePixelData() {
        Response response = getAs(memberUser, siteInstancePixelDataUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide instance pixel data should return 200");
    }

    public void testSiteWideSeriesPixelData() {
        Response response = getAs(memberUser, siteSeriesPixelDataUrl(STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide series pixel data should return 200");
    }

    public void testSiteWideStudyPixelData() {
        Response response = getAs(memberUser, siteStudyPixelDataUrl(STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Site-wide study pixel data should return 200");
    }

    // ---- Filtering tests ----

    public void testSiteWideDisabledReturns404() {
        try {
            disableSiteWide();
            Response response = getAs(memberUser, siteStudiesUrl());
            assertEquals(response.getStatusCode(), 404, "Disabled site-wide should return 404");
        } finally {
            enableSiteWide();
        }
    }

    // ---- Blacklist filtering: studies, series, and instances ----

    public void testBlacklistExcludesProject() {
        try {
            setFilterMode("blacklist");
            setProjectList(projectB.getId());

            Response response = getAs(memberUser, siteStudiesUrl());
            assertEquals(response.getStatusCode(), 200);
            assertTrue(responseContainsStudyUID(response, STUDY_UID_A), "Study A should be included");
            assertFalse(responseContainsStudyUID(response, STUDY_UID_B), "Study B should be excluded (blacklisted)");
        } finally {
            setProjectList("");
        }
    }

    public void testBlacklistExcludesProjectSeries() {
        try {
            setFilterMode("blacklist");
            setProjectList(projectB.getId());

            // Series from non-blacklisted project A should be accessible
            Response responseA = getAs(memberUser, siteSeriesUrl(STUDY_UID_A));
            assertEquals(responseA.getStatusCode(), 200);
            assertTrue(responseContainsStudyUID(responseA, SERIES_UID_A), "Series A should be included");

            // Series from blacklisted project B should be blocked
            Response responseB = getAs(memberUser, siteSeriesUrl(STUDY_UID_B));
            assertTrue(responseB.getStatusCode() == 403 || responseB.getStatusCode() == 404
                            || (responseB.getStatusCode() == 200 && responseB.getBody().asString().equals("[]")),
                    "Series B should be excluded (blacklisted). Got status: " + responseB.getStatusCode());
        } finally {
            setProjectList("");
        }
    }

    public void testBlacklistExcludesProjectInstances() {
        try {
            setFilterMode("blacklist");
            setProjectList(projectB.getId());

            // Instances from non-blacklisted project A should be accessible
            Response responseA = getAs(memberUser, siteInstancesUrl(STUDY_UID_A, SERIES_UID_A));
            assertEquals(responseA.getStatusCode(), 200);
            assertTrue(responseContainsStudyUID(responseA, SOP_UID_A), "Instance A should be included");

            // Instances from blacklisted project B should be blocked
            Response responseB = getAs(memberUser, siteInstancesUrl(STUDY_UID_B, SERIES_UID_B));
            assertTrue(responseB.getStatusCode() == 403 || responseB.getStatusCode() == 404
                            || (responseB.getStatusCode() == 200 && responseB.getBody().asString().equals("[]")),
                    "Instances B should be excluded (blacklisted). Got status: " + responseB.getStatusCode());
        } finally {
            setProjectList("");
        }
    }

    public void testBlacklistExcludesProjectInstanceRetrieve() {
        try {
            setFilterMode("blacklist");
            setProjectList(projectB.getId());

            // Direct instance retrieve from non-blacklisted project A should work
            Response responseA = getAs(memberUser, siteInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
            assertEquals(responseA.getStatusCode(), 200, "Instance A retrieve should succeed");

            // Direct instance retrieve from blacklisted project B should be blocked
            Response responseB = getAs(memberUser, siteInstanceUrl(STUDY_UID_B, SERIES_UID_B, SOP_UID_B));
            assertTrue(responseB.getStatusCode() == 403 || responseB.getStatusCode() == 404,
                    "Instance B retrieve should be blocked (blacklisted). Got status: " + responseB.getStatusCode());
        } finally {
            setProjectList("");
        }
    }

    // ---- Whitelist filtering: studies, series, and instances ----

    public void testWhitelistIncludesOnlyListed() {
        try {
            setFilterMode("whitelist");
            setProjectList(projectA.getId());

            Response response = getAs(memberUser, siteStudiesUrl());
            assertEquals(response.getStatusCode(), 200);
            assertTrue(responseContainsStudyUID(response, STUDY_UID_A), "Study A should be included (whitelisted)");
            assertFalse(responseContainsStudyUID(response, STUDY_UID_B), "Study B should be excluded (not whitelisted)");
        } finally {
            setFilterMode("blacklist");
            setProjectList("");
        }
    }

    public void testWhitelistIncludesOnlyListedSeries() {
        try {
            setFilterMode("whitelist");
            setProjectList(projectA.getId());

            // Series from whitelisted project A should be accessible
            Response responseA = getAs(memberUser, siteSeriesUrl(STUDY_UID_A));
            assertEquals(responseA.getStatusCode(), 200);
            assertTrue(responseContainsStudyUID(responseA, SERIES_UID_A), "Series A should be included (whitelisted)");

            // Series from non-whitelisted project B should be blocked
            Response responseB = getAs(memberUser, siteSeriesUrl(STUDY_UID_B));
            assertTrue(responseB.getStatusCode() == 403 || responseB.getStatusCode() == 404
                            || (responseB.getStatusCode() == 200 && responseB.getBody().asString().equals("[]")),
                    "Series B should be excluded (not whitelisted). Got status: " + responseB.getStatusCode());
        } finally {
            setFilterMode("blacklist");
            setProjectList("");
        }
    }

    public void testWhitelistIncludesOnlyListedInstances() {
        try {
            setFilterMode("whitelist");
            setProjectList(projectA.getId());

            // Instances from whitelisted project A should be accessible
            Response responseA = getAs(memberUser, siteInstancesUrl(STUDY_UID_A, SERIES_UID_A));
            assertEquals(responseA.getStatusCode(), 200);
            assertTrue(responseContainsStudyUID(responseA, SOP_UID_A), "Instance A should be included (whitelisted)");

            // Instances from non-whitelisted project B should be blocked
            Response responseB = getAs(memberUser, siteInstancesUrl(STUDY_UID_B, SERIES_UID_B));
            assertTrue(responseB.getStatusCode() == 403 || responseB.getStatusCode() == 404
                            || (responseB.getStatusCode() == 200 && responseB.getBody().asString().equals("[]")),
                    "Instances B should be excluded (not whitelisted). Got status: " + responseB.getStatusCode());
        } finally {
            setFilterMode("blacklist");
            setProjectList("");
        }
    }

    public void testWhitelistIncludesOnlyListedInstanceRetrieve() {
        try {
            setFilterMode("whitelist");
            setProjectList(projectA.getId());

            // Direct instance retrieve from whitelisted project A should work
            Response responseA = getAs(memberUser, siteInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
            assertEquals(responseA.getStatusCode(), 200, "Instance A retrieve should succeed (whitelisted)");

            // Direct instance retrieve from non-whitelisted project B should be blocked
            Response responseB = getAs(memberUser, siteInstanceUrl(STUDY_UID_B, SERIES_UID_B, SOP_UID_B));
            assertTrue(responseB.getStatusCode() == 403 || responseB.getStatusCode() == 404,
                    "Instance B retrieve should be blocked (not whitelisted). Got status: " + responseB.getStatusCode());
        } finally {
            setFilterMode("blacklist");
            setProjectList("");
        }
    }

    public void testWhitelistEmptyExcludesAll() {
        try {
            setFilterMode("whitelist");
            setProjectList("");

            Response response = getAs(memberUser, siteStudiesUrl());
            assertEquals(response.getStatusCode(), 200);
            assertFalse(responseContainsStudyUID(response, STUDY_UID_A), "Study A should be excluded (empty whitelist)");
            assertFalse(responseContainsStudyUID(response, STUDY_UID_B), "Study B should be excluded (empty whitelist)");
        } finally {
            setFilterMode("blacklist");
            setProjectList("");
        }
    }

    public void testWhitelistEmptyExcludesAllSeries() {
        try {
            setFilterMode("whitelist");
            setProjectList("");

            Response responseA = getAs(memberUser, siteSeriesUrl(STUDY_UID_A));
            assertTrue(responseA.getStatusCode() == 403 || responseA.getStatusCode() == 404
                            || (responseA.getStatusCode() == 200 && responseA.getBody().asString().equals("[]")),
                    "Series A should be excluded (empty whitelist). Got status: " + responseA.getStatusCode());

            Response responseB = getAs(memberUser, siteSeriesUrl(STUDY_UID_B));
            assertTrue(responseB.getStatusCode() == 403 || responseB.getStatusCode() == 404
                            || (responseB.getStatusCode() == 200 && responseB.getBody().asString().equals("[]")),
                    "Series B should be excluded (empty whitelist). Got status: " + responseB.getStatusCode());
        } finally {
            setFilterMode("blacklist");
            setProjectList("");
        }
    }

    public void testWhitelistEmptyExcludesAllInstances() {
        try {
            setFilterMode("whitelist");
            setProjectList("");

            Response responseA = getAs(memberUser, siteInstancesUrl(STUDY_UID_A, SERIES_UID_A));
            assertTrue(responseA.getStatusCode() == 403 || responseA.getStatusCode() == 404
                            || (responseA.getStatusCode() == 200 && responseA.getBody().asString().equals("[]")),
                    "Instances A should be excluded (empty whitelist). Got status: " + responseA.getStatusCode());

            Response responseB = getAs(memberUser, siteInstancesUrl(STUDY_UID_B, SERIES_UID_B));
            assertTrue(responseB.getStatusCode() == 403 || responseB.getStatusCode() == 404
                            || (responseB.getStatusCode() == 200 && responseB.getBody().asString().equals("[]")),
                    "Instances B should be excluded (empty whitelist). Got status: " + responseB.getStatusCode());
        } finally {
            setFilterMode("blacklist");
            setProjectList("");
        }
    }

    // ---- Opt-out and combined filtering ----

    public void testProjectOptOutExcludesProject() {
        try {
            setProjectOptOut(projectB, true);

            Response response = getAs(memberUser, siteStudiesUrl());
            assertEquals(response.getStatusCode(), 200);
            assertTrue(responseContainsStudyUID(response, STUDY_UID_A), "Study A should be included");
            assertFalse(responseContainsStudyUID(response, STUDY_UID_B), "Study B should be excluded (opted out)");
        } finally {
            setProjectOptOut(projectB, false);
        }
    }

    public void testProjectOptOutExcludesSeriesAndInstances() {
        try {
            setProjectOptOut(projectB, true);

            // Series from opted-out project B should be blocked
            Response seriesResponse = getAs(memberUser, siteSeriesUrl(STUDY_UID_B));
            assertTrue(seriesResponse.getStatusCode() == 403 || seriesResponse.getStatusCode() == 404
                            || (seriesResponse.getStatusCode() == 200 && seriesResponse.getBody().asString().equals("[]")),
                    "Series B should be excluded (opted out). Got status: " + seriesResponse.getStatusCode());

            // Instances from opted-out project B should be blocked
            Response instancesResponse = getAs(memberUser, siteInstancesUrl(STUDY_UID_B, SERIES_UID_B));
            assertTrue(instancesResponse.getStatusCode() == 403 || instancesResponse.getStatusCode() == 404
                            || (instancesResponse.getStatusCode() == 200 && instancesResponse.getBody().asString().equals("[]")),
                    "Instances B should be excluded (opted out). Got status: " + instancesResponse.getStatusCode());
        } finally {
            setProjectOptOut(projectB, false);
        }
    }

    public void testBlacklistAndOptOutCombined() {
        try {
            setFilterMode("blacklist");
            setProjectList(projectA.getId());
            setProjectOptOut(projectB, true);

            Response response = getAs(memberUser, siteStudiesUrl());
            assertEquals(response.getStatusCode(), 200);
            assertFalse(responseContainsStudyUID(response, STUDY_UID_A), "Study A should be excluded (blacklisted)");
            assertFalse(responseContainsStudyUID(response, STUDY_UID_B), "Study B should be excluded (opted out)");
        } finally {
            setProjectList("");
            setProjectOptOut(projectB, false);
        }
    }
}
