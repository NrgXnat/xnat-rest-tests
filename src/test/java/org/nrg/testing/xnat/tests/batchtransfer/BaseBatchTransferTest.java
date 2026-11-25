package org.nrg.testing.xnat.tests.batchtransfer;

import org.apache.commons.io.IOUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.transfer.BatchTransferPayload;
import org.nrg.xnat.pogo.transfer.TransferItemStatus;
import org.nrg.xnat.pogo.transfer.TransferRunDetail;
import org.nrg.xnat.pogo.transfer.TransferRunItem;
import org.nrg.xnat.pogo.transfer.TransferRunStatus;
import org.testng.SkipException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

/**
 * Helpers for the Batch Transfer test classes: running a batch and waiting for it, reading projects through XNAT's own
 * API, and reading a session's files.
 *
 * <p><b>A server with more than one web node needs dist-events with the fix in NrgXnat/dist-events#8.</b> Clone and
 * Reimport run on JMS listeners, so any node can pick up an item. Without the fix, the other nodes never learn that
 * the test user owns the private projects the tests create. An item picked up there fails with "You do not have
 * permission to read ...", which makes most Clone and Reimport tests fail at random. Share runs in the web request and
 * isn't affected, and a single-node server doesn't need dist-events at all.
 */
public abstract class BaseBatchTransferTest extends BaseXnatRestTest {

    protected static final int TRANSFER_WAIT_SECONDS = 300;
    protected static final int TRANSFER_POLLING_MILLIS = 1000;

    private AnonScript savedSiteScript;
    private Boolean savedSiteScriptEnabled;
    private Boolean perImportAnon;

    /** Submits a batch as the given user, waits for it to finish and returns it, whatever its final status. */
    protected TransferRunDetail runTransfer(XnatInterface asUser, BatchTransferPayload payload) {
        final String trackingId = asUser.launchBatchTransfer(payload);
        return asUser.waitForTransferRun(trackingId, TRANSFER_WAIT_SECONDS, TRANSFER_POLLING_MILLIS);
    }

    protected TransferRunDetail runTransfer(BatchTransferPayload payload) {
        return runTransfer(mainInterface(), payload);
    }

    /**
     * Skips the test when the plugin says it can't apply a custom Reimport script ({@code capabilities.per_import_anon} is
     * false). Use it in every test that sends {@code anon_script} or {@code anon_replace_pipeline}.
     */
    protected void requirePerImportAnon() {
        if (perImportAnon == null) {
            perImportAnon = mainInterface().readTransferCapabilities().isPerImportAnon();
        }
        if (!perImportAnon) {
            throw new SkipException("This XNAT build doesn't take a custom Reimport script (capabilities.per_import_anon is false).");
        }
    }

    protected String id(SubjectAssessor experiment) {
        return mainInterface().getAccessionNumber(experiment);
    }

    protected void assertRunStatus(TransferRunDetail run, TransferRunStatus expected) {
        assertEquals(run.getRun().getStatus(), expected, run.summary());
    }

    /** Checks an item's status and, when given, the ID of its copy at the destination. */
    protected TransferRunItem assertItem(TransferRunDetail run, String itemId, TransferItemStatus status, String destinationId) {
        final TransferRunItem item = run.findItem(itemId);
        assertNotNull(item, run.summary());
        assertEquals(item.getStatus(), status, run.summary());
        if (destinationId != null) {
            assertEquals(item.getDestinationId(), destinationId, run.summary());
        }
        return item;
    }

    protected Map<String, String> experimentIds(Project project) {
        return idsByLabel(formatRestUrl("projects", project.getId(), "experiments"));
    }

    protected Map<String, String> subjectIds(Project project) {
        return idsByLabel(formatRestUrl("projects", project.getId(), "subjects"));
    }

    /** The labels and IDs a REST listing returns. */
    protected Map<String, String> idsByLabel(String listingUrl) {
        final List<Map<String, Object>> rows = mainQueryBase().queryParam("format", "json").get(listingUrl).
                then().assertThat().statusCode(200).
                extract().jsonPath().getList("ResultSet.Result");
        final Map<String, String> ids = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            ids.put(String.valueOf(row.get("label")), String.valueOf(row.get("ID")));
        }
        return ids;
    }

    /** The project's own prearchive sessions. XNAT's per-project listing also returns the Unassigned ones. */
    protected int prearchiveEntries(Project project) {
        return ownPrearchiveUrls(project).size();
    }

    /**
     * Deletes the project's own prearchive sessions. Unlike the suite's {@code clearPrearchiveSessions}, this leaves the
     * Unassigned sessions that XNAT's per-project listing also returns, which may belong to other clients.
     */
    protected void clearPrearchive(Project project) {
        for (String url : ownPrearchiveUrls(project)) {
            mainAdminQueryBase().delete(formatRestUrl(url)).then().assertThat().statusCode(200);
        }
    }

    private List<String> ownPrearchiveUrls(Project project) {
        final List<String> urls = new ArrayList<>();
        for (Map<String, Object> row : ownPrearchiveRows(project)) {
            urls.add(String.valueOf(row.get("url")));
        }
        return urls;
    }

    /** The project's own rows of the prearchive listing, with their {@code url}, {@code status} and labels. */
    protected List<Map<String, Object>> ownPrearchiveRows(Project project) {
        final List<Map<String, Object>> rows = mainAdminQueryBase().queryParam("format", "json").get(formatRestUrl("prearchive", "projects", project.getId())).
                then().assertThat().statusCode(200).
                extract().jsonPath().getList("ResultSet.Result");
        final List<Map<String, Object>> own = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (project.getId().equals(row.get("project"))) {
                own.add(row);
            }
        }
        return own;
    }

    /** Saves the site-wide anonymization script and whether it's enabled, for {@link #restoreSiteAnonSettings()}. */
    protected void saveSiteAnonSettings() {
        final AnonScript script = mainAdminInterface().readSiteAnonScript();
        final Boolean enabled = mainAdminInterface().readSiteConfig().getEnableSiteAnonScript();
        savedSiteScript = script;
        savedSiteScriptEnabled = enabled;
    }

    /**
     * Puts back the site-wide anonymization script and its on/off setting as {@link #saveSiteAnonSettings()} found them.
     * The setting is put back even if the script can't be, so a test's script is never left switched on. A site without
     * a script keeps the test's, switched off or on as before.
     */
    protected void restoreSiteAnonSettings() {
        if (savedSiteScript == null) {
            return; // nothing was saved
        }
        try {
            if (savedSiteScript.getContents() != null) {
                mainAdminInterface().setSiteAnonScript(savedSiteScript);
            }
        } finally {
            mainAdminInterface().setSiteAnonScriptStatus(Boolean.TRUE.equals(savedSiteScriptEnabled));
        }
    }

    /**
     * A copy of {@code mr_1} as a new study, zipped for upload: new Study, Series and SOP Instance UIDs, and the given
     * PatientName and PatientID. Use it where several sessions must not share a StudyInstanceUID.
     */
    protected File derivedMrStudy(String name, String patientName, String patientId) {
        final String studyInstanceUid = UIDUtils.createUID();
        final String seriesInstanceUid = UIDUtils.createUID();
        try {
            final Path folder = Files.createDirectories(Paths.get(Settings.TEMP_SUBDIR, "batch_transfer", "derived"));
            final Path zip = folder.resolve(name + ".zip");
            final File[] sources = getDataFile("mr_1").listFiles((dir, file) -> file.endsWith(".dcm"));
            assertNotNull(sources, "mr_1 files");
            Arrays.sort(sources);
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (File source : sources) {
                    final Attributes fileMetaInformation;
                    final Attributes dataset;
                    try (DicomInputStream in = new DicomInputStream(source)) {
                        fileMetaInformation = in.readFileMetaInformation();
                        dataset = in.readDataset();
                    }
                    final String sopInstanceUid = UIDUtils.createUID();
                    dataset.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
                    dataset.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
                    dataset.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUid);
                    dataset.setString(Tag.PatientName, VR.PN, patientName);
                    dataset.setString(Tag.PatientID, VR.LO, patientId);
                    final Attributes meta = fileMetaInformation != null ? fileMetaInformation : dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian);
                    meta.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, sopInstanceUid);
                    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (DicomOutputStream dicom = new DicomOutputStream(bytes, UID.ExplicitVRLittleEndian)) {
                        dicom.writeDataset(meta, dataset);
                    }
                    out.putNextEntry(new ZipEntry(source.getName()));
                    out.write(bytes.toByteArray());
                    out.closeEntry();
                }
            }
            return zip.toFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A zip of the files in one folder of the test data, for uploading part of a data set as its own session. */
    protected File zipOfDataFolder(String dataFolder, String name) {
        try {
            final Path folder = Files.createDirectories(Paths.get(Settings.TEMP_SUBDIR, "batch_transfer", "zips"));
            final Path zip = folder.resolve(name + ".zip");
            final File[] files = getDataFile(dataFolder).listFiles(File::isFile);
            assertNotNull(files, dataFolder + " files");
            Arrays.sort(files);
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (File file : files) {
                    out.putNextEntry(new ZipEntry(file.getName()));
                    out.write(Files.readAllBytes(file.toPath()));
                    out.closeEntry();
                }
            }
            return zip.toFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Every file in a session's scans, in one zip download (XNAT serves single files far more slowly). Keyed by the
     * path below the session's folder, such as {@code scans/1-T1/resources/DICOM/files/1.dcm}, so a copy's keys match
     * its source's.
     */
    protected Map<String, byte[]> scanFiles(String sessionId) {
        final byte[] zip = mainQueryBase().queryParam("format", "zip").
                get(formatRestUrl("experiments", sessionId, "scans", "ALL", "files")).
                then().assertThat().statusCode(200).
                extract().asByteArray();
        final Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName().substring(entry.getName().indexOf('/') + 1), IOUtils.toByteArray(entries));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(files.isEmpty(), "session " + sessionId + " has files");
        return files;
    }

    /** The parsed header of each DICOM file in a session's scans. Other files, such as snapshots, are skipped. */
    protected List<Attributes> headers(String sessionId) {
        return dicomOnly(scanFiles(sessionId));
    }

    /** A session's DICOM files written to the test run's temp folder, for the suite's file-based DICOM validators. */
    protected List<File> dicomFiles(String sessionId) {
        return dicomFiles(sessionId, scanFiles(sessionId));
    }

    /**
     * The DICOM files among a session's files, already read with {@link #scanFiles}, written to the test run's temp
     * folder. Other files, such as snapshots, are skipped.
     */
    protected List<File> dicomFiles(String sessionId, Map<String, byte[]> scanFiles) {
        try {
            final Path folder = Files.createDirectories(Paths.get(Settings.TEMP_SUBDIR, "batch_transfer", sessionId));
            final List<File> files = new ArrayList<>();
            int index = 0;
            for (byte[] contents : scanFiles.values()) {
                if (!isDicom(contents)) {
                    continue;
                }
                files.add(Files.write(folder.resolve("file" + index++ + ".dcm"), contents).toFile());
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The parsed header of each DICOM file among a session's files. */
    protected static List<Attributes> dicomOnly(Map<String, byte[]> files) {
        final List<Attributes> headers = new ArrayList<>();
        for (byte[] file : files.values()) {
            if (isDicom(file)) {
                headers.add(DicomUtils.readDicom(new ByteArrayInputStream(file)));
            }
        }
        return headers;
    }

    protected static boolean isDicom(byte[] file) {
        return file.length > 132 && file[128] == 'D' && file[129] == 'I' && file[130] == 'C' && file[131] == 'M';
    }

    protected static byte[] pixelData(Attributes header) {
        try {
            return header.getBytes(Tag.PixelData);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static Map<String, Attributes> bySopInstanceUid(List<Attributes> headers) {
        final Map<String, Attributes> byUid = new TreeMap<>();
        for (Attributes header : headers) {
            byUid.put(header.getString(Tag.SOPInstanceUID), header);
        }
        return byUid;
    }

    /** The tags whose values differ between two datasets, other than the pixel data, which is compared on its own. */
    protected static List<String> headerDifferences(Attributes expected, Attributes actual) {
        final Set<Integer> tags = new TreeSet<>();
        for (int tag : expected.tags()) {
            tags.add(tag);
        }
        for (int tag : actual.tags()) {
            tags.add(tag);
        }
        tags.remove(Tag.PixelData);
        final List<String> differences = new ArrayList<>();
        for (int tag : tags) {
            final String before = describe(expected, tag);
            final String after = describe(actual, tag);
            if (!before.equals(after)) {
                differences.add(String.format("(%04X,%04X) %s -> %s", tag >>> 16, tag & 0xFFFF, before, after));
            }
        }
        return differences;
    }

    /** A comparable description of one element: absent, a sequence and its items, or its values. */
    private static String describe(Attributes dataset, int tag) {
        if (!dataset.contains(tag)) {
            return "absent";
        }
        final Sequence sequence = dataset.getSequence(tag);
        if (sequence != null) {
            final List<String> items = new ArrayList<>();
            for (Attributes item : sequence) {
                items.add(describeItem(item));
            }
            return "sequence " + items;
        }
        return Arrays.toString(dataset.getStrings(tag));
    }

    /**
     * Every element of a sequence item, nested sequences included, in full. Not {@link Attributes#toString()}, which
     * stops after 50 lines and cuts each line at 78 characters, so a long value such as a UID could differ unseen.
     */
    private static String describeItem(Attributes item) {
        final List<String> elements = new ArrayList<>();
        for (int tag : item.tags()) {
            elements.add(String.format("(%04X,%04X) %s", tag >>> 16, tag & 0xFFFF, describe(item, tag)));
        }
        return "{" + String.join(", ", elements) + "}";
    }

    protected byte[] fileNamed(Map<String, byte[]> files, String name) {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.getKey().endsWith("/" + name)) {
                return file.getValue();
            }
        }
        throw new AssertionError("no file named " + name + " in " + files.keySet());
    }

    protected Map<String, String> digests(Map<String, byte[]> files) {
        final Map<String, String> digests = new TreeMap<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            digests.put(file.getKey(), sha256(file.getValue()));
        }
        return digests;
    }

    protected void deleteExperiment(String experimentId) {
        mainQueryBase().queryParam("removeFiles", true).delete(formatRestUrl("experiments", experimentId)).then().assertThat().statusCode(200);
    }

    protected static String sha256(byte[] bytes) {
        try {
            return String.format("%064x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
