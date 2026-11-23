package org.nrg.testing.xnat.tests.batchtransfer;

import org.apache.commons.io.IOUtils;
import org.dcm4che3.data.Attributes;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.transfer.BatchTransferPayload;
import org.nrg.xnat.pogo.transfer.TransferItemStatus;
import org.nrg.xnat.pogo.transfer.TransferRunDetail;
import org.nrg.xnat.pogo.transfer.TransferRunItem;
import org.nrg.xnat.pogo.transfer.TransferRunStatus;

import java.io.ByteArrayInputStream;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        final List<Map<String, Object>> rows = mainAdminQueryBase().queryParam("format", "json").get(formatRestUrl("prearchive", "projects", project.getId())).
                then().assertThat().statusCode(200).
                extract().jsonPath().getList("ResultSet.Result");
        int count = 0;
        for (Map<String, Object> row : rows) {
            if (project.getId().equals(row.get("project"))) {
                count++;
            }
        }
        return count;
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
