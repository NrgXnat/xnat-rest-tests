package org.nrg.testing.xnat.tests;

import org.nrg.testing.TimeUtils;
import org.nrg.testing.UIDList;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.MergeBehavior;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.enums.PrearchiveStatus;
import org.nrg.xnat.importer.importers.DefaultImporterRequest;
import org.nrg.xnat.importer.importers.GradualDicomRequest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.SubjectAssessor;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.prearchive.PrearchiveResultFilter;
import org.nrg.xnat.prearchive.SessionData;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

import static org.nrg.testing.TestGroups.*;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = {IMPORTER, PREARCHIVE})
public class TestPrearchiveMgmt extends BaseXnatRestTest {

    private final File sessionZip = getDataFile("mr_1.zip");
    private final String subjectName = "SPP_0x220790";
    private final String sessionName = "SPP_0x220790_MR2";
    private final Project project1 = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final Project project2 = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final Project project3 = new Project().prearchiveCode(PrearchiveCode.MANUAL);
    private final Subject subject1 = new Subject(project1, "1");
    private final Subject subject2 = new Subject(project1, "2");
    private final Subject subject3 = new Subject(project1, "3");
    private final ImagingSession mr1_1 = new MRSession(project1, subject1, "MR11");
    private final ImagingSession mr1_2 = new MRSession(project1, subject1, "MR12");
    private final ImagingSession mr2_1 = new MRSession(project1, subject2, "MR21");
    private final ImagingSession mr3_1 = new MRSession(project1, subject3, "MR31");

    @BeforeClass
    private void addPrearchiveMgmtProjects() {
        mainInterface().createProject(project1);
        mainInterface().createProject(project2);
        mainInterface().createProject(project3);

        for (Subject subject : project1.getSubjects()) {
            for (SubjectAssessor session : subject.getExperiments()) {
                mainInterface().callImporter(
                        new DefaultImporterRequest().
                                triggerPipelines(false).
                                project(project1).
                                subject(subject).
                                session(session.getLabel()).
                                overwrite(MergeBehavior.APPEND).
                                file(sessionZip)
                );
            }
        }
    }

    @AfterClass(alwaysRun = true)
    private void removePrearchiveMgmtProjects() {
        for (Project project : new Project[]{project1, project2, project3}) {
            restDriver.deleteProjectSilently(mainUser, project);
        }
        mainAdminQueryBase().put(formatRestUrl("prearchive"));
    }

    @Test
    public void testGradualCommitUnassignedMove() {
        restDriver.clearUnassignedPrearchiveSessions(mainAdminUser, UIDList.uids);

        final String sessionUri = mainInterface().callImporter(
                new GradualDicomRequest().
                        destPrearchive().
                        file(getDataFile("mr_1/1.dcm"))
        );

        final String sessionUrl = formatXnatUrl(sessionUri);

        mainAdminQueryBase().queryParam("action", "commit").post(sessionUrl).then().assertThat().statusCode(200);

        mainAdminQueryBase().get(sessionUrl).then().assertThat().statusCode(200);

        mainAdminInterface().movePrearchiveSession(sessionUri, project3, false);

        TimeUtils.sleep(3000);

        final String newUri = getSessionPrearcUri(project3, subjectName, sessionName);

        mainAdminInterface().movePrearchiveSession(newUri, project2, false);

        TimeUtils.sleep(3000);

        final String finalUri = getSessionPrearcUri(project2, subjectName, sessionName);

        mainQueryBase().delete(formatRestUrl(finalUri)).then().assertThat().statusCode(200);
    }

    @Test
    public void testPrearchiveListing() {
        final List<SessionData> projectPrearc = mainInterface().getPrearchiveEntriesForProject(project1);
        for (Subject subject : project1.getSubjects()) {
            for (SubjectAssessor session : subject.getExperiments()) {
                final String sessionUri = new PrearchiveResultFilter().
                        subject(subject.getLabel()).
                        name(session.getLabel()).
                        status(PrearchiveStatus.READY).
                        findUniqueResult(projectPrearc).
                        getUrl();
                mainQueryBase().get(formatRestUrl(sessionUri)).then().assertThat().statusCode(200);
            }
        }
    }

    @Test
    public void testPrearchiveSessionDelete() {
        final String subject = "SUBJ1";
        final String session = "SUBJ1_MR1";

        assertSessionsInProjectPrearc(project2, 0);

        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        param("project", project2.getId()).
                        param("subject", subject).
                        session(session).
                        overwrite(MergeBehavior.APPEND).
                        file(sessionZip)
        );

        assertSessionsInProjectPrearc(project2, 1);

        final String uri = getSessionPrearcUri(project2, subject, session);

        mainQueryBase().delete(formatRestUrl(uri)).then().assertThat().statusCode(200);

        assertSessionsInProjectPrearc(project2, 0);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testPrearchiveWithDestination() {
        final String subject = "SUBJ9";
        final String session = "SUBJ9_MRI";

        final String destination = String.format("/prearchive/projects/%s/20000101_050505/11223344556677", project2);

        assertSessionsInProjectPrearc(project2, 0);

        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        dest(destination).
                        param("subject", subject).
                        session(session).
                        overwrite(MergeBehavior.APPEND).
                        file(sessionZip)
        );

        assertSessionsInProjectPrearc(project2, 1);

        mainQueryBase().delete(formatRestUrl(destination)).then().assertThat().statusCode(200);

        assertSessionsInProjectPrearc(project2, 0);
    }

    @Test
    public void testUnassignedMove() {
        final String subject = "subject1";
        final String session = sessionName;

        restDriver.clearUnassignedPrearchiveSessions(mainAdminUser, UIDList.uids);

        mainInterface().callImporter(
                new DefaultImporterRequest().
                        triggerPipelines(false).
                        destPrearchive().
                        param("subject", subject).
                        file(sessionZip)
        );

        final String uri = new PrearchiveResultFilter().
                subject(subject).
                name(session).
                status(PrearchiveStatus.READY).
                findUniqueResult(mainAdminInterface().getUnassignedPrearchiveEntries()).
                getUrl();

        mainAdminInterface().movePrearchiveSession(uri, project3);

        TimeUtils.sleep(3000);

        assertSessionsInProjectPrearc(project3, 1);

        final String newUri = getSessionPrearcUri(project3, subject, session);

        mainInterface().movePrearchiveSession(newUri, project2);

        TimeUtils.sleep(3000);

        assertSessionsInProjectPrearc(project3, 0);
        assertSessionsInProjectPrearc(project2, 1);

        final String finalUri = getSessionPrearcUri(project2, subject, session);
        mainQueryBase().delete(formatRestUrl(finalUri)).then().assertThat().statusCode(200);
    }

    private void assertSessionsInProjectPrearc(Project project, int numSessions) {
        assertEquals(numSessions, mainInterface().getPrearchiveEntryCountForProject(project));
    }

    private String getSessionPrearcUri(Project project, String subjectLabel, String sessionLabel) {
        return new PrearchiveResultFilter().
                subject(subjectLabel).
                name(sessionLabel).
                findUniqueResult(mainInterface().getPrearchiveEntriesForProject(project)).
                getUrl();
    }

}
