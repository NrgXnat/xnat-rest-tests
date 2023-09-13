package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.ScanFileNameRecord;
import org.nrg.testing.xnat.components.TestComponent;
import org.nrg.testing.xnat.rest.XnatRestDriver;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.prearchive.PrearchiveQuery;
import org.nrg.xnat.prearchive.PrearchiveQueryScope;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.rest.SerializationUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

public class BaseFileNamerTest extends BaseXnatRestTest {

    protected static final Logger LOGGER = Logger.getLogger(BaseFileNamerTest.class);
    protected static final String FILE_NAMES_SUBDIR = "filenames";
    protected static final String SAMPLE1_NAME_SPEC = "sample1.json";
    protected static final String FILE_NAMER_EXPECTED_VALUE = "${StudyInstanceUID}-${SeriesNumber}-${InstanceNumber}-${HashSOPClassUIDWithSOPInstanceUID}";
    protected final TestComponent REBUILD = new RebuildOnlySessionInPrearc();
    protected final TestComponent ARCHIVE = new ArchiveSession();

    /*
        This checks the file namer set currently. If the namer doesn't match the default, we reset it. We could just always make this POST and skip the GET,
        but if the value is still the default, then avoiding touching it at all more closely resembles production use where admins don't normally modify this.
     */
    @BeforeMethod(alwaysRun = true)
    protected void initFileNamer() {
        final String currentFileNamer = mainAdminInterface().readDicomFileNamer();
        if (!FILE_NAMER_EXPECTED_VALUE.equals(currentFileNamer)) {
            mainAdminInterface().updateDicomFileNamer(FILE_NAMER_EXPECTED_VALUE);
        }
    }

    @BeforeMethod(alwaysRun = true)
    protected void disableCustomRouting() {
        try {
            mainAdminInterface().disableSiteAnonScript();
            mainAdminInterface().disableProjectDicomRoutingConfig();
            mainAdminInterface().disableSubjectDicomRoutingConfig();
            mainAdminInterface().disableSessionDicomRoutingConfig();
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
    }

    @BeforeMethod(alwaysRun = true)
    protected void disableSiteAnon() {
        mainAdminInterface().disableSiteAnonScript();
    }

    @AfterClass(alwaysRun = true)
    @BeforeMethod(alwaysRun = true)
    protected void disableFileBackups() {
        mainAdminInterface().setSiteBackupSettings(false, false);
    }

    @AfterClass(alwaysRun = true)
    @BeforeMethod(alwaysRun = true)
    protected void resetSopInstanceForDicomUniqueness() {
        mainAdminInterface().setUseSopInstanceUidToUniquelyIdentifyDicom(true);
    }

    protected class UpdateFileNamer implements TestComponent {
        private final String template;

        UpdateFileNamer(String template) {
            this.template = template;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainAdminInterface().updateDicomFileNamer(template);
        }
    }

    protected class CstoreStep implements TestComponent {
        private final File data;

        CstoreStep(TestData data) {
            this.data = data.toDirectory();
        }

        CstoreStep(LocallyCacheableDicomTransformation dicomTransformation) {
            dicomTransformation.build();
            data = dicomTransformation.locateBaseDirForTransformedData(dicomTransformation.getIdentifier()).toFile();
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            new XnatCStore().data(data).sendDICOMToProject(project);
        }
    }

    protected class DicomZipStep implements TestComponent {
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
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainInterface().callImporter(
                    new DicomZipRequest()
                            .file(data)
                            .project(project)
                            .rename(rename)
            );
        }
    }

    protected abstract class ValidateNames implements TestComponent {
        private final String expectedFileList;

        ValidateNames(String expectedFileList) {
            this.expectedFileList = expectedFileList;
        }

        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            validateFilesInScansMatchExpectedNames(
                    expectedFileList,
                    produceActualScans(project)
            );
        }

        abstract List<Scan> produceActualScans(Project project);
    }

    protected class ValidateNamesInPrearc extends ValidateNames {
        ValidateNamesInPrearc(String expectedFileList) {
            super(expectedFileList);
        }

        @Override
        List<Scan> produceActualScans(Project project) {
            return mainInterface().readScansForPrearchiveSession(expectSinglePrearchiveResultForProject(project));
        }
    }

    protected class ValidateNamesInArchive extends ValidateNames {
        ValidateNamesInArchive(String expectedFileList) {
            super(expectedFileList);
        }

        @Override
        List<Scan> produceActualScans(Project project) {
            return mainInterface().readProject(project.getId()).getSubjects().get(0).getSessions().get(0).getScans();
        }
    }

    protected class ArchiveSession implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainInterface().archiveSession(expectSinglePrearchiveResultForProject(project));
        }
    }

    protected class RebuildOnlySessionInPrearc implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            final SessionData prearcSession = expectSinglePrearchiveResultForProject(project);
            mainInterface().rebuildSession(prearcSession, false);
        }
    }

    protected void validateFilesInScansMatchExpectedNames(String expectedFileList, List<Scan> actualScans) {
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
                scanFileNameRecords.stream().map(ScanFileNameRecord::getScanId).collect(Collectors.toSet()),
                actualScans.stream().map(Scan::getId).collect(Collectors.toSet())
        ); // scan IDs should match exactly now

        for (ScanFileNameRecord fileNameRecord : scanFileNameRecords) {
            fileNameRecord.identifySelfAndValidate(actualScans);
        }
    }

    protected SessionData expectSinglePrearchiveResultForProject(Project project) {
        return mainInterface().queryPrearchiveForSingularResult(new PrearchiveQuery().scope(PrearchiveQueryScope.forProject(project)));
    }

}
