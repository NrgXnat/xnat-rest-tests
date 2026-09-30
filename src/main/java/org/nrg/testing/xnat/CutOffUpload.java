package org.nrg.testing.xnat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** A zip as an upload that broke partway delivers it. */
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
}
