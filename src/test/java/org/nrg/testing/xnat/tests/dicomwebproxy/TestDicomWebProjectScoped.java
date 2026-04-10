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
public class TestDicomWebProjectScoped extends BaseDicomWebProxyTest {

    private User memberUser;
    private Project projectA;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);
        projectA = new Project("DWProjA" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);

        Subject subjectA = new Subject(projectA);
        ImagingSession sessionA = new MRSession(projectA, subjectA);
        new SessionImportExtension(sessionA, DATA_A.locateOverallZip().toFile());

        mainAdminInterface().createProject(projectA);
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        restDriver.deleteProjectSilently(mainAdminUser, projectA);
    }

    public void testSearchStudies() {
        Response response = getAs(memberUser, projectStudiesUrl(projectA));
        assertEquals(response.getStatusCode(), 200, "QIDO search studies should return 200");
        assertTrue(responseContainsStudyUID(response, STUDY_UID_A),
                "Response should contain study UID A");
    }

    public void testSearchSeries() {
        Response response = getAs(memberUser, projectSeriesUrl(projectA, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "QIDO search series should return 200");
        assertTrue(responseContainsStudyUID(response, SERIES_UID_A),
                "Response should contain series UID A");
    }

    public void testSearchInstances() {
        Response response = getAs(memberUser, projectInstancesUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "QIDO search instances should return 200");
        assertTrue(responseContainsStudyUID(response, SOP_UID_A),
                "Response should contain SOP instance UID A");
    }

    public void testRetrieveInstance() {
        Response response = getAs(memberUser, projectInstanceUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "WADO retrieve instance should return 200");
    }

    public void testRetrieveMetadata() {
        Response response = getAs(memberUser, projectMetadataUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "WADO retrieve metadata should return 200");
    }

    // ---- Study/Series retrieval ----

    public void testRetrieveStudy() {
        Response response = getAs(memberUser, projectStudyUrl(projectA, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "WADO retrieve study should return 200");
    }

    public void testRetrieveSeries() {
        Response response = getAs(memberUser, projectSeriesRetrieveUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "WADO retrieve series should return 200");
    }

    // ---- Study/Series metadata ----

    public void testRetrieveStudyMetadata() {
        Response response = getAs(memberUser, projectStudyMetadataUrl(projectA, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "WADO study metadata should return 200");
    }

    public void testRetrieveSeriesMetadata() {
        Response response = getAs(memberUser, projectSeriesMetadataUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "WADO series metadata should return 200");
    }

    // ---- Rendered ----

    public void testRetrieveRenderedInstance() {
        Response response = getAs(memberUser, projectRenderedInstanceUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Rendered instance should return 200");
    }

    public void testRetrieveRenderedStudy() {
        Response response = getAs(memberUser, projectRenderedStudyUrl(projectA, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Rendered study should return 200");
    }

    public void testRetrieveRenderedSeries() {
        Response response = getAs(memberUser, projectRenderedSeriesUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Rendered series should return 200");
    }

    // ---- Thumbnails ----

    public void testRetrieveThumbnailStudy() {
        Response response = getAs(memberUser, projectThumbnailStudyUrl(projectA, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Study thumbnail should return 200");
    }

    public void testRetrieveThumbnailSeries() {
        Response response = getAs(memberUser, projectThumbnailSeriesUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Series thumbnail should return 200");
    }

    public void testRetrieveThumbnailInstance() {
        Response response = getAs(memberUser, projectThumbnailInstanceUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Instance thumbnail should return 200");
    }

    // ---- Frames ----

    public void testRetrieveFrames() {
        Response response = getAs(memberUser, projectFramesUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "1"));
        assertEquals(response.getStatusCode(), 200, "Frame retrieval should return 200");
    }

    public void testRetrieveFrameRendered() {
        Response response = getAs(memberUser, projectFrameRenderedUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "1"));
        assertEquals(response.getStatusCode(), 200, "Frame rendered should return 200");
    }

    public void testRetrieveFrameThumbnail() {
        Response response = getAs(memberUser, projectFrameThumbnailUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "1"));
        assertEquals(response.getStatusCode(), 200, "Frame thumbnail should return 200");
    }

    // ---- Bulk data ----

    public void testRetrieveBulkDataByTag() {
        // 7FE00010 = Pixel Data tag
        Response response = getAs(memberUser, projectBulkDataByTagUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "7FE00010"));
        assertEquals(response.getStatusCode(), 200, "Bulk data by tag should return 200");
    }

    public void testRetrieveInstanceBulkData() {
        Response response = getAs(memberUser, projectInstanceBulkDataUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Instance bulk data should return 200");
    }

    public void testRetrieveSeriesBulkData() {
        Response response = getAs(memberUser, projectSeriesBulkDataUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Series bulk data should return 200");
    }

    public void testRetrieveStudyBulkData() {
        Response response = getAs(memberUser, projectStudyBulkDataUrl(projectA, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Study bulk data should return 200");
    }

    // ---- Pixel data ----

    public void testRetrieveInstancePixelData() {
        Response response = getAs(memberUser, projectInstancePixelDataUrl(projectA, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200, "Instance pixel data should return 200");
    }

    public void testRetrieveSeriesPixelData() {
        Response response = getAs(memberUser, projectSeriesPixelDataUrl(projectA, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200, "Series pixel data should return 200");
    }

    public void testRetrieveStudyPixelData() {
        Response response = getAs(memberUser, projectStudyPixelDataUrl(projectA, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200, "Study pixel data should return 200");
    }

    // ---- Negative test ----

    public void testSearchStudiesWrongProject() {
        // Query against a project that has no data - should return empty
        Project emptyProject = new Project("DWEmpty" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(emptyProject);

        try {
            Response response = getAs(memberUser, projectStudiesUrl(emptyProject));
            assertEquals(response.getStatusCode(), 200, "Empty project should return 200");
            assertEquals(response.getBody().asString(), "[]", "Empty project should return empty array");
        } finally {
            restDriver.deleteProjectSilently(mainAdminUser, emptyProject);
        }
    }
}
