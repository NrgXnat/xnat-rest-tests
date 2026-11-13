package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.dicom.transform.DicomFileWriter;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.dicom.DicomObjectIdentifier;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.versions.Xnat_1_10_2_2;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.nrg.testing.TestGroups.DICOM_SCP;
import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * A C-STORE dataset is encoded in the transfer syntax the association negotiated. Some archives send datasets that
 * still carry a file meta element, such as the Source Application Entity Title, ahead of everything else. From 1.10.0
 * until 1.10.2.2, XNAT took such a dataset for one that starts with file meta information, read the rest of it as
 * Explicit VR Little Endian whatever had been negotiated, and refused the object when the dataset was Implicit VR.
 * <p>
 * Each object is sent as it was written, without header changes, so the leading element reaches XNAT as it would
 * from such an archive. The objects route themselves to the project, and auto-archive, through their Patient Comments.
 */
@Test(groups = {DICOM_SCP, IMPORTER})
@AddedIn(Xnat_1_10_2_2.class)
@TestRequires(admin = true, dicomScp = true, data = TestData.SAMPLE_1_SCAN_4)
public class TestCStoreDatasetWithFileMetaElement extends BaseXnatRestTest {
    private static final Logger LOGGER = Logger.getLogger(TestCStoreDatasetWithFileMetaElement.class);
    private static final int OBJECT_COUNT = 3;
    private static final String SUBJECT_LABEL = "LeadingFileMeta";
    private static final String SENDING_ARCHIVE_AE_TITLE = "ARCHIVE";
    private static final String IMPLICIT_SESSION = "implicit_vr";
    private static final String EXPLICIT_SESSION = "explicit_vr";

    private final Project project = new Project();

    private final DicomScpReceiver receiver = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
                                                                    .host(Settings.DICOM_HOST)
                                                                    .port(Settings.DICOM_PORT)
                                                                    .identifier(DicomObjectIdentifier.DEFAULT.getId())
                                                                    .enabled(true)
                                                                    .customProcessing(false)
                                                                    .directArchive(false)
                                                                    .anonymizationEnabled(false)
                                                                    .whitelistEnabled(false);

    /**
     * The objects for each session, written with a leading (0002,0016) in the session's transfer syntax. They route
     * themselves to this run's project, so they are cached under its ID and removed afterwards.
     */
    private final LocallyCacheableDicomTransformation studies = new LocallyCacheableDicomTransformation("cstore-leading-file-meta-" + project.getId())
            .data(TestData.SAMPLE_1_SCAN_4)
            .transformations(study(IMPLICIT_SESSION, UID.ImplicitVRLittleEndian), study(EXPLICIT_SESSION, UID.ExplicitVRLittleEndian));

    @BeforeClass
    private void setup() {
        mainInterface().createProject(project);
        mainAdminInterface().createDicomScpReceiver(receiver);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 10000);
        studies.build();
    }

    @BeforeMethod(alwaysRun = true)
    private void clearProject() {
        restDriver.drainIngestPipeline(mainUser, project);
        try {
            mainInterface().deleteAllProjectData(project);
            TimeUtils.sleep(1000);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
    }

    @AfterClass(alwaysRun = true)
    private void tearDown() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
        mainAdminInterface().deleteDicomScpReceiver(receiver);
        try (Stream<Path> paths = Files.walk(studies.baseLevelDir())) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            LOGGER.warn("Unable to remove the objects under " + studies.baseLevelDir(), e);
        }
    }

    /** The case that used to fail: an Implicit VR dataset, which XNAT read as Explicit VR from the leading element on. */
    @Test
    public void testImplicitVrDatasetThatStartsWithAFileMetaElement() {
        sendAndCheck(IMPLICIT_SESSION, UID.ImplicitVRLittleEndian);
    }

    /** The Explicit VR case, which XNAT's guess happened to get right, must keep working. */
    @Test
    public void testExplicitVrDatasetThatStartsWithAFileMetaElement() {
        sendAndCheck(EXPLICIT_SESSION, UID.ExplicitVRLittleEndian);
    }

    /**
     * Sends the session's objects, waits for them to archive, and checks that every one arrived intact, in the
     * syntax it was sent in, with no file meta element left in its dataset.
     */
    private void sendAndCheck(String sessionLabel, String transferSyntax) {
        final Path sent = studies.locateBaseDirForTransformedData(sessionLabel);
        final Map<String, byte[]> sentPixelData = pixelDataBySopInstanceUid(sent);
        assertEquals(OBJECT_COUNT, sentPixelData.size());

        try {
            new XnatCStore(receiver).data(sent.toFile()).sendDICOM();
        } catch (Exception e) {
            throw new AssertionError("XNAT should accept every object, but the C-STORE failed", e);
        }
        assertFalse("the objects should have reached the prearchive", mainInterface().getPrearchiveEntriesForProject(project).isEmpty());
        restDriver.waitForPrearchiveEmpty(mainUser, project, 300);

        final ImagingSession session = new MRSession(project, new Subject(project, SUBJECT_LABEL), sessionLabel);
        final List<File> archived = restDriver.downloadAllDicomFromSession(mainUser, project, session.getSubject(), session);
        assertEquals("every object should have been archived", OBJECT_COUNT, archived.size());
        for (File file : archived) {
            try (DicomInputStream dicomInputStream = new DicomInputStream(file)) {
                final Attributes fileMetaInformation = dicomInputStream.readFileMetaInformation();
                assertEquals("the archived object should keep the syntax it was sent in",
                        transferSyntax, fileMetaInformation.getString(Tag.TransferSyntaxUID));
                final Attributes dataset = dicomInputStream.readDataset();
                for (int tag : dataset.tags()) {
                    assertFalse("no file meta element should be left in the dataset, found " + TagUtils.toString(tag),
                            TagUtils.groupNumber(tag) == 0x0002);
                }
                final byte[] expectedPixelData = sentPixelData.get(dataset.getString(Tag.SOPInstanceUID));
                assertNotNull("the archived object should be one of those sent", expectedPixelData);
                assertEquals("the pixel data should arrive intact", expectedPixelData, dataset.getBytes(Tag.PixelData));
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to read the archived " + file, e);
            }
        }
    }

    /**
     * Three sample objects as a new study for the session, routed to this project and auto-archived, each written
     * with file meta information for <b>transferSyntax</b> and a dataset that starts with (0002,0016).
     */
    private DicomTransformation study(String sessionLabel, String transferSyntax) {
        final String studyInstanceUid = UIDUtils.createUID();
        final String seriesInstanceUid = UIDUtils.createUID();
        final String patientComments = String.format("Project:%s Subject:%s Session:%s AA:true", project.getId(), SUBJECT_LABEL, sessionLabel);
        return new DicomTransformation(sessionLabel)
                .prefilter(DicomFilters.subsetWithInstanceNumber(Arrays.asList(1, 2, 3)))
                .transformFunction(TransformFunction.simple(dicom -> {
                    dicom.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
                    dicom.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
                    dicom.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
                    dicom.setString(Tag.PatientName, VR.PN, SUBJECT_LABEL);
                    dicom.setString(Tag.PatientID, VR.LO, sessionLabel);
                    dicom.setString(Tag.PatientComments, VR.LT, patientComments);
                }))
                .dicomFileWriter(leadingFileMetaElementWriter(transferSyntax));
    }

    /** Writes each object with file meta information for <b>transferSyntax</b>, and the Source AE Title in its dataset too. */
    private static DicomFileWriter leadingFileMetaElementWriter(String transferSyntax) {
        return (instance, dataDir, fileIndex) -> {
            final Attributes dataset = new Attributes();
            dataset.addNotSelected(instance, DicomUtils.FMI_ELEMENTS);
            final Attributes fileMetaInformation = dataset.createFileMetaInformation(transferSyntax);
            dataset.setString(Tag.SourceApplicationEntityTitle, VR.AE, SENDING_ARCHIVE_AE_TITLE);
            final File file = dataDir.resolve(fileIndex + ".dcm").toFile();
            try (DicomOutputStream dicomOutputStream = new DicomOutputStream(file)) {
                dicomOutputStream.writeDataset(fileMetaInformation, dataset);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to write " + file, e);
            }
            return file;
        };
    }

    /** The pixel data of each object the transformation wrote, which may be from an earlier run, by SOP Instance UID. */
    private static Map<String, byte[]> pixelDataBySopInstanceUid(Path transformedData) {
        final Map<String, byte[]> pixelData = new HashMap<>();
        try (Stream<Path> files = Files.walk(transformedData)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".dcm")).collect(Collectors.toList())) {
                try (DicomInputStream dicomInputStream = new DicomInputStream(file.toFile())) {
                    dicomInputStream.readFileMetaInformation();
                    final Attributes dataset = dicomInputStream.readDataset();
                    pixelData.put(dataset.getString(Tag.SOPInstanceUID), dataset.getBytes(Tag.PixelData));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the objects under " + transformedData, e);
        }
        return pixelData;
    }
}
