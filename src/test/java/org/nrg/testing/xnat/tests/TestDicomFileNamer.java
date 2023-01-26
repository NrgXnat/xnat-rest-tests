package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DatasetWithFMI;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.rest.XnatRestDriver;
import org.nrg.xnat.enums.MergeBehavior;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.importer.importers.SessionImporterRequest;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.ArchiveParams;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.prearchive.PrearchiveQuery;
import org.nrg.xnat.prearchive.PrearchiveQueryScope;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.rest.SerializationUtils;
import org.nrg.xnat.versions.Xnat_1_8_6_1;
import org.nrg.xnat.versions.Xnat_1_8_7;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = IMPORTER)
@TestRequires(data = TestData.SAMPLE_1)
@AddedIn(Xnat_1_8_6_1.class)
public class TestDicomFileNamer extends BaseXnatRestTest {

    private static final Logger LOGGER = Logger.getLogger(TestDicomFileNamer.class);
    private static final String FILE_NAMER_EXPECTED_VALUE = "${StudyInstanceUID}-${SeriesNumber}-${InstanceNumber}-${HashSOPClassUIDWithSOPInstanceUID}";
    private static final String FILE_NAMER_REPEAT_UID = "${StudyInstanceUID}-${SeriesNumber}-${InstanceNumber}-${HashSOPClassUIDWithSOPInstanceUID}-${StudyInstanceUID}";
    private static final String FILE_NAMER_CAUSE_DATALOSS = "${StudyInstanceUID}-${SeriesNumber}"; // way too simple, causing all files in a series to conflict. This behavior is much more "expected" rather than "desired"
    private static final String FILE_NAMER_SOP_INSTANCE_UID_AND_HASH = "${SOPInstanceUID}-${HashSOPClassUIDWithSOPInstanceUID}";
    private static final String FILE_NAMES_SUBDIR = "filenames";
    private static final String SAMPLE1 = "sample1.json";
    private static final String SAMPLE1_NO_INSTANCE_NUMBERS = "sample1_no_instance_numbers.json";
    private static final String SAMPLE1_ORIG_NAMES = "sample1_orig_names.json";
    private static final String SAMPLE1_ORIG_NAMES_INSTANCE_1_REPLACED = "sample1_orig_names_instance_1_replaced.json";
    private static final String SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED = "sample1_orig_names_instance_1_added.json";
    private static final String SAMPLE1_DUPLICATED = "sample1_duplicated.json";
    private static final String SAMPLE1_REPEATED_UIDS = "sample1_repeated_uid.json";
    private static final String SAMPLE1_DATALOSS = "sample1_dataloss.json";
    private static final String SAMPLE1_ENHMR_COPIED = "sample1_enhmr.json";
    private static final String SAMPLE1_RTSTRUCT_COPIED = "sample1_rt.json";
    private static final String SAMPLE1_FILE_NAME_CLASH_DICOM_ZIP = "sample1_clash_dicomzip.json";
    private static final String SAMPLE1_FILE_NAME_CLASH_SI = "sample1_clash_si.json";
    private static final String SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM = "sample1_one_file_per_series_custom.json";
    private static final LocallyCacheableDicomTransformation FILE_CLASH = generateFileClash();
    private static final LocallyCacheableDicomTransformation FILE_CLASH_DISTINCT_ZIPS = generateFileClashDistinctZips();
    private static final LocallyCacheableDicomTransformation ONE_FILE_PER_SERIES_SAMPLE1 = oneFilePerSeriesSample1();
    private static final LocallyCacheableDicomTransformation DUPLICATE_UID_SIMILAR_SOP_CLASS = produceMinimalCopyWithSopClass("duplicate-uid-similar-sop-class", UID.EnhancedMRImageStorage);
    private static final String DUPLICATE_NAME_FIRST_FILE = "duplicate-name-first-file";
    private static final String DUPLICATE_NAME_SECOND_FILE = "duplicate-name-second-file";
    private static final String SCRIPT_HASH_UIDS = "version \"6.1\"\n(0008,0018) := hashUID[(0008,0018)]";
    private final TestComponent CSTORE_SAMPLE1 = new CstoreStep(TestData.SAMPLE_1);
    private final TestComponent DICOM_ZIP_SAMPLE1_WITH_RENAME = new DicomZipStep(TestData.SAMPLE_1, true);
    private final TestComponent DICOM_ZIP_SAMPLE1_WITHOUT_RENAME = new DicomZipStep(TestData.SAMPLE_1, false);
    private final TestComponent UPLOAD_SAMPLE1_SI = new SessionImporterStep(TestData.SAMPLE_1);
    private final TestComponent REBUILD = new RebuildOnlySessionInPrearc();
    private final TestComponent ARCHIVE = new ArchiveSession();
    private final TestComponent VALIDATE_SAMPLE1_IN_PREARC = new ValidateNamesInPrearc(SAMPLE1);
    private final TestComponent VALIDATE_SAMPLE1_IN_ARCHIVE = new ValidateNamesInArchive(SAMPLE1);
    private final TestComponent VALIDATE_SAMPLE1_ORIG_NAMES_PREARC = new ValidateNamesInPrearc(SAMPLE1_ORIG_NAMES);
    private final TestComponent VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE = new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES);
    private final TestComponent USE_FILE_NAME_AS_UNIQUENESS_SOURCE = new FileNameAsUniqueness();
    private final TestComponent CATALOG_REFRESH = new RefreshCatalog();
    private final TestComponent ENABLE_BACKUPS = new UseBackupOptions();

    /*
        This checks the file namer set currently. If the namer doesn't match the default, we reset it. We could just always make this POST and skip the GET,
        but if the value is still the default, then avoiding touching it at all more closely resembles production use where admins don't normally modify this.
     */
    @BeforeMethod
    private void initFileNamer() {
        final String currentFileNamer = mainAdminInterface().readDicomFileNamer();
        if (!FILE_NAMER_EXPECTED_VALUE.equals(currentFileNamer)) {
            mainAdminInterface().updateDicomFileNamer(FILE_NAMER_EXPECTED_VALUE);
        }
    }

    @BeforeMethod
    private void disableCustomRouting() {
        try {
            mainAdminInterface().disableSiteAnonScript();
            mainAdminInterface().disableProjectDicomRoutingConfig();
            mainAdminInterface().disableSubjectDicomRoutingConfig();
            mainAdminInterface().disableSessionDicomRoutingConfig();
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
    }

    @BeforeMethod
    private void disableFileBackups() {
        mainAdminInterface().setSiteBackupSettings(false, false);
    }

    @AfterMethod
    private void resetSopInstanceForDicomUniqueness() {
        mainAdminInterface().setUseSopInstanceUidToUniquelyIdentifyDicom(true);
    }

    @Test
    public void testCstoreFileNaming() {
        new FileNamerTest(
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ).run();
    }

    @Test
    public void testCstoreFileNamingMissingInstanceNumbers() {
        final String identifier = "no-instance-num";
        final LocallyCacheableDicomTransformation dataWithoutInstanceNum = new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .simpleTransform(TransformFunction.simple(dicom -> dicom.getDataset().remove(Tag.InstanceNumber)));
        new FileNamerTest(
                new CstoreStep(dataWithoutInstanceNum),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_NO_INSTANCE_NUMBERS),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_NO_INSTANCE_NUMBERS)
        ).run();
    }

    @Test
    public void testSessionImporterFileNamer() {
        new FileNamerTest(
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE
        ).run();
    }

    @Test
    public void testDicomZipImportWithoutRename() {
        new FileNamerTest(
                DICOM_ZIP_SAMPLE1_WITHOUT_RENAME,
                REBUILD,
                VALIDATE_SAMPLE1_ORIG_NAMES_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE
        ).run();
    }

    @Test
    public void testDicomZipImportWithRename() {
        new FileNamerTest(
                DICOM_ZIP_SAMPLE1_WITH_RENAME,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ).run();
    }

    /**
     * The behavior described in this test is not "good". This is showing a merge happening
     * with two different file naming schemes going on. Since the file names are used as
     * the identifier for uniqueness of the instances, this results in duplication of those instances.
     * However, in the third send of the same entire study, the file names do match, so the data is
     * at least not triplicated. If we come up with a better scheme later to prevent duplicate DICOM instances,
     * this test should be retired gladly. - Charlie, 2022-11-18
     *
     * Test updated per fixes in XNAT-7273 and XNAT-7274.
     */
    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testDicomFilenameMismatchInstanceDuplication() {
        mainAdminInterface().setUseSopInstanceUidToUniquelyIdentifyDicom(false);
        new FileNamerTest(
                DICOM_ZIP_SAMPLE1_WITHOUT_RENAME,
                REBUILD,
                ARCHIVE,
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_DUPLICATED),
                CSTORE_SAMPLE1,
                REBUILD,
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_DUPLICATED)
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testDicomUidOverwrite() {
        new FileNamerTest(
                DICOM_ZIP_SAMPLE1_WITHOUT_RENAME,
                REBUILD,
                ARCHIVE,
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                CSTORE_SAMPLE1,
                REBUILD,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testSessionImporterUidOverwrite() {
        new FileNamerTest(
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                new SessionImporterStep(TestData.SAMPLE_1, MergeBehavior.DELETE),
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE
        ).run();
    }

    @Test
    public void testDicomFilenameWithAnon() {
        final String deleteInstanceNum = "-(0020,0013)";
        new FileNamerTest(
                new SiteAnon(deleteInstanceNum),
                new ProjectAnon(deleteInstanceNum),
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testDicomFilenamerUpdate() {
        new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_REPEAT_UID),
                CSTORE_SAMPLE1,
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_REPEATED_UIDS),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_REPEATED_UIDS)
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testDicomFilenamerInduceDataloss() {
        new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_CAUSE_DATALOSS),
                CSTORE_SAMPLE1,
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_DATALOSS),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_DATALOSS)
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testDicomFilenamerInduceDatalossDicomZip() {
        new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_CAUSE_DATALOSS),
                DICOM_ZIP_SAMPLE1_WITH_RENAME,
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_DATALOSS),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_DATALOSS)
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testDicomFilenamerInducePotentialDatalossSessionImporter() {
        new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_CAUSE_DATALOSS),
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE // files aren't renamed, so we should actually be safe in this scenario :)
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testUidDuplicationSameResources() {
        new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_SOP_INSTANCE_UID_AND_HASH),
                new CstoreStep(DUPLICATE_UID_SIMILAR_SOP_CLASS),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ENHMR_COPIED),
                new ExpectFailureOnArchive(),
                new ArchiveOverrideExceptions(),
                new ValidateNamesInArchive(SAMPLE1_ENHMR_COPIED)
        ).run();
    }

    @Test
    public void testUidDuplicationSameResourcesLegacyFilenameSetting() {
        new FileNamerTest(
                USE_FILE_NAME_AS_UNIQUENESS_SOURCE,
                new UpdateFileNamer(FILE_NAMER_SOP_INSTANCE_UID_AND_HASH),
                new CstoreStep(DUPLICATE_UID_SIMILAR_SOP_CLASS),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ENHMR_COPIED),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_ENHMR_COPIED)
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testUidDuplicationDifferentResources() {
        new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_SOP_INSTANCE_UID_AND_HASH),
                new CstoreStep(produceMinimalCopyWithSopClass("duplicate-uid-disparate-sop-class", UID.RTStructureSetStorage)),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_RTSTRUCT_COPIED),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_RTSTRUCT_COPIED)
        ).run();
    }

    @Test
    public void testFilenameDuplicationDifferentSopInstanceUidDicomZip() {
        new FileNamerTest(
                new DicomZipStep(FILE_CLASH.locateOverallZip().toFile(), false),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_FILE_NAME_CLASH_DICOM_ZIP),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_FILE_NAME_CLASH_DICOM_ZIP)
        ).run();
    }

    @Test
    public void testFilenameDuplicationDifferentSopInstanceUidSessionImporter() {
        new FileNamerTest(
                new SessionImporterStep(FILE_CLASH.locateOverallZip().toFile()),
                new ValidateNamesInArchive(SAMPLE1_FILE_NAME_CLASH_SI)
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testFilenameDuplicationDifferentSopInstanceUidDicomZipMerge() {
        new FileNamerTest(
                new DicomZipStep(FILE_CLASH_DISTINCT_ZIPS.locateZipForIndividualTransformation(DUPLICATE_NAME_FIRST_FILE).toFile(), false),
                REBUILD,
                ARCHIVE,
                new DicomZipStep(FILE_CLASH_DISTINCT_ZIPS.locateZipForIndividualTransformation(DUPLICATE_NAME_SECOND_FILE).toFile(), false),
                REBUILD,
                new ExpectFailureOnArchiveEvenOverridingExceptions()
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // exact scenario from XNAT-7273
    public void testOverwriteProjectMerge() {
        new FileNamerTest(
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE,
                new SessionImporterStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), MergeBehavior.DELETE),
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_REPLACED),
                CATALOG_REFRESH,
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_REPLACED)
        ).run();
    }

    @Test
    public void testMergeSameUidDifferentNameLegacyFilenameSetting() {
        new FileNamerTest(
                USE_FILE_NAME_AS_UNIQUENESS_SOURCE,
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE,
                new SessionImporterStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), MergeBehavior.DELETE),
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED),
                CATALOG_REFRESH,
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED)
        ).run();
    }

    @Test
    public void testMergeSameUidDifferentNameLegacyFilenameSettingBackup() {
        new FileNamerTest(
                ENABLE_BACKUPS,
                USE_FILE_NAME_AS_UNIQUENESS_SOURCE,
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE,
                new SessionImporterStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), MergeBehavior.DELETE),
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED),
                CATALOG_REFRESH,
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED)
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomSiteAnonUidRemap() {
        new FileNamerTest(
                new SiteAnon(SCRIPT_HASH_UIDS),
                new DicomZipStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), false),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM),
                new CstoreStep(TestData.SAMPLE_1),
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                CATALOG_REFRESH,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomProjectAnonUidRemap() {
        new FileNamerTest(
                new ProjectAnon(SCRIPT_HASH_UIDS),
                new DicomZipStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), false),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM),
                new CstoreStep(TestData.SAMPLE_1),
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                CATALOG_REFRESH,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomSiteAnonUidRemapBackup() {
        new FileNamerTest(
                ENABLE_BACKUPS,
                new SiteAnon(SCRIPT_HASH_UIDS),
                new DicomZipStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), false),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM),
                new CstoreStep(TestData.SAMPLE_1),
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                CATALOG_REFRESH,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomProjectAnonUidRemapBackup() {
        new FileNamerTest(
                ENABLE_BACKUPS,
                new ProjectAnon(SCRIPT_HASH_UIDS),
                new DicomZipStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), false),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_ONE_FILE_PER_SERIES_CUSTOM),
                new CstoreStep(TestData.SAMPLE_1),
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                CATALOG_REFRESH,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomPostArchiveUidRemap() {
        final String identifier = "sample1-1-no-pixel-data";
        final LocallyCacheableDicomTransformation sample1NoPixels = new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .simpleTransform(TransformFunction.simple(
                        (dicomInstance) -> dicomInstance.getDataset().remove(Tag.PixelData)
                ));
        new FileNamerTest(
                new ProjectAnon(SCRIPT_HASH_UIDS),
                new CstoreStep(sample1NoPixels),
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                new ValidateResourceSizeInArchive(10718740, "4", true),
                new ValidateResourceSizeInArchive(10612092, "5", true),
                new ValidateResourceSizeInArchive(10893264, "6", true),
                new RelabelSession("somethingelse"), // remap UIDs for the second time
                new RelabelSession("Sample_ID"), // remap UIDs for the third time, restore original label for merge
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                CATALOG_REFRESH,
                VALIDATE_SAMPLE1_IN_ARCHIVE,
                new ValidateResourceSizeInArchive(33789524, "4", true),
                new ValidateResourceSizeInArchive(33682876, "5", true),
                new ValidateResourceSizeInArchive(33964048, "6", true)
        ).run();
    }

    private SessionData expectSinglePrearchiveResultForProject(Project project) {
        return mainInterface().queryPrearchiveForSingularResult(new PrearchiveQuery().scope(PrearchiveQueryScope.forProject(project)));
    }

    private void validateFilesInScansMatchExpectedNames(String expectedFileList, List<Scan> actualScans) {
        final List<ScanFileNameRecord> scanFileNameRecords;
        try {
            scanFileNameRecords = SerializationUtils.deserializeList(
                    XnatRestDriver.XNAT_REST_MAPPER.readValue(
                            FileIOUtils.readFile(getDataFile(Paths.get(FILE_NAMES_SUBDIR, expectedFileList).toString())),
                            List.class
                    ),
                    ScanFileNameRecord.class
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals(
                scanFileNameRecords.stream().map(record -> record.scanId).collect(Collectors.toSet()),
                actualScans.stream().map(Scan::getId).collect(Collectors.toSet())
        ); // scan IDs should match exactly now

        for (ScanFileNameRecord fileNameRecord : scanFileNameRecords) {
            final Scan correspondingScan = actualScans.stream().filter(scan -> fileNameRecord.scanId.equals(scan.getId())).findFirst().orElseThrow(RuntimeException::new);
            assertEquals(
                    fileNameRecord.resources.size(),
                    correspondingScan.getScanResources().size()
            );
            for (Map.Entry<String, Set<String>> resourceEntry : fileNameRecord.resources.entrySet()) {
                final Resource actualResource = correspondingScan.getScanResources().stream().filter(resource -> resourceEntry.getKey().equals(resource.getFolder())).findFirst().orElseThrow(RuntimeException::new);
                assertEquals(
                        resourceEntry.getValue(),
                        actualResource.getResourceFiles().stream().map(ResourceFile::getName).collect(Collectors.toSet())
                );
            }
        }
    }

    private static class ScanFileNameRecord {
        private String scanId;
        private Map<String, Set<String>> resources;

        public String getScanId() {
            return scanId;
        }

        public void setScanId(String scanId) {
            this.scanId = scanId;
        }

        public Map<String, Set<String>> getResources() {
            return resources;
        }

        public void setResources(Map<String, Set<String>> resources) {
            this.resources = resources;
        }
    }

    private interface TestComponent {
        void perform(Project project);
    }

    private class FileNamerTest {
        private final List<TestComponent> testSteps;

        FileNamerTest(TestComponent... steps) {
            testSteps = Arrays.asList(steps);
        }

        void run() {
            final Project project = registerTempProject().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
            mainInterface().createProject(project);
            for (TestComponent component : testSteps) {
                component.perform(project);
            }
        }
    }

    private class CstoreStep implements TestComponent {
        private final File data;

        CstoreStep(TestData data) {
            this.data = data.toDirectory();
        }

        CstoreStep(LocallyCacheableDicomTransformation dicomTransformation) {
            dicomTransformation.build();
            data = dicomTransformation.locateBaseDirForTransformedData(dicomTransformation.getIdentifier()).toFile();
        }

        @Override
        public void perform(Project project) {
            new XnatCStore().data(data).sendDICOMToProject(project);
        }
    }

    private class SessionImporterStep implements TestComponent {
        private final File data;
        private final MergeBehavior overwrite;

        SessionImporterStep(File data, MergeBehavior overwrite) {
            this.data = data;
            this.overwrite = overwrite;
        }

        SessionImporterStep(TestData data, MergeBehavior overwrite) {
            this(data.toFile(), overwrite);
        }

        SessionImporterStep(File file) {
            this(file, null);
        }

        SessionImporterStep(TestData data) {
            this(data, null);
        }

        @Override
        public void perform(Project project) {
            final SessionImporterRequest request = new SessionImporterRequest()
                    .destArchive(project)
                    .file(data);
            if (overwrite != null) {
                request.overwrite(overwrite);
            }
            mainInterface().callImporter(request);
        }
    }

    private class DicomZipStep implements TestComponent {
        private final File data;
        private final boolean rename;

        DicomZipStep(TestData data, boolean rename) {
            this(data.toFile(), rename);
        }

        DicomZipStep(File zip, boolean rename) {
            data = zip;
            this.rename = rename;
        }

        @Override
        public void perform(Project project) {
            mainInterface().callImporter(
                    new DicomZipRequest()
                            .file(data)
                            .project(project)
                            .rename(rename)
            );
        }
    }

    private class RebuildOnlySessionInPrearc implements TestComponent {
        @Override
        public void perform(Project project) {
            final SessionData prearcSession = expectSinglePrearchiveResultForProject(project);
            mainInterface().rebuildSession(prearcSession, false);
        }
    }

    private abstract class ValidateNames implements TestComponent {
        private final String expectedFileList;

        ValidateNames(String expectedFileList) {
            this.expectedFileList = expectedFileList;
        }

        @Override
        public void perform(Project project) {
            validateFilesInScansMatchExpectedNames(
                    expectedFileList,
                    produceActualScans(project)
            );
        }

        abstract List<Scan> produceActualScans(Project project);
    }

    private class ValidateNamesInPrearc extends ValidateNames {
        ValidateNamesInPrearc(String expectedFileList) {
            super(expectedFileList);
        }

        @Override
        List<Scan> produceActualScans(Project project) {
            return mainInterface().readScansForPrearchiveSession(expectSinglePrearchiveResultForProject(project));
        }
    }

    private class ValidateNamesInArchive extends ValidateNames {
        ValidateNamesInArchive(String expectedFileList) {
            super(expectedFileList);
        }

        @Override
        List<Scan> produceActualScans(Project project) {
            return mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0).getScans();
        }
    }

    private class ValidateResourceSizeInArchive implements TestComponent {
        long expectedSize;
        String scanId;
        String resourceLabel;

        ValidateResourceSizeInArchive(long expectedSize, String scanId, boolean primary) {
            this.expectedSize = expectedSize;
            this.scanId = scanId;
            resourceLabel = primary ? "DICOM" : "secondary";
        }

        @Override
        public void perform(Project project) {
            final Scan scan = mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0).findScan(scanId);
            final Resource scanResource = scan.getScanResources().stream().filter(resource -> resource.getFolder().equals(resourceLabel)).findFirst().orElseThrow(RuntimeException::new);
            assertEquals(expectedSize, scanResource.getFileSize());
        }
    }

    private class ArchiveSession implements TestComponent {
        @Override
        public void perform(Project project) {
            mainInterface().archiveSession(expectSinglePrearchiveResultForProject(project));
        }
    }

    private class ExpectFailureOnArchive extends ArchiveSession {
        @Override
        public void perform(Project project) {
            try {
                super.perform(project);
                throw new RuntimeException("Archival attempt should have failed!");
            } catch (AssertionError ignored) {
                // expected
            }
        }
    }

    private class ArchiveOverrideExceptions implements TestComponent {
        @Override
        public void perform(Project project) {
            mainInterface().archiveSession(expectSinglePrearchiveResultForProject(project), new ArchiveParams().delete());
        }
    }

    private class ExpectFailureOnArchiveEvenOverridingExceptions extends ArchiveOverrideExceptions {
        @Override
        public void perform(Project project) {
            try {
                super.perform(project);
                throw new RuntimeException("Archival attempt should have failed!");
            } catch (AssertionError ignored) {
                // expected
            }
        }
    }

    private class FileNameAsUniqueness implements TestComponent {
        @Override
        public void perform(Project project) {
            mainAdminInterface().setUseSopInstanceUidToUniquelyIdentifyDicom(false);
        }
    }

    private class UpdateFileNamer implements TestComponent {
        private final String template;

        UpdateFileNamer(String template) {
            this.template = template;
        }

        @Override
        public void perform(Project project) {
            mainAdminInterface().updateDicomFileNamer(template);
        }
    }

    private class RefreshCatalog implements TestComponent {
        @Override
        public void perform(Project project) {
            mainInterface().refreshCatalog(mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0));
        }
    }

    private class ProjectAnon implements TestComponent {
        private final String contents;

        ProjectAnon(String contents) {
            this.contents = contents;
        }

        @Override
        public void perform(Project project) {
            mainInterface().setProjectAnonScript(project, new AnonScript().contents(contents));
            mainInterface().enableProjectAnonScript(project);
        }
    }

    private class SiteAnon implements TestComponent {
        private final String contents;

        SiteAnon(String contents) {
            this.contents = contents;
        }

        @Override
        public void perform(Project project) {
            mainAdminInterface().enableSiteAnonScript();
            mainAdminInterface().setSiteAnonScript(new AnonScript().contents(contents));
        }
    }

    private class UseBackupOptions implements TestComponent {
        @Override
        public void perform(Project project) {
            mainAdminInterface().setSiteBackupSettings(true, true);
        }
    }

    private class RelabelSession implements TestComponent {
        private final String label;

        RelabelSession(String label) {
            this.label = label;
        }

        @Override
        public void perform(Project project) {
            final ImagingSession session = mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0);
            mainInterface().relabelSubjectAssessor(session, label);
        }
    }

    private static LocallyCacheableDicomTransformation produceMinimalCopyWithSopClass(String identifier, String targetSopClassUid) {
        return new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .transformations(
                        new DicomTransformation(identifier)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(1))
                                .transformFunction(
                                        TransformFunction.generalTransform(
                                                dicomList -> dicomList.stream()
                                                        .map(original -> {
                                                            final Attributes copyWithDifferentSopClass = DicomUtils.clone(original).getDataset();
                                                            copyWithDifferentSopClass.setString(Tag.SOPClassUID, VR.UI, targetSopClassUid);
                                                            return Arrays.asList(
                                                                    original,
                                                                    new DatasetWithFMI(
                                                                            copyWithDifferentSopClass.createFileMetaInformation(UID.ExplicitVRLittleEndian),
                                                                            copyWithDifferentSopClass
                                                                    )
                                                            );
                                                        }).flatMap(Collection::stream)
                                                        .collect(Collectors.toList())
                                        )
                                )
                );
    }

    private static LocallyCacheableDicomTransformation generateFileClash() {
        final String identifier = "duplicated-file-names";
        final LocallyCacheableDicomTransformation twoFilesPerSeriesDupeNames = new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .createZip()
                .transformations(
                        new DicomTransformation(identifier)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(Arrays.asList(1, 2)))
                                .dicomFileWriter((datasetWithFMI, path, i) -> {
                                    final Path subdir = path.resolve(String.valueOf(i));
                                    FileIOUtils.mkdirs(subdir);
                                    final File outputFile = subdir.resolve("repeated_file_name.dcm").toFile();
                                    DicomUtils.writeDicomToFile(datasetWithFMI, outputFile);
                                    return outputFile;
                                })
                );
        twoFilesPerSeriesDupeNames.build();
        return twoFilesPerSeriesDupeNames;
    }

    private static LocallyCacheableDicomTransformation generateFileClashDistinctZips() {
        final String identifier = "duplicated-file-names-distinct-zips";
        final LocallyCacheableDicomTransformation twoFilesPerSeriesDupeNames = new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .transformations(
                        new DicomTransformation(DUPLICATE_NAME_FIRST_FILE)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(1))
                                .produceZip(),
                        new DicomTransformation(DUPLICATE_NAME_SECOND_FILE)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(2))
                                .produceZip()
                );
        twoFilesPerSeriesDupeNames.build();
        return twoFilesPerSeriesDupeNames;
    }

    private static LocallyCacheableDicomTransformation oneFilePerSeriesSample1() {
        final String identifier = "sample1-1-file-per-series";
        final LocallyCacheableDicomTransformation oneFilePerSeries = new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .createZip()
                .transformations(
                        new DicomTransformation(identifier)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(1))
                );
        oneFilePerSeries.build();
        return oneFilePerSeries;
    }

}
