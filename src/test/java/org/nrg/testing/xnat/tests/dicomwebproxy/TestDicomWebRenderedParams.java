package org.nrg.testing.xnat.tests.dicomwebproxy;

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

/**
 * Tests for rendered image and thumbnail endpoints: rendering parameters
 * (viewport, window, quality), content negotiation, and format selection.
 * Mirrors coverage from the old TestDicomWebWadoRendered tests against the
 * new project-scoped DICOMweb plugin endpoints.
 */
@Test(groups = {PERMISSIONS})
public class TestDicomWebRenderedParams extends BaseDicomWebProxyTest {

    // Image format magic bytes
    private static final byte JPEG_FF = (byte) 0xFF;
    private static final byte JPEG_D8 = (byte) 0xD8;
    private static final byte PNG_89 = (byte) 0x89;
    private static final byte PNG_50 = (byte) 0x50;
    private static final byte GIF_47 = (byte) 0x47;
    private static final byte GIF_49 = (byte) 0x49;

    private User memberUser;
    private Project project;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);
        project = new Project("DWRend" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(project);

        // Use DirectArchive with no delay so data is immediately queryable
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        stowAs(memberUser, projectStowUrl(project), DATA_A);
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        updatePrefs("defaultStrategy", "GradualDicomImporter");
        updatePrefs("buildDelayMs", 5000);
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    // ==================== Default Rendered Format ====================

    /**
     * Instance rendered endpoint should return JPEG by default.
     */
    public void testInstanceRenderedReturnsJpeg() {
        Response response = getAs(memberUser,
                projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "Rendered image should not be empty");
        assertTrue(isJpeg(body), "Default rendered format should be JPEG");
    }

    /**
     * Study rendered endpoint should return an image.
     */
    public void testStudyRenderedReturnsImage() {
        Response response = getAs(memberUser,
                projectRenderedStudyUrl(project, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "Study rendered image should not be empty");
    }

    /**
     * Series rendered endpoint should return an image.
     */
    public void testSeriesRenderedReturnsImage() {
        Response response = getAs(memberUser,
                projectRenderedSeriesUrl(project, STUDY_UID_A, SERIES_UID_A));
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "Series rendered image should not be empty");
    }

    // ==================== Content Negotiation ====================

    /**
     * Accept: image/png should return PNG.
     */
    public void testRenderedAcceptPngReturnsPng() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept("image/png")
                .get(projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "PNG rendered image should not be empty");
        assertTrue(isPng(body), "Accept: image/png should produce PNG image");
    }

    /**
     * Accept: image/gif on study (multi-instance) should return GIF.
     */
    public void testStudyRenderedAcceptGifReturnsGif() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept("image/gif")
                .get(projectRenderedStudyUrl(project, STUDY_UID_A));
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "GIF rendered image should not be empty");
        assertTrue(isGif(body), "Accept: image/gif should produce GIF image");
    }

    // ==================== Rendering Parameters ====================

    /**
     * viewport parameter should produce a resized image.
     */
    public void testRenderedWithViewport() {
        // Get default size
        Response defaultResponse = getAs(memberUser,
                projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        int defaultSize = defaultResponse.asByteArray().length;

        // Get smaller viewport
        String url = projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A)
                + "?viewport=64,64";
        Response smallResponse = getAs(memberUser, url);
        assertEquals(smallResponse.getStatusCode(), 200);

        int smallSize = smallResponse.asByteArray().length;
        assertTrue(smallSize < defaultSize,
                "viewport=64,64 should produce smaller image than default. " +
                "Small: " + smallSize + ", Default: " + defaultSize);
    }

    /**
     * window parameter (VOI LUT) should be accepted and produce a valid image.
     */
    public void testRenderedWithWindow() {
        String url = projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A)
                + "?window=400,2000";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "Windowed rendered image should not be empty");
    }

    /**
     * quality parameter should affect output size (low quality = smaller file).
     */
    public void testRenderedWithQuality() {
        String lowUrl = projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A)
                + "?quality=10";
        String highUrl = projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A)
                + "?quality=95";

        Response lowResponse = getAs(memberUser, lowUrl);
        Response highResponse = getAs(memberUser, highUrl);

        assertEquals(lowResponse.getStatusCode(), 200);
        assertEquals(highResponse.getStatusCode(), 200);

        int lowSize = lowResponse.asByteArray().length;
        int highSize = highResponse.asByteArray().length;

        assertTrue(lowSize < highSize,
                "quality=10 should produce smaller file than quality=95. " +
                "Low: " + lowSize + ", High: " + highSize);
    }

    /**
     * Combined parameters should all be applied together.
     */
    public void testRenderedWithAllParams() {
        String url = projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A)
                + "?viewport=128,128&window=400,2000&quality=50";
        Response response = getAs(memberUser, url);
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "Combined-param rendered image should not be empty");
    }

    // ==================== Thumbnails ====================

    /**
     * Thumbnails should be smaller than full rendered images.
     */
    public void testThumbnailSmallerThanRendered() {
        Response rendered = getAs(memberUser,
                projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        Response thumbnail = getAs(memberUser,
                projectThumbnailInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));

        assertEquals(rendered.getStatusCode(), 200);
        assertEquals(thumbnail.getStatusCode(), 200);

        int renderedSize = rendered.asByteArray().length;
        int thumbnailSize = thumbnail.asByteArray().length;

        assertTrue(thumbnailSize < renderedSize,
                "Thumbnail should be smaller than full rendered. " +
                "Thumbnail: " + thumbnailSize + ", Rendered: " + renderedSize);
    }

    /**
     * Thumbnail with Accept: image/png should return PNG.
     */
    public void testThumbnailAcceptPngReturnsPng() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept("image/png")
                .get(projectThumbnailInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(isPng(body), "Thumbnail with Accept: image/png should return PNG");
    }

    /**
     * Thumbnail with custom viewport should produce a smaller image.
     */
    public void testThumbnailWithCustomViewport() {
        // Default thumbnail (typically 128x128)
        Response defaultResponse = getAs(memberUser,
                projectThumbnailInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));

        // Smaller thumbnail
        String url = projectThumbnailInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A)
                + "?viewport=32,32";
        Response smallResponse = getAs(memberUser, url);

        assertEquals(defaultResponse.getStatusCode(), 200);
        assertEquals(smallResponse.getStatusCode(), 200);

        int defaultSize = defaultResponse.asByteArray().length;
        int smallSize = smallResponse.asByteArray().length;

        assertTrue(smallSize < defaultSize,
                "viewport=32,32 thumbnail should be smaller than default. " +
                "Small: " + smallSize + ", Default: " + defaultSize);
    }

    // ==================== Frame Endpoints ====================

    /**
     * Frame rendered endpoint should return an image.
     */
    public void testFrameRenderedReturnsImage() {
        Response response = getAs(memberUser,
                projectFrameRenderedUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "1"));
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "Frame rendered should return an image");
    }

    /**
     * Frame thumbnail endpoint should return a small image.
     */
    public void testFrameThumbnailReturnsImage() {
        Response response = getAs(memberUser,
                projectFrameThumbnailUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A, "1"));
        assertEquals(response.getStatusCode(), 200);

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "Frame thumbnail should return an image");
    }

    // ==================== Helpers ====================

    private static boolean isJpeg(byte[] data) {
        return data.length >= 2 && data[0] == JPEG_FF && data[1] == JPEG_D8;
    }

    private static boolean isPng(byte[] data) {
        return data.length >= 2 && data[0] == PNG_89 && data[1] == PNG_50;
    }

    private static boolean isGif(byte[] data) {
        return data.length >= 3 && data[0] == GIF_47 && data[1] == GIF_49 && data[2] == (byte) 0x46;
    }
}
