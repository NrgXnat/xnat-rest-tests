package org.nrg.testing.xnat.tests;

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.LocalTestDicom;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.OffsetIndexDicomWriter;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.dicom.transform.XnatDefaultTemplatizedNamerWriter;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.enums.DicomEditVersion;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.ArchiveParams;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.experiments.sessions.PETSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.ResourceMitigationReport;
import org.nrg.xnat.pogo.resources.ResourceSurveyReport;
import org.nrg.xnat.pogo.resources.ResourceSurveyRequest;
import org.nrg.xnat.pogo.resources.ScanResource;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.versions.Xnat_1_8_7;
import org.nrg.xnat.versions.Xnat_1_8_9;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.nrg.testing.TestGroups.FILE_MITIGATION;
import static org.nrg.testing.TestGroups.IMPORTER;
import static org.nrg.xnat.pogo.resources.ResourceSurveyRequest.Status.*;
import static org.testng.AssertJUnit.*;
import org.nrg.testing.annotations.MutatesServerState;

@Test(groups = {FILE_MITIGATION, IMPORTER})
@TestRequires(data = TestData.SAMPLE_1)
@AddedIn(Xnat_1_8_7.class)
@MutatesServerState
public class TestFileMitigation extends BaseFileNamerTest {

    private static final String DICOM = "DICOM";
    private static final List<ResourceSurveyRequest.Status> NO_RESOURCE_ACTION_ACTIVE = Arrays.asList(CONFORMING, DIVERGENT, CANCELED, NONCOMPLIANT);
    private static final String SIMPLE_PET_FILE_SPEC = "simple_pet.json";
    private static final String SAMPLE1_SUBSET_FILE_SPEC = "sample1_subset.json";
    private static final String SAMPLE1_SUBSET_NONCONFORMING_FILE_SPEC = "sample1_nonconforming.json";
    private static final String SAMPLE1_SUBSET_INSTANCE_NUM_FILE_SPEC = "sample1_subset_instance_num.json";
    private static final String SAMPLE1_PRIVATE_SOP_CLASS_FILE_SPEC = "sample1_private_sop_class.json";
    private static final String SAMPLE1_SUBJECT = "Sample_Patient";
    private static final String SAMPLE1_SESSION = "Sample_ID";
    private static final String SUBSET_4_NAME = "sample1_series_4_subset_si.json";
    private static final String SUBSET_5_NAME = "sample1_series_5_subset_si.json";
    private static final String SUBSET_6_NAME = "sample1_series_6_subset_si.json";
    private static final String SUBSET_5_DUPLICATE_NAME = "sample1_series_5_subset_duplication_si.json";
    private static final String SUBSET_6_BADFILE_NAME = "sample1_series_6_subset_badfile.json";
    private static final String SAMPLE1_MULTIPLE_ISSUES_ID = "sample1-custom-multiple-issues";
    private static final String SERIES_5_TRANSFORM = "series5";
    private static final LocallyCacheableDicomTransformation SAMPLE1_MULTIPLE_ISSUES = sample1MultipleIssues();
    private final ResourceSurveyRequest SAMPLE1_SERIES_4_SI_SURVEY = readSurveyRequest("sample1_series_4_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_5_SI_SURVEY = readSurveyRequest("sample1_series_5_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SI_SURVEY = readSurveyRequest("sample1_series_6_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_4_SUBSET_SI_SURVEY = readSurveyRequest(SUBSET_4_NAME);
    private final ResourceSurveyRequest SAMPLE1_SERIES_5_SUBSET_SI_SURVEY = readSurveyRequest(SUBSET_5_NAME);
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_SI_SURVEY = readSurveyRequest(SUBSET_6_NAME);
    private final ResourceSurveyRequest SIMPLE_PET_SI_SURVEY = readSurveyRequest("simple_pet_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_4_SUBSET_DUPLICATION_SI_SURVEY = readSurveyRequest("sample1_series_4_subset_duplication_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_5_SUBSET_DUPLICATION_SI_SURVEY = readSurveyRequest(SUBSET_5_DUPLICATE_NAME);
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_DUPLICATION_SI_SURVEY = readSurveyRequest("sample1_series_6_subset_duplication_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY = readSurveyRequest("sample1_series_4_subset_conforming.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY = readSurveyRequest("sample1_series_5_subset_conforming.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_CONFORMING_SURVEY = readSurveyRequest("sample1_series_6_subset_conforming.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_INSTANCE_NUM_SURVEY = readSurveyRequest("sample1_series_6_subset_instance_num.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_BADFILE_SURVEY = readSurveyRequest(SUBSET_6_BADFILE_NAME);
    private final ResourceSurveyRequest SAMPLE1_PRIVATE_SOP_CLASS_SURVEY = readSurveyRequest("sample1_private_sop_class.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_4_SI_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_4_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_5_SI_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_5_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SI_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_6_si.json");
    private final ResourceSurveyRequest SIMPLE_PET_SI_SURVEY_AND_MITIGATION = readSurveyWithMitigation("simple_pet_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_4_SUBSET_DUPLICATION_SI_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_4_subset_duplication_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_5_SUBSET_DUPLICATION_SI_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_5_subset_duplication_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_DUPLICATION_SI_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_6_subset_duplication_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_INSTANCE_NUM_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_6_subset_instance_num.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_BADFILE_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_6_subset_badfile.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_4_SUBSET_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_4_subset_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_5_SUBSET_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_5_subset_si.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_6_SUBSET_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_series_6_subset_si.json");
    private final ResourceSurveyRequest SAMPLE1_PRIVATE_SOP_CLASS_SURVEY_AND_MITIGATION = readSurveyWithMitigation("sample1_private_sop_class.json");
    private final ResourceSurveyRequest SAMPLE1_SERIES_4_NONACTIONABLE_SURVEY = readSurveyRequest("sample1_series_4_subset_nonactionable.json");
    private String archivePath;
    private String cachePath;

    @BeforeClass
    public void readPaths() {
        archivePath = StringUtils.stripEnd(mainAdminInterface().readSiteConfigPreference("archivePath"), "/");
        cachePath = StringUtils.stripEnd(mainAdminInterface().readSiteConfigPreference("cachePath"), "/");
    }

    @Test
    @TestRequires(data = TestData.SIMPLE_PET)
    public void testSimpleFileMitigationProjectApis() {
        final Project project = registerTempProject();
        final Subject subject = new Subject(project);
        final ImagingSession mrSession = new MRSession(project, subject);
        new SessionImportExtension(mrSession, TestData.SAMPLE_1.toFile());
        final ImagingSession petSession = new PETSession(project, subject);
        new SessionImportExtension(petSession, TestData.SIMPLE_PET.toFile());

        new FullWorkflowMitigationTest()
                .project(project)
                .expectedSurveyMap(
                        new SurveyMapping()
                                .add(mrSession, SAMPLE1_SERIES_4_SI_SURVEY, SAMPLE1_SERIES_5_SI_SURVEY, SAMPLE1_SERIES_6_SI_SURVEY)
                                .add(petSession, SIMPLE_PET_SI_SURVEY)
                ).expectedSurveysWithMitigation(
                        new SurveyMapping()
                                .add(mrSession, SAMPLE1_SERIES_4_SI_SURVEY_AND_MITIGATION, SAMPLE1_SERIES_5_SI_SURVEY_AND_MITIGATION, SAMPLE1_SERIES_6_SI_SURVEY_AND_MITIGATION)
                                .add(petSession, SIMPLE_PET_SI_SURVEY_AND_MITIGATION)
                )
                .expectedFilesAfterMitigation(
                        new SessionFileMapping()
                                .add(mrSession, SAMPLE1_NAME_SPEC)
                                .add(petSession, SIMPLE_PET_FILE_SPEC)
                ).run();
    }

    @Test
    public void testSimpleFileMitigationDuplicates() {
        mainAdminInterface().setUseSopInstanceUidToUniquelyIdentifyDicom(false);
        final LocallyCacheableDicomTransformation dicomTransformation = LocalTestDicom.SAMPLE1_SMALL_SUBSET.build();
        final Project project = registerTempProject();
        final Subject subject = new Subject(project, SAMPLE1_SUBJECT);
        final ImagingSession mrSession = new MRSession(project, subject, SAMPLE1_SESSION);
        new SessionImportExtension(mrSession, dicomTransformation.locateOverallZip().toFile());
        new FullWorkflowMitigationTest()
                .project(project)
                .postProjectAction(cstoreAndArchive(dicomTransformation, project))
                .expectedSurveyMap(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_DUPLICATION_SI_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_DUPLICATION_SI_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_DUPLICATION_SI_SURVEY
                        )
                ).expectedSurveysWithMitigation(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_DUPLICATION_SI_SURVEY_AND_MITIGATION,
                                SAMPLE1_SERIES_5_SUBSET_DUPLICATION_SI_SURVEY_AND_MITIGATION,
                                SAMPLE1_SERIES_6_SUBSET_DUPLICATION_SI_SURVEY_AND_MITIGATION
                        )
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping().add(mrSession, SAMPLE1_SUBSET_FILE_SPEC)
                ).run();
    }

    @Test
    public void testSimpleFileMitigationPartiallyConforming() {
        mainAdminInterface().enableSiteAnonScript();
        mainAdminInterface().setSiteAnonScript(XnatObjectUtils.anonScriptFromFile(DicomEditVersion.DE_6, "hardcodeInstanceNumForSeries.das"));
        final LocallyCacheableDicomTransformation dicomTransformation = LocalTestDicom.SAMPLE1_SMALL_SUBSET.build();
        final Project project = registerTempProject();
        final Subject subject = new Subject(project, SAMPLE1_SUBJECT);
        final ImagingSession mrSession = new MRSession(project, subject, SAMPLE1_SESSION);
        new FullWorkflowMitigationTest()
                .project(project)
                .postProjectAction(cstoreAndArchive(dicomTransformation, project))
                .expectedSurveyMap(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_INSTANCE_NUM_SURVEY
                        )
                ).expectedSurveysWithMitigation(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_INSTANCE_NUM_SURVEY_AND_MITIGATION
                        )
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping().add(mrSession, SAMPLE1_SUBSET_INSTANCE_NUM_FILE_SPEC)
                ).run();
    }

    @Test
    public void testSimpleFileMitigationConforming() {
        final LocallyCacheableDicomTransformation dicomTransformation = LocalTestDicom.SAMPLE1_SMALL_SUBSET.build();
        final Project project = registerTempProject();
        final Subject subject = new Subject(project, SAMPLE1_SUBJECT);
        final ImagingSession mrSession = new MRSession(project, subject, SAMPLE1_SESSION);
        new FullWorkflowMitigationTest()
                .project(project)
                .postProjectAction(cstoreAndArchive(dicomTransformation, project))
                .expectedSurveyMap(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_CONFORMING_SURVEY
                        )
                ).expectedSurveysWithMitigation(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_CONFORMING_SURVEY
                        )
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping().add(mrSession, SAMPLE1_SUBSET_FILE_SPEC)
                ).run();
    }

    @Test
    public void testSimpleFileMitigationBadFiles() {
        final LocallyCacheableDicomTransformation dicomTransformation = LocalTestDicom.SAMPLE1_SMALL_SUBSET.build();
        final Project project = registerTempProject();
        final Subject subject = new Subject(project, SAMPLE1_SUBJECT);
        final ImagingSession mrSession = new MRSession(project, subject, SAMPLE1_SESSION);
        new FullWorkflowMitigationTest()
                .project(project)
                .postProjectAction(() -> {
                    cstoreAndArchive(dicomTransformation, project).run();
                    overwriteDicomWithCat(project, subject, mrSession).run();
                })
                .expectedSurveyMap(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_BADFILE_SURVEY
                        )
                ).expectedSurveysWithMitigation(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_BADFILE_SURVEY_AND_MITIGATION
                        )
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping().add(mrSession, SAMPLE1_SUBSET_FILE_SPEC)
                ).run();
    }

    @Test
    public void testSimpleFileMitigationPrivateSopClass() {
        final String transformId = "private-sop-class-custom";
        final String privateSopClassUid = "9.9.9.9.9.123.456.789";
        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation(transformId)
                .data(TestData.SAMPLE_1_SCAN_4)
                .createZip()
                .transformations(
                        new DicomTransformation(transformId)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(1))
                                .transformFunction(
                                        TransformFunction.simple(fullInstance -> {
                                            fullInstance.setString(Tag.MediaStorageSOPClassUID, VR.UI, privateSopClassUid);
                                            fullInstance.setString(Tag.SOPClassUID, VR.UI, privateSopClassUid);
                                        })
                                )
                ).build();
        final Project project = registerTempProject();
        final Subject subject = new Subject(project);
        final ImagingSession mrSession = new MRSession(project, subject);
        new SessionImportExtension(mrSession, dicomTransformation.locateOverallZip().toFile());
        new FullWorkflowMitigationTest()
                .project(project)
                .expectedSurveyMap(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_PRIVATE_SOP_CLASS_SURVEY
                        )
                ).expectedSurveysWithMitigation(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_PRIVATE_SOP_CLASS_SURVEY_AND_MITIGATION
                        )
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping().add(mrSession, SAMPLE1_PRIVATE_SOP_CLASS_FILE_SPEC)
                ).run();
    }

    @Test
    @AddedIn(Xnat_1_8_9.class) // see XNAT-7794
    public void testSimpleFileMitigationNoncompliantDuplicates() {
        final String transformId = "noncompliant-duplicate-instance";
        final LocallyCacheableDicomTransformation dicomTransformation = new LocallyCacheableDicomTransformation(transformId)
                .data(TestData.SAMPLE_1_SCAN_4)
                .createZip()
                .transformations(
                        new DicomTransformation(transformId)
                                .prefilter(DicomFilters.subsetWithInstanceNumber(1))
                                .transformFunction(
                                        TransformFunction.generalTransform(fullInstances -> {
                                            final List<Attributes> instancePlusCopy = new ArrayList<>();
                                            final Attributes originalInstance = fullInstances.get(0);
                                            instancePlusCopy.add(originalInstance);
                                            final Attributes clone = DicomUtils.clone(originalInstance);
                                            clone.setInt(Tag.InstanceNumber, VR.IS, 2);
                                            instancePlusCopy.add(clone);
                                            return instancePlusCopy;
                                        })
                                )
                ).build();

        final Project project = registerTempProject();
        final Subject subject = new Subject(project, SAMPLE1_SUBJECT);
        final ImagingSession mrSession = new MRSession(project, subject, SAMPLE1_SESSION);
        new FullWorkflowMitigationTest()
                .project(project)
                .postProjectAction(cstoreAndArchive(dicomTransformation, project))
                .expectedSurveyMap(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_NONACTIONABLE_SURVEY
                        )
                ).expectedSurveysWithMitigation(
                        new SurveyMapping().add(
                                mrSession,
                                SAMPLE1_SERIES_4_NONACTIONABLE_SURVEY
                        )
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping().add(mrSession, SAMPLE1_SUBSET_NONCONFORMING_FILE_SPEC)
                ).run();
    }

    @Test
    public void testSimpleFileMitigationCanceling() {
        final LocallyCacheableDicomTransformation dicomTransformation = LocalTestDicom.SAMPLE1_SMALL_SUBSET.build();
        final Project project = registerTempProject();
        final Subject subject = new Subject(project);
        final ImagingSession mrSession = new MRSession(project, subject);
        new SessionImportExtension(mrSession, dicomTransformation.locateOverallZip().toFile());
        final SurveyMapping initialSurvey = new SurveyMapping()
                .add(
                        mrSession,
                        readSurveyRequest(SUBSET_4_NAME),
                        readSurveyRequest(SUBSET_5_NAME),
                        readSurveyRequest(SUBSET_6_NAME)
                );

        new FullWorkflowMitigationTest()
                .project(project)
                .postProjectAction(() -> {
                    new FullWorkflowMitigationTest()
                            .project(project)
                            .launchSurveyAndValidateResults(initialSurvey);
                    assertTrue(mainInterface().launchSurveyRequests(project).isEmpty());
                    mainInterface().cancelAllOpenResourceSurveyRequestsInProject(project);
                    final List<ResourceSurveyRequest> canceledRequests = waitForNoActiveResourceActionInProject(project, initialSurvey.expectedNumResources());
                    for (ResourceSurveyRequest surveyRequest : initialSurvey.asMap.get(mrSession)) {
                        surveyRequest.setRsnStatus(CANCELED);
                    }
                    initialSurvey.validateSurveyRequests(canceledRequests);
                })
                .expectedSurveyMap(
                        new SurveyMapping()
                                .add(mrSession, SAMPLE1_SERIES_4_SUBSET_SI_SURVEY, SAMPLE1_SERIES_5_SUBSET_SI_SURVEY, SAMPLE1_SERIES_6_SUBSET_SI_SURVEY)
                                .merge(initialSurvey)
                ).expectedSurveysWithMitigation(
                        new SurveyMapping()
                                .add(mrSession, SAMPLE1_SERIES_4_SUBSET_SURVEY_AND_MITIGATION, SAMPLE1_SERIES_5_SUBSET_SURVEY_AND_MITIGATION, SAMPLE1_SERIES_6_SUBSET_SURVEY_AND_MITIGATION)
                                .merge(initialSurvey)
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping()
                                .add(mrSession, SAMPLE1_SUBSET_FILE_SPEC)
                ).run();
    }

    @Test
    public void testSimpleFileMitigationDateFilter() {
        final LocalDate today = LocalDate.now();
        final LocalDate tomorrow = today.plusDays(1);
        final LocallyCacheableDicomTransformation dicomTransformation = LocalTestDicom.SAMPLE1_SMALL_SUBSET.build();
        final Project project = registerTempProject();
        final Subject subject = new Subject(project);
        final ImagingSession mrSession = new MRSession(project, subject);
        new SessionImportExtension(mrSession, dicomTransformation.locateOverallZip().toFile());

        new FullWorkflowMitigationTest()
                .project(project)
                .postProjectAction(() -> assertEquals(0, mainInterface().launchSurveyRequests(project, tomorrow).size()))
                .expectedSurveyMap(
                        new SurveyMapping()
                                .add(mrSession, SAMPLE1_SERIES_4_SUBSET_SI_SURVEY, SAMPLE1_SERIES_5_SUBSET_SI_SURVEY, SAMPLE1_SERIES_6_SUBSET_SI_SURVEY)
                ).initialSurveyDateFilter(today)
                .expectedSurveysWithMitigation(
                        new SurveyMapping()
                                .add(mrSession, SAMPLE1_SERIES_4_SUBSET_SURVEY_AND_MITIGATION, SAMPLE1_SERIES_5_SUBSET_SURVEY_AND_MITIGATION, SAMPLE1_SERIES_6_SUBSET_SURVEY_AND_MITIGATION)
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping()
                                .add(mrSession, SAMPLE1_SUBSET_FILE_SPEC)
                ).run();
    }

    @Test
    public void testSimpleFileMitigationSanitizeSurvey() {
        mainAdminInterface().setUseSopInstanceUidToUniquelyIdentifyDicom(false);
        final Project project = new Project();
        final Subject subject = new Subject(project, SAMPLE1_SUBJECT);
        final ImagingSession session = new MRSession(project, subject, SAMPLE1_SESSION);
        new SessionImportExtension(session, SAMPLE1_MULTIPLE_ISSUES.build().locateOverallZip().toFile());
        mainInterface().createProject(project);

        cstoreAndArchive(SAMPLE1_MULTIPLE_ISSUES.locateDataForIndividualTransformationInstance(SERIES_5_TRANSFORM).toFile(), project).run();
        overwriteDicomWithCat(project, subject, session).run();

        final List<ResourceSurveyRequest> surveyRequests = new FullWorkflowMitigationTest().project(project).launchSurveyAndValidateResults(
                new SurveyMapping()
                        .add(session, SAMPLE1_SERIES_4_SUBSET_SI_SURVEY, SAMPLE1_SERIES_5_SUBSET_DUPLICATION_SI_SURVEY, SAMPLE1_SERIES_6_SUBSET_BADFILE_SURVEY)
        );
        for (ResourceSurveyRequest surveyRequest : surveyRequests) {
            mainInterface().cleanResourceReportsByRequestId(surveyRequest.getId());
        }

        final SurveyMapping cleanedSurveys = new SurveyMapping();
        Stream.of(
                readSurveyRequest(SUBSET_4_NAME),
                readSurveyRequest(SUBSET_5_DUPLICATE_NAME),
                readSurveyRequest(SUBSET_6_BADFILE_NAME)
        ).forEach(survey -> {
                    final ResourceSurveyReport report = survey.getSurveyReport();
                    survey.setRsnStatus(CANCELED);
                    report.setUids(null);
                    report.setDuplicates(null);
                    report.setMismatchedFiles(null);
                    report.setBadFiles(null);
                    cleanedSurveys.add(session, survey);
        });
        cleanedSurveys.validateSurveyRequests(mainInterface().readResourceSurveysForProject(project));
    }

    @Test
    public void testSimpleFileMitigationSanitizeBoth() {
        mainAdminInterface().setUseSopInstanceUidToUniquelyIdentifyDicom(false);
        final Project project = new Project();
        final Subject subject = new Subject(project, SAMPLE1_SUBJECT);
        final ImagingSession session = new MRSession(project, subject, SAMPLE1_SESSION);
        new SessionImportExtension(session, SAMPLE1_MULTIPLE_ISSUES.build().locateOverallZip().toFile());

        new FullWorkflowMitigationTest()
                .project(project)
                .postProjectAction(() -> {
                    cstoreAndArchive(SAMPLE1_MULTIPLE_ISSUES.locateDataForIndividualTransformationInstance(SERIES_5_TRANSFORM).toFile(), project).run();
                    overwriteDicomWithCat(project, subject, session).run();
                })
                .expectedSurveyMap(
                        new SurveyMapping().add(
                                session,
                                SAMPLE1_SERIES_4_SUBSET_SI_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_DUPLICATION_SI_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_BADFILE_SURVEY
                        )
                ).expectedSurveysWithMitigation(
                        new SurveyMapping().add(
                                session,
                                SAMPLE1_SERIES_4_SUBSET_SURVEY_AND_MITIGATION,
                                SAMPLE1_SERIES_5_SUBSET_DUPLICATION_SI_SURVEY_AND_MITIGATION,
                                SAMPLE1_SERIES_6_SUBSET_BADFILE_SURVEY_AND_MITIGATION
                        )
                ).expectedFilesAfterMitigation(
                        new SessionFileMapping()
                                .add(session, SAMPLE1_SUBSET_FILE_SPEC)
                ).run();
        for (ResourceSurveyRequest surveyRequest : mainInterface().readResourceSurveysForProject(project)) {
            mainInterface().cleanResourceReportsByResourceId(surveyRequest.getResourceId());
        }

        final SurveyMapping cleanedSurveys = new SurveyMapping();
        Stream.of(
                readSurveyWithMitigation(SUBSET_4_NAME),
                readSurveyWithMitigation(SUBSET_5_DUPLICATE_NAME),
                readSurveyWithMitigation(SUBSET_6_BADFILE_NAME)
        ).forEach(survey -> {
            final ResourceSurveyReport report = survey.getSurveyReport();
            report.setUids(null);
            report.setDuplicates(null);
            report.setMismatchedFiles(null);
            report.setBadFiles(null);
            final ResourceMitigationReport mitigationReport = survey.getMitigationReport();
            mitigationReport.setMovedFiles(null);
            mitigationReport.setRemovedFiles(null);
            mitigationReport.setRetainedFiles(null);
            cleanedSurveys.add(session, survey);
        });
        cleanedSurveys.validateSurveyRequests(mainInterface().readResourceSurveysForProject(project));
    }

    @Test
    @TestRequires(data = TestData.SIMPLE_PET)
    public void testFileMitigationViaCsv() throws IOException {
        final LocallyCacheableDicomTransformation dicomTransformation = LocalTestDicom.SAMPLE1_SMALL_SUBSET;
        final Project project = new Project();
        final Subject subject = new Subject(project, SAMPLE1_SUBJECT);
        final ImagingSession mrSession = new MRSession(project, subject, SAMPLE1_SESSION);
        final ImagingSession petSession = new PETSession(project, subject);
        new SessionImportExtension(petSession, TestData.SIMPLE_PET.toFile());
        mainInterface().createProject(project);
        cstoreAndArchive(dicomTransformation, project).run();

        final FullWorkflowMitigationTest testDelegate = new FullWorkflowMitigationTest().project(project);
        testDelegate.launchSurveyAndValidateResults(
                new SurveyMapping()
                        .add(
                                mrSession,
                                SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY,
                                SAMPLE1_SERIES_6_SUBSET_CONFORMING_SURVEY
                        ).add(
                                petSession,
                                SIMPLE_PET_SI_SURVEY
                        )
        );

        final String csvContent = mainInterface().readResourceSurveyReportCsvForProject(project);
        final File csvFile = Files.createTempFile("mitigation", ".csv").toFile();
        FileUtils.writeStringToFile(csvFile, csvContent, StandardCharsets.UTF_8);

        final Map<String, List<Integer>> csvMitigationResults = mainInterface().launchMitigationViaCsv(csvFile);
        assertEquals(2, csvMitigationResults.size());
        assertEquals(3, csvMitigationResults.get("invalid").size());
        assertEquals(1, csvMitigationResults.get("queued").size());
        final SurveyMapping expectedSurveysWithMitigation = new SurveyMapping()
                .add(
                        mrSession,
                        SAMPLE1_SERIES_4_SUBSET_CONFORMING_SURVEY,
                        SAMPLE1_SERIES_5_SUBSET_CONFORMING_SURVEY,
                        SAMPLE1_SERIES_6_SUBSET_CONFORMING_SURVEY
                ).add(
                        petSession,
                        SIMPLE_PET_SI_SURVEY_AND_MITIGATION
                );
        final List<ResourceSurveyRequest> surveyRequestsAfterMitigation = waitForNoActiveResourceActionInProject(project, expectedSurveysWithMitigation.expectedNumResources());
        expectedSurveysWithMitigation.validateSurveyRequests(surveyRequestsAfterMitigation);
        new SessionFileMapping()
                .add(mrSession, SAMPLE1_SUBSET_FILE_SPEC)
                .add(petSession, SIMPLE_PET_FILE_SPEC)
                .validateFilesMatch(project.getId());
    }

    @Test
    public void testSurveyApiPermissions() {
        final int expectedNumResources = 3;
        final LocallyCacheableDicomTransformation dicomTransformation = LocalTestDicom.SAMPLE1_SMALL_SUBSET;
        final Project project = new Project();
        final Subject subject = new Subject(project);
        final ImagingSession session = new MRSession(project, subject);
        new SessionImportExtension(session, dicomTransformation.locateOverallZip().toFile());
        project.addMember(mainUser);
        mainAdminInterface().createProject(project);
        expect403(() -> mainInterface().launchSurveyRequests(project));
        expect403(() -> mainInterface().readResourceSurveysForProject(project));
        assertEquals(0, mainAdminInterface().readResourceSurveysForProject(project).size());
        mainAdminInterface().launchSurveyRequests(project);
        expect403(() -> mainInterface().launchMitigationRequestsForProject(project));
        for (ResourceSurveyRequest surveyRequest : waitForNoActiveResourceActionInProject(project, expectedNumResources, mainAdminUser)) {
            assertEquals(DIVERGENT, surveyRequest.getRsnStatus());
        }
    }

    private class FullWorkflowMitigationTest {
        private Project project;
        private Runnable postProjectAction;
        private SurveyMapping expectedSurveyMap;
        private LocalDate initialSurveyDateFilter;
        private SurveyMapping expectedSurveysWithMitigation;
        private SessionFileMapping expectedFilesAfterMitigation;

        FullWorkflowMitigationTest project(Project project) {
            this.project = project;
            return this;
        }

        FullWorkflowMitigationTest postProjectAction(Runnable postProjectAction) {
            this.postProjectAction = postProjectAction;
            return this;
        }

        FullWorkflowMitigationTest expectedSurveyMap(SurveyMapping expectedSurveyMap) {
            this.expectedSurveyMap = expectedSurveyMap;
            return this;
        }

        FullWorkflowMitigationTest initialSurveyDateFilter(LocalDate initialSurveyDateFilter) {
            this.initialSurveyDateFilter = initialSurveyDateFilter;
            return this;
        }

        FullWorkflowMitigationTest expectedSurveysWithMitigation(SurveyMapping expectedSurveysWithMitigation) {
            this.expectedSurveysWithMitigation = expectedSurveysWithMitigation;
            return this;
        }

        FullWorkflowMitigationTest expectedFilesAfterMitigation(SessionFileMapping expectedFilesAfterMitigation) {
            this.expectedFilesAfterMitigation = expectedFilesAfterMitigation;
            return this;
        }

        void run() {
            mainInterface().createProject(project);
            if (postProjectAction != null) {
                postProjectAction.run();
            }
            final List<ResourceSurveyRequest> surveyRequests = launchSurveyAndValidateResults(expectedSurveyMap);
            final List<Integer> mitigationIds = mainInterface().launchMitigationRequestsForProject(project);
            final Set<Integer> divergentIds = surveyRequests
                    .stream()
                    .filter(surveyRequest -> surveyRequest.getRsnStatus() == DIVERGENT)
                    .map(ResourceSurveyRequest::getId)
                    .collect(Collectors.toSet());
            assertEquals(divergentIds, Sets.newHashSet(mitigationIds));

            final List<ResourceSurveyRequest> surveyRequestsAfterMitigation = waitForNoActiveResourceActionInProject(project, expectedSurveysWithMitigation.expectedNumResources());
            expectedSurveysWithMitigation.validateSurveyRequests(surveyRequestsAfterMitigation);
            expectedFilesAfterMitigation.validateFilesMatch(project.getId());
        }

        List<ResourceSurveyRequest> launchSurveyAndValidateResults(SurveyMapping currentMapping) {
            final int expectedNumResources = currentMapping.expectedNumResources();
            final List<Integer> surveyIds = mainInterface().launchSurveyRequests(project, initialSurveyDateFilter);
            final List<ResourceSurveyRequest> surveyRequests = waitForNoActiveResourceActionInProject(project, expectedNumResources);
            final Set<Integer> queriedRequestIds = surveyRequests
                    .stream()
                    .map(ResourceSurveyRequest::getId)
                    .collect(Collectors.toSet());
            for (Integer newSurveyId : surveyIds) {
                assertTrue(queriedRequestIds.contains(newSurveyId));
            } // there may be other previous requests, hence we check that the newly launched ids are a subset
            currentMapping.validateSurveyRequests(surveyRequests);
            return surveyRequests;
        }
    }

    private class SurveyMapping {
        private final Map<ImagingSession, Set<ResourceSurveyRequest>> asMap;

        SurveyMapping() {
            asMap = new HashMap<>();
        }

        SurveyMapping add(ImagingSession session, ResourceSurveyRequest... surveyRequests) {
            final Set<ResourceSurveyRequest> surveySet = asMap.computeIfAbsent(session, key -> new HashSet<>());
            surveySet.addAll(Sets.newHashSet(surveyRequests));
            return this;
        }

        int expectedNumResources() {
            return asMap.values().stream().map(Set::size).reduce(0, Integer::sum);
        }

        private void validateSurveyRequests(List<ResourceSurveyRequest> actualSurveys) {
            int surveysProcessed = 0;
            for (Map.Entry<ImagingSession, Set<ResourceSurveyRequest>> surveyEntry : asMap.entrySet()) {
                final ImagingSession session = surveyEntry.getKey();
                for (ResourceSurveyRequest expectedSurvey : surveyEntry.getValue()) {
                    expectedSurvey.setProjectId(session.getPrimaryProject().getId());
                    expectedSurvey.setSubjectId(session.getSubject().getAccessionNumber());
                    expectedSurvey.setExperimentId(session.getAccessionNumber());
                    expectedSurvey.setXsiType(session.getDataType().getXsiType());
                    expectedSurvey.setSubjectLabel(session.getSubject().getLabel());
                    expectedSurvey.setExperimentLabel(session.getLabel());
                    expectedSurvey.setRequester(mainUser.getUsername());

                    final ResourceSurveyRequest actualSurvey = actualSurveys
                            .stream()
                            .filter(survey -> survey.getExperimentId().equals(expectedSurvey.getExperimentId())
                                    && survey.getScanLabel().equals(expectedSurvey.getScanLabel())
                                    && survey.getResourceLabel().equals(expectedSurvey.getResourceLabel())
                                    && survey.getRsnStatus().equals(expectedSurvey.getRsnStatus()))
                            .findAny()
                            .orElseThrow(RuntimeException::new);

                    assertEquals(expectedSurvey.getRsnStatus(), actualSurvey.getRsnStatus());
                    assertEquals(expectedSurvey.getProjectId(), actualSurvey.getProjectId());
                    assertEquals(expectedSurvey.getSubjectId(), actualSurvey.getSubjectId());
                    assertEquals(expectedSurvey.getXsiType(), actualSurvey.getXsiType());
                    assertEquals(expectedSurvey.getResourceLabel(), actualSurvey.getResourceLabel());
                    assertEquals(buildPathPopulator(session, actualSurvey).apply(expectedSurvey.getResourceUri()), actualSurvey.getResourceUri());
                    assertEquals(expectedSurvey.getSubjectLabel(), actualSurvey.getSubjectLabel());
                    assertEquals(expectedSurvey.getExperimentLabel(), actualSurvey.getExperimentLabel());
                    assertEquals(expectedSurvey.getScanDescription(), actualSurvey.getScanDescription());
                    assertEquals(expectedSurvey.getRequester(), actualSurvey.getRequester());

                    // validate survey report
                    final ResourceSurveyReport expectedReport = expectedSurvey.getSurveyReport();
                    final ResourceSurveyReport actualReport = actualSurvey.getSurveyReport();
                    assertEquals(actualSurvey.getId(), actualReport.getResourceSurveyRequestId());
                    assertEquals(expectedReport.getTotalEntries(), actualReport.getTotalEntries());
                    assertEquals(expectedReport.getTotalUids(), actualReport.getTotalUids());
                    assertEquals(expectedReport.getTotalBadFiles(), actualReport.getTotalBadFiles());
                    assertEquals(expectedReport.getTotalMismatchedFiles(), actualReport.getTotalMismatchedFiles());
                    assertEquals(expectedReport.getTotalDuplicates(), actualReport.getTotalDuplicates());
                    // TODO: how to handle new field "totalFilesInDuplicates"?
                    assertEquals(expectedReport.getTotalNonActionableDuplicates(), actualReport.getTotalNonActionableDuplicates());
                    assertEquals(expectedReport.getTotalFilesInNonActionableDuplicates(), actualReport.getTotalFilesInNonActionableDuplicates());

                    if (expectedReport.getUids() == null) {
                        assertNull(actualReport.getUids());
                    } else {
                        assertEquals(expectedReport.getUids().keySet(), actualReport.getUids().keySet());
                        for (Map.Entry<String, List<String>> expectedEntry : expectedReport.getUids().entrySet()) {
                            final String sopClassUid = expectedEntry.getKey();
                            assertEquals(
                                    Sets.newHashSet(expectedEntry.getValue()),
                                    Sets.newHashSet(actualReport.getUids().get(sopClassUid))
                            );
                        }
                    }

                    if (expectedReport.getBadFiles() == null) {
                        assertNull(actualReport.getBadFiles());
                    } else {
                        assertEquals(
                                populatePossiblePlaceholders(expectedReport.getBadFiles(), session, actualSurvey),
                                genericizeFileList(actualReport.getBadFiles())
                        );
                    }

                    if (expectedReport.getMismatchedFiles() == null) {
                        assertNull(actualReport.getMismatchedFiles());
                    } else {
                        assertEquals(
                                populatePossiblePlaceholders(expectedReport.getMismatchedFiles(), session, actualSurvey),
                                fileMapToStringMap(actualReport.getMismatchedFiles())
                        );
                    }

                    validateDuplicates(expectedReport.getDuplicates(), actualReport.getDuplicates(), session, actualSurvey);
                    validateDuplicates(expectedReport.getNonActionableDuplicates(), actualReport.getNonActionableDuplicates(), session, actualSurvey);

                    // validate mitigation report
                    final ResourceMitigationReport expectedMitigation = expectedSurvey.getMitigationReport();
                    final ResourceMitigationReport actualMitigation = actualSurvey.getMitigationReport();
                    if (expectedMitigation == null) {
                        assertNull(actualMitigation);
                    } else {
                        assertEquals(actualSurvey.getId(), actualMitigation.getResourceSurveyRequestId());

                        final Map<File, File> expectedMovedFiles = expectedMitigation.getMovedFiles();
                        final Map<File, File> actualMovedFiles = actualMitigation.getMovedFiles();
                        if (expectedMovedFiles == null) {
                            assertNull(actualMovedFiles);
                        } else {
                            assertEquals(
                                    populatePossiblePlaceholders(expectedMovedFiles, session, actualSurvey),
                                    coerceToStringMap(actualMovedFiles)
                            );
                        }

                        final Map<File, File> expectedRemovedFiles = expectedMitigation.getRemovedFiles();
                        final Map<File, File> actualRemovedFiles = actualMitigation.getRemovedFiles();
                        if (expectedRemovedFiles == null) {
                            assertNull(actualRemovedFiles);
                        } else {
                            assertEquals(
                                    populatePossiblePlaceholders(expectedRemovedFiles, session, actualSurvey),
                                    coerceToStringMap(actualRemovedFiles)
                            );
                        }

                        final List<File> expectedRetainedFiles = expectedMitigation.getRetainedFiles();
                        final List<File> actualRetainedFiles = actualMitigation.getRetainedFiles();
                        if (expectedRetainedFiles == null) {
                            assertNull(actualRetainedFiles);
                        } else {
                            assertEquals(
                                    populatePossiblePlaceholders(expectedRetainedFiles, session, actualSurvey),
                                    genericizeFileList(actualRetainedFiles)
                            );
                        }

                        assertEquals(expectedMitigation.getTotalMovedFiles(), actualMitigation.getTotalMovedFiles());
                        assertEquals(expectedMitigation.getTotalRemovedFiles(), actualMitigation.getTotalRemovedFiles());
                        assertEquals(expectedMitigation.getTotalFileErrors(), actualMitigation.getTotalFileErrors());

                        if (actualMitigation.getBackupErrors() != null) {
                            fail("Unexpected backupErrors");
                        }
                        if (actualMitigation.getMoveErrors() != null) {
                            fail("Unexpected moveErrors");
                        }
                        if (actualMitigation.getDeleteErrors() != null) {
                            fail("Unexpected deleteErrors");
                        }
                        if (actualMitigation.getCatalogWriteError() != null) {
                            fail("Unexpected catalogWriteError");
                        }
                        if (actualMitigation.getResourceSaveError() != null) {
                            fail("Unexpected resourceSaveError");
                        }
                    }
                    surveysProcessed++;
                }
            }
            assertEquals(surveysProcessed, actualSurveys.size());
        }

        // valid for nonactionable as well
        private void validateDuplicates(Map<String, Map<String, Map<File, String>>> expected, Map<String, Map<String, Map<File, String>>> actual, ImagingSession session, ResourceSurveyRequest actualSurvey) {
            if (expected == null) {
                assertNull(actual);
            } else {
                assertEquals(expected.keySet(), actual.keySet()); // SOP Class UIDs match
                for (Map.Entry<String, Map<String, Map<File, String>>> expectedEntry : expected.entrySet()) {
                    final String sopClassUid = expectedEntry.getKey();
                    final Map<String, Map<File, String>> expectedSopInstanceMap = expectedEntry.getValue();
                    final Map<String, Map<File, String>> actualSopInstanceMap = actual.get(sopClassUid);
                    assertEquals(expectedSopInstanceMap.keySet(), actualSopInstanceMap.keySet()); // SOP Instance UIDs for SOP Class UID match
                    for (Map.Entry<String, Map<File, String>> expectedFileMapEntry : expectedSopInstanceMap.entrySet()) {
                        final String sopInstanceUid = expectedFileMapEntry.getKey();
                        final Map<File, String> expectedFileMap = expectedFileMapEntry.getValue();
                        final Map<File, String> actualFileMap = actualSopInstanceMap.get(sopInstanceUid);
                        assertEquals(
                                populatePossiblePlaceholders(expectedFileMap, session, actualSurvey),
                                coerceToStringMap(actualFileMap)
                        );
                    }
                }
            }
        }

        private SurveyMapping merge(SurveyMapping other) {
            for (Map.Entry<ImagingSession, Set<ResourceSurveyRequest>> otherMapEntry : other.asMap.entrySet()) {
                add(otherMapEntry.getKey(), otherMapEntry.getValue().toArray(new ResourceSurveyRequest[0]));
            }
            return this;
        }
    }

    private class SessionFileMapping {
        private final Map<ImagingSession, String> fileMap;

        SessionFileMapping() {
            fileMap = new HashMap<>();
        }

        SessionFileMapping add(ImagingSession session, String fileSpecName) {
            fileMap.put(session, fileSpecName);
            return this;
        }

        private void validateFilesMatch(String projectId) {
            final Runnable checkFiles = () -> {
                final Project freshProject = mainInterface().readProject(projectId);
                for (Map.Entry<ImagingSession, String> sessionFileEntry : fileMap.entrySet()) {
                    final ImagingSession session = sessionFileEntry.getKey();
                    final String fileSpec = sessionFileEntry.getValue();
                    validateFilesInScansMatchExpectedNames(fileSpec, freshProject.findSession(session.getLabel()).getScans());
                }
            };
            checkFiles.run();
            for (ImagingSession session : fileMap.keySet()) {
                mainInterface().refreshCatalog(session);
            }
            checkFiles.run();
        }
    }

    private List<ResourceSurveyRequest> waitForNoActiveResourceActionInProject(Project project, int expectedNumSurveys, User authenticatingUser) {
        final List<ResourceSurveyRequest> surveys = new ArrayList<>();
        await().atMost(15, TimeUnit.SECONDS).until(() -> {
            surveys.clear();
            surveys.addAll(interfaceFor(authenticatingUser).readResourceSurveysForProject(project));
            return surveys.size() == expectedNumSurveys && surveys.stream().allMatch(survey -> NO_RESOURCE_ACTION_ACTIVE.contains(survey.getRsnStatus()));
        });
        return surveys;
    }
    private List<ResourceSurveyRequest> waitForNoActiveResourceActionInProject(Project project, int expectedNumSurveys) {
        return waitForNoActiveResourceActionInProject(project, expectedNumSurveys, mainUser);
    }

        private Set<String> genericizeFileList(List<File> files) {
        return files.stream().map(this::coerceToString).collect(Collectors.toSet());
    }

    private Map<String, String> coerceToStringMap(Map<?, ?> inputMap) {
        return inputMap
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                entry -> coerceToString(entry.getKey()),
                                entry -> coerceToString(entry.getValue())
                        )
                );
    }

    private Map<String, String> fileMapToStringMap(Map<File, String> fileMap) {
        return fileMap.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().getPath(), Map.Entry::getValue));
    }

    private Map<String, String> populatePossiblePlaceholders(Map<?, ?> fileMap, ImagingSession session, ResourceSurveyRequest surveyRequest) {
        final Function<Object, String> populator = buildPathPopulator(session, surveyRequest);
        return fileMap
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                entry -> populator.apply(entry.getKey()),
                                entry -> populator.apply(entry.getValue())
                        )
                );
    }

    private Set<String> populatePossiblePlaceholders(List<?> files, ImagingSession session, ResourceSurveyRequest surveyRequest) {
        final Function<Object, String> populator = buildPathPopulator(session, surveyRequest);
        return files
                .stream()
                .map(populator)
                .collect(Collectors.toSet());
    }

    private Function<Object, String> buildPathPopulator(ImagingSession session, ResourceSurveyRequest surveyRequest) {
        return (input) -> coerceToString(input)
                .replace("$ARCHIVE", archivePath)
                .replace("$PROJECT", session.getPrimaryProject().getId())
                .replace("$SESSION_LABEL", session.getLabel())
                .replace("$SESSION_ID", session.getAccessionNumber())
                .replace("$CACHE", cachePath)
                .replace("$TIMESTAMP", surveyRequest.getRequestTime())
                .replace("$SCAN_PK", String.valueOf(surveyRequest.getScanId()));
    }

    private String coerceToString(Object input) {
        if (input instanceof String) {
            return (String) input;
        } else if (input instanceof File) {
            return ((File) input).getPath();
        } else {
            throw new RuntimeException("Only works for String or File");
        }
    }

    private ResourceSurveyRequest readSurveyRequest(String name) {
        try {
            return XnatInterface.XNAT_REST_MAPPER.readValue(getDataFile(Paths.get("file_mitigation", "survey", name)), ResourceSurveyRequest.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ResourceSurveyRequest readSurveyWithMitigation(String name) {
        try {
            return XnatInterface.XNAT_REST_MAPPER.readValue(getDataFile(Paths.get("file_mitigation", "survey_with_mitigation", name)), ResourceSurveyRequest.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Runnable cstoreAndArchive(LocallyCacheableDicomTransformation dicomTransformation, Project project) {
        return cstoreAndArchive(dicomTransformation.baseLevelDir().toFile(), project);
    }

    private Runnable cstoreAndArchive(File dir, Project project) {
        return () -> {
            new XnatCStore().data(dir).sendDICOMToProject(project);
            final SessionData prearcSession = mainInterface().expectSinglePrearchiveResultForProject(project);
            mainInterface().rebuildSession(prearcSession, false);
            mainInterface().archiveSession(prearcSession, new ArchiveParams().delete());
        };
    }

    private Runnable overwriteDicomWithCat(Project project, Subject subject, ImagingSession mrSession) {
        return () -> {
            final MRScan series6 = new MRScan(mrSession, "6");
            final ScanResource resource = new ScanResource(project, subject, mrSession, series6, DICOM);
            final ResourceFile resourceFile = new ResourceFile(resource, "1.3.12.2.1107.5.2.32.35177.30000006121218324675000000034-6-80-ncojqu.dcm").fileFormat(DICOM).overwrite(true);
            new SimpleResourceFileExtension(resourceFile, getDataFile("louie.jpg")).uploadTo(mainInterface(), resource);
            mainInterface().refreshCatalog(mrSession);
        };
    }

    private static Function<List<Attributes>, List<Attributes>> sample1MidSubsetForSeries(int seriesNum) {
        return datasetWithFmis -> LocalTestDicom.SAMPLE1_MIDDLEISH_INSTANCES
                .apply(datasetWithFmis)
                .stream()
                .filter(instance -> instance.getInt(Tag.SeriesNumber, -1) == seriesNum)
                .collect(Collectors.toList());
    }

    private static LocallyCacheableDicomTransformation sample1MultipleIssues() {
        return new LocallyCacheableDicomTransformation(SAMPLE1_MULTIPLE_ISSUES_ID)
                .data(TestData.SAMPLE_1)
                .createZip()
                .transformations(
                        new DicomTransformation("series4")
                                .prefilter(sample1MidSubsetForSeries(4)),
                        new DicomTransformation(SERIES_5_TRANSFORM)
                                .prefilter(sample1MidSubsetForSeries(5))
                                .dicomFileWriter(new OffsetIndexDicomWriter(3)), // hack to make numbers match existing JSON
                        new DicomTransformation("series6")
                                .prefilter(sample1MidSubsetForSeries(6))
                                .dicomFileWriter(new XnatDefaultTemplatizedNamerWriter())
                );
    }

}
