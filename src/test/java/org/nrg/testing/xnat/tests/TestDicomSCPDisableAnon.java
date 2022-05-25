package org.nrg.testing.xnat.tests;

import org.apache.log4j.Logger;
import org.dcm4che2.data.Tag;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.ProjectScript;
import org.nrg.testing.dicom.SiteScript;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.testing.xnat.conf.Settings;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.dicom.DicomObjectIdentifier;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.nrg.testing.TimeUtils;

import java.io.File;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.nrg.xnat.enums.DicomEditVersion.DE_6;

@AddedIn(Xnat_1_8_5.class)
@TestRequires(admin = true, data = TestData.ANON_2)
public class TestDicomSCPDisableAnon extends BaseXnatRestTest {
    private static final Logger LOG            = Logger.getLogger(TestDicomSCPDisableAnon.class);
    private static final long   FIVE_MINUTES   = 300000;
    private static final long   THIRTY_SECONDS = 30000;

    private final AnonScript      siteScript = XnatObjectUtils.anonScriptFromFile(DE_6, "siteAnon.das");
    private final AnonScript      projScript = XnatObjectUtils.anonScriptFromFile(DE_6, "projectAnon.das");
    private final Project         project    = new Project();
    private final Subject         subject    = new Subject(project, "Sample_Patient");
    private final ImagingSession  session    = new MRSession(project, subject, "Sample_ID");

    private final String patientComments
                    = String.format("Project:%s Subject:%s Session:%s AA:true",
                                    project.getId(), subject.getLabel(), session.getLabel());

    private final DicomScpReceiver receiver
                    = new DicomScpReceiver().aeTitle(RandomHelper.randomID())
                                            .host(Settings.DICOM_HOST)
                                            .port(Settings.DICOM_PORT)
                                            .identifier(DicomObjectIdentifier.DEFAULT.getId())
                                            .enabled(true)
                                            .customProcessing(false)
                                            .directArchive(false)
                                            .anonymizationEnabled(true)
                                            .whitelistEnabled(false);

    @BeforeClass
    public void setup() {
        mainInterface().createProject(project);
        mainInterface().setProjectAnonScript(project, projScript);

        mainAdminInterface().enableSiteAnonScript();
        mainAdminInterface().setSiteAnonScript(siteScript);

        mainAdminInterface().createDicomScpReceiver(receiver);
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 10000);
    }

    @BeforeMethod(alwaysRun = true)
    @AfterClass(alwaysRun = true)
    private void clearPrearchive(){
        try {
            restDriver.clearPrearchiveSessions(mainUser, project);
        } catch (Throwable throwable) {
            LOG.warn(throwable);
        }
    }

    @BeforeMethod(alwaysRun = true)
    private void clearArchive(){
        try {
            mainInterface().deleteAllProjectData(project);
            TimeUtils.sleep(1000);
        } catch (Throwable throwable) {
            LOG.warn(throwable);
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        mainAdminInterface().deleteProject(project);
        mainAdminInterface().deleteDicomScpReceiver(receiver);
    }

    @Test
    public void testAnonymizationEnabled() {
        mainAdminInterface().updateDicomScpReceiver(receiver.anonymizationEnabled(true));
        new XnatCStore(receiver).overwrittenHeaders(Collections.singletonMap(Tag.PatientComments, patientComments))
                                .data(TestData.ANON_2)
                                .sendDICOM();

        final List<SessionData> prearcSessions = mainInterface().getPrearchiveEntriesForProject(project);
        assertEquals(1, prearcSessions.size());
        waitForAutoArchive();

        final List<File> files = downloadResourceFiles(session);
        new SiteScript().validateScriptRan(files);
        new ProjectScript().validateScriptRan(files);
    }

    @Test
    public void testAnonymizationDisabled() {
        mainAdminInterface().updateDicomScpReceiver(receiver.anonymizationEnabled(false));
        new XnatCStore(receiver).overwrittenHeaders(Collections.singletonMap(Tag.PatientComments, patientComments))
                                .data(TestData.ANON_2)
                                .sendDICOM();

        final List<SessionData> prearcSessions = mainInterface().getPrearchiveEntriesForProject(project);
        assertEquals(1, prearcSessions.size());
        waitForAutoArchive();

        final List<File> files = downloadResourceFiles(session);
        new SiteScript().validateScriptDidntRun(files);
        new ProjectScript().validateScriptDidntRun(files);
    }

    @Test
    public void testDirectArchiveWithAnonymizationEnabled() {
        mainAdminInterface().updateDicomScpReceiver(receiver.anonymizationEnabled(true).directArchive(true));
        new XnatCStore(receiver).overwrittenHeaders(Collections.singletonMap(Tag.PatientComments, patientComments))
                .data(TestData.ANON_2)
                .sendDICOM();

        waitForDirectArchive(session);
        final List<File> files = downloadResourceFiles(session);
        new SiteScript().validateScriptRan(files);
        new ProjectScript().validateScriptRan(files);
    }

    @Test
    public void testDirectArchiveWithAnonymizationDisabled() {
        mainAdminInterface().updateDicomScpReceiver(receiver.anonymizationEnabled(false).directArchive(true));
        new XnatCStore(receiver).overwrittenHeaders(Collections.singletonMap(Tag.PatientComments, patientComments))
                .data(TestData.ANON_2)
                .sendDICOM();

        waitForDirectArchive(session);
        final List<File> files = downloadResourceFiles(session);
        new SiteScript().validateScriptDidntRun(files);
        new ProjectScript().validateScriptDidntRun(files);
    }


    private void waitForAutoArchive(){
        final long startTime      = System.currentTimeMillis();
        while((System.currentTimeMillis() - startTime) < FIVE_MINUTES ) {
            if(mainInterface().getPrearchiveEntriesForProject(project).size() == 0){
                return;
            }
            LOG.debug("Session not in archive. Waiting 30 seconds.");
            TimeUtils.sleep(THIRTY_SECONDS);
        }
        throw new RuntimeException("Session never archived.");
    }

    private void waitForDirectArchive(ImagingSession session) {
        TimeUtils.sleep(60000);
        restDriver.waitForDirectArchiveEmpty(mainUser, project, 300);
        TimeUtils.sleep(1000); // sleep for 1s to accommodate a little gap between prearchive being empty and session being accessible
        mainQueryBase().get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);
    }

    private List<File> downloadResourceFiles(ImagingSession session) {
        return restDriver.downloadAllDicomFromSession(mainUser, project, session.getSubject(), session);
    }

}
