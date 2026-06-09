package org.nrg.testing.xnat.tests.dicomwebproxy;

import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import io.restassured.response.Response;
import org.nrg.testing.util.RandomHelper;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.Assert.*;
import org.nrg.testing.annotations.MutatesServerState;

/**
 * WADO-RS error handling tests: non-existent resources, invalid UIDs, wrong project.
 * Mirrors coverage from the old TestDicomWebWado negative tests against the new
 * project-scoped and site-wide DICOMweb plugin endpoints.
 */
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = "dicomwebplugin"))
@Test(groups = {PERMISSIONS})
@MutatesServerState
public class TestDicomWebWadoErrors extends BaseDicomWebProxyTest {

    private static final String FAKE_STUDY = "2.25.00000000000000000000000000000000001";
    private static final String FAKE_SERIES = "2.25.00000000000000000000000000000000002";
    private static final String FAKE_INSTANCE = "2.25.00000000000000000000000000000000003";

    private User memberUser;
    private Project project;
    private Project emptyProject;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);

        project = new Project("DWWado" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(project);

        emptyProject = new Project("DWEmpty" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(emptyProject);

        // Use DirectArchive with no delay so data is immediately queryable
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        // Upload test data to project
        stowAs(memberUser, projectStowUrl(project), DATA_A);
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        updatePrefs("defaultStrategy", "GradualDicomImporter");
        updatePrefs("buildDelayMs", 5000);
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        restDriver.deleteProjectSilently(mainAdminUser, project);
        restDriver.deleteProjectSilently(mainAdminUser, emptyProject);
    }

    // ==================== Non-Existent Instance Retrieval ====================

    /**
     * Retrieve a non-existent instance should return 404.
     */
    public void testRetrieveNonExistentInstance() {
        Response response = getAs(memberUser,
                projectInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, FAKE_INSTANCE));
        assertEquals(response.getStatusCode(), 404,
                "Non-existent instance retrieval should return 404");
    }

    /**
     * Retrieve metadata for a non-existent instance should return 404.
     */
    public void testRetrieveMetadataNonExistentInstance() {
        Response response = getAs(memberUser,
                projectMetadataUrl(project, STUDY_UID_A, SERIES_UID_A, FAKE_INSTANCE));
        assertEquals(response.getStatusCode(), 404,
                "Non-existent instance metadata should return 404");
    }

    /**
     * Retrieve an instance from a non-existent study should return 404.
     */
    public void testRetrieveFromNonExistentStudy() {
        Response response = getAs(memberUser,
                projectInstanceUrl(project, FAKE_STUDY, FAKE_SERIES, FAKE_INSTANCE));
        assertEquals(response.getStatusCode(), 404,
                "Retrieval from non-existent study should return 404");
    }

    // ==================== Non-Existent Rendered/Thumbnail ====================

    /**
     * Rendered view of non-existent study should return 404.
     */
    public void testRenderedNonExistentStudy() {
        Response response = getAs(memberUser,
                projectRenderedStudyUrl(project, FAKE_STUDY));
        assertEquals(response.getStatusCode(), 404,
                "Rendered of non-existent study should return 404");
    }

    /**
     * Rendered view of non-existent series should return 404.
     */
    public void testRenderedNonExistentSeries() {
        Response response = getAs(memberUser,
                projectRenderedSeriesUrl(project, STUDY_UID_A, FAKE_SERIES));
        assertEquals(response.getStatusCode(), 404,
                "Rendered of non-existent series should return 404");
    }

    /**
     * Rendered view of non-existent instance should return 404.
     */
    public void testRenderedNonExistentInstance() {
        Response response = getAs(memberUser,
                projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, FAKE_INSTANCE));
        assertEquals(response.getStatusCode(), 404,
                "Rendered of non-existent instance should return 404");
    }

    /**
     * Thumbnail of non-existent study should return 404.
     */
    public void testThumbnailNonExistentStudy() {
        Response response = getAs(memberUser,
                projectThumbnailStudyUrl(project, FAKE_STUDY));
        assertEquals(response.getStatusCode(), 404,
                "Thumbnail of non-existent study should return 404");
    }

    /**
     * Thumbnail of non-existent series should return 404.
     */
    public void testThumbnailNonExistentSeries() {
        Response response = getAs(memberUser,
                projectThumbnailSeriesUrl(project, STUDY_UID_A, FAKE_SERIES));
        assertEquals(response.getStatusCode(), 404,
                "Thumbnail of non-existent series should return 404");
    }

    /**
     * Thumbnail of non-existent instance should return 404.
     */
    public void testThumbnailNonExistentInstance() {
        Response response = getAs(memberUser,
                projectThumbnailInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, FAKE_INSTANCE));
        assertEquals(response.getStatusCode(), 404,
                "Thumbnail of non-existent instance should return 404");
    }

    /**
     * Frame rendered of non-existent instance should return 404.
     */
    public void testFrameRenderedNonExistentInstance() {
        Response response = getAs(memberUser,
                projectFrameRenderedUrl(project, STUDY_UID_A, SERIES_UID_A, FAKE_INSTANCE, "1"));
        assertEquals(response.getStatusCode(), 404,
                "Frame rendered of non-existent instance should return 404");
    }

    // ==================== Empty Project ====================

    /**
     * QIDO on empty project should return 200 with empty array.
     */
    public void testSearchStudiesEmptyProject() {
        Response response = getAs(memberUser, projectStudiesUrl(emptyProject));
        assertEquals(response.getStatusCode(), 200);

        String body = response.getBody().asString();
        assertEquals(body.trim(), "[]",
                "Empty project should return empty JSON array");
    }

    // ==================== Metadata Response Validation ====================

    /**
     * Instance metadata endpoint should return DICOM JSON (not raw DICOM).
     */
    public void testMetadataReturnsDicomJson() {
        Response response = getAs(memberUser,
                projectMetadataUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);

        String contentType = response.getContentType();
        assertTrue(contentType.contains("dicom+json") || contentType.contains("application/json"),
                "Metadata should return DICOM JSON content type. Got: " + contentType);
    }

    /**
     * Study metadata should return metadata for all instances in the study.
     */
    public void testStudyMetadataReturnsArray() {
        Response response = getAs(memberUser,
                projectStudyMetadataUrl(project, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200);

        // Should be a JSON array of instance metadata
        String body = response.getBody().asString();
        assertTrue(body.startsWith("["),
                "Study metadata should return a JSON array");
    }

    /**
     * Series metadata should return metadata for all instances in the series.
     */
    public void testSeriesMetadataReturnsArray() {
        Response response = getAs(memberUser,
                projectSeriesMetadataUrl(project, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200);

        String body = response.getBody().asString();
        assertTrue(body.startsWith("["),
                "Series metadata should return a JSON array");
    }
}
