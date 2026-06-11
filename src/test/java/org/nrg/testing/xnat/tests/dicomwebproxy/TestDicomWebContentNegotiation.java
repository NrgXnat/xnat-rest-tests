package org.nrg.testing.xnat.tests.dicomwebproxy;

import io.restassured.response.Response;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.util.RandomHelper;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * WADO-RS content negotiation tests for the DICOMweb plugin, exercising
 * the rules in DICOM PS3.18 Section 8.3.3 against both the per-project
 * and site-wide endpoints.
 *
 * <p>Each test fixes a client request shape — an {@code Accept} header,
 * an {@code accept} query parameter, or both — and asserts that the
 * server returns a response whose {@code Content-Type} matches one of
 * the media types the client was willing to accept. The covered rules:
 * <ul>
 *   <li>The {@code Accept} header is honored when present.</li>
 *   <li>The {@code accept} query parameter is honored when present
 *       (Section 8.3.3.1).</li>
 *   <li>When both are present, the plugin gives the query parameter
 *       precedence over the header. PS3.18 does not specify which
 *       wins; this is a plugin convention.</li>
 *   <li>Single-instance retrieve negotiates between single-part
 *       {@code application/dicom} and {@code multipart/related;
 *       type="application/dicom"}.</li>
 *   <li>Metadata negotiates between {@code application/dicom+json} and
 *       {@code application/dicom+xml}.</li>
 *   <li>Rendered endpoints negotiate between {@code image/jpeg},
 *       {@code image/png} and {@code image/gif}.</li>
 * </ul>
 */
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = "dicomwebplugin"))
@Test(groups = {PERMISSIONS})
public class TestDicomWebContentNegotiation extends BaseDicomWebProxyTest {

    private static final String APPLICATION_DICOM = "application/dicom";
    private static final String MULTIPART_RELATED = "multipart/related";
    private static final String MULTIPART_RELATED_DICOM = "multipart/related;type=\"application/dicom\"";
    private static final String APPLICATION_DICOM_JSON = "application/dicom+json";
    private static final String APPLICATION_DICOM_XML = "application/dicom+xml";

    private User memberUser;
    private Project project;

    @BeforeClass(groups = {PERMISSIONS})
    private void setup() {
        memberUser = createGenericUsers(1).get(0);

        project = new Project("DWNeg" + RandomHelper.randomID(6))
                .accessibility(Accessibility.PRIVATE)
                .addMember(memberUser);
        mainAdminInterface().createProject(project);

        // DirectArchive with no build delay so data is immediately queryable
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "true");
        updatePrefs("defaultStrategy", "DirectArchive");
        updatePrefs("buildDelayMs", 0);

        stowAs(memberUser, projectStowUrl(project), DATA_A);

        // Site-wide tests need site-wide querying enabled
        enableSiteWide();
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        resetSiteWideDefaults();
        updatePrefs("defaultStrategy", "GradualDicomImporter");
        updatePrefs("buildDelayMs", 5000);
        mainAdminInterface().postSiteConfigProperty("enableDirectArchiveAppend", "false");
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    // ========== Per-project single-instance retrieve: multipart vs single-part ==========

    /**
     * {@code Accept: application/dicom} should produce a single-part
     * {@code application/dicom} response.
     */
    public void testInstance_AcceptDicom_ReturnsSinglePart() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(APPLICATION_DICOM)
                .get(projectInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isSinglePartDicom(response),
                "Accept: application/dicom should yield single-part DICOM, got: " + response.getContentType());
    }

    /**
     * {@code Accept: multipart/related} should produce a
     * {@code multipart/related} response carrying the single instance.
     */
    public void testInstance_AcceptMultipart_ReturnsMultipart() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(MULTIPART_RELATED)
                .get(projectInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isMultipart(response),
                "Accept: multipart/related should yield multipart, got: " + response.getContentType());
    }

    /**
     * {@code Accept: multipart/related;type="application/dicom"} should
     * also produce a multipart/related response. The DICOM-specific
     * parameter is informational; the negotiator matches on type/subtype.
     */
    public void testInstance_AcceptMultipartWithDicomType_ReturnsMultipart() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(MULTIPART_RELATED_DICOM)
                .get(projectInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isMultipart(response),
                "Accept: " + MULTIPART_RELATED_DICOM + " should yield multipart, got: " + response.getContentType());
    }

    /**
     * Wildcard {@code Accept: *&#47;*} should resolve to multipart because
     * multipart is listed first in the server's supported set.
     */
    public void testInstance_AcceptWildcard_ResolvesToMultipart() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept("*/*")
                .get(projectInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isMultipart(response),
                "Accept: */* should resolve to multipart (first in supported list), got: " + response.getContentType());
    }

    // ========== Per-project single-instance retrieve: accept query parameter ==========

    /**
     * The {@code accept} query parameter alone (no {@code Accept}
     * header) should drive negotiation.
     */
    public void testInstance_AcceptQueryParam_Honored() {
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("accept", MULTIPART_RELATED)
                .get(projectInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isMultipart(response),
                "?accept=multipart/related should yield multipart, got: " + response.getContentType());
    }

    /**
     * When both {@code Accept} header and {@code accept} query
     * parameter are present and disagree, the plugin gives the query
     * parameter precedence. PS3.18 does not specify which wins; this
     * test pins the plugin's chosen behavior.
     */
    public void testInstance_QueryParamOverridesHeader() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(APPLICATION_DICOM)
                .queryParam("accept", MULTIPART_RELATED)
                .get(projectInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isMultipart(response),
                "?accept=multipart/related should override Accept: application/dicom, got: " + response.getContentType());
    }

    // ========== Per-project metadata: JSON vs XML ==========

    /**
     * {@code Accept: application/dicom+json} should yield JSON metadata.
     */
    public void testMetadata_AcceptJson_ReturnsJson() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(APPLICATION_DICOM_JSON)
                .get(projectMetadataUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertContentTypeStartsWith(response, APPLICATION_DICOM_JSON);
    }

    /**
     * {@code Accept: application/dicom+xml} should yield XML metadata.
     */
    public void testMetadata_AcceptXml_ReturnsXml() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(APPLICATION_DICOM_XML)
                .get(projectMetadataUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertContentTypeStartsWith(response, APPLICATION_DICOM_XML);
    }

    /**
     * The accept query parameter should be honored for metadata
     * negotiation just as for retrieval.
     */
    public void testMetadata_AcceptQueryParam_Honored() {
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("accept", APPLICATION_DICOM_XML)
                .get(projectMetadataUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertContentTypeStartsWith(response, APPLICATION_DICOM_XML);
    }

    /**
     * Query parameter wins over Accept header for metadata too.
     */
    public void testMetadata_QueryParamOverridesHeader() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(APPLICATION_DICOM_JSON)
                .queryParam("accept", APPLICATION_DICOM_XML)
                .get(projectMetadataUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertContentTypeStartsWith(response, APPLICATION_DICOM_XML);
    }

    // ========== Per-project rendered: query param fix ==========

    /**
     * The accept query parameter should drive image-format negotiation
     * on rendered endpoints.
     */
    public void testRendered_AcceptQueryParam_Honored() {
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("accept", "image/png")
                .get(projectRenderedInstanceUrl(project, STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertContentTypeStartsWith(response, "image/png");
    }

    // ========== Site-wide single-instance ==========

    /**
     * Site-wide single-instance retrieve should honor {@code Accept:
     * multipart/related} and return a multipart response.
     */
    public void testSiteInstance_AcceptMultipart_ReturnsMultipart() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(MULTIPART_RELATED)
                .get(siteInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isMultipart(response),
                "Site-wide: Accept: multipart/related should yield multipart, got: " + response.getContentType());
    }

    /**
     * Site-wide single-instance should default to single-part when the
     * client asks for {@code application/dicom}.
     */
    public void testSiteInstance_AcceptDicom_ReturnsSinglePart() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(APPLICATION_DICOM)
                .get(siteInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isSinglePartDicom(response),
                "Site-wide: Accept: application/dicom should yield single-part, got: " + response.getContentType());
    }

    // ========== Site-wide: accept query parameter ==========

    /**
     * Site-wide single-instance should honor the accept query parameter.
     */
    public void testSiteInstance_AcceptQueryParam_Honored() {
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("accept", MULTIPART_RELATED)
                .get(siteInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isMultipart(response),
                "Site-wide: ?accept=multipart/related should yield multipart, got: " + response.getContentType());
    }

    /**
     * Site-wide metadata should honor the accept query parameter.
     */
    public void testSiteMetadata_AcceptQueryParam_Honored() {
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("accept", APPLICATION_DICOM_XML)
                .get(siteMetadataUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertContentTypeStartsWith(response, APPLICATION_DICOM_XML);
    }

    /**
     * Site-wide rendered should honor the accept query parameter.
     */
    public void testSiteRendered_AcceptQueryParam_Honored() {
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("accept", "image/png")
                .get(siteRenderedInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertContentTypeStartsWith(response, "image/png");
    }

    /**
     * Site-wide: query parameter wins over Accept header.
     */
    public void testSiteInstance_QueryParamOverridesHeader() {
        Response response = restDriver.queryBaseFor(memberUser)
                .accept(APPLICATION_DICOM)
                .queryParam("accept", MULTIPART_RELATED)
                .get(siteInstanceUrl(STUDY_UID_A, SERIES_UID_A, SOP_UID_A));
        assertEquals(response.getStatusCode(), 200);
        assertTrue(isMultipart(response),
                "Site-wide: ?accept=multipart/related should override Accept: application/dicom, got: "
                        + response.getContentType());
    }

    // ========== Helpers ==========

    private static boolean isMultipart(Response response) {
        String ct = response.getContentType();
        return ct != null && ct.toLowerCase().startsWith(MULTIPART_RELATED);
    }

    private static boolean isSinglePartDicom(Response response) {
        String ct = response.getContentType();
        if (ct == null) return false;
        ct = ct.toLowerCase();
        // Reject anything starting with multipart/
        return ct.startsWith(APPLICATION_DICOM) && !ct.startsWith(MULTIPART_RELATED);
    }

    private static void assertContentTypeStartsWith(Response response, String expectedPrefix) {
        String ct = response.getContentType();
        assertNotNull(ct, "Response missing Content-Type");
        assertTrue(ct.toLowerCase().startsWith(expectedPrefix.toLowerCase()),
                "Expected Content-Type starting with '" + expectedPrefix + "', got: " + ct);
    }
}
