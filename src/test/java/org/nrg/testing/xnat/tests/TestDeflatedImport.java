package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.transform.DicomFileWriter;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.nrg.testing.TestGroups.PREARCHIVE;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

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
    private static final String NO_SCRIPT_SESSION = "zip";
    private static final String SITE_SCRIPT_SESSION = "zip_site_script";

    /** Writes each object with Deflated Explicit VR Little Endian file meta, so its dataset is deflated. */
    private static final DicomFileWriter DEFLATED_WRITER = (instance, dataDir, fileIndex) -> {
        final Attributes dataset = new Attributes();
        dataset.addNotSelected(instance, DicomUtils.FMI_ELEMENTS); // new file meta below, from the remapped UIDs
        final File file = dataDir.resolve(fileIndex + ".dcm").toFile();
        try (DicomOutputStream dicomOutputStream = new DicomOutputStream(file)) {
            dicomOutputStream.writeDataset(dataset.createFileMetaInformation(UID.DeflatedExplicitVRLittleEndian), dataset);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write " + file, e);
        }
        return file;
    };

    /** A Deflated study of three sample objects for each method, zipped for upload. */
    private static final LocallyCacheableDicomTransformation DEFLATED_STUDIES = new LocallyCacheableDicomTransformation("deflated-import")
            .data(TestData.SAMPLE_1_SCAN_4)
            .transformations(deflatedStudy(NO_SCRIPT_SESSION), deflatedStudy(SITE_SCRIPT_SESSION));

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
        mainAdminInterface().enableSiteAnonScript();
        mainAdminInterface().setSiteAnonScript(restDriver.getDefaultXnatAnonScript());
        restDriver.deleteProjectSilently(mainAdminUser, project);
    }

    @Test(groups = PREARCHIVE)
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testDeflatedZipUpload() {
        importDeflated(NO_SCRIPT_SESSION, false);
    }

    @Test(groups = PREARCHIVE)
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testDeflatedZipUploadWithSiteScript() {
        importDeflated(SITE_SCRIPT_SESSION, true);
    }

    /**
     * Uploads the session's Deflated objects to the prearchive, archives them, and checks what arrived. The site
     * script, when there is one, removes (0008,0031), which shows it ran on the Deflated objects.
     */
    private void importDeflated(String sessionLabel, boolean withSiteScript) {
        if (withSiteScript) {
            mainAdminInterface().setSiteAnonScript(XnatObjectUtils.anonScriptFromFile(DicomEditVersion.DE_6, "simplisticDelete.das"));
            mainAdminInterface().enableSiteAnonScript();
        } else {
            mainAdminInterface().disableSiteAnonScript();
        }
        DEFLATED_STUDIES.build();
        final Map<String, byte[]> sentPixelData = pixelDataBySopInstanceUid(DEFLATED_STUDIES.locateBaseDirForTransformedData(sessionLabel));
        assertEquals(OBJECT_COUNT, sentPixelData.size());

        mainInterface().callImporter(new DicomZipRequest().project(project).file(DEFLATED_STUDIES.locateZipForIndividualTransformation(sessionLabel).toFile()));
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
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to read the archived " + file, e);
            }
        }
    }

    /** Three sample objects as one new study, labelled for the session, written Deflated and zipped. */
    private static DicomTransformation deflatedStudy(String sessionLabel) {
        final String studyInstanceUid = UIDUtils.createUID();
        final String seriesInstanceUid = UIDUtils.createUID();
        return new DicomTransformation(sessionLabel)
                .prefilter(DicomFilters.subsetWithInstanceNumber(Arrays.asList(1, 2, 3)))
                .transformFunction(TransformFunction.simple(dicom -> {
                    dicom.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
                    dicom.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
                    dicom.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
                    dicom.setString(Tag.PatientName, VR.PN, SUBJECT_LABEL);
                    dicom.setString(Tag.PatientID, VR.LO, sessionLabel);
                }))
                .dicomFileWriter(DEFLATED_WRITER)
                .produceZip();
    }

    /** The pixel data of each object the transformation wrote, which may be from an earlier run, by SOP Instance UID. */
    private static Map<String, byte[]> pixelDataBySopInstanceUid(Path transformedData) {
        final Map<String, byte[]> pixelData = new HashMap<>();
        try (Stream<Path> files = Files.walk(transformedData)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".dcm")).collect(Collectors.toList())) {
                try (DicomInputStream dicomInputStream = new DicomInputStream(file.toFile())) {
                    final Attributes dataset = dicomInputStream.readDataset();
                    pixelData.put(dataset.getString(Tag.SOPInstanceUID), dataset.getBytes(Tag.PixelData));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the Deflated objects under " + transformedData, e);
        }
        return pixelData;
    }
}
