package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.enums.DicomEditVersion;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.versions.Xnat_1_10_3;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Objects in Deflated Explicit VR Little Endian must archive whole from a DICOM-zip upload, whether or not a site-wide
 * anonymization script applies. Before 1.10.3, an object with no script to apply was written from a partial read
 * followed by a raw copy of the rest of the stream, which a deflated dataset can't survive, so it was refused.
 * <p>
 * There is no C-STORE counterpart: XNAT's DICOM receiver doesn't accept this transfer syntax, so a C-STORE client
 * sends such objects as Explicit VR Little Endian, and nothing Deflated reaches XNAT that way.
 */
@Test(groups = IMPORTER)
@AddedIn(Xnat_1_10_3.class)
public class TestDeflatedImport extends BaseXnatRestTest {
    private static final Logger LOGGER = Logger.getLogger(TestDeflatedImport.class);
    private static final int OBJECT_COUNT = 3;
    private static final String SUBJECT_LABEL = "Deflated";

    private final Project project = new Project().prearchiveCode(PrearchiveCode.MANUAL);

    @BeforeClass(groups = IMPORTER)
    private void setup() {
        mainInterface().createProject(project);
        mainAdminInterface().disableSiteAnonScript();
    }

    @BeforeMethod(groups = IMPORTER, alwaysRun = true)
    private void clearProject() {
        restDriver.drainIngestPipeline(mainUser, project);
        try {
            mainInterface().deleteAllProjectData(project);
            TimeUtils.sleep(1000);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
    }

    @AfterClass(groups = IMPORTER, alwaysRun = true)
    private void tearDown() {
        mainAdminInterface().disableSiteAnonScript();
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testDeflatedZipUpload() throws IOException {
        importDeflated("zip", false);
    }

    @Test
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testDeflatedZipUploadWithSiteScript() throws IOException {
        importDeflated("zip_site_script", true);
    }

    /**
     * Uploads Deflated copies of a few sample objects to the prearchive, archives the session, and checks what arrived.
     * The site script, when there is one, removes (0008,0031), which shows it ran on the Deflated objects.
     */
    private void importDeflated(String sessionLabel, boolean withSiteScript) throws IOException {
        if (withSiteScript) {
            mainAdminInterface().setSiteAnonScript(XnatObjectUtils.anonScriptFromFile(DicomEditVersion.DE_6, "simplisticDelete.das"));
            mainAdminInterface().enableSiteAnonScript();
        } else {
            mainAdminInterface().disableSiteAnonScript();
        }
        final Path directory = Files.createTempDirectory(Paths.get(Settings.TEMP_SUBDIR), "deflated_" + sessionLabel);
        final Map<String, byte[]> sentPixelData = writeDeflatedCopies(directory, sessionLabel);

        mainInterface().callImporter(new DicomZipRequest().project(project).file(zip(directory)));
        final SessionData received = mainInterface().expectSinglePrearchiveResultForProject(project);
        mainInterface().rebuildSession(received, false);
        mainInterface().archiveSession(received);

        final Subject subject = new Subject(project, SUBJECT_LABEL);
        final ImagingSession session = new MRSession(project, subject, sessionLabel);
        final List<File> archived = restDriver.downloadAllDicomFromSession(mainUser, project, subject, session);
        assertEquals("every object should have been archived", OBJECT_COUNT, archived.size());
        for (File file : archived) {
            try (DicomInputStream dicomInputStream = new DicomInputStream(file)) {
                final Attributes fileMetaInformation = dicomInputStream.readFileMetaInformation();
                assertEquals("the archived object should still be Deflated",
                        UID.DeflatedExplicitVRLittleEndian, fileMetaInformation.getString(Tag.TransferSyntaxUID));
                final Attributes dataset = dicomInputStream.readDataset();
                final byte[] expectedPixelData = sentPixelData.get(dataset.getString(Tag.SOPInstanceUID));
                assertNotNull("the archived object should be one of those sent", expectedPixelData);
                assertEquals("the pixel data should arrive intact", expectedPixelData, dataset.getBytes(Tag.PixelData));
                assertEquals(withSiteScript ? "the site script should have removed (0008,0031)" : "(0008,0031) should be kept when no script runs",
                        !withSiteScript, dataset.contains(Tag.SeriesTime));
            }
        }
    }

    /**
     * Writes Deflated copies of the first few sample objects, as one new study routed to the project, and returns the
     * pixel data of each by its SOP Instance UID.
     */
    private Map<String, byte[]> writeDeflatedCopies(Path directory, String sessionLabel) throws IOException {
        final List<Path> sources;
        try (Stream<Path> files = Files.list(TestData.SAMPLE_1_SCAN_4.toDirectory().toPath())) {
            sources = files.filter(path -> path.toString().endsWith(".dcm")).sorted().limit(OBJECT_COUNT).collect(Collectors.toList());
        }
        assertEquals(OBJECT_COUNT, sources.size());
        final String studyInstanceUid = UIDUtils.createUID();
        final String seriesInstanceUid = UIDUtils.createUID();
        final Map<String, byte[]> pixelData = new HashMap<>();
        for (Path source : sources) {
            final Attributes dataset;
            try (DicomInputStream dicomInputStream = new DicomInputStream(source.toFile())) {
                dataset = dicomInputStream.readDataset();
            }
            assertTrue("the sample should carry (0008,0031) for the site script to remove", dataset.contains(Tag.SeriesTime));
            dataset.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
            dataset.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
            dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
            dataset.setString(Tag.StudyDescription, VR.LO, project.getId());
            dataset.setString(Tag.PatientName, VR.PN, SUBJECT_LABEL);
            dataset.setString(Tag.PatientID, VR.LO, sessionLabel);
            try (DicomOutputStream dicomOutputStream = new DicomOutputStream(directory.resolve(source.getFileName()).toFile())) {
                dicomOutputStream.writeDataset(dataset.createFileMetaInformation(UID.DeflatedExplicitVRLittleEndian), dataset);
            }
            pixelData.put(dataset.getString(Tag.SOPInstanceUID), dataset.getBytes(Tag.PixelData));
        }
        return pixelData;
    }

    private File zip(Path directory) throws IOException {
        final File zip = directory.resolveSibling(directory.getFileName() + ".zip").toFile();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zip));
             Stream<Path> files = Files.list(directory)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                zipOutputStream.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, zipOutputStream);
                zipOutputStream.closeEntry();
            }
        }
        return zip;
    }
}
