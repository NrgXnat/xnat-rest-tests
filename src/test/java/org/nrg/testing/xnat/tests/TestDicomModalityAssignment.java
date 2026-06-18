package org.nrg.testing.xnat.tests;

import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.DeprecatedIn;
import org.nrg.testing.annotations.RequireXnatVersion;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.PetMrProcessingSetting;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.extensions.subject_assessor.DicomZipImportExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SubjectAssessorExtension;
import org.nrg.xnat.versions.Xnat_1_8_10;
import org.nrg.xnat.versions.Xnat_1_8_4;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.nrg.testing.TestGroups.METADATA_EXTRACTION;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = {IMPORTER, METADATA_EXTRACTION})
public class TestDicomModalityAssignment extends BaseXnatRestTest {

    private final Attributes baseDicom = DicomUtils.readDicom(getDataFile("scan1/000000.dcm"));
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
    private void setup() {
        mainAdminInterface().setupDataType(OTHER_SESSION);
        mainAdminInterface().setupDataType(XA_SESSION);
    }

    @BeforeClass
    private void initTestProject() {
        mainInterface().createProject(testProject);
    }

    @AfterClass
    private void teardownTestProject() {
        restDriver.deleteProjectSilently(mainUser, testProject);
    }

    @AfterMethod
    private void clearData() {
        // Drain the async ingest pipeline (clear prearchive, then wait for both queues to empty) before
        // deleting files, so the delete can't race the server's build/rebuild. On NFS that race leaves
        // .nfs* placeholders ("Device or resource busy") that break the recursive delete.
        restDriver.drainIngestPipeline(mainUser, testProject);
        mainAdminInterface().deleteAllProjectData(testProject);
    }

    public void testModalityExtractionStandardMr() {
        new SimpleDicomModalityTest(DataType.MR_SESSION)
                .withSeries(
                        new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 10),
                        new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 15)
                ).run();
    }

    public void testModalityExtractionStandardPet() {
        new SimpleDicomModalityTest(DataType.PET_SESSION)
                .withSeries(
                        new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 1)
                ).run();
    }

    public void testModalityExtractionSecondaryCapture() {
        new SimpleDicomModalityTest(DataType.CT_SESSION)
                .withSeries(
                        new SeriesSpec(CT, SECONDARY_CAPTURE_SCAN).consistentInstances(UID.SecondaryCaptureImageStorage, 2)
                ).run();
    }

    public void testModalityExtractionSecondaryCaptureUnmappableModality() {
        new SimpleDicomModalityTest(OTHER_SESSION)
                .withSeries(
                        new SeriesSpec("SC", SECONDARY_CAPTURE_SCAN).consistentInstances(UID.SecondaryCaptureImageStorage, 1)
                ).run();
    }

    @AddedIn(Xnat_1_8_4.class) // might actually work on earlier versions of 1.8.x
    public void testModalityExtractionMrAndRt() {
        new SimpleDicomModalityTest(DataType.MR_SESSION)
                .withSeries(
                        new SeriesSpec(RTSTRUCT, RT_SCAN).consistentInstances(UID.RTStructureSetStorage, 1),
                        new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.EnhancedMRImageStorage, 1)
                ).run();
    }

    public void testModalityExtractionMrInconsistentSopClass() {
        new SimpleDicomModalityTest(DataType.MR_SESSION)
                .withSeries(
                        new SeriesSpec(MR, DataType.MR_SCAN).instances(
                                new InstanceSpec(UID.RawDataStorage),
                                new InstanceSpec(UID.MRImageStorage)
                        )
                ).run();
    }

    public void testModalityExtractionXa() {
        new SimpleDicomModalityTest(XA_SESSION)
                .withSeries(
                        new SeriesSpec(XA, XA_SCAN).consistentInstances(UID.XRayAngiographicImageStorage, 1)
                ).run();
    }

    @AddedIn(Xnat_1_8_5.class)
    public void testModalityExtractionXa3d() {
        new SimpleDicomModalityTest(XA_SESSION)
                .withSeries(
                        new SeriesSpec(XA, XA_SCAN).consistentInstances(UID.XRay3DAngiographicImageStorage, 1)
                ).run();
    }

    @DeprecatedIn(Xnat_1_8_5.class)
    public void testModalityExtractionXa3dLegacy() {
        new SimpleDicomModalityTest(XA_SESSION)
                .withSeries(
                        new SeriesSpec(XA, OTHER_SCAN).consistentInstances(UID.XRay3DAngiographicImageStorage, 1)
                ).run();
    }

    public void testModalityExtractionCr() {
        new SimpleDicomModalityTest(DataType.CR_SESSION)
                .withSeries(
                        new SeriesSpec(CR, CR_SCAN).consistentInstances(UID.ComputedRadiographyImageStorage, 2)
                ).run();
    }

    @AddedIn(Xnat_1_8_5.class)
    public void testModalityExtractionMrSopClassMissingModality() {
        new SimpleDicomModalityTest(DataType.MR_SESSION)
                .withSeries(
                        new SeriesSpec("", DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 4)
                ).run();
    }

    @RequireXnatVersion(allowedVersions = Xnat_1_8_4.class) // might actually work on earlier versions of 1.8.x
    public void testModalityExtractionMrSopClassMissingModalityLegacy() {
        new SimpleDicomModalityTest(OTHER_SESSION)
                .withSeries(
                        new SeriesSpec("", DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 4)
                ).run();
    }

    public void testModalityExtractionPetCt() {
        new SimpleDicomModalityTest(DataType.PET_SESSION)
                .withSeries(
                        new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 5),
                        new SeriesSpec(CT, DataType.CT_SCAN).consistentInstances(UID.CTImageStorage, 10)
                ).run();
    }

    @AddedIn(Xnat_1_8_4.class) // might actually work on earlier versions of 1.8.x
    public void testModalityExtractionPetCtRt() {
        new SimpleDicomModalityTest(DataType.PET_SESSION)
                .withSeries(
                        new SeriesSpec(RTSTRUCT, RT_SCAN).consistentInstances(UID.RTStructureSetStorage, 1),
                        new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 5),
                        new SeriesSpec(CT, DataType.CT_SCAN).consistentInstances(UID.CTImageStorage, 10)
                ).run();
    }

    public void testModalityExtractionPetMr() {
        mainAdminInterface().setProjectPetMrSetting(testProject, PetMrProcessingSetting.AS_PET_MR);
        new SimpleDicomModalityTest(DataType.PET_MR_SESSION)
                .withSeries(
                        new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 2),
                        new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 3)
                ).usingDicomZipImporter()
                .run();
    }

    public void testModalityExtractionPetMrAsPet() {
        mainAdminInterface().setProjectPetMrSetting(testProject, PetMrProcessingSetting.AS_PET);
        new SimpleDicomModalityTest(DataType.PET_SESSION)
                .withSeries(
                        new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 2),
                        new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 3)
                ).usingDicomZipImporter()
                .run();
    }

    @AddedIn(Xnat_1_8_10.class)
    public void testModalityExtractionPetMrSplit() {
        final Map<DataType, List<SeriesSpec>> seriesSpecs = new HashMap<>();
        seriesSpecs.put(
                DataType.MR_SESSION,
                Arrays.asList(
                        new SeriesSpec(MR, DataType.MR_SCAN).consistentInstances(UID.MRImageStorage, 3),
                        new SeriesSpec(RTSTRUCT, RT_SCAN).consistentInstances(UID.RTStructureSetStorage, 1)
                )
        );
        seriesSpecs.put(
                DataType.PET_SESSION,
                Arrays.asList(
                        new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 5),
                        new SeriesSpec(PT, DataType.PET_SCAN).consistentInstances(UID.PositronEmissionTomographyImageStorage, 3)
                )
        );
        mainAdminInterface().setProjectPetMrSetting(testProject, PetMrProcessingSetting.SPLIT);
        new GeneralDicomModalityTest<>()
                .withSeries(seriesSpecs)
                .withCustomUploadMethod((session, sessionZip) -> {
                    return new SubjectAssessorExtension(session) {
                        @Override
                        public void create(XnatInterface xnatInterface, Project project, Subject subject) {
                            xnatInterface.callImporter(
                                    new DicomZipRequest()
                                            .file(sessionZip)
                                            .destArchive()
                                            .project(project)
                                            .subject(subject)
                                            .param("action", "commit")
                            );
                        }
                    };
                }).run();
    }

    @SuppressWarnings("unchecked")
    private class GeneralDicomModalityTest<X extends GeneralDicomModalityTest<X>> {
        private Map<DataType, List<SeriesSpec>> seriesSpecs;
        private boolean useDicomZipImporter = false;
        private BiFunction<ImagingSession, File, SubjectAssessorExtension> customUpload;

        X withSeries(Map<DataType, List<SeriesSpec>> seriesSpecs) {
            this.seriesSpecs = seriesSpecs;
            return (X) this;
        }

        X usingDicomZipImporter() {
            useDicomZipImporter = true;
            return (X) this;
        }

        X withCustomUploadMethod(BiFunction<ImagingSession, File, SubjectAssessorExtension> customUpload) {
            this.customUpload = customUpload;
            return (X) this;
        }

        void run() {
            final String studyInstanceUid = UIDUtils.createUID();
            int seriesIndex = 0;
            final List<Attributes> dicomInstances = new ArrayList<>();
            for (List<SeriesSpec> specs : seriesSpecs.values()) {
                for (SeriesSpec series : specs) {
                    for (InstanceSpec instance : series.instances) {
                        final Attributes dicomForInstance = new Attributes();
                        dicomForInstance.addAll(baseDicom);
                        dicomForInstance.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
                        dicomForInstance.setString(Tag.SeriesInstanceUID, VR.UI, series.seriesInstanceUid);
                        dicomForInstance.setString(Tag.SOPInstanceUID, VR.UI, instance.sopInstanceUid);
                        dicomForInstance.setString(Tag.SOPClassUID, VR.UI, instance.sopClassUid);
                        dicomForInstance.setString(Tag.Modality, VR.CS, series.modality);
                        dicomForInstance.setInt(Tag.SeriesNumber, VR.IS, seriesIndex);
                        dicomInstances.add(dicomForInstance);
                    }
                    seriesIndex++;
                }
            }

            final File sessionZip = DicomUtils.composeDicomInstanceToZip(dicomInstances);

            final Subject subject = new Subject(testProject);
            final ImagingSession session = new ImagingSession(testProject, subject);
            if (customUpload != null) {
                customUpload.apply(session, sessionZip);
            } else if (useDicomZipImporter) {
                new DicomZipImportExtension(session, sessionZip);
            } else {
                new SessionImportExtension(session, sessionZip);
            }
            mainInterface().createSubject(subject);

            final List<ImagingSession> sessionsInXnat = mainInterface().readProject(testProject.getId()).findSubject(subject.getLabel()).getSessions();
            assertEquals(
                    seriesSpecs.keySet().stream().map(DataType::getXsiType).collect(Collectors.toSet()),
                    sessionsInXnat.stream().map(sessionObj -> sessionObj.getDataType().getXsiType()).collect(Collectors.toSet())
            );
            assertEquals(seriesSpecs.size(), sessionsInXnat.size());
            // TODO: assert modality? complication: can't just add modality as a column to read for subject assessors since that will cause the results to only include sessions

            for (Map.Entry<DataType, List<SeriesSpec>> expectedStudyEntry : seriesSpecs.entrySet()) {
                final ImagingSession sessionAsExistsInXnat = sessionsInXnat
                        .stream()
                        .filter(candidate -> candidate.getDataType().getXsiType().equals(expectedStudyEntry.getKey().getXsiType()))
                        .findFirst()
                        .orElseThrow(RuntimeException::new);
                assertEquals(expectedStudyEntry.getValue().size(), sessionAsExistsInXnat.getScans().size());

                for (SeriesSpec series : expectedStudyEntry.getValue()) {
                    final Scan scanAsExistsInXnat = sessionAsExistsInXnat.findSeriesByUid(series.seriesInstanceUid);
                    assertEquals(series.expectedXsiType, scanAsExistsInXnat.getXsiType());
                }
            }
        }
    }

    private class SimpleDicomModalityTest extends GeneralDicomModalityTest<SimpleDicomModalityTest> {
        private final DataType expectedDataType;

        SimpleDicomModalityTest(DataType expectedDataType) {
            this.expectedDataType = expectedDataType;
        }

        SimpleDicomModalityTest withSeries(SeriesSpec... series) {
            return withSeries(Collections.singletonMap(expectedDataType, Arrays.asList(series)));
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
