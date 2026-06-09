package org.nrg.testing.xnat.tests.dicomedit;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.LocalTestDicom;
import org.nrg.testing.UIDList;
import org.nrg.testing.annotations.AddedIn;
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
import org.nrg.testing.xnat.tests.BaseFileNamerTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.versions.Xnat_1_8_10;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.nrg.testing.TestGroups.ANONYMIZATION;
import static org.nrg.testing.TestGroups.IMPORTER;
import org.nrg.testing.annotations.MutatesServerState;

@AddedIn(Xnat_1_8_10.class)
@Test(groups = {IMPORTER, ANONYMIZATION})
@MutatesServerState
public class TestAnonymizationReject extends BaseFileNamerTest {

    private static final String REJECT_MR_SCRIPT = "rejectMrImageStorage.das";
    private static final String REJECT = "reject";
    private static final String MIXED_SOP_ID = "sample1_subset_mixed_sop_classes";
    private static final LocallyCacheableDicomTransformation SAMPLE1_SMALL_SUBSET_MIXED_SOP_CLASS = new LocallyCacheableDicomTransformation(MIXED_SOP_ID)
            .data(TestData.SAMPLE_1)
            .createZip()
            .transformations(
                    new DicomTransformation(MIXED_SOP_ID)
                            .produceZip()
                            .prefilter(DicomFilters.subsetWithInstanceNumber(Arrays.asList(1, 2)))
                            .transformFunction(TransformFunction.simple((dicom) -> {
                                if (dicom.getInt(Tag.InstanceNumber, 0) == 1) {
                                    dicom.setString(Tag.SOPClassUID, VR.UI, "1.2.3.4.99999999999999999999999");
                                }
                            }))
            );

    private final TestComponent WAIT_UNTIL_DIRECT_ARCHIVE_EMPTY = new WaitForDirectArchiveEmpty();
    private final TestComponent CSTORE_SAMPLE1_SUBSET = new CstoreStep(LocalTestDicom.SAMPLE1_SMALL_SUBSET);
    private final TestComponent CSTORE_SAMPLE1_SUBSET_MIXED_SOP_CLASS = new CstoreStep(SAMPLE1_SMALL_SUBSET_MIXED_SOP_CLASS);
    private final TestComponent SITE_ANON_RETAIN_1_PER_SERIES = siteAnon("retain1PerSeries.das");
    private final TestComponent SITE_ANON_REJECT_INSTANCE_80 = siteAnon("rejectInstance80.das");
    private final TestComponent SITE_ANON_REJECT_MR_IMAGE_SOP_CLASS = siteAnon(REJECT_MR_SCRIPT);
    private final TestComponent PROJECT_ANON_REJECT_INSTANCE_81 = projectAnon("rejectInstance81.das");
    private final TestComponent PROJECT_ANON_REJECT_SERIES_5 = projectAnon("rejectSeries5.das");
    private final TestComponent PROJECT_ANON_REJECT_MR_IMAGE_SOP_CLASS = projectAnon(REJECT_MR_SCRIPT);
    private final TestComponent PREARC_VALIDATION_INSTANCES_81_82 = new ValidateNamesInPrearc("sample1_instances_81_82.json");
    private final TestComponent PREARC_VALIDATION_INSTANCE_1_CUSTOM = new ValidateNamesInPrearc("sample1_instance_1_custom.json");
    private final TestComponent PREARC_VALIDATION_INSTANCES_1_2_CUSTOM = new ValidateNamesInPrearc("sample1_instances_1_2_custom.json");
    private final TestComponent ARCHIVE_VALIDATION_INSTANCES_80_THROUGH_82 = new ValidateNamesInArchive("sample1_first_instances.json");
    private final TestComponent ARCHIVE_VALIDATION_INSTANCE_82 = new ValidateNamesInArchive("sample1_instance_82.json");
    private final TestComponent ARCHIVE_VALIDATION_INSTANCES_81_82_NO_SERIES_5 = new ValidateNamesInArchive("sample1_instances_81_82_no_series_5.json");
    private final TestComponent ARCHIVE_VALIDATION_INSTANCE_1_CUSTOM = new ValidateNamesInArchive("sample1_instance_1_custom.json");
    private final TestComponent DICOM_ZIP_SAMPLE1_SUBSET_TO_PREARC = new DicomZipStep(
            LocalTestDicom.SAMPLE1_SMALL_SUBSET.locateOverallZip().toFile(),
            true
    );
    private final TestComponent DICOM_ZIP_SAMPLE1_SUBSET_ARCHIVE = new DicomZipStep(
            LocalTestDicom.SAMPLE1_SMALL_SUBSET.locateOverallZip().toFile(),
            true,
            true
    );
    private final TestComponent DICOM_ZIP_SUBSET_MIXED_SOP_CLASS = new DicomZipStep(
            SAMPLE1_SMALL_SUBSET_MIXED_SOP_CLASS.locateOverallZip().toFile(),
            true,
            true
    );

    @BeforeClass(groups = {IMPORTER, ANONYMIZATION})
    private void clearUnassigned() {
        restDriver.clearUnassignedPrearchiveSessions(mainAdminUser, UIDList.uids);
    }

    public void testDicomEditRejectPartialCStoreSiteAnon() {
        run(new ComponentizedTest(
                SITE_ANON_RETAIN_1_PER_SERIES,
                CSTORE_SAMPLE1,
                REBUILD,
                new ValidateNamesInPrearc("sample1_first_instances.json"),
                ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCES_80_THROUGH_82
        ));
    }

    public void testDicomEditRejectEntireScanCStoreSiteAnon() {
        run(new ComponentizedTest(
                siteAnon("retain1Instance.das"),
                CSTORE_SAMPLE1,
                REBUILD,
                new ValidateNamesInPrearc("sample1_1_instance.json"),
                ARCHIVE,
                new ValidateNamesInArchive("sample1_1_instance.json")
        ));
    }

    public void testDicomEditRejectPartialCStoreUnassignedSiteAnon() {
        run(new ComponentizedTest(
                SITE_ANON_RETAIN_1_PER_SERIES,
                new CstoreWithoutRoutingStep(TestData.SAMPLE_1),
                new RebuildOnlyUnassignedSession(),
                new ValidateNamesForUnassignedSession("sample1_first_instances.json"),
                new MoveOnlyUnassignedSessionToProject(),
                ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCES_80_THROUGH_82
        ));
    }

    public void testDicomEditRejectPartialCStoreDualAnon() {
        run(new ComponentizedTest(
                SITE_ANON_REJECT_INSTANCE_80,
                PROJECT_ANON_REJECT_INSTANCE_81,
                CSTORE_SAMPLE1_SUBSET,
                REBUILD,
                PREARC_VALIDATION_INSTANCES_81_82,
                ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCE_82
        ));
    }

    public void testDicomEditRejectPartialCStoreDirectArchiveDualAnon() {
        final DicomScpReceiver receiver = newDefaultReceiver().directArchive(true);
        mainAdminInterface().createDicomScpReceiver(receiver);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
        run(new ComponentizedTest(
                SITE_ANON_REJECT_INSTANCE_80,
                PROJECT_ANON_REJECT_INSTANCE_81,
                new CstoreStep(LocalTestDicom.SAMPLE1_SMALL_SUBSET).to(receiver),
                WAIT_UNTIL_DIRECT_ARCHIVE_EMPTY,
                ARCHIVE_VALIDATION_INSTANCE_82
        ));
    }

    public void testDicomEditRejectEntireScanCStoreDualAnon() {
        run(new ComponentizedTest(
                SITE_ANON_REJECT_INSTANCE_80,
                PROJECT_ANON_REJECT_SERIES_5,
                CSTORE_SAMPLE1_SUBSET,
                REBUILD,
                PREARC_VALIDATION_INSTANCES_81_82,
                ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCES_81_82_NO_SERIES_5
        ));
    }

    public void testDicomEditRejectEntireScanCStoreDirectArchiveDualAnon() {
        final DicomScpReceiver receiver = newDefaultReceiver().directArchive(true);
        mainAdminInterface().createDicomScpReceiver(receiver);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
        run(new ComponentizedTest(
                SITE_ANON_REJECT_INSTANCE_80,
                PROJECT_ANON_REJECT_SERIES_5,
                new CstoreStep(LocalTestDicom.SAMPLE1_SMALL_SUBSET).to(receiver),
                WAIT_UNTIL_DIRECT_ARCHIVE_EMPTY,
                ARCHIVE_VALIDATION_INSTANCES_81_82_NO_SERIES_5
        ));
    }

    public void testDicomEditRejectResourceCStoreSiteAnon() {
        run(new ComponentizedTest(
                SITE_ANON_REJECT_MR_IMAGE_SOP_CLASS,
                CSTORE_SAMPLE1_SUBSET_MIXED_SOP_CLASS,
                REBUILD,
                PREARC_VALIDATION_INSTANCE_1_CUSTOM,
                ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCE_1_CUSTOM
        ));
    }

    public void testDicomEditRejectResourceCStoreProjectAnon() {
        run(new ComponentizedTest(
                PROJECT_ANON_REJECT_MR_IMAGE_SOP_CLASS,
                CSTORE_SAMPLE1_SUBSET_MIXED_SOP_CLASS,
                REBUILD,
                PREARC_VALIDATION_INSTANCES_1_2_CUSTOM,
                ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCE_1_CUSTOM
        ));
    }

    public void testDicomEditRejectPartialDicomZipPrearchive() {
        run(new ComponentizedTest(
                SITE_ANON_REJECT_INSTANCE_80,
                PROJECT_ANON_REJECT_INSTANCE_81,
                DICOM_ZIP_SAMPLE1_SUBSET_TO_PREARC,
                REBUILD,
                PREARC_VALIDATION_INSTANCES_81_82,
                ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCE_82
        ));
    }

    public void testDicomEditRejectEntireScanDicomZipPrearchive() {
        run(new ComponentizedTest(
                SITE_ANON_REJECT_INSTANCE_80,
                PROJECT_ANON_REJECT_SERIES_5,
                DICOM_ZIP_SAMPLE1_SUBSET_TO_PREARC,
                REBUILD,
                PREARC_VALIDATION_INSTANCES_81_82,
                ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCES_81_82_NO_SERIES_5
        ));
    }

    public void testDicomEditRejectPartialDicomZipArchive() {
        run(new ComponentizedTest(
                SITE_ANON_REJECT_INSTANCE_80,
                PROJECT_ANON_REJECT_INSTANCE_81,
                DICOM_ZIP_SAMPLE1_SUBSET_ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCE_82
        ));
    }

    public void testDicomEditRejectEntireSeriesDicomZipArchive() {
        run(new ComponentizedTest(
                SITE_ANON_REJECT_INSTANCE_80,
                PROJECT_ANON_REJECT_SERIES_5,
                DICOM_ZIP_SAMPLE1_SUBSET_ARCHIVE,
                ARCHIVE_VALIDATION_INSTANCES_81_82_NO_SERIES_5
        ));
    }

    public void testDicomEditRejectResourceDicomZipSiteAnon() {
        run(new ComponentizedTest(
                SITE_ANON_REJECT_MR_IMAGE_SOP_CLASS,
                DICOM_ZIP_SUBSET_MIXED_SOP_CLASS,
                ARCHIVE_VALIDATION_INSTANCE_1_CUSTOM
        ));
    }

    public void testDicomEditRejectResourceDicomZipProjectAnon() {
        run(new ComponentizedTest(
                PROJECT_ANON_REJECT_MR_IMAGE_SOP_CLASS,
                DICOM_ZIP_SUBSET_MIXED_SOP_CLASS,
                ARCHIVE_VALIDATION_INSTANCE_1_CUSTOM
        ));
    }

    private SiteAnon siteAnon(String scriptName) {
        return new SiteAnon().fromFile(REJECT, scriptName);
    }

    private ProjectAnon projectAnon(String scriptName) {
        return new ProjectAnon().fromFile(REJECT, scriptName);
    }

    private class RebuildOnlyUnassignedSession implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            final SessionData prearcSession = mainAdminInterface().expectSinglePrearchiveResultInUnassigned();
            mainAdminInterface().rebuildSession(prearcSession, false);
        }
    }

    private class ValidateNamesForUnassignedSession extends ValidateNames {
        private ValidateNamesForUnassignedSession(String expectedFileList) {
            super(expectedFileList);
        }

        @Override
        public List<Scan> produceActualScans(Project project) {
            return mainAdminInterface().readScansForPrearchiveSession(mainAdminInterface().expectSinglePrearchiveResultInUnassigned());
        }
    }

    private class MoveOnlyUnassignedSessionToProject implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            mainAdminInterface().movePrearchiveSession(
                    mainAdminInterface().expectSinglePrearchiveResultInUnassigned().getUrl(),
                    project
            );
        }
    }

    private class WaitForDirectArchiveEmpty implements TestComponent {
        @Override
        public void perform(BaseXnatRestTest xnatRestTest, Project project) {
            restDriver.waitForDirectArchiveEmpty(mainUser, project, 120);
        }
    }

}
