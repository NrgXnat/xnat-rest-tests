package org.nrg.testing.xnat.tests;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DatasetWithFMI;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.components.ComponentizedTest;
import org.nrg.testing.xnat.components.ProjectAnon;
import org.nrg.testing.xnat.components.SiteAnon;
import org.nrg.testing.xnat.components.TestComponent;
import org.nrg.xnat.enums.MergeBehavior;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.importers.SessionImporterRequest;
import org.nrg.xnat.pogo.ArchiveParams;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.versions.Xnat_1_8_6_1;
import org.nrg.xnat.versions.Xnat_1_8_7;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = IMPORTER)
@TestRequires(data = TestData.SAMPLE_1)
@AddedIn(Xnat_1_8_6_1.class)
public class TestDicomFileNamer extends BaseFileNamerTest {

    private static final String FILE_NAMER_REPEAT_UID = "${StudyInstanceUID}-${SeriesNumber}-${InstanceNumber}-${HashSOPClassUIDWithSOPInstanceUID}-${StudyInstanceUID}";
    private static final String FILE_NAMER_CAUSE_DATALOSS = "${StudyInstanceUID}-${SeriesNumber}"; // way too simple, causing all files in a series to conflict. This behavior is much more "expected" rather than "desired"
    private static final String FILE_NAMER_SOP_INSTANCE_UID_AND_HASH = "${SOPInstanceUID}-${HashSOPClassUIDWithSOPInstanceUID}";
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
    private final TestComponent VALIDATE_SAMPLE1_IN_PREARC = new ValidateNamesInPrearc(SAMPLE1_NAME_SPEC);
    private final TestComponent VALIDATE_SAMPLE1_IN_ARCHIVE = new ValidateNamesInArchive(SAMPLE1_NAME_SPEC);
    private final TestComponent VALIDATE_SAMPLE1_ORIG_NAMES_PREARC = new ValidateNamesInPrearc(SAMPLE1_ORIG_NAMES);
    private final TestComponent VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE = new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES);
    private final TestComponent USE_FILE_NAME_AS_UNIQUENESS_SOURCE = new FileNameAsUniqueness();
    private final TestComponent CATALOG_REFRESH = new RefreshCatalog();
    private final TestComponent ENABLE_BACKUPS = new UseBackupOptions();

    @Test
    public void testCstoreFileNaming() {
        run(new FileNamerTest(
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ));
    }

    @Test
    public void testCstoreFileNamingMissingInstanceNumbers() {
        final String identifier = "no-instance-num";
        final LocallyCacheableDicomTransformation dataWithoutInstanceNum = new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .simpleTransform(TransformFunction.simple(dicom -> dicom.getDataset().remove(Tag.InstanceNumber)));
        run(new FileNamerTest(
                new CstoreStep(dataWithoutInstanceNum),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_NO_INSTANCE_NUMBERS),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_NO_INSTANCE_NUMBERS)
        ));
    }

    @Test
    public void testSessionImporterFileNamer() {
        run(new FileNamerTest(
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE
        ));
    }

    @Test
    public void testDicomZipImportWithoutRename() {
        run(new FileNamerTest(
                DICOM_ZIP_SAMPLE1_WITHOUT_RENAME,
                REBUILD,
                VALIDATE_SAMPLE1_ORIG_NAMES_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE
        ));
    }

    @Test
    public void testDicomZipImportWithRename() {
        run(new FileNamerTest(
                DICOM_ZIP_SAMPLE1_WITH_RENAME,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ));
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
        run(new FileNamerTest(
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
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testDicomUidOverwrite() {
        run(new FileNamerTest(
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
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testSessionImporterUidOverwrite() {
        run(new FileNamerTest(
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                new SessionImporterStep(TestData.SAMPLE_1, MergeBehavior.DELETE),
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE
        ));
    }

    @Test
    public void testDicomFilenameWithAnon() {
        final String deleteInstanceNum = "-(0020,0013)";
        run(new FileNamerTest(
                new SiteAnon(deleteInstanceNum),
                new ProjectAnon(deleteInstanceNum),
                CSTORE_SAMPLE1,
                REBUILD,
                VALIDATE_SAMPLE1_IN_PREARC,
                ARCHIVE,
                VALIDATE_SAMPLE1_IN_ARCHIVE
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testDicomFilenamerUpdate() {
        run(new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_REPEAT_UID),
                CSTORE_SAMPLE1,
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_REPEATED_UIDS),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_REPEATED_UIDS)
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testDicomFilenamerInduceDataloss() {
        run(new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_CAUSE_DATALOSS),
                CSTORE_SAMPLE1,
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_DATALOSS),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_DATALOSS)
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testDicomFilenamerInduceDatalossDicomZip() {
        run(new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_CAUSE_DATALOSS),
                DICOM_ZIP_SAMPLE1_WITH_RENAME,
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_DATALOSS),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_DATALOSS)
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testDicomFilenamerInducePotentialDatalossSessionImporter() {
        run(new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_CAUSE_DATALOSS),
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE // files aren't renamed, so we should actually be safe in this scenario :)
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testUidDuplicationSameResources() {
        run(new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_SOP_INSTANCE_UID_AND_HASH),
                new CstoreStep(DUPLICATE_UID_SIMILAR_SOP_CLASS),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ENHMR_COPIED),
                new ExpectFailureOnArchive(),
                new ArchiveOverrideExceptions(),
                new ValidateNamesInArchive(SAMPLE1_ENHMR_COPIED)
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testUidDuplicationSameResourcesLegacyFilenameSetting() {
        run(new FileNamerTest(
                USE_FILE_NAME_AS_UNIQUENESS_SOURCE,
                new UpdateFileNamer(FILE_NAMER_SOP_INSTANCE_UID_AND_HASH),
                new CstoreStep(DUPLICATE_UID_SIMILAR_SOP_CLASS),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ENHMR_COPIED),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_ENHMR_COPIED)
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // See XNAT-7279
    public void testUidDuplicationDifferentResources() {
        run(new FileNamerTest(
                new UpdateFileNamer(FILE_NAMER_SOP_INSTANCE_UID_AND_HASH),
                new CstoreStep(produceMinimalCopyWithSopClass("duplicate-uid-disparate-sop-class", UID.RTStructureSetStorage)),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_RTSTRUCT_COPIED),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_RTSTRUCT_COPIED)
        ));
    }

    @Test
    public void testFilenameDuplicationDifferentSopInstanceUidDicomZip() {
        run(new FileNamerTest(
                new DicomZipStep(FILE_CLASH.locateOverallZip().toFile(), false),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_FILE_NAME_CLASH_DICOM_ZIP),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_FILE_NAME_CLASH_DICOM_ZIP)
        ));
    }

    @Test
    public void testFilenameDuplicationDifferentSopInstanceUidSessionImporter() {
        run(new FileNamerTest(
                new SessionImporterStep(FILE_CLASH.locateOverallZip().toFile()),
                new ValidateNamesInArchive(SAMPLE1_FILE_NAME_CLASH_SI)
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testFilenameDuplicationDifferentSopInstanceUidDicomZipMerge() {
        run(new FileNamerTest(
                new DicomZipStep(FILE_CLASH_DISTINCT_ZIPS.locateZipForIndividualTransformation(DUPLICATE_NAME_FIRST_FILE).toFile(), false),
                REBUILD,
                ARCHIVE,
                new DicomZipStep(FILE_CLASH_DISTINCT_ZIPS.locateZipForIndividualTransformation(DUPLICATE_NAME_SECOND_FILE).toFile(), false),
                REBUILD,
                new ExpectFailureOnArchiveEvenOverridingExceptions()
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class) // exact scenario from XNAT-7273
    public void testOverwriteProjectMerge() {
        run(new FileNamerTest(
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE,
                new SessionImporterStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), MergeBehavior.DELETE),
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_REPLACED),
                CATALOG_REFRESH,
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_REPLACED)
        ));
    }

    @Test
    public void testMergeSameUidDifferentNameLegacyFilenameSetting() {
        run(new FileNamerTest(
                USE_FILE_NAME_AS_UNIQUENESS_SOURCE,
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE,
                new SessionImporterStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), MergeBehavior.DELETE),
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED),
                CATALOG_REFRESH,
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED)
        ));
    }

    @Test
    public void testMergeSameUidDifferentNameLegacyFilenameSettingBackup() {
        run(new FileNamerTest(
                ENABLE_BACKUPS,
                USE_FILE_NAME_AS_UNIQUENESS_SOURCE,
                UPLOAD_SAMPLE1_SI,
                VALIDATE_SAMPLE1_ORIG_NAMES_ARCHIVE,
                new SessionImporterStep(ONE_FILE_PER_SERIES_SAMPLE1.locateOverallZip().toFile(), MergeBehavior.DELETE),
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED),
                CATALOG_REFRESH,
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES_INSTANCE_1_ADDED)
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomSiteAnonUidRemap() {
        run(new FileNamerTest(
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
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomProjectAnonUidRemap() {
        run(new FileNamerTest(
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
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomSiteAnonUidRemapBackup() {
        run(new FileNamerTest(
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
        ));
    }

    @Test
    @AddedIn(Xnat_1_8_7.class)
    public void testMergeSameOriginalDicomProjectAnonUidRemapBackup() {
        run(new FileNamerTest(
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
        ));
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
        run(new FileNamerTest(
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
        ));
    }

    private class FileNamerTest extends ComponentizedTest {
        FileNamerTest(TestComponent... steps) {
            super(steps);
        }

        @Override
        public Project createTestProject(BaseXnatRestTest xnatRestTest) {
            final Project project = registerTempProject().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE_OVERWRITE);
            mainInterface().createProject(project);
            return project;
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

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            final SessionImporterRequest request = new SessionImporterRequest()
                    .destArchive(project)
                    .file(data);
            if (overwrite != null) {
                request.overwrite(overwrite);
            }
            mainInterface().callImporter(request);
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
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            final Scan scan = mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0).findScan(scanId);
            final Resource scanResource = scan.getScanResources().stream().filter(resource -> resource.getFolder().equals(resourceLabel)).findFirst().orElseThrow(RuntimeException::new);
            assertEquals(expectedSize, scanResource.getFileSize());
        }
    }

    private class ExpectFailureOnArchive extends ArchiveSession {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            try {
                super.perform(xnatRestTest, project);
                throw new RuntimeException("Archival attempt should have failed!");
            } catch (AssertionError ignored) {
                // expected
            }
        }
    }

    private class ArchiveOverrideExceptions implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainInterface().archiveSession(expectSinglePrearchiveResultForProject(project), new ArchiveParams().delete());
        }
    }

    private class ExpectFailureOnArchiveEvenOverridingExceptions extends ArchiveOverrideExceptions {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            try {
                super.perform(xnatRestTest, project);
                throw new RuntimeException("Archival attempt should have failed!");
            } catch (AssertionError ignored) {
                // expected
            }
        }
    }

    private class FileNameAsUniqueness implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainAdminInterface().setUseSopInstanceUidToUniquelyIdentifyDicom(false);
        }
    }

    private class RefreshCatalog implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainInterface().refreshCatalog(mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0));
        }
    }

    private class UseBackupOptions implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainAdminInterface().setSiteBackupSettings(true, true);
        }
    }

    private class RelabelSession implements TestComponent {
        private final String label;

        RelabelSession(String label) {
            this.label = label;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
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
        return new LocallyCacheableDicomTransformation(identifier)
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
                ).build();
    }

    private static LocallyCacheableDicomTransformation generateFileClashDistinctZips() {
        final String identifier = "duplicated-file-names-distinct-zips";
        return new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .transformations(
                        new DicomTransformation(DUPLICATE_NAME_FIRST_FILE)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(1))
                                .produceZip(),
                        new DicomTransformation(DUPLICATE_NAME_SECOND_FILE)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(2))
                                .produceZip()
                ).build();
    }

    private static LocallyCacheableDicomTransformation oneFilePerSeriesSample1() {
        final String identifier = "sample1-1-file-per-series";
        return new LocallyCacheableDicomTransformation(identifier)
                .data(TestData.SAMPLE_1)
                .createZip()
                .transformations(
                        new DicomTransformation(identifier)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(1))
                ).build();
    }

}
