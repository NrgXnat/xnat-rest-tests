package org.nrg.testing.xnat.tests;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.log4j.Logger;
import org.dcm4che2.data.Tag;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.XnatCStore;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.PrearchiveStatus;
import org.nrg.xnat.importer.importers.DicomZipRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.dicom.DicomScpReceiver;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.prearchive.SessionData;
import org.nrg.xnat.versions.Xnat_1_8_3;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@AddedIn(Xnat_1_8_3.class)
public class TestDirectArchive extends BaseXnatRestTest {
    private final Project project = new Project();
    private final Project altProject = new Project();
    private final File testZip = getDataFile("mr_1.zip");
    final Subject apiSubject = new Subject(project, "SPP_0x220790");
    final ImagingSession apiSession = new MRSession(project, apiSubject, "SPP_0x220790_MR2");
    final Subject scpSubject = new Subject(project, "Sample_Patient");
    final ImagingSession scpSession = new MRSession(project, scpSubject, "Sample_ID");
    DicomScpReceiver directArchiveReceiver;
    private static final Logger LOGGER = Logger.getLogger(TestDirectArchive.class);

    @BeforeClass
    public void setup() {
        mainInterface().createProject(project);
        mainAdminInterface().disableSiteAnonScript();
        mainAdminInterface().setSessionXmlRebuilderTimes(1, 10000);

        directArchiveReceiver = mainAdminInterface().getDefaultDicomSCPInstance();
        directArchiveReceiver.directArchive(true);
        mainAdminInterface().updateDefaultDicomSCPInstance(directArchiveReceiver);
    }

    @BeforeMethod(alwaysRun = true) // clear out prearchive/archive for each test
    @AfterClass(alwaysRun = true) // ... and then clear them out when we're all done
    public void clearArchives() {
        try {
            restDriver.clearPrearchiveSessions(mainUser, project);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }
        try {
            mainInterface().deleteAllProjectData(project);
            TimeUtils.sleep(1000);
        } catch (Throwable throwable) {
            LOGGER.warn(throwable);
        }

        mainAdminInterface().setSessionXmlRebuilderTimes(1, 10000);
        restDriver.deleteProjectSilently(mainAdminUser, altProject);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
        directArchiveReceiver.directArchive(false);
        mainAdminInterface().updateDefaultDicomSCPInstance(directArchiveReceiver);
    }

    @Test
    public void testDirectArchiveAPI() {
        mainInterface().callImporter(
                new DicomZipRequest().
                        directArchive().
                        project(project).
                        file(testZip)
        );

        waitForDirectArchive(apiSession);
    }

    @Test
    @TestRequires(data = {
            TestData.SAMPLE_1_SCAN_4
    })
    public void testDirectArchiveCStore() {
        uploadViaCStore();
        waitForDirectArchive(scpSession);
    }

    @Test
    public void testDirectArchiveConflict() {
        uploadViaCStore();
        waitForDirectArchive(scpSession);
        uploadViaCStore();

        // verify direct archive table empty
        restDriver.waitForDirectArchiveEmpty(mainUser, project, 1);

        // verify session in prearchive in conflict state
        final StopWatch stopWatch = TimeUtils.launchStopWatch();
        PrearchiveStatus status = null;
        do {
            TimeUtils.checkStopWatch(stopWatch, 300,
                    "Expected a prearchive conflict, but status is " + status);
            if (status != null) TimeUtils.sleep(1000);
            final List<SessionData> sessions = mainInterface().getPrearchiveEntriesForProjectWithSessionLabel(project, scpSession);
            assertEquals(1, sessions.size());
            status = sessions.get(0).getStatus();
        } while (status != PrearchiveStatus.CONFLICT);
    }

    @Test
    public void testDirectArchiveError() {
        // To force an archival error, set institution name to have 300 chars. XNAT allows 256 (string) for the db field,
        // but even this is actually invalid DICOM (LO is 64 chars). So, postgres won’t like it, but XNAT will happily
        // write it to xml. And XNAT would never be updated to support this bc it isn’t valid DICOM.
        final Map<Integer, String> hdr = new HashMap<>();
        hdr.put(Tag.InstitutionName, StringUtils.repeat("A", 300));
        uploadViaCStore(hdr, true);
        restDriver.waitForDirectArchiveEmpty(mainUser, project, 300);

        // verify session is in prearchive in error state
        final List<SessionData> sessions = mainInterface().getPrearchiveEntriesForProjectWithSessionLabel(project, scpSession);
        assertEquals(1, sessions.size());
        final SessionData session = sessions.get(0);
        assertEquals(PrearchiveStatus.ERROR, session.getStatus());

        // verify prearchive error message about direct archive failure
        final List<String> logs = mainInterface().getPrearchiveLogMessages(project, session);
        assertTrue(logs.stream().anyMatch(log -> log.contains("Attempt to direct-archive failed")));
    }

    @Test
    public void testDirectArchiveProjectFilter() {
        mainInterface().createProject(altProject);
        mainAdminInterface().setSessionXmlRebuilderTimes(5, 60000);
        uploadViaCStore();
        uploadViaCStore(Collections.singletonMap(Tag.StudyDescription, altProject.getId()), false);

        final List<SessionData> sessions = mainInterface().getDirectArchiveEntriesForProject(project);
        assertEquals(1, sessions.size());

        final List<SessionData> altSessions = mainInterface().getDirectArchiveEntriesForProject(altProject);
        assertEquals(1, altSessions.size());

        final List<SessionData> all = mainInterface().getDirectArchiveEntries();
        assertEquals(2, all.size()); // TODO: technically this can fail if other sessions are also in the direct-archive table
    }

    private void uploadViaCStore() {
        uploadViaCStore(new HashMap<>(), true);
    }

    private void uploadViaCStore(Map<Integer, String> hdr, boolean addProject) {
        if (addProject) {
            hdr.put(Tag.StudyDescription, project.getId());
        }
        new XnatCStore().data(TestData.SAMPLE_1_SCAN_4).overwrittenHeaders(hdr).sendDICOM();
    }

    private void waitForDirectArchive(ImagingSession session) {
        TimeUtils.sleep(60000);
        restDriver.waitForDirectArchiveEmpty(mainUser, project, 300);
        verifyImport(session);
    }

    private void verifyImport(SubjectAssessor session) {
        // if session can be retrieved at this URL, then project, subject, session are all labelled properly
        TimeUtils.sleep(1000); // sleep for 1s to accommodate a little gap between prearchive being empty and session being accessible
        mainQueryBase().get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);
    }
}
