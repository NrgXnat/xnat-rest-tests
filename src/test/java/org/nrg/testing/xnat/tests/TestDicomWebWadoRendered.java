package org.nrg.testing.xnat.tests;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.CTSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.nrg.testing.TestGroups.*;
import static org.testng.Assert.*;

/**
 * Functional Tests: WADO-RS Rendered & Thumbnail Endpoints
 *
 * Tests DICOMweb WADO-RS rendered and thumbnail endpoints:
 *
 * Rendered endpoints:
 * - GET .../studies/{study}/rendered
 * - GET .../studies/{study}/series/{series}/rendered
 * - GET .../instances/{instance}/rendered  (existing, now with rendering params)
 * - GET .../instances/{instance}/frames/{frames}/rendered
 *
 * Thumbnail endpoints:
 * - GET .../studies/{study}/thumbnail
 * - GET .../studies/{study}/series/{series}/thumbnail
 * - GET .../instances/{instance}/thumbnail
 * - GET .../instances/{instance}/frames/{frames}/thumbnail
 *
 * Rendering query parameters: viewport, window, quality
 *
 * Test Data Strategy:
 * - EXTRACTION_MR: Single-frame MR image for basic rendered/thumbnail tests
 * - EXTRACTION_CT: Multi-instance CT for study/series level rendering
 *
 * DICOM Standard: PS 3.18 Sections 8.3.5, 10.4
 */
@TestRequires(
        specificPluginRequirements = {
                @PluginRequirement(pluginId = "dicomwebproxy")
        },
        data = {
                TestData.EXTRACTION_MR,
                TestData.EXTRACTION_CT
        }
)
@Test(groups = {IMPORTER, METADATA_EXTRACTION})
public class TestDicomWebWadoRendered extends BaseXnatRestTest {

    // DICOM tag constants (hex notation)
    private static final String TAG_SERIES_INSTANCE_UID = "0020000E";
    private static final String TAG_SOP_INSTANCE_UID = "00080018";

    // JPEG magic bytes: FF D8 FF
    private static final byte JPEG_SOI_FF = (byte) 0xFF;
    private static final byte JPEG_SOI_D8 = (byte) 0xD8;

    // PNG magic bytes: 89 50 4E 47
    private static final byte PNG_SIG_89 = (byte) 0x89;
    private static final byte PNG_SIG_50 = (byte) 0x50;

    // GIF magic bytes: 47 49 46 (GIF)
    private static final byte GIF_SIG_47 = (byte) 0x47;
    private static final byte GIF_SIG_49 = (byte) 0x49;
    private static final byte GIF_SIG_46 = (byte) 0x46;

    private final Project testProject = new Project("WRend" + RandomHelper.randomID(8));
    private final Subject testSubject = new Subject(testProject);

    private final MRSession mrSession = new MRSession(testProject, testSubject);
    private final CTSession ctSession = new CTSession(testProject, testSubject);

    // Stored UIDs from uploads
    private String mrStudyUID;
    private String mrSeriesUID;
    private String mrInstanceUID;
    private String ctStudyUID;
    private String ctSeriesUID;

    @BeforeClass
    public void setupTests() {
        new SessionImportExtension(mrSession, TestData.EXTRACTION_MR.toFile());
        new SessionImportExtension(ctSession, TestData.EXTRACTION_CT.toFile());

        mainInterface().createProject(testProject);

        extractUIDsForSession(mrSession, true);
        extractUIDsForSession(ctSession, false);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupTests() {
        restDriver.deleteProjectSilently(mainAdminUser, testProject);
    }


    // ========================================
    // Study Rendered
    // ========================================

    /**
     * Test: Retrieve rendered image for a study.
     * Selects a representative instance and renders it as JPEG.
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/rendered
     *
     * Expected: HTTP 200, image/jpeg content, valid JPEG bytes
     */
    @Test(groups = SMOKE, priority = 1)
    public void testStudyRendered_ReturnsJpeg() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/rendered",
                testProject.getId(), mrStudyUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Study rendered should return JPEG");
    }

    /**
     * Test: Study rendered with Accept: image/gif returns GIF.
     */
    @Test(priority = 2)
    public void testStudyRendered_AcceptGif_ReturnsGif() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/rendered",
                testProject.getId(), mrStudyUID);

        Response response = mainQueryBase()
                .header("Accept", "image/gif")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertGifContent(response, "Study rendered with Accept: image/gif should return GIF");
    }

    /**
     * Test: Study rendered for non-existent study returns 404.
     */
    @Test(priority = 3)
    public void testStudyRendered_NotFound() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/rendered",
                testProject.getId(), "9.9.9.9.9.9.9.9.9");

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(404);
    }

    // ========================================
    // Series Rendered
    // ========================================

    /**
     * Test: Retrieve rendered image for a series.
     * Selects a representative instance from the series and renders it.
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/rendered
     *
     * Expected: HTTP 200, image/jpeg content, valid JPEG bytes
     */
    @Test(groups = SMOKE, priority = 1)
    public void testSeriesRendered_ReturnsJpeg() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/rendered",
                testProject.getId(), mrStudyUID, mrSeriesUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Series rendered should return JPEG");
    }

    /**
     * Test: Series rendered for non-existent series returns 404.
     */
    @Test(priority = 3)
    public void testSeriesRendered_NotFound() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/rendered",
                testProject.getId(), mrStudyUID, "9.9.9.9.9.9.9.9.9");

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(404);
    }

    // ========================================
    // Instance Rendered (existing, with new params)
    // ========================================

    /**
     * Test: Existing instance rendered endpoint still works.
     */
    @Test(groups = SMOKE, priority = 1)
    public void testInstanceRendered_ReturnsJpeg() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Instance rendered should return JPEG");
        assertNotNull(response.getHeader("X-Frame-Count"),
                "Should include X-Frame-Count header");
        assertNotNull(response.getHeader("X-Frame-Number"),
                "Should include X-Frame-Number header");
    }

    /**
     * Test: Instance rendered with Accept: image/png returns PNG.
     */
    @Test(priority = 2)
    public void testInstanceRendered_AcceptPng_ReturnsPng() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/png")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertPngContent(response, "Instance rendered with Accept: image/png should return PNG");
    }

    // ========================================
    // Instance Rendered with Rendering Parameters
    // ========================================

    /**
     * Test: Instance rendered with viewport parameter scales the image.
     *
     * Verifies that the viewport query parameter is accepted and produces
     * a valid image (exact dimension checking requires decoding the JPEG).
     */
    @Test(priority = 2)
    public void testInstanceRendered_WithViewport() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered?viewport=64,64",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Rendered with viewport should return JPEG");

        // Viewport-scaled image should be smaller than full-size
        byte[] scaledData = response.asByteArray();

        // Get full-size for comparison
        String fullEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response fullResponse = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(fullEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        byte[] fullData = fullResponse.asByteArray();
        assertTrue(scaledData.length < fullData.length,
                "64x64 viewport image (" + scaledData.length + " bytes) should be smaller than " +
                "full-size image (" + fullData.length + " bytes)");
    }

    /**
     * Test: Instance rendered with window parameter (VOI LUT).
     * Verifies the endpoint accepts window center/width.
     */
    @Test(priority = 2)
    public void testInstanceRendered_WithWindow() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered?window=400,2000",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Rendered with window should return JPEG");
    }

    /**
     * Test: Instance rendered with quality parameter.
     * Lower quality should produce a smaller file.
     */
    @Test(priority = 2)
    public void testInstanceRendered_WithQuality() {
        String lowQEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered?quality=10",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response lowQResponse = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(lowQEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(lowQResponse, "Rendered with quality=10 should return JPEG");

        String highQEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered?quality=95",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response highQResponse = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(highQEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(highQResponse, "Rendered with quality=95 should return JPEG");

        assertTrue(lowQResponse.asByteArray().length < highQResponse.asByteArray().length,
                "quality=10 (" + lowQResponse.asByteArray().length + " bytes) should be smaller " +
                "than quality=95 (" + highQResponse.asByteArray().length + " bytes)");
    }

    /**
     * Test: Instance rendered with all rendering parameters combined.
     */
    @Test(priority = 2)
    public void testInstanceRendered_WithAllParams() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered" +
                "?viewport=128,128&window=400,2000&quality=50",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Rendered with all params should return JPEG");
    }

    /**
     * Test: Invalid viewport format returns 400.
     */
    @Test(priority = 3)
    public void testInstanceRendered_InvalidViewport_Returns400() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered?viewport=abc",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(400);
    }

    /**
     * Test: Invalid quality value (out of range) returns 400.
     */
    @Test(priority = 3)
    public void testInstanceRendered_InvalidQuality_Returns400() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered?quality=200",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(400);
    }

    // ========================================
    // Frame Rendered
    // ========================================

    /**
     * Test: Retrieve rendered image for a specific frame.
     *
     * Endpoint: GET .../instances/{instance}/frames/{frameList}/rendered
     *
     * Expected: HTTP 200, image/jpeg
     */
    @Test(priority = 2)
    public void testFrameRendered_ReturnsJpeg() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/frames/1/rendered",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Frame rendered should return JPEG");
    }

    /**
     * Test: Frame rendered for non-existent instance returns 404.
     */
    @Test(priority = 3)
    public void testFrameRendered_NotFound() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/frames/1/rendered",
                testProject.getId(), mrStudyUID, mrSeriesUID, "9.9.9.9.9.9.9.9.9");

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(404);
    }

    // ========================================
    // Study Thumbnail
    // ========================================

    /**
     * Test: Retrieve thumbnail for a study.
     * Should return a small JPEG image (default 128x128).
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/thumbnail
     *
     * Expected: HTTP 200, image/jpeg, small image size
     */
    @Test(groups = SMOKE, priority = 1)
    public void testStudyThumbnail_ReturnsSmallJpeg() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/thumbnail",
                testProject.getId(), mrStudyUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Study thumbnail should return JPEG");
        assertThumbnailSize(response);
    }

    /**
     * Test: Study thumbnail for non-existent study returns 404.
     */
    @Test(priority = 3)
    public void testStudyThumbnail_NotFound() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/thumbnail",
                testProject.getId(), "9.9.9.9.9.9.9.9.9");

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(404);
    }

    // ========================================
    // Series Thumbnail
    // ========================================

    /**
     * Test: Retrieve thumbnail for a series.
     *
     * Endpoint: GET /dicomweb/projects/{projectId}/studies/{studyUID}/series/{seriesUID}/thumbnail
     *
     * Expected: HTTP 200, image/jpeg, small image
     */
    @Test(groups = SMOKE, priority = 1)
    public void testSeriesThumbnail_ReturnsSmallJpeg() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/thumbnail",
                testProject.getId(), mrStudyUID, mrSeriesUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Series thumbnail should return JPEG");
        assertThumbnailSize(response);
    }

    /**
     * Test: Series thumbnail for non-existent series returns 404.
     */
    @Test(priority = 3)
    public void testSeriesThumbnail_NotFound() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/thumbnail",
                testProject.getId(), mrStudyUID, "9.9.9.9.9.9.9.9.9");

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(404);
    }

    // ========================================
    // Instance Thumbnail
    // ========================================

    /**
     * Test: Retrieve thumbnail for an instance.
     *
     * Endpoint: GET .../instances/{instance}/thumbnail
     *
     * Expected: HTTP 200, image/jpeg, small image
     */
    @Test(groups = SMOKE, priority = 1)
    public void testInstanceThumbnail_ReturnsSmallJpeg() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/thumbnail",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Instance thumbnail should return JPEG");
        assertThumbnailSize(response);
    }

    /**
     * Test: Instance thumbnail is smaller than full rendered image.
     */
    @Test(priority = 2)
    public void testInstanceThumbnail_SmallerThanRendered() {
        String thumbEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/thumbnail",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response thumbResponse = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(thumbEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        String renderedEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/rendered",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response renderedResponse = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(renderedEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertTrue(thumbResponse.asByteArray().length < renderedResponse.asByteArray().length,
                "Thumbnail (" + thumbResponse.asByteArray().length + " bytes) should be smaller " +
                "than full rendered (" + renderedResponse.asByteArray().length + " bytes)");
    }

    /**
     * Test: Instance thumbnail for non-existent instance returns 404.
     */
    @Test(priority = 3)
    public void testInstanceThumbnail_NotFound() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/thumbnail",
                testProject.getId(), mrStudyUID, mrSeriesUID, "9.9.9.9.9.9.9.9.9");

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(404);
    }

    // ========================================
    // Frame Thumbnail
    // ========================================

    /**
     * Test: Retrieve thumbnail for a specific frame.
     *
     * Endpoint: GET .../instances/{instance}/frames/{frameList}/thumbnail
     *
     * Expected: HTTP 200, image/jpeg, small image
     */
    @Test(priority = 2)
    public void testFrameThumbnail_ReturnsSmallJpeg() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/frames/1/thumbnail",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Frame thumbnail should return JPEG");
        assertThumbnailSize(response);
    }

    /**
     * Test: Frame thumbnail for non-existent instance returns 404.
     */
    @Test(priority = 3)
    public void testFrameThumbnail_NotFound() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/frames/1/thumbnail",
                testProject.getId(), mrStudyUID, mrSeriesUID, "9.9.9.9.9.9.9.9.9");

        mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(404);
    }

    // ========================================
    // Thumbnail with Custom Viewport
    // ========================================

    /**
     * Test: Instance thumbnail with custom viewport overrides the 128x128 default.
     */
    @Test(priority = 2)
    public void testInstanceThumbnail_WithCustomViewport() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/thumbnail?viewport=64,64",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "Thumbnail with custom viewport should return JPEG");

        // 64x64 thumbnail should be smaller than default 128x128
        String defaultEndpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/thumbnail",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response defaultResponse = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(defaultEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertTrue(response.asByteArray().length < defaultResponse.asByteArray().length,
                "64x64 thumbnail (" + response.asByteArray().length + " bytes) should be smaller " +
                "than default 128x128 (" + defaultResponse.asByteArray().length + " bytes)");
    }

    // ========================================
    // CT Dataset Tests (multi-instance)
    // ========================================

    /**
     * Test: Study rendered works for CT dataset.
     */
    @Test(priority = 2)
    public void testStudyRendered_CT() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/rendered",
                testProject.getId(), ctStudyUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "CT study rendered should return JPEG");
    }

    /**
     * Test: Series rendered works for CT dataset.
     */
    @Test(priority = 2)
    public void testSeriesRendered_CT() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/rendered",
                testProject.getId(), ctStudyUID, ctSeriesUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "CT series rendered should return JPEG");
    }

    /**
     * Test: Study thumbnail works for CT dataset.
     */
    @Test(priority = 2)
    public void testStudyThumbnail_CT() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/thumbnail",
                testProject.getId(), ctStudyUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertJpegContent(response, "CT study thumbnail should return JPEG");
        assertThumbnailSize(response);
    }

    // ========================================
    // Content Negotiation on New Endpoints
    // ========================================

    /**
     * Test: Series rendered with accept query parameter overrides header.
     */
    @Test(priority = 2)
    public void testSeriesRendered_AcceptQueryParam_OverridesHeader() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/rendered?accept=image/gif",
                testProject.getId(), mrStudyUID, mrSeriesUID);

        Response response = mainQueryBase()
                .header("Accept", "image/jpeg")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertGifContent(response, "Accept query param image/gif should override header");
    }

    /**
     * Test: Thumbnail with Accept: image/png returns PNG.
     */
    @Test(priority = 2)
    public void testInstanceThumbnail_AcceptPng_ReturnsPng() {
        String endpoint = String.format(
                "/dicomweb/projects/%s/studies/%s/series/%s/instances/%s/thumbnail",
                testProject.getId(), mrStudyUID, mrSeriesUID, mrInstanceUID);

        Response response = mainQueryBase()
                .header("Accept", "image/png")
                .get(formatXapiUrl(endpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        assertPngContent(response, "Thumbnail with Accept: image/png should return PNG");
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Extract Study/Series/Instance UIDs for a session using QIDO-RS.
     */
    private void extractUIDsForSession(ImagingSession session, boolean isMR) {
        JsonPath sessionJson = mainInterface().jsonQuery()
                .get(mainInterface().subjectAssessorUrl(session))
                .then().assertThat().statusCode(200).and().extract().jsonPath();
        Map<String, Object> sessionDataFields = sessionJson.get("items[0].data_fields");
        String studyUID = (String) sessionDataFields.get("UID");

        // Query series using QIDO-RS
        String seriesEndpoint = String.format("/dicomweb/projects/%s/studies/%s/series",
                testProject.getId(), studyUID);

        Response seriesResponse = mainQueryBase()
                .get(formatXapiUrl(seriesEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        JsonPath seriesJson = seriesResponse.jsonPath();
        List<Map<String, Object>> seriesList = seriesJson.getList("$");
        assertFalse(seriesList.isEmpty(), "Should have at least one series");

        Map<String, Object> firstSeries = seriesList.get(0);
        String seriesUID = getDicomTagValue(firstSeries, TAG_SERIES_INSTANCE_UID);

        // Query instances using QIDO-RS
        String instancesEndpoint = String.format("/dicomweb/projects/%s/studies/%s/series/%s/instances",
                testProject.getId(), studyUID, seriesUID);

        Response instancesResponse = mainQueryBase()
                .get(formatXapiUrl(instancesEndpoint))
                .then()
                .assertThat()
                .statusCode(200)
                .extract()
                .response();

        JsonPath instancesJson = instancesResponse.jsonPath();
        List<Map<String, Object>> instancesList = instancesJson.getList("$");
        assertFalse(instancesList.isEmpty(), "Should have at least one instance");

        String instanceUID = getDicomTagValue(instancesList.get(0), TAG_SOP_INSTANCE_UID);

        if (isMR) {
            mrStudyUID = studyUID;
            mrSeriesUID = seriesUID;
            mrInstanceUID = instanceUID;
        } else {
            ctStudyUID = studyUID;
            ctSeriesUID = seriesUID;
        }
    }

    /**
     * Extract DICOM tag value from DICOM JSON object.
     */
    @SuppressWarnings("unchecked")
    private String getDicomTagValue(Map<String, Object> dicomJson, String tag) {
        if (!dicomJson.containsKey(tag)) {
            return null;
        }
        Map<String, Object> tagData = (Map<String, Object>) dicomJson.get(tag);
        List<String> values = (List<String>) tagData.get("Value");
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * Assert response contains valid JPEG data.
     */
    private void assertJpegContent(Response response, String message) {
        String contentType = response.getContentType();
        assertTrue(contentType.contains("image/jpeg"),
                message + " - Content-Type should be image/jpeg, got: " + contentType);

        byte[] data = response.asByteArray();
        assertTrue(data.length > 2, message + " - JPEG data should not be empty");
        assertEquals(data[0], JPEG_SOI_FF, message + " - First byte should be 0xFF (JPEG SOI)");
        assertEquals(data[1], JPEG_SOI_D8, message + " - Second byte should be 0xD8 (JPEG SOI)");
    }

    /**
     * Assert response contains valid PNG data.
     */
    private void assertPngContent(Response response, String message) {
        String contentType = response.getContentType();
        assertTrue(contentType.contains("image/png"),
                message + " - Content-Type should be image/png, got: " + contentType);

        byte[] data = response.asByteArray();
        assertTrue(data.length > 4, message + " - PNG data should not be empty");
        assertEquals(data[0], PNG_SIG_89, message + " - First byte should be 0x89 (PNG signature)");
        assertEquals(data[1], PNG_SIG_50, message + " - Second byte should be 0x50 (P)");
    }

    /**
     * Assert response contains valid GIF data.
     */
    private void assertGifContent(Response response, String message) {
        String contentType = response.getContentType();
        assertTrue(contentType.contains("image/gif"),
                message + " - Content-Type should be image/gif, got: " + contentType);

        byte[] data = response.asByteArray();
        assertTrue(data.length > 3, message + " - GIF data should not be empty");
        assertEquals(data[0], GIF_SIG_47, message + " - First byte should be 0x47 (G)");
        assertEquals(data[1], GIF_SIG_49, message + " - Second byte should be 0x49 (I)");
        assertEquals(data[2], GIF_SIG_46, message + " - Third byte should be 0x46 (F)");
    }

    /**
     * Assert response is a thumbnail-sized image (smaller than typical full render).
     * Thumbnails at 128x128 default should be under 50KB for typical DICOM images.
     */
    private void assertThumbnailSize(Response response) {
        byte[] data = response.asByteArray();
        assertTrue(data.length > 0, "Thumbnail should not be empty");
        assertTrue(data.length < 50_000,
                "Thumbnail should be small (< 50KB), got " + data.length + " bytes");
    }
}
