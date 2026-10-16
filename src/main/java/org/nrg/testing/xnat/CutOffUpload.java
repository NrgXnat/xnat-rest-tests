package org.nrg.testing.xnat;

import org.apache.commons.io.IOUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.DicomUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/** A zip as an upload that broke partway delivers it, and the checks for what the whole study leaves once it arrives. */
public final class CutOffUpload {
    private CutOffUpload() {
    }

    /**
     * The first half of the zip's bytes: the entries before the midpoint whole, the one it falls in cut short, and no
     * central directory, as a connection dropped halfway through an upload leaves it.
     */
    public static File firstHalfOf(final File zip, final Path directory) throws IOException {
        final byte[] bytes  = Files.readAllBytes(zip.toPath());
        final Path   cutOff = Files.createTempFile(directory, "cut-off-", ".zip");
        Files.write(cutOff, Arrays.copyOf(bytes, bytes.length / 2));
        return cutOff.toFile();
    }

    /**
     * The zip's objects whole, followed by the first of them again as the first object of another study (new study,
     * series and SOP instance UIDs, and the given patient), cut off three quarters of the way through: an upload of two
     * studies that broke in the second study's first object.
     */
    public static File withSecondStudyCutInItsFirstObject(final File zip, final Path directory, final String patient) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final int start;
        final int end;
        try (ZipFile source = new ZipFile(zip); ZipOutputStream out = new ZipOutputStream(bytes)) {
            byte[] first = null;
            for (final ZipEntry entry : Collections.list(source.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                final byte[] object = IOUtils.toByteArray(source.getInputStream(entry));
                if (first == null) {
                    first = object;
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(object);
                out.closeEntry();
            }
            if (first == null) {
                throw new IOException(zip + " holds no objects");
            }
            out.flush();
            start = bytes.size();
            out.putNextEntry(new ZipEntry("second-study/" + patient + ".dcm"));
            out.write(asAnotherStudy(first, patient));
            out.closeEntry();
            out.flush();
            end = bytes.size();
        }
        final Path cutOff = Files.createTempFile(directory, "cut-off-second-study-", ".zip");
        Files.write(cutOff, Arrays.copyOf(bytes.toByteArray(), start + (end - start) * 3 / 4));
        return cutOff.toFile();
    }

    private static byte[] asAnotherStudy(final byte[] object, final String patient) throws IOException {
        try (DicomInputStream in = new DicomInputStream(new ByteArrayInputStream(object))) {
            final Attributes fileMetaInformation = in.readFileMetaInformation();
            final Attributes dataset             = in.readDataset();
            final String     sopInstanceUid      = UIDUtils.createUID();
            dataset.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
            dataset.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
            dataset.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUid);
            dataset.setString(Tag.PatientName, VR.PN, patient);
            dataset.setString(Tag.PatientID, VR.LO, patient);
            fileMetaInformation.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, sopInstanceUid);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DicomOutputStream dicom = new DicomOutputStream(out, UID.ExplicitVRLittleEndian)) {
                dicom.writeDataset(fileMetaInformation, dataset);
            }
            return out.toByteArray();
        }
    }

    /** How many objects the zip holds: its entries that aren't directories. */
    public static int objectsIn(final File zip) throws IOException {
        try (ZipFile zipFile = new ZipFile(zip)) {
            return (int) zipFile.stream().filter(entry -> !entry.isDirectory()).count();
        }
    }

    /** Asserts that {@code expected} objects were archived, each whole: read to its end, with its pixel data. */
    public static void assertWholeObjects(final List<File> archived, final int expected) {
        assertEquals("every object of the study should be archived", expected, archived.size());
        for (final File object : archived) {
            // Reads the whole object, so one cut short fails here.
            final Attributes dataset = DicomUtils.readDicom(object);
            assertTrue(object.getName() + " should have its pixel data", dataset.contains(Tag.PixelData));
        }
    }
}
