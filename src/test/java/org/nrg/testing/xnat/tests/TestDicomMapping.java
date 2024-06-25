package org.nrg.testing.xnat.tests;

import org.apache.commons.lang3.RandomStringUtils;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.TestNgUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.VariableType;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.dicom.DicomMapping;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.versions.Xnat_1_8_0;
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

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.nrg.testing.TestGroups.METADATA_EXTRACTION;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

@Test(groups = {IMPORTER, METADATA_EXTRACTION})
@TestRequires(data = TestData.DICOM_WEB_CTRT1)
@AddedIn(Xnat_1_8_0.class)
public class TestDicomMapping extends BaseXnatRestTest {

    private Project otherProject;
    private final List<DicomMapping> tempMappings = new ArrayList<>();
    private static final LocallyCacheableDicomTransformation CT_RT_TEST_DATA = new LocallyCacheableDicomTransformation("dicom_mapping_test_ct")
            .data(TestData.DICOM_WEB_CTRT1)
            .createZip()
            .simpleTransform((dicom) -> dicom.setString(Tag.PatientAddress, VR.LO, (dicom.getInt(Tag.SeriesNumber, 0) == 2) ? "true" : "false"));
    private static final LocallyCacheableDicomTransformation MR_TEST_DATA = new LocallyCacheableDicomTransformation("dicom_mapping_test_mr")
            .data(TestData.SAMPLE_1)
            .createZip()
            .simpleTransform((dicom) -> dicom.setString(Tag.PerformedProcedureStepID, VR.SH, "false"));
    private static final List<String> POSSIBLE_FIELDS = new ArrayList<>();
    private static final String CT_RT_LABEL = "0522c0001";
    private static final String CT_RT_STUDY_ID = "0522c0001_0003";
    private static final String ALWAYS_MISSING_FIELD = field("neverpresent");
    private static final String NUM_FRAMES_FIELD_NAME = field("numframes");
    private static final String STUDY_ID_FIELD_NAME = field("studyid");
    private static final String SOP_INSTANCE_UID_FIELD_NAME = field("sopinstanceuid");
    private static final String RESCALE_SLOPE_FIELD_NAME = field("rescaleslope");
    private static final String PATIENT_ADDRESS_FIELD_NAME = field("patientaddress");
    private static final String INSTANCE_CREATION_DATE_FIELD_NAME = field("instancecreationdate");
    private static final String MR_LABEL = "Sample_ID";
    private static final String PHOTOMETRIC_INTERPRETATION_FIELD_NAME = field("photometricinterpretation");
    private static final String ROWS_FIELD_NAME = field("rows");
    private static final String PATIENT_WEIGHT_FIELD_NAME = field("patientweight");
    private static final String PERFORMED_PROCEDURE_STEP_ID_FIELD_NAME = field("performedprocedurestepid");
    private static final String STUDY_DATE_FIELD_NAME = field("studydate");
    private static final SessionValidation CTRT_VALIDATION = new SessionValidation()
            .sessionLabel(CT_RT_LABEL)
            .withValidation(FieldValidation.EMPTY)
            .validatingScan(
                    "1",
                    new FieldValidation()
                            .expectFieldsMissing(NUM_FRAMES_FIELD_NAME, STUDY_ID_FIELD_NAME, SOP_INSTANCE_UID_FIELD_NAME, PATIENT_ADDRESS_FIELD_NAME, INSTANCE_CREATION_DATE_FIELD_NAME)
                            .expect(RESCALE_SLOPE_FIELD_NAME, "1.001")
            ).validatingScan(
                    "2",
                    new FieldValidation()
                            .expectFieldsMissing(NUM_FRAMES_FIELD_NAME, SOP_INSTANCE_UID_FIELD_NAME, RESCALE_SLOPE_FIELD_NAME, INSTANCE_CREATION_DATE_FIELD_NAME)
                            .expect(STUDY_ID_FIELD_NAME, CT_RT_STUDY_ID)
                            .expect(PATIENT_ADDRESS_FIELD_NAME, "true")
            ).validatingScan(
                    "3",
                    new FieldValidation()
                            .expectFieldsMissing(NUM_FRAMES_FIELD_NAME, SOP_INSTANCE_UID_FIELD_NAME, RESCALE_SLOPE_FIELD_NAME)
                            .expect(STUDY_ID_FIELD_NAME, CT_RT_STUDY_ID)
                            .expect(PATIENT_ADDRESS_FIELD_NAME, "false")
                            .expect(INSTANCE_CREATION_DATE_FIELD_NAME, "2005-10-28")
            ).validatingScan(
                    "4",
                    new FieldValidation()
                            .expectFieldsMissing(SOP_INSTANCE_UID_FIELD_NAME, RESCALE_SLOPE_FIELD_NAME)
                            .expect(NUM_FRAMES_FIELD_NAME, "65")
                            .expect(STUDY_ID_FIELD_NAME, CT_RT_STUDY_ID)
                            .expect(PATIENT_ADDRESS_FIELD_NAME, "false")
            );
    private static final SessionValidation CTRT_SKIPPED_VALIDATION = new SessionValidation()
            .sessionLabel(CT_RT_LABEL)
            .withValidation(FieldValidation.EMPTY)
            .validatingScansEmpty("1", "2", "3", "4");
    private static final SessionValidation MR_VALIDATION = new SessionValidation()
            .sessionLabel(MR_LABEL)
            .withValidation(
                    new FieldValidation()
                            .expect(PHOTOMETRIC_INTERPRETATION_FIELD_NAME, "MONOCHROME2")
                            .expect(ROWS_FIELD_NAME, "256")
                            .expect(PATIENT_WEIGHT_FIELD_NAME, "68.038864155")
                            .expect(PERFORMED_PROCEDURE_STEP_ID_FIELD_NAME, "false")
                            .expect(STUDY_DATE_FIELD_NAME, "2006-12-14")
            ).validatingScansEmpty("4", "5", "6");

    @BeforeClass(alwaysRun = true, groups = {IMPORTER, METADATA_EXTRACTION})
    private void setupOtherProject() {
        otherProject = registerTempProject();
        mainAdminInterface().createProject(otherProject);
        mainAdminInterface().disableSessionDicomRoutingConfig();
        mainAdminInterface().disableProjectDicomRoutingConfig();
    }

    @AfterMethod(alwaysRun = true, groups = {IMPORTER, METADATA_EXTRACTION})
    private void clearMappings() {
        for (DicomMapping dicomMapping : tempMappings) {
            mainAdminInterface().deleteDicomMapping(dicomMapping);
        }
        tempMappings.clear();
    }

    public void testDicomMappingPermissions() {
        mainInterface().disableAdminCheck();
        final String fieldName = RandomStringUtils.randomAlphabetic(20).toLowerCase();
        mainInterface().createProject(testSpecificProject);

        final DicomMapping dicomMapping = new DicomMapping()
                .forProject(testSpecificProject)
                .fieldName(fieldName)
                .fieldType(VariableType.FLOAT)
                .dicomTag(Tag.PatientWeight)
                .forDataType(DataType.MR_SESSION);

        expect403(() -> mainInterface().createOrUpdateDicomMapping(dicomMapping));
        expect403(() -> mainInterface().readDicomMappings());
        assertMappingNotFound(dicomMapping);

        mainAdminInterface().createOrUpdateDicomMapping(dicomMapping);
        assertMappingFound(dicomMapping);

        expect403(() -> mainInterface().deleteDicomMapping(dicomMapping));
        assertMappingFound(dicomMapping);

        mainAdminInterface().deleteDicomMapping(dicomMapping);
        assertMappingNotFound(dicomMapping);
    }

    public void testDicomMappingScanProject() {
        new DicomMappingTest()
                .usingImportApi()
                .addProjectSpecificDicomMappings(
                        mappingNumberOfFrames(),
                        mappingStudyId(),
                        mappingSopInstanceUid(),
                        mappingRescaleSlope(),
                        mappingPatientAddress(),
                        mappingInstanceCreationDate()
                ).addDicomMappings(mappingWrongProject()).uploadingData(CT_RT_TEST_DATA, CTRT_VALIDATION)
                .run();

        mainInterface().deleteAllProjectData(testSpecificProject);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
        new DicomMappingTest()
                .usingCstore()
                .uploadingData(CT_RT_TEST_DATA, CTRT_VALIDATION)
                .run();
    }

    public void testDicomMappingScanSite() {
        new DicomMappingTest()
                .usingImportApi()
                .addSiteLevelDicomMappings(
                        mappingNumberOfFrames(),
                        mappingStudyId(),
                        mappingSopInstanceUid(),
                        mappingRescaleSlope(),
                        mappingPatientAddress(),
                        mappingInstanceCreationDate()
                ).uploadingData(CT_RT_TEST_DATA, CTRT_VALIDATION)
                .run();

        mainInterface().deleteAllProjectData(testSpecificProject);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
        new DicomMappingTest()
                .usingCstore()
                .uploadingData(CT_RT_TEST_DATA, CTRT_VALIDATION)
                .run();
    }

    public void testDicomMappingSessionProject() {
        new DicomMappingTest()
                .usingImportApi()
                .addProjectSpecificDicomMappings(
                        mappingPhotometricInterpretation(),
                        mappingRows(),
                        mappingPatientWeight(),
                        mappingPerformedProcedureStepId(),
                        mappingStudyDate()
                ).addDicomMappings(mappingWrongProjectSession())
                .uploadingData(CT_RT_TEST_DATA, CTRT_SKIPPED_VALIDATION)
                .uploadingData(MR_TEST_DATA, MR_VALIDATION)
                .run();

        mainInterface().deleteAllProjectData(testSpecificProject);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
        new DicomMappingTest()
                .usingCstore()
                .uploadingData(CT_RT_TEST_DATA, CTRT_SKIPPED_VALIDATION)
                .uploadingData(MR_TEST_DATA, MR_VALIDATION)
                .run();
    }

    public void testDicomMappingSessionSite() {
        new DicomMappingTest()
                .usingImportApi()
                .addSiteLevelDicomMappings(
                        mappingPhotometricInterpretation(),
                        mappingRows(),
                        mappingPatientWeight(),
                        mappingPerformedProcedureStepId(),
                        mappingStudyDate()
                ).uploadingData(CT_RT_TEST_DATA, CTRT_SKIPPED_VALIDATION)
                .uploadingData(MR_TEST_DATA, MR_VALIDATION)
                .run();

        mainInterface().deleteAllProjectData(testSpecificProject);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
        new DicomMappingTest()
                .usingCstore()
                .uploadingData(CT_RT_TEST_DATA, CTRT_SKIPPED_VALIDATION)
                .uploadingData(MR_TEST_DATA, MR_VALIDATION)
                .run();
    }

    public void testDicomMappingMixed() {
        final SessionValidation validation = new SessionValidation()
                .sessionLabel(CT_RT_LABEL)
                .withValidation(
                        new FieldValidation()
                                .expectFieldMissing(RESCALE_SLOPE_FIELD_NAME)
                                .expect(STUDY_DATE_FIELD_NAME, "1999-08-31")
                )
                .validatingScan(
                        "1",
                        new FieldValidation()
                                .expectFieldsMissing(STUDY_DATE_FIELD_NAME)
                                .expect(RESCALE_SLOPE_FIELD_NAME, "1.001")
                ).validatingScansEmpty("2", "3", "4");

        new DicomMappingTest()
                .usingImportApi()
                .addSiteLevelDicomMappings(mappingStudyDate().forDataType(DataType.CT_SESSION))
                .addProjectSpecificDicomMappings(mappingRescaleSlope())
                .uploadingData(CT_RT_TEST_DATA, validation)
                .run();

        mainInterface().deleteAllProjectData(testSpecificProject);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);

        new DicomMappingTest()
                .usingCstore()
                .uploadingData(CT_RT_TEST_DATA, validation)
                .run();
    }

    private static String field(String field) {
        final String fullField = field + RandomStringUtils.randomAlphabetic(10).toLowerCase();
        POSSIBLE_FIELDS.add(fullField);
        return fullField;
    }

    private void assertMappingFound(DicomMapping dicomMapping) {
        assertTrue(
                mainAdminInterface()
                        .readDicomMappings()
                        .stream()
                        .anyMatch(mapping -> dicomMapping.getFieldName().equals(mapping.getFieldName()))
        );
    }

    private void assertMappingNotFound(DicomMapping dicomMapping) {
        for (DicomMapping foundMapping : mainAdminInterface().readDicomMappings()) {
            TestNgUtils.assertNotEquals(dicomMapping.getFieldName(), foundMapping.getFieldName());
        }
    }

    private DicomMapping mappingNumberOfFrames() {
        return new DicomMapping()
                .forDataType(DataType.RT_SCAN)
                .fieldName(NUM_FRAMES_FIELD_NAME)
                .fieldType(VariableType.INTEGER)
                .dicomTag(Tag.NumberOfFrames);
    }

    private DicomMapping mappingStudyId() {
        return new DicomMapping()
                .forDataType(DataType.RT_SCAN)
                .fieldName(STUDY_ID_FIELD_NAME)
                .fieldType(VariableType.STRING)
                .dicomTag(Tag.StudyID);
    }

    private DicomMapping mappingSopInstanceUid() {
        return new DicomMapping()
                .forDataType(DataType.CT_SCAN)
                .fieldName(SOP_INSTANCE_UID_FIELD_NAME)
                .fieldType(VariableType.STRING)
                .dicomTag(Tag.SOPInstanceUID);
    }

    private DicomMapping mappingRescaleSlope() {
        return new DicomMapping()
                .forDataType(DataType.CT_SCAN)
                .fieldName(RESCALE_SLOPE_FIELD_NAME)
                .fieldType(VariableType.FLOAT)
                .dicomTag(Tag.RescaleSlope);
    }

    private DicomMapping mappingPatientAddress() {
        return new DicomMapping()
                .forDataType(DataType.RT_SCAN)
                .fieldName(PATIENT_ADDRESS_FIELD_NAME)
                .fieldType(VariableType.BOOLEAN)
                .dicomTag(Tag.PatientAddress);
    }

    private DicomMapping mappingInstanceCreationDate() {
        return new DicomMapping()
                .forDataType(DataType.RT_SCAN)
                .fieldName(INSTANCE_CREATION_DATE_FIELD_NAME)
                .fieldType(VariableType.DATE)
                .dicomTag(Tag.InstanceCreationDate);
    }

    private DicomMapping mappingWrongProject() {
        return new DicomMapping()
                .forDataType(DataType.RT_SCAN)
                .fieldName(ALWAYS_MISSING_FIELD)
                .fieldType(VariableType.STRING)
                .dicomTag(Tag.StudyInstanceUID)
                .forProject(otherProject);
    }

    private DicomMapping mappingPhotometricInterpretation() {
        return new DicomMapping()
                .forDataType(DataType.MR_SESSION)
                .fieldName(PHOTOMETRIC_INTERPRETATION_FIELD_NAME)
                .fieldType(VariableType.STRING)
                .dicomTag(Tag.PhotometricInterpretation);
    }

    private DicomMapping mappingRows() {
        return new DicomMapping()
                .forDataType(DataType.MR_SESSION)
                .fieldName(ROWS_FIELD_NAME)
                .fieldType(VariableType.INTEGER)
                .dicomTag(Tag.Rows);
    }

    private DicomMapping mappingPatientWeight() {
        return new DicomMapping()
                .forDataType(DataType.MR_SESSION)
                .fieldName(PATIENT_WEIGHT_FIELD_NAME)
                .fieldType(VariableType.FLOAT)
                .dicomTag(Tag.PatientWeight);
    }

    private DicomMapping mappingPerformedProcedureStepId() {
        return new DicomMapping()
                .forDataType(DataType.MR_SESSION)
                .fieldName(PERFORMED_PROCEDURE_STEP_ID_FIELD_NAME)
                .fieldType(VariableType.BOOLEAN)
                .dicomTag(Tag.PerformedProcedureStepID);
    }

    private DicomMapping mappingStudyDate() {
        return new DicomMapping()
                .forDataType(DataType.MR_SESSION)
                .fieldName(STUDY_DATE_FIELD_NAME)
                .fieldType(VariableType.DATE)
                .dicomTag(Tag.StudyDate);
    }

    private DicomMapping mappingWrongProjectSession() {
        return new DicomMapping()
                .forDataType(DataType.MR_SESSION)
                .fieldName(ALWAYS_MISSING_FIELD)
                .fieldType(VariableType.STRING)
                .dicomTag(Tag.StudyInstanceUID)
                .forProject(otherProject);
    }

    private class DicomMappingTest {
        private final List<DicomMapping> dicomMappings = new ArrayList<>();
        private final Map<File, SessionValidation> dataUploadAndValidation = new HashMap<>();
        private UploadStep uploadStep;

        DicomMappingTest addDicomMappings(DicomMapping... dicomMappings) {
            this.dicomMappings.addAll(Arrays.asList(dicomMappings));
            return this;
        }

        DicomMappingTest addProjectSpecificDicomMappings(DicomMapping... dicomMappings) {
            for (DicomMapping dicomMapping : dicomMappings) {
                this.dicomMappings.add(dicomMapping.forProject(testSpecificProject));
            }
            return this;
        }

        DicomMappingTest addSiteLevelDicomMappings(DicomMapping... dicomMappings) {
            for (DicomMapping dicomMapping : dicomMappings) {
                this.dicomMappings.add(dicomMapping.forSite());
            }
            return this;
        }

        DicomMappingTest usingImportApi() {
            uploadStep = new ImportApiStep();
            return this;
        }

        DicomMappingTest usingCstore() {
            uploadStep = new CstoreStep();
            return this;
        }

        DicomMappingTest uploadingData(TestData uploadedStudy, SessionValidation sessionValidation) {
            checkUploadStep();
            dataUploadAndValidation.put(uploadStep.mapTestDataToFile(uploadedStudy), sessionValidation);
            return this;
        }

        DicomMappingTest uploadingData(LocallyCacheableDicomTransformation uploadedStudy, SessionValidation sessionValidation) {
            checkUploadStep();
            dataUploadAndValidation.put(uploadStep.mapDicomTransformationToFile(uploadedStudy), sessionValidation);
            return this;
        }

        void run() {
            mainInterface().createProject(testSpecificProject);
            for (DicomMapping dicomMapping : dicomMappings) {
                mainAdminInterface().createOrUpdateDicomMapping(dicomMapping);
                tempMappings.add(dicomMapping);
            }
            for (Map.Entry<File, SessionValidation> dataUpload : dataUploadAndValidation.entrySet()) {
                uploadStep.performUpload(testSpecificProject, dataUpload.getKey());
            }
            for (Map.Entry<File, SessionValidation> dataUpload : dataUploadAndValidation.entrySet()) {
                uploadStep.archiveData(testSpecificProject, dataUpload.getKey());
            }
            final Project readProject = mainInterface().readProject(testSpecificProject.getId());
            for (SessionValidation sessionValidation : dataUploadAndValidation.values()) {
                final ImagingSession session = readProject.findSession(sessionValidation.sessionLabel);
                final FieldValidation sessionFields = sessionValidation.sessionFields;
                if (sessionFields != null) {
                    sessionValidation.sessionFields.validateAgainst(session.getFields());
                }

                for (Map.Entry<String, FieldValidation> scanValidation : sessionValidation.scanFields.entrySet()) {
                    final Scan scan = session.findScan(scanValidation.getKey());
                    mainInterface().readAdditionalScanMetadata(testSpecificProject, session.getSubject(), session, scan);
                    scanValidation.getValue().validateAgainst(scan.getAddParams());
                }
            }
        }

        private void checkUploadStep() {
            if (uploadStep == null) {
                throw new IllegalArgumentException("uploadStep must be specified before data");
            }
        }
    }

    private abstract class UploadStep {
        abstract void performUpload(Project project, File testData);

        abstract void archiveData(Project project, File testData);

        abstract File mapTestDataToFile(TestData testData);

        abstract File mapDicomTransformationToFile(LocallyCacheableDicomTransformation dicomTransformation);
    }

    private class ImportApiStep extends UploadStep {
        @Override
        void performUpload(Project project, File testData) {
            mainInterface().uploadToSessionZipImporter(testData, project);
        }

        @Override
        void archiveData(Project project, File testData) {
            // already done by upload step
        }

        @Override
        File mapTestDataToFile(TestData testData) {
            return testData.toFile();
        }

        @Override
        File mapDicomTransformationToFile(LocallyCacheableDicomTransformation dicomTransformation) {
            return dicomTransformation.build().locateOverallZip().toFile();
        }
    }

    private class CstoreStep extends UploadStep {
        @Override
        void performUpload(Project project, File testData) {
            new XnatCStore().data(testData).sendDICOMToProject(project);
        }

        @Override
        void archiveData(Project project, File testData) {
            restDriver.waitForPrearchiveEmpty(mainUser, project, 120);
        }

        @Override
        File mapTestDataToFile(TestData testData) {
            return testData.toDirectory();
        }

        @Override
        File mapDicomTransformationToFile(LocallyCacheableDicomTransformation dicomTransformation) {
            return dicomTransformation.locateBaseDirForOnlyTransformation().toFile();
        }
    }

    private static class SessionValidation {
        private FieldValidation sessionFields;
        private final Map<String, FieldValidation> scanFields = new HashMap<>();
        private String sessionLabel;

        SessionValidation sessionLabel(String label) {
            sessionLabel = label;
            return this;
        }

        SessionValidation validatingScan(String scanId, FieldValidation validation) {
            scanFields.put(scanId, validation);
            return this;
        }

        SessionValidation validatingScansEmpty(String... scanIds) {
            for (String scanId : scanIds) {
                scanFields.put(scanId, FieldValidation.EMPTY);
            }
            return this;
        }

        SessionValidation withValidation(FieldValidation validation) {
            sessionFields = validation;
            return this;
        }
    }

    private static class FieldValidation {
        private final Map<String, String> validations = new HashMap<>();
        private final List<String> missingFields = new ArrayList<>(Collections.singletonList(ALWAYS_MISSING_FIELD));
        static final FieldValidation EMPTY = new FieldValidation().expectFieldsMissing(POSSIBLE_FIELDS);

        FieldValidation expect(String fieldName, String value) {
            validations.put(fieldName, value);
            return this;
        }

        FieldValidation expectFieldMissing(String fieldName) {
            missingFields.add(fieldName);
            return this;
        }

        FieldValidation expectFieldsMissing(String... fields) {
            for (String field : fields) {
                expectFieldMissing(field);
            }
            return this;
        }

        FieldValidation expectFieldsMissing(List<String> fields) {
            return expectFieldsMissing(fields.toArray(new String[]{}));
        }

        void validateAgainst(Map<String, ?> comparedMap) {
            for (Map.Entry<String, String> expected : validations.entrySet()) {
                assertEquals(
                        expected.getValue(),
                        comparedMap.get(expected.getKey())
                );
            }
            for (String missingField : missingFields) {
                assertFalse(comparedMap.containsKey(missingField));
            }
        }
    }

}
