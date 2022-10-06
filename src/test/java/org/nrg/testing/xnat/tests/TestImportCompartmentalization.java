package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.importer.importers.DefaultImporterRequest;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.dicom.DicomObjectIdentifier;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.versions.Xnat_1_8_6;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.nrg.testing.TestGroups.*;
import static org.testng.AssertJUnit.assertEquals;

@TestRequires(data = TestData.SAMPLE_1)
@Test(groups = {ARCHIVE, DICOM_ROUTING, DICOM_SCP, IMPORTER})
@AddedIn(Xnat_1_8_6.class)
public class TestImportCompartmentalization extends BaseXnatRestTest {

    private static final String EXPECTED_SUBJECT_LABEL = "Sample_Patient";
    private static final String EXPECTED_SESSION_LABEL = "Sample_ID";
    private static final String ROUTE_ON_STUDY_INSTANCE_UID = "(0020,000d):(.*)";
    private final List<DicomScpReceiver> existingReceivers = new ArrayList<>();
    private final DicomScpReceiver receiverWithCustomRouting = new DicomScpReceiver()
            .host(Settings.DICOM_HOST)
            .port(Settings.DICOM_PORT)
            .aeTitle("XNAT")
            .identifier(DicomObjectIdentifier.DEFAULT)
            .enabled(true)
            .customProcessing(false)
            .directArchive(false)
            .anonymizationEnabled(true)
            .whitelistEnabled(false)
            .routingExpressionsEnabled(true)
            .projectRoutingExpression(ROUTE_ON_STUDY_INSTANCE_UID)
            .subjectRoutingExpression(ROUTE_ON_STUDY_INSTANCE_UID)
            .sessionRoutingExpression(ROUTE_ON_STUDY_INSTANCE_UID);

    private final Consumer<Project> uploadViaDicomZip = project -> {
        final String uri = mainInterface().callImporter(
                new DicomZipRequest()
                        .file(TestData.SAMPLE_1.toFile())
                        .destPrearchive()
                        .project(project)
        ); // don't specify subject/session metadata to see how it gets routed
        mainQueryBase().queryParam("action", "commit").post(formatXnatUrl(uri))
                .then().assertThat().statusCode(Matchers.oneOf(200, 301)); // TODO: Move this and other usages to a Subinterface
    };

    private final Consumer<Project> uploadViaSessionImporter = project -> {
        mainInterface().callImporter(
                new DefaultImporterRequest()
                        .file(TestData.SAMPLE_1.toFile())
                        .destArchive()
                        .project(project)
        ); // don't specify subject/session metadata to see how it gets routed
    };

    @BeforeClass
    private void cacheReceiversAndDisableCustomRouting() {
        existingReceivers.addAll(mainAdminInterface().readAllDicomScpReceivers());
        mainAdminInterface().disableProjectDicomRoutingConfig();
        mainAdminInterface().disableSubjectDicomRoutingConfig();
        mainAdminInterface().disableSessionDicomRoutingConfig();
    }

    @BeforeMethod
    private void clearScpReceivers() {
        for (DicomScpReceiver definedReceiver : mainAdminInterface().readAllDicomScpReceivers()) {
            mainAdminInterface().deleteDicomScpReceiver(definedReceiver);
        }
    }

    @AfterClass(alwaysRun = true)
    private void restoreReceivers() {
        clearScpReceivers();
        for (DicomScpReceiver receiver : existingReceivers) {
            receiver.setId(null);
            mainAdminInterface().createDicomScpReceiver(receiver);
        }
    }

    public void testDicomZipPerReceiverRoutingLeak() {
        runRoutingLeakTest(true, uploadViaDicomZip);
    }

    public void testSessionImporterPerReceiverRoutingLeak() {
        runRoutingLeakTest(true, uploadViaSessionImporter);
    }

    public void testDicomZipPerReceiverRoutingLeakMissingReceivers() {
        runRoutingLeakTest(false, uploadViaDicomZip);
    }

    public void testSessionImporterPerReceiverRoutingLeakMissingReceivers() {
        runRoutingLeakTest(false, uploadViaSessionImporter);
    }

    private void runRoutingLeakTest(boolean defineReceiver, Consumer<Project> uploadFunction) {
        if (defineReceiver) {
            mainAdminInterface().createDicomScpReceiver(receiverWithCustomRouting);
        }
        final Project project = registerTempProject();
        mainInterface().createProject(project);
        uploadFunction.accept(project);
        final Subject foundSubject = mainInterface().readProject(project.getId()).getSubjects().get(0);
        assertEquals(EXPECTED_SUBJECT_LABEL, foundSubject.getLabel());
        final ImagingSession foundSession = foundSubject.getSessions().get(0);
        assertEquals(EXPECTED_SESSION_LABEL, foundSession.getLabel());
    }

}
