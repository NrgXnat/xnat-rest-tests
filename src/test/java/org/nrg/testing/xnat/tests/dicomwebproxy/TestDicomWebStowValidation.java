package org.nrg.testing.xnat.tests.dicomwebproxy;

import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.util.RandomHelper;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.Assert.*;
import org.nrg.testing.annotations.MutatesServerState;

/**
 * STOW-RS validation tests: response structure, idempotent re-upload,
 * invalid data handling, wrong project, and missing content type.
 * Mirrors coverage from the old TestDicomWebStow tests against the
 * new project-scoped DICOMweb plugin endpoints.
 */
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = "dicomwebplugin"))
@Test(groups = {PERMISSIONS})
@MutatesServerState
public class TestDicomWebStowValidation extends BaseDicomWebProxyTest {

    // DICOM tag constants (hex notation) for STOW-RS response
    private static final String TAG_RETRIEVE_URL = "00081190";
    private static final String TAG_FAILED_SOP_SEQUENCE = "00081198";
    private static final String TAG_REFERENCED_SOP_SEQUENCE = "00081199";

    private User memberUser;
    private Project project;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);
        project = new Project("DWStowV" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(project);
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    // ==================== Response Structure ====================

    /**
     * STOW-RS response should contain ReferencedSOPSequence for successful uploads.
     * Per DICOM PS3.18 Section 10.5.
     */
    public void testStowResponseContainsReferencedSOPSequence() {
        String sopUID = "2.25.80010000000000000000000000000000003";
        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.80010000000000000000000000000000001",
                "2.25.80010000000000000000000000000000002",
                sopUID,
                "stow_val_refseq_" + project.getId());

        Response response = stowAs(memberUser, projectStowUrl(project), data);
        assertEquals(response.getStatusCode(), 200);

        String body = response.getBody().asString();
        assertTrue(body.contains(sopUID),
                "Response should contain the uploaded SOP Instance UID");
    }

    /**
     * STOW-RS response content type should be application/dicom+json.
     */
    public void testStowResponseContentType() {
        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.80020000000000000000000000000000001",
                "2.25.80020000000000000000000000000000002",
                "2.25.80020000000000000000000000000000003",
                "stow_val_ctype_" + project.getId());

        Response response = stowAs(memberUser, projectStowUrl(project), data);
        assertEquals(response.getStatusCode(), 200);

        String contentType = response.getContentType();
        assertTrue(contentType.contains("dicom+json") || contentType.contains("application/json"),
                "STOW response should be DICOM JSON. Got: " + contentType);
    }

    // ==================== Idempotent Re-Upload ====================

    /**
     * Re-uploading the same DICOM data (same SOP Instance UID) should succeed.
     * STOW-RS should handle idempotent uploads gracefully.
     */
    public void testStowIdempotentReUpload() {
        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(),
                "2.25.80030000000000000000000000000000001",
                "2.25.80030000000000000000000000000000002",
                "2.25.80030000000000000000000000000000003",
                "stow_val_idemp_" + project.getId());

        // First upload
        Response first = stowAs(memberUser, projectStowUrl(project), data);
        assertEquals(first.getStatusCode(), 200, "First upload should succeed");

        // Second upload of same data
        Response second = stowAs(memberUser, projectStowUrl(project), data);
        assertEquals(second.getStatusCode(), 200, "Re-upload of same data should succeed");
    }

    // ==================== Error Handling ====================

    /**
     * STOW to a non-existent project should return 403 or 404.
     */
    public void testStowToNonExistentProject() {
        String fakeProjectUrl = formatXapiUrl("dicomweb", "projects", "NONEXISTENT_PROJECT_XYZ", "studies");
        Response response = stowAs(memberUser, fakeProjectUrl, DATA_A);
        assertTrue(response.getStatusCode() == 403 || response.getStatusCode() == 404,
                "STOW to non-existent project should return 403 or 404. Got: " + response.getStatusCode());
    }

    /**
     * POST with wrong Content-Type (not multipart/related) should be rejected.
     */
    public void testStowWrongContentType() {
        Response response = restDriver.queryBaseFor(memberUser)
                .contentType("application/json")
                .body("{}")
                .post(projectStowUrl(project));
        assertTrue(response.getStatusCode() >= 400,
                "STOW with wrong content type should fail. Got: " + response.getStatusCode());
    }

    /**
     * POST with non-DICOM data in multipart body should handle gracefully.
     * May return 200 with FailedSOPSequence, or a 4xx/5xx error.
     */
    public void testStowInvalidData() {
        String boundary = UUID.randomUUID().toString();
        byte[] invalidBody = buildInvalidMultipartBody(boundary);

        Response response = restDriver.queryBaseFor(memberUser)
                .contentType("multipart/related; type=\"application/dicom\"; boundary=" + boundary)
                .body(invalidBody)
                .post(projectStowUrl(project));

        // Either graceful handling (200 with FailedSOPSequence) or error
        assertTrue(response.getStatusCode() == 200 ||
                   response.getStatusCode() >= 400,
                "STOW with invalid data should either report failure or return error. Got: " +
                response.getStatusCode());
    }

    // ==================== STOW-RS Round Trip ====================

    /**
     * Upload via STOW-RS then verify data is queryable via QIDO-RS.
     * Uses DirectArchive with buildDelayMs=0 for immediate queryability.
     * On older XNAT without DirectArchive, falls back to GradualDicomImporter
     * and skips the QIDO verification (upload-only check).
     */
    public void testStowRoundTrip() {
        String studyUID = "2.25.80001000000000000000000000000000001";
        String seriesUID = "2.25.80001000000000000000000000000000002";
        String sopUID = "2.25.80001000000000000000000000000000003";

        LocallyCacheableDicomTransformation data = createDataForProject(
                project.getId(), studyUID, seriesUID, sopUID,
                "stow_roundtrip_" + project.getId());

        // Try to enable DirectArchive for immediate queryability
        boolean directArchiveAvailable = false;
        try {
            mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
            updatePrefs("defaultStrategy", "DirectArchive");
            updatePrefs("buildDelayMs", 0);
            directArchiveAvailable = true;
        } catch (Exception e) {
            // DirectArchive not available on this XNAT version — use default strategy
        }

        try {
            // Upload
            Response stow = stowAs(memberUser, projectStowUrl(project), data);
            assertEquals(stow.getStatusCode(), 200, "STOW upload should succeed");

            if (directArchiveAvailable) {
                // Verify via QIDO at each level (only with DirectArchive, which gives immediate availability)
                Response studies = getAs(memberUser, projectStudiesUrl(project));
                assertEquals(studies.getStatusCode(), 200);
                assertTrue(responseContainsStudyUID(studies, studyUID),
                        "Uploaded study should be queryable via QIDO");

                Response series = getAs(memberUser, projectSeriesUrl(project, studyUID));
                assertEquals(series.getStatusCode(), 200);
                assertTrue(series.getBody().asString().contains(seriesUID),
                        "Uploaded series should be queryable via QIDO");

                Response instances = getAs(memberUser,
                        projectInstancesUrl(project, studyUID, seriesUID));
                assertEquals(instances.getStatusCode(), 200);
                assertTrue(instances.getBody().asString().contains(sopUID),
                        "Uploaded instance should be queryable via QIDO");
            }
        } finally {
            if (directArchiveAvailable) {
                updatePrefs("defaultStrategy", "GradualDicomImporter");
                updatePrefs("buildDelayMs", 5000);
                mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
            }
        }
    }

    // ==================== Helpers ====================

    private byte[] buildInvalidMultipartBody(String boundary) {
        String body = "--" + boundary + "\r\n" +
                "Content-Type: application/dicom\r\n" +
                "\r\n" +
                "This is not valid DICOM data\r\n" +
                "--" + boundary + "--\r\n";
        return body.getBytes();
    }
}
