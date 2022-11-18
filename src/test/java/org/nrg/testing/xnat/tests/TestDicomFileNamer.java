package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che3.data.Tag;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.rest.XnatRestDriver;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.importer.importers.SessionImporterRequest;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.prearchive.PrearchiveQuery;
import org.nrg.xnat.prearchive.PrearchiveQueryScope;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.rest.SerializationUtils;
import org.nrg.xnat.versions.Xnat_1_8_6_1;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = IMPORTER)
@TestRequires(data = TestData.SAMPLE_1)
@AddedIn(Xnat_1_8_6_1.class)
public class TestDicomFileNamer extends BaseXnatRestTest {

    private static final Logger LOGGER = Logger.getLogger(TestDicomFileNamer.class);
    private static final String FILE_NAMER_PREF_NAME = "dicomFileNameTemplate";
    private static final String FILE_NAMER_EXPECTED_VALUE = "${StudyInstanceUID}-${SeriesNumber}-${InstanceNumber}-${HashSOPClassUIDWithSOPInstanceUID}";
    private static final String FILE_NAMES_SUBDIR = "filenames";
    private static final String SAMPLE1 = "sample1.json";
    private static final String SAMPLE1_NO_INSTANCE_NUMBERS = "sample1_no_instance_numbers.json";
    private static final String SAMPLE1_ORIG_NAMES = "sample1_orig_names.json";
    private static final String SAMPLE1_DUPLICATED = "sample1_duplicated.json";
    private final TestComponent REBUILD = new RebuildOnlySessionInPrearc();
    private final TestComponent ARCHIVE = new ArchiveSession();

    /*
        This checks the file namer set currently. If the namer doesn't match the default, we reset it. We could just always make this POST and skip the GET,
        but if the value is still the default, then avoiding touching it at all more closely resembles production use where admins don't normally modify this.
     */
    @BeforeMethod
    private void initFileNamer() {
        final String currentFileNamer = mainAdminInterface().readSiteConfigPreference(FILE_NAMER_PREF_NAME);
        if (!FILE_NAMER_EXPECTED_VALUE.equals(currentFileNamer)) {
            mainAdminInterface().postSiteConfigProperty(FILE_NAMER_PREF_NAME, FILE_NAMER_EXPECTED_VALUE);
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

    @Test
    public void testCstoreFileNaming() {
        new FileNamerTest(
                new CstoreStep(TestData.SAMPLE_1),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1)
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
                new SessionImporterStep(TestData.SAMPLE_1),
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES)
        ).run();
    }

    @Test
    public void testDicomZipImportWithoutRename() {
        new FileNamerTest(
                new DicomZipStep(TestData.SAMPLE_1, false),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1_ORIG_NAMES),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_ORIG_NAMES)
        ).run();
    }

    @Test
    public void testDicomZipImportWithRename() {
        new FileNamerTest(
                new DicomZipStep(TestData.SAMPLE_1, true),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1)
        ).run();
    }

    /**
     * The behavior described in this test is not "good". This is showing a merge happening
     * with two different file naming schemes going on. Since the file names are used as
     * the identifier for uniqueness of the instances, this results in duplication of those instances.
     * However, in the third send of the same entire study, the file names do match, so the data is
     * at least not triplicated. However, the third send also seems to unexpectedly overwrite the whole
     * series, which is unexpected. If we come up with a better scheme later to prevent duplicate DICOM instances,
     * this test should be retired gladly. - Charlie, 2022-11-18
     */
    @Test
    public void testDicomFilenameMismatchInstanceDuplication() {
        new FileNamerTest(
                new DicomZipStep(TestData.SAMPLE_1, false),
                REBUILD,
                ARCHIVE,
                new CstoreStep(TestData.SAMPLE_1),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1_DUPLICATED),
                new CstoreStep(TestData.SAMPLE_1),
                REBUILD,
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1)
        ).run();
    }

    @Test
    public void testDicomFilenameWithAnon() {
        final AnonScript deleteInstanceNum = new AnonScript().contents("-(0020,0013)");
        new FileNamerTest(
                project -> {
                    mainAdminInterface().setSiteAnonScript(deleteInstanceNum);
                    mainInterface().setProjectAnonScript(project, deleteInstanceNum);
                },
                new CstoreStep(TestData.SAMPLE_1),
                REBUILD,
                new ValidateNamesInPrearc(SAMPLE1),
                ARCHIVE,
                new ValidateNamesInArchive(SAMPLE1)
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
                            FileIOUtils.readFile(getDataFile(FILE_NAMES_SUBDIR + File.separator + expectedFileList)),
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
        private final TestData data;

        SessionImporterStep(TestData data) {
            this.data = data;
        }

        @Override
        public void perform(Project project) {
            mainInterface().callImporter(new SessionImporterRequest().destArchive(project).file(data.toFile()));
        }
    }

    private class DicomZipStep implements TestComponent {
        private final TestData data;
        private final boolean rename;

        DicomZipStep(TestData data, boolean rename) {
            this.data = data;
            this.rename = rename;
        }

        @Override
        public void perform(Project project) {
            mainInterface().callImporter(
                    new DicomZipRequest()
                            .file(data.toFile())
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

    private class ArchiveSession implements TestComponent {
        @Override
        public void perform(Project project) {
            mainInterface().archiveSession(expectSinglePrearchiveResultForProject(project));
        }
    }

}
