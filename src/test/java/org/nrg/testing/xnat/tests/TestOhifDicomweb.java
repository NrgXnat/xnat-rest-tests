package org.nrg.testing.xnat.tests;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.transform.DicomFilters;
import org.nrg.testing.dicom.transform.DicomTransformation;
import org.nrg.testing.dicom.transform.LocallyCacheableDicomTransformation;
import org.nrg.testing.dicom.transform.TransformFunction;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.XnatConnectionConfig;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.DataAccessLevel;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Share;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.users.CustomUserGroup;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.nrg.testing.TestGroups.PERMISSIONS;

@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = PluginRegistry.OHIF_VIEWER_ID, minimumSupportedVersion = "3.7"))
@Test(groups = {PERMISSIONS})
public class TestOhifDicomweb extends BaseXnatRestTest {

    private static final String STUDY_INSTANCE_UID = "2.25.182639788860844868733118791291388673718";
    private static final String SERIES_INSTANCE_UID = "2.25.333677886806301450316021192217822717633";
    private static final String SOP_INSTANCE_UID = "2.25.95832577551808249790848662129083683893";
    private static final String DATA_ID = "fake_microscopy_data";
    private static final LocallyCacheableDicomTransformation FAKE_MICROSCOPY_DATA = new LocallyCacheableDicomTransformation(DATA_ID)
            .data(TestData.SAMPLE_1)
            .createZip()
            .transformations(
                    new DicomTransformation(DATA_ID)
                            .produceZip()
                            .prefilter(DicomFilters.subsetWithSeriesAndInstanceNumbers(4, 100))
                            .transformFunction(
                                    TransformFunction.simple((dicom) -> {
                                        dicom.setString(Tag.SOPClassUID, VR.UI, UID.VLWholeSlideMicroscopyImageStorage);
                                        dicom.setString(Tag.StudyInstanceUID, VR.UI, STUDY_INSTANCE_UID);
                                        dicom.setString(Tag.SeriesInstanceUID, VR.UI, SERIES_INSTANCE_UID);
                                        dicom.setString(Tag.SOPInstanceUID, VR.UI, SOP_INSTANCE_UID);
                                        dicom.setString(Tag.Modality, VR.CS, "SM");
                                    })
                            )
            ).build();

    private User testUser;
    private XnatInterface nonadminInterface;

    @BeforeClass(groups = {PERMISSIONS})
    private void setupUserAndData() {
        testUser = createGenericUsers(1).get(0);
        nonadminInterface = interfaceFor(testUser);
        mainAdminInterface().setupDataType(DataType.SM_SESSION);
    }

    public void testViewerDicomWebSecuritySite() {
        expect403(() -> nonadminInterface.viewerDicomwebGenerateData(false));
        expect403(() -> nonadminInterface.viewerDicomwebGenerateData(true));
    }

    public void testViewerDicomWebSecurityPublicProject() {
        final Project publicProject = new Project().accessibility(Accessibility.PUBLIC);
        final ImagingSession session = new ImagingSession(publicProject, new Subject(publicProject));
        new ViewerSecurityTest(session)
                .withReadAccess()
                .run();
    }

    public void testViewerDicomWebSecurityProtectedProject() {
        final Project protectedProject = new Project().accessibility(Accessibility.PROTECTED);
        final ImagingSession session = new ImagingSession(protectedProject, new Subject(protectedProject));
        new ViewerSecurityTest(session).run();
    }

    public void testViewerDicomWebSecurityPrivateProject() {
        final Project privateProject = new Project().accessibility(Accessibility.PRIVATE);
        final ImagingSession session = new ImagingSession(privateProject, new Subject(privateProject));
        new ViewerSecurityTest(session).run();
    }

    public void testViewerDicomWebSecurityOwnerProject() {
        final Project ownerProject = new Project().addOwner(testUser);
        final ImagingSession session = new ImagingSession(ownerProject, new Subject(ownerProject));
        new ViewerSecurityTest(session)
                .withFullEditAccess()
                .run();
    }

    public void testViewerDicomWebSecurityMemberProject() {
        final Project memberProject = new Project().addMember(testUser);
        final ImagingSession session = new ImagingSession(memberProject, new Subject(memberProject));
        new ViewerSecurityTest(session)
                .withFullEditAccess()
                .run();
    }

    public void testViewerDicomWebSecurityCollaboratorProject() {
        final Project collaboratorProject = new Project().addCollaborator(testUser);
        final ImagingSession session = new ImagingSession(collaboratorProject, new Subject(collaboratorProject));
        new ViewerSecurityTest(session)
                .withReadAccess()
                .run();
    }

    public void testViewerDicomWebSecurityCustomGroupWithoutAccessProject() {
        final CustomUserGroup customUserGroup = new CustomUserGroup("customgroup");
        final Project project = new Project().addUserGroup(customUserGroup, Collections.singletonList(testUser));
        final ImagingSession session = new ImagingSession(project, new Subject(project));
        new ViewerSecurityTest(session).run();
    }

    public void testViewerDicomWebSecurityCustomGroupWithAccessProject() {
        final CustomUserGroup customUserGroup = new CustomUserGroup("customgroup");
        customUserGroup.permission(DataType.SM_SESSION, DataAccessLevel.ALL);
        final Project project = new Project().addUserGroup(customUserGroup, Collections.singletonList(testUser));
        final ImagingSession session = new ImagingSession(project, new Subject(project));
        new ViewerSecurityTest(session)
                .withSessionEditAccess()
                .run();
    }

    @TestRequires(openXnat = true)
    public void testViewerDicomWebSecurityGuestOnPublicProject() {
        final Project publicProject = new Project().accessibility(Accessibility.PUBLIC);
        final ImagingSession session = new ImagingSession(publicProject, new Subject(publicProject));
        new ViewerSecurityTest(session)
                .withReadAccess()
                .asGuest()
                .run();
    }

    public void testViewerDicomWebSecuritySharedIntoOwnerProject() {
        final Project project = new Project().accessibility(Accessibility.PRIVATE).addOwner(testUser);
        final Project otherProject = new Project().accessibility(Accessibility.PRIVATE);
        final ImagingSession session = new ImagingSession(otherProject, new Subject(otherProject));
        new ViewerSecurityTest(project, session)
                .withReadAccess()
                .run();
    }

    private class ViewerSecurityTest {
        private final Project project;
        private final Subject subject;
        private final ImagingSession session;
        private boolean readAccess = false;
        private boolean fullEditAccess = false;
        private boolean sessionEditAccess = false;
        private boolean isGuest = false;
        private XnatInterface xnatInterface = nonadminInterface;

        ViewerSecurityTest(ImagingSession session) {
            this.session = session;
            subject = session.getSubject();
            project = subject.getProject();
            new SessionImportExtension(session, FAKE_MICROSCOPY_DATA.locateOverallZip().toFile());
            mainAdminInterface().createProject(project);
        }

        ViewerSecurityTest(Project testingProject, ImagingSession sessionToShare) {
            session = sessionToShare;
            subject = sessionToShare.getSubject();
            project = testingProject;
            new SessionImportExtension(session, FAKE_MICROSCOPY_DATA.locateOverallZip().toFile());
            subject.addShare(new Share(project));
            session.addShare(new Share(project));
            mainAdminInterface().createProject(project);
            mainAdminInterface().createProject(session.getPrimaryProject());
        }

        ViewerSecurityTest withReadAccess() {
            readAccess = true;
            return this;
        }

        ViewerSecurityTest withSessionEditAccess() {
            sessionEditAccess = true;
            return withReadAccess();
        }

        ViewerSecurityTest withFullEditAccess() {
            fullEditAccess = true;
            return withSessionEditAccess();
        }

        ViewerSecurityTest asGuest() {
            isGuest = true;
            final XnatConnectionConfig connectionConfig = new XnatConnectionConfig();
            connectionConfig.setSkipAuth(true);
            xnatInterface = XnatInterface.authenticateAsGuest(Settings.BASEURL, connectionConfig);
            return this;
        }

        void run() {
            final List<Runnable> testActionsRequiringReadAccess = Arrays.asList(
                    () -> xnatInterface.viewerDicomwebSearchInstances(project, session),
                    () -> xnatInterface.viewerDicomwebSearchInstances(project, session, STUDY_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebSearchInstances(project, session, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebSearchSeries(project, session),
                    () -> xnatInterface.viewerDicomwebSearchSeries(project, session, STUDY_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebSearchSeries(project, session, STUDY_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebSearchStudies(project, session),
                    () -> xnatInterface.viewerDicomwebRetrieveInstances(project, session, STUDY_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebRetrieveInstances(project, session, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebRetrieveInstances(project, session, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID, SOP_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebRetrieveMetadata(project, session, STUDY_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebRetrieveMetadata(project, session, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebRetrieveMetadata(project, session, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID, SOP_INSTANCE_UID),
                    () -> xnatInterface.viewerDicomwebRetrieveBulkdata(project, session, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID, SOP_INSTANCE_UID, Collections.singletonList("7FE00010")), // Pixel Data
                    () -> xnatInterface.viewerDicomwebRetrieveFrames(project, session, STUDY_INSTANCE_UID, SERIES_INSTANCE_UID, SOP_INSTANCE_UID, Collections.singletonList(1)),
                    () -> xnatInterface.viewerDicomwebCheckMetadataExists(project, session)
            );

            final List<Runnable> testActionsRequiringFullEditAccess = Arrays.asList(
                    () -> xnatInterface.viewerDicomwebGenerateDataForSubject(project, subject, true),
                    () -> xnatInterface.viewerDicomwebGenerateDataForSubject(project, subject, false)
            );

            final List<Runnable> testActionsRequiringSessionEditAccess = Arrays.asList(
                    () -> xnatInterface.viewerDicomwebGenerateDataForSession(project, session, true),
                    () -> xnatInterface.viewerDicomwebGenerateDataForSession(project, session, false)
            );

            final List<Runnable> testActionsRequiringAdminAccess = Arrays.asList(
                    () -> xnatInterface.viewerDicomwebGenerateDataForProject(project, true),
                    () -> xnatInterface.viewerDicomwebGenerateDataForProject(project, false),
                    () -> xnatInterface.viewerDicomwebDeleteMetadata(project, session)
            );

            for (Runnable testStep : testActionsRequiringReadAccess) {
                if (readAccess) {
                    testStep.run();
                } else {
                    expectStatusCode(testStep, 403, 422);
                }
            }

            for (Runnable testStep : testActionsRequiringSessionEditAccess) {
                if (sessionEditAccess) {
                    testStep.run();
                } else if (isGuest) {
                    expectStatusCode(testStep, 401);
                } else {
                    expectStatusCode(testStep, 403, 422);
                }
            }

            for (Runnable testStep : testActionsRequiringFullEditAccess) {
                if (fullEditAccess) {
                    testStep.run();
                } else if (isGuest) {
                    expectStatusCode(testStep, 401);
                } else {
                    expectStatusCode(testStep, 403, 422);
                }
            }

            for (Runnable testStep : testActionsRequiringAdminAccess) {
                if (isGuest) {
                    expectStatusCode(testStep, 401);
                } else {
                    expect403(testStep); // this class assumes non-admin
                }
            }
        }
    }

}
