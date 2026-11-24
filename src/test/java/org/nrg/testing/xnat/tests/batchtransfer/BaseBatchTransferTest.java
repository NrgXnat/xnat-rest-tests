package org.nrg.testing.xnat.tests.batchtransfer;

import org.apache.commons.io.IOUtils;
import org.dcm4che3.data.Attributes;
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
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

/**
 * Helpers for the Batch Transfer test classes (NrgXnat/batch-transfer-plugin#38): running a batch and waiting for it,
 * reading projects through XNAT's own API, and reading a session's files.
 */
public abstract class BaseBatchTransferTest extends BaseXnatRestTest {

    protected static final int TRANSFER_WAIT_SECONDS = 300;
    protected static final int TRANSFER_POLLING_MILLIS = 1000;

    private AnonScript savedSiteScript;
    private Boolean savedSiteScriptEnabled;

    /** Submits a batch as the given user, waits for it to finish and returns it, whatever its final status. */
    protected TransferRunDetail runTransfer(XnatInterface asUser, BatchTransferPayload payload) {
        final String trackingId = asUser.launchBatchTransfer(payload);
        return asUser.waitForTransferRun(trackingId, TRANSFER_WAIT_SECONDS, TRANSFER_POLLING_MILLIS);
    }

    protected TransferRunDetail runTransfer(BatchTransferPayload payload) {
        return runTransfer(mainInterface(), payload);
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
        final List<Map<String, Object>> rows = mainAdminQueryBase().queryParam("format", "json").get(formatRestUrl("prearchive", "projects", project.getId())).
                then().assertThat().statusCode(200).
                extract().jsonPath().getList("ResultSet.Result");
        final List<String> urls = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (project.getId().equals(row.get("project"))) {
                urls.add(String.valueOf(row.get("url")));
            }
        }
        return urls;
    }

    /** Saves the site-wide anonymization script and whether it's enabled, for {@link #restoreSiteAnonSettings()}. */
    protected void saveSiteAnonSettings() {
        savedSiteScript = mainAdminInterface().readSiteAnonScript();
        savedSiteScriptEnabled = mainAdminInterface().readSiteConfig().getEnableSiteAnonScript();
    }

    /** Puts back the site-wide anonymization script and its on/off setting as {@link #saveSiteAnonSettings()} found them. */
    protected void restoreSiteAnonSettings() {
        if (savedSiteScript != null) {
            mainAdminInterface().setSiteAnonScript(savedSiteScript);
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

    /** The parsed header of each of a session's files. */
    protected List<Attributes> headers(String sessionId) {
        final List<Attributes> headers = new ArrayList<>();
        for (byte[] file : scanFiles(sessionId).values()) {
            headers.add(DicomUtils.readDicom(new ByteArrayInputStream(file)));
        }
        return headers;
    }

    /** A session's files written to the test run's temp folder, for the suite's file-based DICOM validators. */
    protected List<File> dicomFiles(String sessionId) {
        try {
            final Path folder = Files.createDirectories(Paths.get(Settings.TEMP_SUBDIR, "batch_transfer", sessionId));
            final List<File> files = new ArrayList<>();
            int index = 0;
            for (byte[] contents : scanFiles(sessionId).values()) {
                files.add(Files.write(folder.resolve("file" + index++ + ".dcm"), contents).toFile());
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
