package org.nrg.testing.xnat.tests.batchtransfer;

import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Tag;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.HardDependency;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.SubjectResource;
import org.nrg.xnat.pogo.transfer.BatchTransferPayload;
import org.nrg.xnat.pogo.transfer.TransferItemStatus;
import org.nrg.xnat.pogo.transfer.TransferMode;
import org.nrg.xnat.pogo.transfer.TransferRequest;
import org.nrg.xnat.pogo.transfer.TransferRunDetail;
import org.nrg.xnat.pogo.transfer.TransferRunItem;
import org.nrg.xnat.pogo.transfer.TransferRunStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.nrg.testing.TestGroups.SHARING;
import static org.nrg.xnat.enums.DicomEditVersion.DE_6;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Batch Transfer's Share and Clone modes (NrgXnat/batch-transfer-plugin#38, SC-01 to SC-13).
 *
 * <p>Neither mode anonymizes. Share links the source's own session into the destination; Clone makes a new session
 * whose files are hard links to the source's. Each destination carries a probe project script that writes
 * {@code StationName = "BT project"}, so a script that ran by mistake shows in the headers.
 *
 * <p>The source holds one {@code mr_1} session per scenario, so the tests don't change each other's data. Each
 * transfer is its own test; checks that rely on an earlier transfer name it with {@link HardDependency}. Shares are
 * removed before the projects are deleted.
 */
@Slf4j
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = PluginRegistry.BATCH_TRANSFER_ID, minimumSupportedVersion = "2.0.0"))
@Test(groups = SHARING)
public class TestBatchTransferShareClone extends BaseBatchTransferTest {

    private static final String PROJECT_PROBE_VALUE = "BT project";
    private static final String NOTES_RESOURCE = "BT_NOTES";

    private final AnonScript projectProbe = XnatObjectUtils.anonScriptFromFile(DE_6, "bt_project_probe.das");
    private final File sessionZip = getDataFile("mr_1.zip");
    private final File notesFile = getDataFile("batch_share/original/resource_file.txt");

    private final Project source = new Project();
    private final Project shareDestination = new Project();
    private final Project cloneDestination = new Project();

    // Source data, one subject per scenario.
    private Subject sharedSubject;      // SC-01, SC-03, SC-07, SC-09; also cloned in SC-04, SC-05, SC-11
    private MRSession sharedSession;
    private Subject subjectWithNotes;   // SC-02 and SC-06: a session and a subject resource
    private MRSession notesSubjectSession;
    private Subject shareConflictSubject;   // SC-08
    private MRSession shareConflictSession;
    private Subject assessorSubject;    // SC-10
    private MRSession assessorSession;
    private Subject cloneConflictSubject;   // SC-12
    private MRSession cloneConflictSession;
    private Subject deletionSubject;    // SC-13
    private MRSession cloneToDelete;
    private MRSession sourceToDelete;

    private String assessorId;
    private String assessorLabel;
    private String unrelatedSessionId;  // in shareDestination, with shareConflictSession's label
    private String unrelatedSubjectId;  // in cloneDestination, with cloneConflictSubject's label
    private Map<String, String> sharedSessionDigests;

    @BeforeClass
    public void setUpProjects() {
        mainInterface().createProject(source);
        for (Project destination : new Project[]{shareDestination, cloneDestination}) {
            mainInterface().createProject(destination);
            mainInterface().setProjectAnonScript(destination, projectProbe);
            mainInterface().enableProjectAnonScript(destination);
        }

        sharedSubject = new Subject(source);
        sharedSession = sessionFor(sharedSubject);
        mainInterface().createSubject(sharedSubject);

        subjectWithNotes = new Subject(source);
        notesSubjectSession = sessionFor(subjectWithNotes);
        new ResourceFile(new SubjectResource(source, subjectWithNotes, NOTES_RESOURCE), notesFile.getName()).
                extension(new SimpleResourceFileExtension(notesFile));
        mainInterface().createSubject(subjectWithNotes);

        shareConflictSubject = new Subject(source);
        shareConflictSession = sessionFor(shareConflictSubject);
        mainInterface().createSubject(shareConflictSubject);

        assessorSubject = new Subject(source);
        assessorSession = sessionFor(assessorSubject);
        new QC(source, assessorSubject, assessorSession);
        mainInterface().createSubject(assessorSubject);
        final Map<String, String> assessors = idsByLabel(formatRestUrl("experiments", id(assessorSession), "assessors"));
        assertEquals(assessors.size(), 1, "assessors of the SC-10 session: " + assessors);
        assessorLabel = assessors.keySet().iterator().next();
        assessorId = assessors.get(assessorLabel);

        cloneConflictSubject = new Subject(source);
        cloneConflictSession = sessionFor(cloneConflictSubject);
        mainInterface().createSubject(cloneConflictSubject);

        deletionSubject = new Subject(source);
        cloneToDelete = sessionFor(deletionSubject);
        sourceToDelete = sessionFor(deletionSubject);
        mainInterface().createSubject(deletionSubject);

        // Unrelated data in the destinations with the labels SC-08 and SC-12 transfer.
        final Subject unrelatedOwner = new Subject(shareDestination);
        final MRSession unrelatedSession = new MRSession(shareDestination, unrelatedOwner, shareConflictSession.getLabel());
        mainInterface().createSubject(unrelatedOwner);
        unrelatedSessionId = mainInterface().getAccessionNumber(unrelatedSession);

        final Subject unrelatedSubject = new Subject(cloneDestination, cloneConflictSubject.getLabel());
        mainInterface().createSubject(unrelatedSubject);
        unrelatedSubjectId = mainInterface().getAccessionNumber(unrelatedSubject);

        sharedSessionDigests = digests(scanFiles(id(sharedSession)));
    }

    @AfterClass(alwaysRun = true)
    public void deleteProjects() {
        // Remove the shares first, so deleting the destination can't reach the source's data.
        if (sharedSession != null) {
            removeShareQuietly(formatRestUrl("projects", source.getId(), "subjects", sharedSubject.getLabel(), "experiments", sharedSession.getLabel(), "projects", shareDestination.getId()));
        }
        if (subjectWithNotes != null) {
            removeShareQuietly(formatRestUrl("projects", source.getId(), "subjects", subjectWithNotes.getLabel(), "projects", shareDestination.getId()));
        }
        restDriver.deleteProjectSilently(mainAdminUser, shareDestination);
        restDriver.deleteProjectSilently(mainAdminUser, cloneDestination);
        restDriver.deleteProjectSilently(mainAdminUser, source);
    }

    /**
     * Control: XNAT's own upload into a destination runs its probe script, so the checks below that the script didn't
     * run can fail. Both destinations get the same script.
     */
    @Test
    public void testProjectProbeRunsOnADirectUpload() {
        final Subject subject = new Subject(cloneDestination);
        final MRSession session = new MRSession(cloneDestination, subject);
        new SessionImportExtension(session, sessionZip);
        mainInterface().createSubject(subject);
        for (Map.Entry<String, byte[]> file : scanFiles(mainInterface().getAccessionNumber(session)).entrySet()) {
            assertEquals(DicomUtils.readDicom(new ByteArrayInputStream(file.getValue())).getString(Tag.StationName), PROJECT_PROBE_VALUE, file.getKey());
        }
    }

    /** SC-01: a shared session appears in the destination under its own ID, with the source's files untouched. */
    @Test
    public void testShareSession() {
        final TransferRunDetail run = transfer(TransferMode.SHARE, id(sharedSession), shareDestination);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        assertItem(run, id(sharedSession), TransferItemStatus.DONE, id(sharedSession));

        assertEquals(experimentIds(shareDestination).get(sharedSession.getLabel()), id(sharedSession));
        assertEquals(scanIds(shareDestination, sharedSession), scanIds(source, sharedSession), "the session's scans are shared with it");
        final Map<String, byte[]> files = scanFiles(id(sharedSession));
        assertEquals(digests(files), sharedSessionDigests, "the source's files are unchanged");
        assertProbeDidNotRun(files);
    }

    /** SC-03: sharing a session doesn't share its subject, and the session is still reachable in the destination. */
    @Test
    @HardDependency("testShareSession")
    public void testSharingASessionLeavesItsSubjectBehind() {
        assertFalse(subjectIds(shareDestination).containsKey(sharedSubject.getLabel()), "subjects in the destination: " + subjectIds(shareDestination));
        mainQueryBase().get(formatRestUrl("projects", shareDestination.getId(), "experiments", id(sharedSession))).then().assertThat().statusCode(200);
    }

    /** SC-07: sharing a session again is skipped: the item is Done and the destination doesn't change. */
    @Test
    @HardDependency("testShareSession")
    public void testShareAgainChangesNothing() {
        final Map<String, String> before = experimentIds(shareDestination);
        final TransferRunDetail run = transfer(TransferMode.SHARE, id(sharedSession), shareDestination);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        assertItem(run, id(sharedSession), TransferItemStatus.DONE, id(sharedSession));
        assertEquals(experimentIds(shareDestination), before);
    }

    /** SC-09 (MDACC-150): cloning a session into a project it's shared into is skipped, so there's still one copy. */
    @Test
    @HardDependency("testShareSession")
    public void testCloningIntoAProjectWithTheShareIsSkipped() {
        final Map<String, String> before = experimentIds(shareDestination);
        final TransferRunDetail run = transfer(TransferMode.CLONE, id(sharedSession), shareDestination);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        assertItem(run, id(sharedSession), TransferItemStatus.DONE, id(sharedSession));
        assertEquals(experimentIds(shareDestination), before);
    }

    /** SC-02: sharing a subject shares the subject record only; its sessions stay behind. */
    @Test
    public void testShareSubject() {
        final String subjectId = mainInterface().getAccessionNumber(subjectWithNotes);
        final TransferRunDetail run = transfer(TransferMode.SHARE, subjectId, shareDestination);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        assertItem(run, subjectId, TransferItemStatus.DONE, subjectId);

        assertEquals(subjectIds(shareDestination).get(subjectWithNotes.getLabel()), subjectId);
        assertFalse(experimentIds(shareDestination).containsKey(notesSubjectSession.getLabel()), "the subject's session isn't shared");
    }

    /** SC-08: sharing a session fails when an unrelated session in the destination has its label. */
    @Test
    public void testShareFailsWhenTheLabelIsTaken() {
        final TransferRunDetail run = transfer(TransferMode.SHARE, id(shareConflictSession), shareDestination);
        assertRunStatus(run, TransferRunStatus.FAILED);
        final TransferRunItem item = assertItem(run, id(shareConflictSession), TransferItemStatus.FAILED, null);
        assertTrue(item.getMessage().contains("same label (" + shareConflictSession.getLabel() + ") already exists"), item.getMessage());
        assertEquals(experimentIds(shareDestination).get(shareConflictSession.getLabel()), unrelatedSessionId, "the unrelated session keeps the label");
    }

    /** SC-04: a clone is a new session with the source's files byte for byte; no destination script runs. */
    @Test
    @Basic
    public void testCloneSession() {
        final TransferRunDetail run = transfer(TransferMode.CLONE, id(sharedSession), cloneDestination);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String cloneId = experimentIds(cloneDestination).get(sharedSession.getLabel());
        assertNotNull(cloneId, "the clone is in the destination under the source's label");
        assertNotEquals(cloneId, id(sharedSession), "the clone has its own ID");
        assertItem(run, id(sharedSession), TransferItemStatus.DONE, cloneId);

        final String cloneSubjectId = subjectIds(cloneDestination).get(sharedSubject.getLabel());
        assertNotNull(cloneSubjectId, "the subject record is copied with the session");
        assertNotEquals(cloneSubjectId, mainInterface().getAccessionNumber(sharedSubject));

        final Map<String, byte[]> cloneFiles = scanFiles(cloneId);
        assertEquals(digests(cloneFiles), sharedSessionDigests, "the clone's files are the source's, byte for byte");
        assertProbeDidNotRun(cloneFiles);
        assertEquals(digests(scanFiles(id(sharedSession))), sharedSessionDigests, "the source's files are unchanged");
    }

    /**
     * SC-05: replacing a file in the clone leaves the source's file as it was. The clone's files are hard links to the
     * source's, so this holds only while XNAT replaces a file rather than writing into it.
     */
    @Test
    @HardDependency("testCloneSession")
    public void testReplacingAClonedFileLeavesTheSourceIntact() {
        final String cloneId = experimentIds(cloneDestination).get(sharedSession.getLabel());
        final List<Map<String, Object>> listing = mainQueryBase().queryParam("format", "json").
                get(formatRestUrl("experiments", cloneId, "scans", "ALL", "files")).
                then().assertThat().statusCode(200).
                extract().jsonPath().getList("ResultSet.Result");
        assertTrue(listing.size() >= 2, "the clone has at least two files");
        final String targetName = String.valueOf(listing.get(0).get("Name"));
        final String targetUri = String.valueOf(listing.get(0).get("URI"));
        final byte[] replacement = fileNamed(scanFiles(cloneId), String.valueOf(listing.get(1).get("Name")));

        mainQueryBase().queryParam("overwrite", true).
                multiPart("file", targetName, replacement).
                put(formatXnatUrl(targetUri)).
                then().assertThat().statusCode(200);

        assertEquals(sha256(fileNamed(scanFiles(cloneId), targetName)), sha256(replacement), "the clone's file is replaced");
        assertEquals(digests(scanFiles(id(sharedSession))), sharedSessionDigests, "the source's files are unchanged");
    }

    /** SC-11: cloning a session again is skipped: the item is Done and names the existing clone. */
    @Test
    @HardDependency("testCloneSession")
    public void testCloneAgainChangesNothing() {
        final Map<String, String> before = experimentIds(cloneDestination);
        final TransferRunDetail run = transfer(TransferMode.CLONE, id(sharedSession), cloneDestination);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        assertItem(run, id(sharedSession), TransferItemStatus.DONE, before.get(sharedSession.getLabel()));
        assertEquals(experimentIds(cloneDestination), before);
    }

    /** SC-06: cloning a subject copies the record and its subject resources, not its sessions. */
    @Test
    public void testCloneSubject() throws Exception {
        final String subjectId = mainInterface().getAccessionNumber(subjectWithNotes);
        final TransferRunDetail run = transfer(TransferMode.CLONE, subjectId, cloneDestination);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String cloneSubjectId = subjectIds(cloneDestination).get(subjectWithNotes.getLabel());
        assertNotNull(cloneSubjectId, "the subject is in the destination");
        assertNotEquals(cloneSubjectId, subjectId, "the clone has its own ID");
        assertItem(run, subjectId, TransferItemStatus.DONE, cloneSubjectId);

        assertFalse(experimentIds(cloneDestination).containsKey(notesSubjectSession.getLabel()), "the subject's session isn't cloned");
        final byte[] notes = mainQueryBase().get(formatRestUrl("subjects", cloneSubjectId, "resources", NOTES_RESOURCE, "files", notesFile.getName())).
                then().assertThat().statusCode(200).extract().asByteArray();
        assertEquals(sha256(notes), sha256(Files.readAllBytes(notesFile.toPath())), "the subject resource is cloned");
    }

    /** SC-10: cloning an assessor on its own clones its session too. */
    @Test
    public void testCloningAnAssessorBringsItsSession() {
        final TransferRunDetail run = transfer(TransferMode.CLONE, assessorId, cloneDestination);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String cloneSessionId = experimentIds(cloneDestination).get(assessorSession.getLabel());
        assertNotNull(cloneSessionId, "the assessor's session is cloned");
        assertNotEquals(cloneSessionId, id(assessorSession));

        final String cloneAssessorId = idsByLabel(formatRestUrl("experiments", cloneSessionId, "assessors")).get(assessorLabel);
        assertNotNull(cloneAssessorId, "the assessor is cloned under the session's clone");
        assertNotEquals(cloneAssessorId, assessorId);
        assertItem(run, assessorId, TransferItemStatus.DONE, cloneAssessorId);
    }

    /** SC-12: cloning a session fails when an unrelated subject in the destination has its subject's label. */
    @Test
    public void testCloneFailsWhenTheSubjectLabelIsTaken() {
        final TransferRunDetail run = transfer(TransferMode.CLONE, id(cloneConflictSession), cloneDestination);
        assertRunStatus(run, TransferRunStatus.FAILED);
        final TransferRunItem item = assertItem(run, id(cloneConflictSession), TransferItemStatus.FAILED, null);
        assertTrue(item.getMessage().contains("A subject with the same label (" + cloneConflictSubject.getLabel() + ") already exists"), item.getMessage());
        assertEquals(subjectIds(cloneDestination).get(cloneConflictSubject.getLabel()), unrelatedSubjectId, "the unrelated subject keeps the label");
        assertFalse(experimentIds(cloneDestination).containsKey(cloneConflictSession.getLabel()), "nothing is cloned");
    }

    /** SC-13, first half: deleting a clone leaves the source's files intact. */
    @Test
    public void testDeletingACloneLeavesTheSourceIntact() {
        assertRunStatus(transfer(TransferMode.CLONE, id(cloneToDelete), cloneDestination), TransferRunStatus.COMPLETED);
        final String cloneId = experimentIds(cloneDestination).get(cloneToDelete.getLabel());
        final Map<String, String> sourceDigests = digests(scanFiles(id(cloneToDelete)));

        deleteExperiment(cloneId);

        assertEquals(digests(scanFiles(id(cloneToDelete))), sourceDigests);
    }

    /** SC-13, second half: deleting the source leaves the clone's files intact. */
    @Test
    public void testDeletingTheSourceLeavesTheCloneIntact() {
        assertRunStatus(transfer(TransferMode.CLONE, id(sourceToDelete), cloneDestination), TransferRunStatus.COMPLETED);
        final String cloneId = experimentIds(cloneDestination).get(sourceToDelete.getLabel());
        final Map<String, String> cloneDigests = digests(scanFiles(cloneId));

        deleteExperiment(id(sourceToDelete));

        assertEquals(digests(scanFiles(cloneId)), cloneDigests);
    }

    private MRSession sessionFor(Subject subject) {
        final MRSession session = new MRSession(source, subject);
        new SessionImportExtension(session, sessionZip);
        return session;
    }

    private TransferRunDetail transfer(TransferMode mode, String itemId, Project destination) {
        return runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                addRequest(new TransferRequest(mode, itemId, destination.getId())));
    }

    private void assertProbeDidNotRun(Map<String, byte[]> files) {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            assertNotEquals(DicomUtils.readDicom(new ByteArrayInputStream(file.getValue())).getString(Tag.StationName), PROJECT_PROBE_VALUE,
                    "the destination's project script ran on " + file.getKey());
        }
    }

    private List<String> scanIds(Project project, MRSession session) {
        return mainQueryBase().queryParam("format", "json").
                get(formatRestUrl("projects", project.getId(), "experiments", id(session), "scans")).
                then().assertThat().statusCode(200).
                extract().jsonPath().getList("ResultSet.Result.ID", String.class);
    }

    /** Removes a share during cleanup. A failure is logged rather than thrown, so the project deletes that follow still run. */
    private void removeShareQuietly(String shareUrl) {
        try {
            final int status = mainQueryBase().delete(shareUrl).getStatusCode();
            if (status / 100 != 2) {
                log.warn("Couldn't remove the share {}: HTTP {}", shareUrl, status);
            }
        } catch (Exception | Error e) {
            log.warn("Couldn't remove the share {}", shareUrl, e);
        }
    }

}
