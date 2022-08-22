package org.nrg.testing.xnat.tests;

import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.DeprecatedIn;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.PetMrProcessingSetting;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.extensions.subject_assessor.DicomZipImportExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.nrg.testing.TestGroups.METADATA_EXTRACTION;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = {IMPORTER, METADATA_EXTRACTION})
public class TestDicomModalityAssignment extends BaseXnatRestTest {

    private final Attributes baseDicom = DicomUtils.readDicom(getDataFile("scan1/000000.dcm")).getDataset();
    private final Project testProject = new Project();
    private static final String CR = "CR";
    private static final String CT = "CT";
    private static final String MR = "MR";
    private static final String PT = "PT";
    private static final String XA = "XA";
    private static final String RTSTRUCT = "RTSTRUCT";
    private static final DataType CR_SCAN = new DataType().xsiType("xnat:crScanData");
    private static final DataType OTHER_SCAN = new DataType().xsiType("xnat:otherDicomScanData");
    private static final DataType RT_SCAN = new DataType().xsiType("xnat:rtImageScanData");
    private static final DataType SECONDARY_CAPTURE_SCAN = new DataType().xsiType("xnat:scScanData");
    private static final DataType XA_SCAN = new DataType().xsiType("xnat:xaScanData");
    private static final DataType OTHER_SESSION = new DataType().xsiType("xnat:otherDicomSessionData");
    private static final DataType XA_SESSION = new DataType().xsiType("xnat:xaSessionData");

    @BeforeClass
    private void initTestProject() {
        mainInterface().createProject(testProject);
    }

    @AfterClass
    private void teardownTestProject() {
        restDriver.deleteProjectSilently(mainUser, testProject);
    }

    public void testModalityExtractionStandardMr() {
        new DicomModalityTest(
                new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 10),
                new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 15)
        ).run(DataType.MR_SESSION);
    }

    public void testModalityExtractionStandardPet() {
        new DicomModalityTest(
                new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 1)
        ).run(DataType.PET_SESSION);
    }

    public void testModalityExtractionSecondaryCapture() {
        new DicomModalityTest(
                new SeriesSpec(CT, SECONDARY_CAPTURE_SCAN).consistentInstances(UID.SecondaryCaptureImageStorage, 2)
        ).run(DataType.CT_SESSION);
    }

    public void testModalityExtractionSecondaryCaptureUnmappableModality() {
        new DicomModalityTest(
                new SeriesSpec("SC", SECONDARY_CAPTURE_SCAN).consistentInstances(UID.SecondaryCaptureImageStorage, 1)
        ).run(OTHER_SESSION);
    }

    public void testModalityExtractionMrAndRt() {
        new DicomModalityTest(
                new SeriesSpec(RTSTRUCT, RT_SCAN).consistentInstances(UID.RTStructureSetStorage, 1),
                new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.EnhancedMRImageStorage, 1)
        ).run(DataType.MR_SESSION);
    }

    public void testModalityExtractionMrInconsistentSopClass() {
        new DicomModalityTest(
                new SeriesSpec(MR, DataType.MR_SCAN).instances(
                        new InstanceSpec(UID.RawDataStorage),
                        new InstanceSpec(UID.MRImageStorage)
                )
        ).run(DataType.MR_SESSION);
    }

    public void testModalityExtractionXa() {
        new DicomModalityTest(
                new SeriesSpec(XA, XA_SCAN).consistentInstances(UID.XRayAngiographicImageStorage, 1)
        ).run(XA_SESSION);
    }

    @AddedIn(Xnat_1_8_5.class)
    public void testModalityExtractionXa3d() {
        new DicomModalityTest(
                new SeriesSpec(XA, XA_SCAN).consistentInstances(UID.XRay3DAngiographicImageStorage, 1)
        ).run(XA_SESSION);
    }

    @DeprecatedIn(Xnat_1_8_5.class)
    public void testModalityExtractionXa3dLegacy() {
        new DicomModalityTest(
                new SeriesSpec(XA, OTHER_SCAN).consistentInstances(UID.XRay3DAngiographicImageStorage, 1)
        ).run(XA_SESSION);
    }

    public void testModalityExtractionCr() {
        new DicomModalityTest(
                new SeriesSpec(CR, CR_SCAN).consistentInstances(UID.ComputedRadiographyImageStorage, 2)
        ).run(DataType.CR_SESSION);
    }

    @AddedIn(Xnat_1_8_5.class)
    public void testModalityExtractionMrSopClassMissingModality() {
        new DicomModalityTest(
                new SeriesSpec("", DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 4)
        ).run(DataType.MR_SESSION);
    }

    @DeprecatedIn(Xnat_1_8_5.class)
    public void testModalityExtractionMrSopClassMissingModalityLegacy() {
        new DicomModalityTest(
                new SeriesSpec("", DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 4)
        ).run(OTHER_SESSION);
    }

    public void testModalityExtractionPetCt() {
        new DicomModalityTest(
                new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 5),
                new SeriesSpec(CT, DataType.CT_SCAN).consistentInstances(UID.CTImageStorage, 10)
        ).run(DataType.PET_SESSION);
    }

    public void testModalityExtractionPetCtRt() {
        new DicomModalityTest(
                new SeriesSpec(RTSTRUCT, RT_SCAN).consistentInstances(UID.RTStructureSetStorage, 1),
                new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 5),
                new SeriesSpec(CT, DataType.CT_SCAN).consistentInstances(UID.CTImageStorage, 10)
        ).run(DataType.PET_SESSION);
    }

    public void testModalityExtractionPetMr() {
        mainAdminInterface().setProjectPetMrSetting(testProject, PetMrProcessingSetting.AS_PET_MR);
        new DicomModalityTest(
                new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 2),
                new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 3)
        ).usingDicomZipImporter().run(DataType.PET_MR_SESSION);
    }

    public void testModalityExtractionPetMrAsPet() {
        mainAdminInterface().setProjectPetMrSetting(testProject, PetMrProcessingSetting.AS_PET);
        new DicomModalityTest(
                new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 2),
                new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 3)
        ).usingDicomZipImporter().run(DataType.PET_SESSION);
    }

    private class DicomModalityTest {
        private final List<SeriesSpec> seriesSpecs;
        private boolean useDicomZipImporter = false;

        DicomModalityTest(SeriesSpec... seriesSpecs) {
            this.seriesSpecs = Arrays.asList(seriesSpecs);
        }

        DicomModalityTest usingDicomZipImporter() {
            useDicomZipImporter = true;
            return this;
        }

        void run(DataType expectedSessionType) {
            final String studyInstanceUid = UIDUtils.createUID();
            int seriesIndex = 0;
            final List<DatasetWithFMI> dicomInstances = new ArrayList<>();
            for (SeriesSpec series : seriesSpecs) {
                for (InstanceSpec instance : series.instances) {
                    final Attributes dicomForInstance = new Attributes();
                    dicomForInstance.addAll(baseDicom);
                    dicomForInstance.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
                    dicomForInstance.setString(Tag.SeriesInstanceUID, VR.UI, series.seriesInstanceUid);
                    dicomForInstance.setString(Tag.SOPInstanceUID, VR.UI, instance.sopInstanceUid);
                    dicomForInstance.setString(Tag.SOPClassUID, VR.UI, instance.sopClassUid);
                    dicomForInstance.setString(Tag.Modality, VR.CS, series.modality);
                    dicomForInstance.setInt(Tag.SeriesNumber, VR.IS, seriesIndex);
                    dicomInstances.add(
                            new DatasetWithFMI(
                                    dicomForInstance.createFileMetaInformation(UID.ExplicitVRLittleEndian),
                                    dicomForInstance
                            )
                    );
                }
                seriesIndex++;
            }

            final File sessionZip = DicomUtils.composeDicomInstanceToZip(dicomInstances);

            final Subject subject = new Subject(testProject);
            final ImagingSession session = new ImagingSession(testProject, subject);
            if (useDicomZipImporter) {
                new DicomZipImportExtension(session, sessionZip);
            } else {
                new SessionImportExtension(session, sessionZip);
            }
            mainInterface().createSubject(subject);

            final ImagingSession sessionAsExistsInXnat = (ImagingSession) mainInterface().readSubjectAssessors(testProject, subject).get(0);
            assertEquals(expectedSessionType.getXsiType(), sessionAsExistsInXnat.getDataType().getXsiType());
            // TODO: assert modality? complication: can't just add modality as a column to read for subject assessors since that will cause the results to only include sessions

            for (SeriesSpec series : seriesSpecs) {
                final Scan scanAsExistsInXnat = sessionAsExistsInXnat.findSeriesByUid(series.seriesInstanceUid);
                assertEquals(series.expectedXsiType, scanAsExistsInXnat.getXsiType());
            }
        }
    }

    private class SeriesSpec {
        String seriesInstanceUid;
        String modality;
        String expectedXsiType;
        List<InstanceSpec> instances;

        SeriesSpec(String modality, DataType expectedXsiType) {
            this.modality = modality;
            this.expectedXsiType = expectedXsiType.getXsiType();
            seriesInstanceUid = UIDUtils.createUID();
        }

        SeriesSpec consistentInstances(String sopClassUid, int numInstances) {
            return instances(
                    IntStream.range(0, numInstances)
                            .mapToObj(x -> new InstanceSpec(sopClassUid))
                            .collect(Collectors.toList())
            );
        }

        SeriesSpec instances(List<InstanceSpec> instances) {
            this.instances = instances;
            return this;
        }

        SeriesSpec instances(InstanceSpec... instances) {
            return instances(Arrays.asList(instances));
        }
    }

    private class InstanceSpec {
        String sopClassUid;
        String sopInstanceUid;

        InstanceSpec(String sopClassUid) {
            this.sopClassUid = sopClassUid;
            sopInstanceUid = UIDUtils.createUID();
        }
    }

}
