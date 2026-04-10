package org.nrg.testing.xnat.tests.dicomwebproxy;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.users.User;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.nrg.testing.TestGroups.PERMISSIONS;

/**
 * Base class for DICOMweb proxy plugin integration tests.
 * Provides constants, DICOM data transforms, URL helpers, and preference helpers.
 */
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = "dicomwebplugin"))
public abstract class BaseDicomWebProxyTest extends BaseXnatRestTest {

    // Deterministic DICOM UIDs for project A data
    protected static final String STUDY_UID_A = "2.25.11111111111111111111111111111111111";
    protected static final String SERIES_UID_A = "2.25.22222222222222222222222222222222222";
    protected static final String SOP_UID_A = "2.25.33333333333333333333333333333333333";

    // Deterministic DICOM UIDs for project B data
    protected static final String STUDY_UID_B = "2.25.44444444444444444444444444444444444";
    protected static final String SERIES_UID_B = "2.25.55555555555555555555555555555555555";
    protected static final String SOP_UID_B = "2.25.66666666666666666666666666666666666";

    // DICOM data transforms with deterministic UIDs
    protected static final LocallyCacheableDicomTransformation DATA_A =
            new LocallyCacheableDicomTransformation("dicomweb_proxy_data_a")
                    .data(TestData.SAMPLE_1)
                    .createZip()
                    .transformations(
                            new DicomTransformation("data_a")
                                    .produceZip()
                                    .prefilter(DicomFilters.subsetWithSeriesAndInstanceNumbers(4, 100))
                                    .transformFunction(
                                            TransformFunction.simple((dicom) -> {
                                                dicom.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID_A);
                                                dicom.setString(Tag.SeriesInstanceUID, VR.UI, SERIES_UID_A);
                                                dicom.setString(Tag.SOPInstanceUID, VR.UI, SOP_UID_A);
                                            })
                                    )
                    ).build();

    protected static final LocallyCacheableDicomTransformation DATA_B =
            new LocallyCacheableDicomTransformation("dicomweb_proxy_data_b")
                    .data(TestData.SAMPLE_1)
                    .createZip()
                    .transformations(
                            new DicomTransformation("data_b")
                                    .produceZip()
                                    .prefilter(DicomFilters.subsetWithSeriesAndInstanceNumbers(4, 100))
                                    .transformFunction(
                                            TransformFunction.simple((dicom) -> {
                                                dicom.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID_B);
                                                dicom.setString(Tag.SeriesInstanceUID, VR.UI, SERIES_UID_B);
                                                dicom.setString(Tag.SOPInstanceUID, VR.UI, SOP_UID_B);
                                            })
                                    )
                    ).build();

    // Additional DICOM data for STOW-RS merge/conflict tests
    // Same study as A, same series, different SOP (new instance in existing series)
    protected static final String SOP_UID_A2 = "2.25.33333333333333333333333333333333334";
    protected static final LocallyCacheableDicomTransformation DATA_A_NEW_INSTANCE =
            new LocallyCacheableDicomTransformation("dicomweb_proxy_data_a_new_instance")
                    .data(TestData.SAMPLE_1)
                    .createZip()
                    .transformations(
                            new DicomTransformation("data_a_new_instance")
                                    .produceZip()
                                    .prefilter(DicomFilters.subsetWithSeriesAndInstanceNumbers(4, 100))
                                    .transformFunction(
                                            TransformFunction.simple((dicom) -> {
                                                dicom.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID_A);
                                                dicom.setString(Tag.SeriesInstanceUID, VR.UI, SERIES_UID_A);
                                                dicom.setString(Tag.SOPInstanceUID, VR.UI, SOP_UID_A2);
                                            })
                                    )
                    ).build();

    // Same study as A, different series (new series in existing study)
    protected static final String SERIES_UID_A2 = "2.25.22222222222222222222222222222222223";
    protected static final String SOP_UID_A3 = "2.25.33333333333333333333333333333333335";
    protected static final LocallyCacheableDicomTransformation DATA_A_NEW_SERIES =
            new LocallyCacheableDicomTransformation("dicomweb_proxy_data_a_new_series")
                    .data(TestData.SAMPLE_1)
                    .createZip()
                    .transformations(
                            new DicomTransformation("data_a_new_series")
                                    .produceZip()
                                    .prefilter(DicomFilters.subsetWithSeriesAndInstanceNumbers(4, 100))
                                    .transformFunction(
                                            TransformFunction.simple((dicom) -> {
                                                dicom.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID_A);
                                                dicom.setString(Tag.SeriesInstanceUID, VR.UI, SERIES_UID_A2);
                                                dicom.setString(Tag.SOPInstanceUID, VR.UI, SOP_UID_A3);
                                            })
                                    )
                    ).build();

    /**
     * Create DICOM data with StudyDescription set to a project ID and unique PatientID.
     * Required for site-wide STOW-RS where XNAT routes based on StudyDescription.
     * PatientID is set to a unique value derived from the cache name to avoid
     * session UID conflicts with other test data already in the archive.
     */
    protected static LocallyCacheableDicomTransformation createDataForProject(
            String projectId, String studyUID, String seriesUID, String sopUID, String cacheName) {
        final String patientId = "DWP_" + cacheName.hashCode();
        return new LocallyCacheableDicomTransformation(cacheName)
                .data(TestData.SAMPLE_1)
                .createZip()
                .transformations(
                        new DicomTransformation(cacheName + "_transform")
                                .produceZip()
                                .prefilter(DicomFilters.subsetWithSeriesAndInstanceNumbers(4, 100))
                                .transformFunction(
                                        TransformFunction.simple((dicom) -> {
                                            dicom.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
                                            dicom.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
                                            dicom.setString(Tag.SOPInstanceUID, VR.UI, sopUID);
                                            dicom.setString(Tag.StudyDescription, VR.LO, projectId);
                                            dicom.setString(Tag.PatientID, VR.LO, patientId);
                                            dicom.setString(Tag.PatientName, VR.PN, patientId);
                                        })
                                )
                ).build();
    }

    // ---- STOW-RS URL helpers ----

    protected String projectStowUrl(Project p) {
        return formatXapiUrl("dicomweb", "projects", p.getId(), "studies");
    }

    protected String projectStowUrl(Project p, String strategy) {
        return projectStowUrl(p) + "?strategy=" + strategy;
    }

    protected String siteStowUrl() {
        return formatXapiUrl("dicomweb", "studies");
    }

    // ---- STOW-RS upload helpers ----

    /**
     * Upload DICOM data via STOW-RS using multipart/related.
     * Extracts DICOM files from the transformation zip and posts them.
     */
    protected Response stowAs(User user, String url, LocallyCacheableDicomTransformation data) {
        return buildStowRequest(restDriver.queryBaseFor(user), url, data);
    }

    protected Response stowAsAdmin(String url, LocallyCacheableDicomTransformation data) {
        return buildStowRequest(mainAdminQueryBase(), url, data);
    }

    private Response buildStowRequest(RequestSpecification spec, String url, LocallyCacheableDicomTransformation data) {
        File zipFile = data.locateOverallZip().toFile();
        String boundary = UUID.randomUUID().toString();

        try {
            byte[] multipartBody = buildMultipartRelatedBody(zipFile, boundary);
            return spec
                    .contentType("multipart/related; type=\"application/dicom\"; boundary=" + boundary)
                    .body(multipartBody)
                    .post(url);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build STOW-RS multipart body", e);
        }
    }

    /**
     * Build a multipart/related body from DICOM files in a zip.
     */
    private byte[] buildMultipartRelatedBody(File zipFile, String boundary) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase();
                if (!name.endsWith(".dcm") && !name.endsWith(".ima") && name.contains(".")) continue;

                byte[] dicomBytes;
                try (java.io.InputStream is = zip.getInputStream(entry)) {
                    dicomBytes = readAllBytes(is);
                }

                baos.write(("--" + boundary + "\r\n").getBytes());
                baos.write("Content-Type: application/dicom\r\n".getBytes());
                baos.write("\r\n".getBytes());
                baos.write(dicomBytes);
                baos.write("\r\n".getBytes());
            }
        }
        baos.write(("--" + boundary + "--\r\n").getBytes());
        return baos.toByteArray();
    }

    private byte[] readAllBytes(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return buffer.toByteArray();
    }

    // ---- Project-scoped URL helpers ----

    protected String projectStudiesUrl(Project p) {
        return formatXapiUrl("dicomweb", "projects", p.getId(), "studies");
    }

    protected String projectSeriesUrl(Project p, String studyUID) {
        return formatXapiUrl("dicomweb", "projects", p.getId(), "studies", studyUID, "series");
    }

    protected String projectInstancesUrl(Project p, String studyUID, String seriesUID) {
        return formatXapiUrl("dicomweb", "projects", p.getId(), "studies", studyUID,
                "series", seriesUID, "instances");
    }

    protected String projectInstanceUrl(Project p, String studyUID, String seriesUID, String sopUID) {
        return formatXapiUrl("dicomweb", "projects", p.getId(), "studies", studyUID,
                "series", seriesUID, "instances", sopUID);
    }

    protected String projectMetadataUrl(Project p, String studyUID, String seriesUID, String sopUID) {
        return projectInstanceUrl(p, studyUID, seriesUID, sopUID) + "/metadata";
    }

    protected String projectStudyUrl(Project p, String studyUID) {
        return formatXapiUrl("dicomweb", "projects", p.getId(), "studies", studyUID);
    }

    protected String projectSeriesRetrieveUrl(Project p, String studyUID, String seriesUID) {
        return formatXapiUrl("dicomweb", "projects", p.getId(), "studies", studyUID,
                "series", seriesUID);
    }

    protected String projectStudyMetadataUrl(Project p, String studyUID) {
        return projectStudyUrl(p, studyUID) + "/metadata";
    }

    protected String projectSeriesMetadataUrl(Project p, String studyUID, String seriesUID) {
        return projectSeriesRetrieveUrl(p, studyUID, seriesUID) + "/metadata";
    }

    protected String projectRenderedInstanceUrl(Project p, String studyUID, String seriesUID, String sopUID) {
        return projectInstanceUrl(p, studyUID, seriesUID, sopUID) + "/rendered";
    }

    protected String projectRenderedStudyUrl(Project p, String studyUID) {
        return projectStudyUrl(p, studyUID) + "/rendered";
    }

    protected String projectRenderedSeriesUrl(Project p, String studyUID, String seriesUID) {
        return projectSeriesRetrieveUrl(p, studyUID, seriesUID) + "/rendered";
    }

    protected String projectThumbnailStudyUrl(Project p, String studyUID) {
        return projectStudyUrl(p, studyUID) + "/thumbnail";
    }

    protected String projectThumbnailSeriesUrl(Project p, String studyUID, String seriesUID) {
        return projectSeriesRetrieveUrl(p, studyUID, seriesUID) + "/thumbnail";
    }

    protected String projectThumbnailInstanceUrl(Project p, String studyUID, String seriesUID, String sopUID) {
        return projectInstanceUrl(p, studyUID, seriesUID, sopUID) + "/thumbnail";
    }

    protected String projectFramesUrl(Project p, String studyUID, String seriesUID, String sopUID, String frameList) {
        return projectInstanceUrl(p, studyUID, seriesUID, sopUID) + "/frames/" + frameList;
    }

    protected String projectFrameRenderedUrl(Project p, String studyUID, String seriesUID, String sopUID, String frameList) {
        return projectFramesUrl(p, studyUID, seriesUID, sopUID, frameList) + "/rendered";
    }

    protected String projectFrameThumbnailUrl(Project p, String studyUID, String seriesUID, String sopUID, String frameList) {
        return projectFramesUrl(p, studyUID, seriesUID, sopUID, frameList) + "/thumbnail";
    }

    protected String projectBulkDataByTagUrl(Project p, String studyUID, String seriesUID, String sopUID, String tag) {
        return projectInstanceUrl(p, studyUID, seriesUID, sopUID) + "/bulkdata/" + tag;
    }

    protected String projectInstanceBulkDataUrl(Project p, String studyUID, String seriesUID, String sopUID) {
        return projectInstanceUrl(p, studyUID, seriesUID, sopUID) + "/bulkdata";
    }

    protected String projectSeriesBulkDataUrl(Project p, String studyUID, String seriesUID) {
        return projectSeriesRetrieveUrl(p, studyUID, seriesUID) + "/bulkdata";
    }

    protected String projectStudyBulkDataUrl(Project p, String studyUID) {
        return projectStudyUrl(p, studyUID) + "/bulkdata";
    }

    protected String projectInstancePixelDataUrl(Project p, String studyUID, String seriesUID, String sopUID) {
        return projectInstanceUrl(p, studyUID, seriesUID, sopUID) + "/pixeldata";
    }

    protected String projectSeriesPixelDataUrl(Project p, String studyUID, String seriesUID) {
        return projectSeriesRetrieveUrl(p, studyUID, seriesUID) + "/pixeldata";
    }

    protected String projectStudyPixelDataUrl(Project p, String studyUID) {
        return projectStudyUrl(p, studyUID) + "/pixeldata";
    }

    // ---- Site-wide URL helpers ----

    protected String siteStudiesUrl() {
        return formatXapiUrl("dicomweb", "studies");
    }

    protected String siteSeriesUrl(String studyUID) {
        return formatXapiUrl("dicomweb", "studies", studyUID, "series");
    }

    protected String siteInstancesUrl(String studyUID, String seriesUID) {
        return formatXapiUrl("dicomweb", "studies", studyUID, "series", seriesUID, "instances");
    }

    protected String siteInstanceUrl(String studyUID, String seriesUID, String sopUID) {
        return formatXapiUrl("dicomweb", "studies", studyUID, "series", seriesUID, "instances", sopUID);
    }

    protected String siteMetadataUrl(String studyUID, String seriesUID, String sopUID) {
        return siteInstanceUrl(studyUID, seriesUID, sopUID) + "/metadata";
    }

    protected String siteStudyUrl(String studyUID) {
        return formatXapiUrl("dicomweb", "studies", studyUID);
    }

    protected String siteSeriesRetrieveUrl(String studyUID, String seriesUID) {
        return formatXapiUrl("dicomweb", "studies", studyUID, "series", seriesUID);
    }

    protected String siteStudyMetadataUrl(String studyUID) {
        return siteStudyUrl(studyUID) + "/metadata";
    }

    protected String siteSeriesMetadataUrl(String studyUID, String seriesUID) {
        return siteSeriesRetrieveUrl(studyUID, seriesUID) + "/metadata";
    }

    protected String siteRenderedInstanceUrl(String studyUID, String seriesUID, String sopUID) {
        return siteInstanceUrl(studyUID, seriesUID, sopUID) + "/rendered";
    }

    protected String siteRenderedStudyUrl(String studyUID) {
        return siteStudyUrl(studyUID) + "/rendered";
    }

    protected String siteRenderedSeriesUrl(String studyUID, String seriesUID) {
        return siteSeriesRetrieveUrl(studyUID, seriesUID) + "/rendered";
    }

    protected String siteThumbnailStudyUrl(String studyUID) {
        return siteStudyUrl(studyUID) + "/thumbnail";
    }

    protected String siteThumbnailSeriesUrl(String studyUID, String seriesUID) {
        return siteSeriesRetrieveUrl(studyUID, seriesUID) + "/thumbnail";
    }

    protected String siteThumbnailInstanceUrl(String studyUID, String seriesUID, String sopUID) {
        return siteInstanceUrl(studyUID, seriesUID, sopUID) + "/thumbnail";
    }

    protected String siteFramesUrl(String studyUID, String seriesUID, String sopUID, String frameList) {
        return siteInstanceUrl(studyUID, seriesUID, sopUID) + "/frames/" + frameList;
    }

    protected String siteFrameRenderedUrl(String studyUID, String seriesUID, String sopUID, String frameList) {
        return siteFramesUrl(studyUID, seriesUID, sopUID, frameList) + "/rendered";
    }

    protected String siteFrameThumbnailUrl(String studyUID, String seriesUID, String sopUID, String frameList) {
        return siteFramesUrl(studyUID, seriesUID, sopUID, frameList) + "/thumbnail";
    }

    protected String siteBulkDataByTagUrl(String studyUID, String seriesUID, String sopUID, String tag) {
        return siteInstanceUrl(studyUID, seriesUID, sopUID) + "/bulkdata/" + tag;
    }

    protected String siteInstanceBulkDataUrl(String studyUID, String seriesUID, String sopUID) {
        return siteInstanceUrl(studyUID, seriesUID, sopUID) + "/bulkdata";
    }

    protected String siteSeriesBulkDataUrl(String studyUID, String seriesUID) {
        return siteSeriesRetrieveUrl(studyUID, seriesUID) + "/bulkdata";
    }

    protected String siteStudyBulkDataUrl(String studyUID) {
        return siteStudyUrl(studyUID) + "/bulkdata";
    }

    protected String siteInstancePixelDataUrl(String studyUID, String seriesUID, String sopUID) {
        return siteInstanceUrl(studyUID, seriesUID, sopUID) + "/pixeldata";
    }

    protected String siteSeriesPixelDataUrl(String studyUID, String seriesUID) {
        return siteSeriesRetrieveUrl(studyUID, seriesUID) + "/pixeldata";
    }

    protected String siteStudyPixelDataUrl(String studyUID) {
        return siteStudyUrl(studyUID) + "/pixeldata";
    }

    // ---- Admin URL helpers ----

    protected String prefsUrl() {
        return formatXapiUrl("dicomweb", "prefs");
    }

    protected String projectConfigUrl(Project p) {
        return formatXapiUrl("dicomweb", "projects", p.getId(), "config", "site-wide");
    }

    // ---- Preference helpers ----

    protected void enableSiteWide() {
        updatePrefs("siteWideEnabled", true);
    }

    protected void disableSiteWide() {
        updatePrefs("siteWideEnabled", false);
    }

    protected void setFilterMode(String mode) {
        updatePrefs("filterMode", mode);
    }

    protected void setProjectList(String commaSepIds) {
        updatePrefs("projectList", commaSepIds);
    }

    protected void updatePrefs(String key, Object value) {
        Map<String, Object> body = new HashMap<>();
        body.put(key, value);
        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .post(prefsUrl())
                .then()
                .assertThat()
                .statusCode(200);
    }

    protected void setProjectOptOut(Project p, boolean excluded) {
        Map<String, Object> body = new HashMap<>();
        body.put("excludeFromSiteWide", excluded);
        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .put(projectConfigUrl(p))
                .then()
                .assertThat()
                .statusCode(200);
    }

    protected void resetSiteWideDefaults() {
        Map<String, Object> body = new HashMap<>();
        body.put("siteWideEnabled", false);
        body.put("filterMode", "blacklist");
        body.put("projectList", "");
        mainAdminQueryBase()
                .contentType("application/json")
                .body(body)
                .post(prefsUrl());
    }

    // ---- Query helpers ----

    protected Response getAs(User user, String url) {
        return restDriver.queryBaseFor(user).get(url);
    }

    protected Response getAsAdmin(String url) {
        return mainAdminQueryBase().get(url);
    }

    protected boolean responseContainsStudyUID(Response response, String studyUID) {
        String body = response.getBody().asString();
        return body.contains(studyUID);
    }
}
