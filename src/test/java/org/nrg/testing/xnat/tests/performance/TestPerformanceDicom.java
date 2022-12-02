package org.nrg.testing.xnat.tests.performance;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.dicom.transform.*;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.testing.xnat.performance.PerformanceStateHelper;
import org.nrg.testing.xnat.performance.XnatPerformanceTests;
import org.nrg.testing.xnat.performance.actions.*;
import org.nrg.testing.xnat.performance.validator.PolynomialRegressionValidator;
import org.nrg.xnat.enums.DicomEditVersion;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.enums.PrearchiveStatus;
import org.nrg.xnat.enums.XnatRecursionLevel;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.CachedSearch;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.search.XnatSearchParams;
import org.nrg.xnat.prearchive.*;
import org.testng.annotations.Test;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.Assert.fail;
import static org.testng.AssertJUnit.assertTrue;

public class TestPerformanceDicom extends XnatPerformanceTests {

    private static final int HARD_WAIT_LIMIT = 7200;
    private static final String UNIQUE_SUBJECT_SESSION_ROUTING = "(0020,000D):(.*)";
    private static final String AUTOARCHIVE_PROJECT_ID = "AUTOARCHIVE_TEST";
    private static final String PREARCHIVE_PROJECT_ID = "PREARC_TEST";
    private static final String MANY_SERIES_PROJECT_ID = "MANY_SERIES";
    private static final String SIMPLISTIC_ANON_SCRIPT = "simplisticDelete.das";
    private static final String BASIC_PIXEL_ANON_SCRIPT = "alterPixelsSimplistic.das";

    @Test
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testBulkCStore() {
        final int totalNumInstances = 1000;
        final String nameCstore = "bulk-cstore";

        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation(nameCstore)
                .data(TestData.SAMPLE_1_SCAN_4)
                .transformations(
                        new DicomTransformation("duplicate")
                                .prefilter(DicomFilters.ONLY_ONE_FILE)
                                .transformationCount(totalNumInstances)
                                .transformFunction(DicomTransforms.REMAP_UIDS)
                );

        final Project project = new Project(AUTOARCHIVE_PROJECT_ID).prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);
        final BiConsumer<XnatInterface, ActionAggregator> cstoreAndAutoarchiveAction = (xnatInterface, actionAggregator) -> {
            cstoreDicomFromTransformation(dicomTransformation).accept(xnatInterface, null);
            xnatInterface.queryPrearchive(
                    new PrearchiveQuery()
                            .maxQueryTime(HARD_WAIT_LIMIT)
                            .scope(PrearchiveQueryScope.forProject(project))
                            .expectedResult(PrearchiveResultExpectations.allResultsHaveStatus(PrearchiveStatus.ERROR)) // archiving is done once the prearchive for the project is empty, or the only entries remaining have failed to archive
            );
            final int numError = xnatInterface.getPrearchiveEntriesForProject(project).size();
            final int numSuccess = xnatInterface.countSubjectAssessorsInProject(project);
            if (numError + numSuccess != totalNumInstances) {
                fail(String.format("%d sessions were supposed to be imported, but I ended up with %d failing in the prearchive, and %d archived successfully.", totalNumInstances, numError, numSuccess));
            }
            actionAggregator.incrementFailure(numError);
            actionAggregator.incrementSuccess(numSuccess);
        };
        final Consumer<XnatInterface> readSubjectListing = (xnatInterface) -> {
            final XnatSearchDocument defaultSubjectSearch = xnatInterface.getDefaultSearch(project, DataType.SUBJECT);
            final CachedSearch cachedSearch = xnatInterface.cacheSearch(defaultSubjectSearch);
            assertTrue(xnatInterface.retrieveCachedSearchResultsAsXList(cachedSearch, new XnatSearchParams()).contains("sub_project_identifier"));
        };
        performanceScenario()
                .setup(setupForCStoreToProject(project))
                .tests(
                        new ErrorProneAggregatableAction(nameCstore)
                                .withSetup(dicomTransformation)
                                .asUser(mainAdminUser)
                                .performanceTestAction(cstoreAndAutoarchiveAction),
                        new RepeatedMonitorableAction("subject-default-stored-search-1000-subjects")
                                .asUser(mainAdminUser)
                                .performanceTestAction(readSubjectListing)
                                .actionsPerSnapshot(1)
                                .overallIterationCount(5)
                                .validateUsing(PolynomialRegressionValidator.LINEAR)
                ).run();
    }

    @Test
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testLargeSession() {
        final int totalNumInstances = 2000;
        final String name = "large-study";
        final Project project = new Project(PREARCHIVE_PROJECT_ID).prearchiveCode(PrearchiveCode.MANUAL);

        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation(name)
                .data(TestData.SAMPLE_1_SCAN_4)
                .transformations(
                        new DicomTransformation("duplicate")
                                .prefilter(DicomFilters.ONLY_ONE_FILE)
                                .transformFunction(DicomTransforms.duplicateInstance(totalNumInstances))
                );
        performanceScenario()
                .setup(setupForCStoreToProject(project))
                .tests(
                        new SimpleTimedAction("cstore-large-study")
                                .withSetup(dicomTransformation)
                                .asUser(mainAdminUser)
                                .performanceTestAction(cstoreDicomFromTransformation(dicomTransformation)),
                        new SimpleTimedAction("rebuild-and-archive-large-study")
                                .asUser(mainAdminUser)
                                .performanceTestAction(archiveActionForSingleSession(project)),
                        new SimpleTimedAction("anonymize-via-relabel-de4")
                                .asUser(mainAdminUser)
                                .performanceTestAction(anonActionViaRelabel(project, DicomEditVersion.DE_4, SIMPLISTIC_ANON_SCRIPT)),
                        new SimpleTimedAction("anonymize-via-relabel-de6")
                                .asUser(mainAdminUser)
                                .performanceTestAction(anonActionViaRelabel(project, DicomEditVersion.DE_6, SIMPLISTIC_ANON_SCRIPT)),
                        new SimpleTimedAction("anonymize-via-relabel-de6-alterPixels")
                                .asUser(mainAdminUser)
                                .performanceTestAction(anonActionViaRelabel(project, DicomEditVersion.DE_6, BASIC_PIXEL_ANON_SCRIPT))
                ).run();
    }

    @Test
    @TestRequires(data = TestData.SAMPLE_1_SCAN_4)
    public void testLargeSeriesCountPerformance() {
        final String name = "many-series";
        final int numSeries = 1000;
        final Project project = new Project(MANY_SERIES_PROJECT_ID).prearchiveCode(PrearchiveCode.MANUAL);

        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation(name)
                .data(TestData.SAMPLE_1_SCAN_4)
                .transformations(
                        new DicomTransformation(name)
                                .prefilter(DicomFilters.ONLY_ONE_FILE)
                                .transformFunction(
                                        TransformFunction.composition(
                                                DicomTransforms.duplicateInstance(numSeries),
                                                TransformFunction.strictlyTransformative(listOfDicom -> {
                                                    final String newStudyInstanceUid = UIDUtils.createUID();
                                                    for (int i = 0; i < numSeries; i++) {
                                                        final Attributes dicom = listOfDicom.get(i).getDataset();
                                                        dicom.setString(Tag.StudyInstanceUID, VR.UI, newStudyInstanceUid);
                                                        dicom.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
                                                        dicom.setInt(Tag.SeriesNumber, VR.IS, i);
                                                    }
                                                })
                                        )
                                )
                );

        performanceScenario()
                .setup(setupForCStoreToProject(project))
                .tests(
                        new SimpleTimedAction("cstore-many-series")
                                .withSetup(dicomTransformation)
                                .asUser(mainAdminUser)
                                .performanceTestAction(cstoreDicomFromTransformation(dicomTransformation)),
                        new SimpleTimedAction("rebuild-and-archive-study-many-series")
                                .asUser(mainAdminUser)
                                .performanceTestAction(archiveActionForSingleSession(project)),
                        new SimpleTimedAction("retrieve-html-study-many-series")
                                .asUser(mainAdminUser)
                                .performanceTestAction((xnatInterface, actionMonitor) -> {
                                    final Subject subject = xnatInterface.readProject(project.getId(), XnatRecursionLevel.SUBJECTS_WITH_METADATA).getSubjects().get(0);
                                    final String sessionPage = xnatInterface.subjectAssessorUrl(
                                            project,
                                            subject,
                                            new MRSession().label(subject.getLabel())
                                    );
                                    actionMonitor.reset();
                                    xnatInterface.queryBase().queryParam("format", "html").get(sessionPage).then().assertThat().statusCode(200);
                                })
                ).run();
    }


    private Consumer<PerformanceStateHelper> setupForCStoreToProject(Project project) {
        return performanceStateHelper -> {
            mainAdminInterface().createProject(project);
            mainAdminInterface().setProjectDicomRoutingConfig(projectRoutingString(project.getId()));
            mainAdminInterface().setSubjectDicomRoutingConfig(UNIQUE_SUBJECT_SESSION_ROUTING);
            mainAdminInterface().setSessionDicomRoutingConfig(UNIQUE_SUBJECT_SESSION_ROUTING);
            mainAdminInterface().setSessionXmlRebuilderTimes(1, 5000);
        };
    }

    private BiConsumer<XnatInterface, ActionMonitor> anonActionViaRelabel(Project project, DicomEditVersion dicomEditVersion, String scriptName) {
        return (xnatInterface, actionMonitor) -> {
            xnatInterface.setProjectAnonScript(project, XnatObjectUtils.anonScriptFromFile(dicomEditVersion, scriptName));
            final Subject subject = xnatInterface.readProject(project.getId()).getSubjects().get(0);
            final ImagingSession session = subject.getSessions().get(0);
            xnatInterface.relabelSubjectAssessor(project, subject, session, session.getLabel() + "_1");
        };
    }

    private BiConsumer<XnatInterface, ActionMonitor> cstoreDicomFromTransformation(LocallyCacheableDicomTransformation dicomTransformation) {
        return (xnatInterface, actionMonitor) -> {
            new XnatCStore().data(dicomTransformation.baseLevelDir().toFile()).sendDICOM(); // TODO: exceptions logged for non-DICOM in the directory (README & .DS_Store)
        };
    }

    private BiConsumer<XnatInterface, ActionMonitor> archiveActionForSingleSession(Project project) {
        return (xnatInterface, actionMonitor) -> {
            final SessionData incomingSession = xnatInterface.getPrearchiveEntriesForProject(project).get(0);
            xnatInterface
                    .rebuildSession(incomingSession, false)
                    .archiveSession(incomingSession);
            xnatInterface.queryPrearchive(
                    new PrearchiveQuery()
                            .scope(PrearchiveQueryScope.forProject(project))
                            .expectedResult(PrearchiveResultExpectations.EMPTY)
            );
        };
    }

    private static Consumer<PerformanceStateHelper> createSpecificProject(Project projectSpec) {
        return stateHelper -> stateHelper.mainAdminInterface().createProject(projectSpec);
    }

    private static String projectRoutingString(String projectId) {
        return "(0008,0018):(.*):1 t:.+ r:" + projectId;
    }

}
