package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.versions.Xnat_1_10_1;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Snapshot generation must work for compressed pixel data, not only uncompressed (XNAT-6581,
 * XNAT-6743).
 * <p>
 * Snapshots are rendered by dcm4che 5's {@code DicomImageReader}, which resolves a codec for the
 * transfer syntax through {@code ImageReaderFactory}. Its stock configuration names the OpenCV-backed
 * plugins for every JPEG-family syntax, so an XNAT that does not ship them renders uncompressed
 * studies happily and fails on compressed ones with:
 * <pre>
 *   java.lang.RuntimeException: No Reader for format: jpeg2000-cv registered
 *       at org.dcm4che3.imageio.codec.ImageReaderFactory.getImageReaderFromImageIOServiceRegistry
 *       at org.nrg.xnat.snapshot.generator.impl.DicomImageRenderer.readImage
 * </pre>
 * The fixture holds both series in one study so a single import covers them, and so the uncompressed
 * one is a true control: it differs from the compressed series in transfer syntax and nothing else.
 * That distinction is the point. A compressed series failing on its own could mean the codecs are
 * missing or that snapshot generation is broken outright, and those want different fixes; a control
 * that renders regardless says which.
 *
 * @see <a href="file:../../../../../../resources/data/j2k_snapshots.README.md">fixture provenance</a>
 */
@AddedIn(Xnat_1_10_1.class)
@Test(groups = IMPORTER)
public class TestSnapshotCodecs extends BaseXnatRestTest {

    // registerTempProject rather than new Project: the base class deletes registered projects in an
    // @AfterClass, and this archives a session, so leaving it behind leaves files behind too.
    private final Project        project = registerTempProject();
    private final Subject        subject = new Subject(project, "J2K_TEST_001");
    private final ImagingSession session = new MRSession(project, subject, "J2K_SNAPSHOTS");
    private final File           testZip = getDataFile("j2k_snapshots.zip");

    /** Scan ids follow SeriesNumber. See j2k_snapshots.README.md for what each series encodes. */
    private static final String SCAN_UNCOMPRESSED_CONTROL = "1";
    private static final String SCAN_JPEG2000_LOSSLESS    = "2";

    @BeforeClass(groups = IMPORTER)
    private void importSession() {
        mainInterface().createProject(project);
        // destArchive rather than directArchive: this test only needs the session archived, and
        // dest=/archive does it in the one synchronous call. Direct archive would hold the session
        // open for a quiet period first and then archive on a scheduler, so the test would have to
        // sit and poll for it -- timing this test has no reason to depend on.
        mainInterface().callImporter(new DicomZipRequest().destArchive().project(project).file(testZip));
    }

    /**
     * Guards the case below. If this fails, snapshot generation is broken for reasons that have
     * nothing to do with codecs, and the compressed result says nothing either way.
     */
    public void testUncompressedControlRenders() {
        assertSnapshotRenders(SCAN_UNCOMPRESSED_CONTROL);
    }

    public void testJpeg2000LosslessRenders() {
        assertSnapshotRenders(SCAN_JPEG2000_LOSSLESS);
    }

    /**
     * Asks for both the thumbnail and the full-size snapshot, since {@code ThumbnailGenerator} and
     * {@code MontageGenerator} are separate paths over the same reader, and checks that what comes
     * back is an image rather than an empty 200.
     */
    private void assertSnapshotRenders(final String scanId) {
        for (final String kind : new String[]{"thumbnail", "snapshot"}) {
            final byte[] image = mainQueryBase()
                    .get(mainInterface().formatXapiUrl("experiments", mainInterface().getAccessionNumber(session),
                                                       "scan", scanId, kind))
                    .then().assertThat().statusCode(200)
                    .extract().asByteArray();
            assertTrue(String.format("%s for scan %s came back empty", kind, scanId),
                       image != null && image.length > 0);
        }
    }
}
