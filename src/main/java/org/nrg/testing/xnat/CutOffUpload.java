package org.nrg.testing.xnat;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.nrg.testing.DicomUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

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
