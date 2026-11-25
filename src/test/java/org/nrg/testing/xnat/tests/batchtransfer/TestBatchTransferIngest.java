package org.nrg.testing.xnat.tests.batchtransfer;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.nrg.testing.DicomUtils;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.HardDependency;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.nrg.testing.TestGroups.IMPORTER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * What Batch Transfer's Reimport puts in the destination when no anonymization script runs: the sessions, labels, files
 * and headers, what it leaves behind, and how failures are reported.
 *
 * <p>The site-wide anonymization script is off for the class, so a Reimport has nothing to change and the copy's
 * headers can be compared with the source's. The class puts back the script and its on/off setting it found when it
 * ends. Sessions that go to the same destination in one batch are derived from {@code mr_1} with new UIDs, so they
 * don't share a StudyInstanceUID.
 */
@Slf4j
@TestRequires(specificPluginRequirements = @PluginRequirement(pluginId = PluginRegistry.BATCH_TRANSFER_ID, minimumSupportedVersion = "2.0.0"))
@Test(groups = IMPORTER)
public class TestBatchTransferIngest extends BaseBatchTransferTest {

    private static final String NOTES_RESOURCE = "BT_NOTES";
    private static final String EXTRA_SCAN_RESOURCE = "BT_EXTRA";
    private static final String COLLISION_SUBJECT = "IN05_SUBJ";
    private static final String COLLISION_SESSION = "IN05_SESS";
    private static final String SECONDARY_CAPTURE_GAP = "batch-transfer ROADMAP: Reimport every DICOM scan resource, not only folders named DICOM";

    private final File mrSessionZip = getDataFile("mr_1.zip");

    private final Project source = new Project();
    private final Project destination = new Project().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);

    private MRSession fidelitySession;                          // IN-01, IN-02, IN-13
    private final List<MRSession> labelSessions = new ArrayList<>();    // IN-03
    private final List<MRSession> subjectSessions = new ArrayList<>();  // IN-09
    private MRSession collisionSource;                          // IN-05
    private String existingCollisionId;
    private Map<String, String> existingCollisionDigests;
    private Subject sessionlessSubject;                         // IN-10
    private String assessorId;                                  // IN-10
    private MRSession codecsSession;                            // IN-11
    private MRSession secondaryCaptureSession;                  // IN-11; XNAT stores it as an "other DICOM" session
    private String secondaryCaptureSessionId;
    private MRSession goodSession;                              // IN-12

    private Map<String, String> fidelityDigests;
    private String fidelityScanId;

    @BeforeClass
    public void setUpProjects() {
        saveSiteAnonSettings();
        mainAdminInterface().disableSiteAnonScript();
        mainInterface().createProject(source);
        mainInterface().createProject(destination);

        fidelitySession = upload(new Subject(source), mrSessionZip);
        final String fidelityId = id(fidelitySession);
        fidelityScanId = mainQueryBase().queryParam("format", "json").get(formatRestUrl("experiments", fidelityId, "scans")).
                then().assertThat().statusCode(200).extract().jsonPath().getString("ResultSet.Result[0].ID");
        addTextResource(formatRestUrl("experiments", fidelityId, "resources", NOTES_RESOURCE), "notes.txt");
        addTextResource(formatRestUrl("experiments", fidelityId, "scans", fidelityScanId, "resources", EXTRA_SCAN_RESOURCE), "extra.txt");
        fidelityDigests = digests(scanFiles(fidelityId));

        for (String variant : Arrays.asList("A", "C", "D")) {
            labelSessions.add(upload(new Subject(source), derivedMrStudy("in03" + variant, "IN03^" + variant, "IN03_" + variant)));
        }
        for (String variant : Arrays.asList("A", "B", "C")) {
            subjectSessions.add(upload(new Subject(source), derivedMrStudy("in09" + variant, "IN09^" + variant, "IN09_" + variant)));
        }

        // IN-05: the destination already has a session with the label, from a different study.
        final MRSession existing = new MRSession(destination, new Subject(destination, COLLISION_SUBJECT), COLLISION_SESSION);
        new SessionImportExtension(existing, getDataFile("scan2.zip"));
        mainInterface().createSubject(existing.getSubject());
        existingCollisionId = id(existing);
        existingCollisionDigests = digests(scanFiles(existingCollisionId));
        collisionSource = upload(new Subject(source), getDataFile("scan2_diffUID.zip"));

        sessionlessSubject = new Subject(source);
        mainInterface().createSubject(sessionlessSubject);
        final Subject assessorSubject = new Subject(source);
        final MRSession assessorSession = new MRSession(source, assessorSubject);
        new SessionImportExtension(assessorSession, derivedMrStudy("in10", "IN10^A", "IN10_A"));
        new QC(source, assessorSubject, assessorSession);
        mainInterface().createSubject(assessorSubject);
        assessorId = idsByLabel(formatRestUrl("experiments", id(assessorSession), "assessors")).values().iterator().next();

        codecsSession = upload(new Subject(source), getDataFile("snapshot_codecs.zip"));
        secondaryCaptureSession = upload(new Subject(source), zipOfDataFolder("snapshot_codecs/5_SECONDARY_CAPTURE", "secondary_capture_only"));
        secondaryCaptureSessionId = experimentId(source, secondaryCaptureSession.getSubject().getLabel(), secondaryCaptureSession.getLabel());
        assertNotNull(secondaryCaptureSessionId, "the Secondary Capture session is archived in the source");
        goodSession = upload(new Subject(source), derivedMrStudy("in12", "IN12^A", "IN12_A"));
    }

    @AfterClass(alwaysRun = true)
    public void restoreSiteScriptAndDeleteProjects() {
        try {
            restoreSiteAnonSettings();
        } finally {
            try {
                clearPrearchive(destination);
            } catch (Exception | Error e) {
                log.warn("Couldn't clear the destination's prearchive", e);
            }
            restDriver.deleteProjectSilently(mainAdminUser, destination);
            restDriver.deleteProjectSilently(mainAdminUser, source);
        }
    }

    /**
     * IN-01: a Reimport is a new session with the source's scans and instances, and with no script to run, the same
     * headers and pixel data.
     */
    @Test
    public void testReimportCopiesTheSession() {
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(fidelitySession), destination.getId()).preserveSubjectLabel(true).preserveSessionLabel(true)));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String copyId = experimentIds(destination).get(fidelitySession.getLabel());
        assertNotNull(copyId, "the copy is in the destination under the source's label");
        assertNotEquals(copyId, id(fidelitySession), "the copy has its own ID");
        assertItem(run, id(fidelitySession), TransferItemStatus.DONE, copyId);
        assertTrue(subjectIds(destination).containsKey(fidelitySession.getSubject().getLabel()), "the copy is under the source's subject label");

        final Map<String, byte[]> sourceFiles = scanFiles(id(fidelitySession));
        final Map<String, byte[]> copyFiles = scanFiles(copyId);
        assertEquals(dicomFileCountsByScan(copyFiles), dicomFileCountsByScan(sourceFiles), "DICOM files per scan");

        final Map<String, Attributes> sourceHeaders = bySopInstanceUid(dicomOnly(sourceFiles));
        final Map<String, Attributes> copyHeaders = bySopInstanceUid(dicomOnly(copyFiles));
        assertEquals(copyHeaders.keySet(), sourceHeaders.keySet(), "SOP Instance UIDs");
        for (Map.Entry<String, Attributes> copy : copyHeaders.entrySet()) {
            final Attributes original = sourceHeaders.get(copy.getKey());
            assertTrue(Arrays.equals(pixelData(copy.getValue()), pixelData(original)), "pixel data of " + copy.getKey());
            assertEquals(headerDifferences(original, copy.getValue()), new ArrayList<String>(), "header differences in " + copy.getKey());
        }
    }

    /** IN-02: the source is unchanged by its Reimport, and isn't shared into the destination. */
    @Test
    @HardDependency("testReimportCopiesTheSession")
    public void testReimportLeavesTheSourceUnchanged() {
        assertEquals(experimentIds(source).get(fidelitySession.getLabel()), id(fidelitySession), "same ID and label");
        assertTrue(idsByLabel(formatRestUrl("projects", source.getId(), "subjects", fidelitySession.getSubject().getLabel(), "experiments")).containsKey(fidelitySession.getLabel()),
                "same subject");
        assertEquals(digests(scanFiles(id(fidelitySession))), fidelityDigests, "the source's files are unchanged");
        assertFalse(experimentIds(destination).containsValue(id(fidelitySession)), "the source isn't shared into the destination");
    }

    /** IN-13: a Reimport carries only the scans' DICOM: not session resources, and not other scan resources. */
    @Test
    @HardDependency("testReimportCopiesTheSession")
    public void testReimportLeavesOutResourcesOtherThanScanDicom() {
        final String copyId = experimentIds(destination).get(fidelitySession.getLabel());
        assertTrue(resourceLabels(formatRestUrl("experiments", id(fidelitySession), "resources")).contains(NOTES_RESOURCE), "the source has the session resource");
        assertEquals(resourceLabels(formatRestUrl("experiments", copyId, "resources")), new HashSet<String>(), "session resources on the copy");
        assertTrue(resourceLabels(formatRestUrl("experiments", id(fidelitySession), "scans", fidelityScanId, "resources")).contains(EXTRA_SCAN_RESOURCE), "the source has the scan resource");
        assertFalse(resourceLabels(formatRestUrl("experiments", copyId, "scans", fidelityScanId, "resources")).contains(EXTRA_SCAN_RESOURCE), "the non-DICOM scan resource isn't copied");
    }

    /**
     * IN-03: label precedence, three items in one batch. Preserving both labels keeps the source's labels; explicit
     * labels are used as given, and win over Preserve.
     */
    @Test
    public void testLabelPrecedence() {
        final MRSession preserved = labelSessions.get(0);
        final MRSession explicit = labelSessions.get(1);
        final MRSession explicitAndPreserved = labelSessions.get(2);
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(preserved), destination.getId()).preserveSubjectLabel(true).preserveSessionLabel(true)).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(explicit), destination.getId()).destinationSubjectLabel("IN03C_SUBJ").destinationSessionLabel("IN03C_SESS")).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(explicitAndPreserved), destination.getId()).
                        preserveSubjectLabel(true).preserveSessionLabel(true).destinationSubjectLabel("IN03D_SUBJ").destinationSessionLabel("IN03D_SESS")));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        assertLabels(preserved.getSubject().getLabel(), preserved.getLabel());
        assertLabels("IN03C_SUBJ", "IN03C_SESS");
        assertLabels("IN03D_SUBJ", "IN03D_SESS");
        assertFalse(experimentIds(destination).containsKey(explicitAndPreserved.getLabel()), "Preserve doesn't add a second copy under the source's label");
    }

    /**
     * IN-05: a Reimport into a label that a different study already has fails the item, and the existing session is
     * unchanged. XNAT's own conflict rule is covered in TestImport; this checks what the plugin reports.
     */
    @Test
    public void testLabelTakenByAnotherStudyFailsTheItem() {
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(collisionSource), destination.getId()).
                        destinationSubjectLabel(COLLISION_SUBJECT).destinationSessionLabel(COLLISION_SESSION)));
        assertRunStatus(run, TransferRunStatus.FAILED);
        final TransferRunItem item = assertItem(run, id(collisionSource), TransferItemStatus.FAILED, null);
        assertTrue(item.getMessage() != null && !item.getMessage().trim().isEmpty(), "the item gives a reason: " + run.summary());
        log.info("IN-05 item message: {}", item.getMessage());
        assertEquals(experimentIds(destination).get(COLLISION_SESSION), existingCollisionId, "the existing session keeps the label");
        assertEquals(digests(scanFiles(existingCollisionId)), existingCollisionDigests, "the existing session's files are unchanged");
        log.info("IN-05 left {} session(s) in the destination's prearchive; clearing them", prearchiveEntries(destination));
        clearPrearchive(destination);
    }

    /** IN-09: several sessions sent to one new destination subject in one batch all land under that one subject. */
    @Test
    public void testSessionsIntoOneNewSubject() {
        final BatchTransferPayload payload = new BatchTransferPayload().sourceProject(source.getId());
        final List<String> sessionLabels = Arrays.asList("IN09_A", "IN09_B", "IN09_C");
        for (int index = 0; index < subjectSessions.size(); index++) {
            payload.addRequest(new TransferRequest(TransferMode.REIMPORT, id(subjectSessions.get(index)), destination.getId()).
                    destinationSubjectLabel("IN09_SUBJ").destinationSessionLabel(sessionLabels.get(index)));
        }
        final TransferRunDetail run = runTransfer(payload);
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        assertEquals(idsByLabel(formatRestUrl("projects", destination.getId(), "subjects", "IN09_SUBJ", "experiments")).keySet(), new HashSet<>(sessionLabels),
                "sessions under IN09_SUBJ");
    }

    /**
     * IN-10: items Reimport can't handle. A subject is skipped without a copy; an image assessor fails; and so does a
     * session sent to its own project.
     */
    @Test
    public void testItemsReimportCannotHandle() {
        final String subjectId = mainInterface().getAccessionNumber(sessionlessSubject);
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                addRequest(new TransferRequest(TransferMode.REIMPORT, subjectId, destination.getId())).
                addRequest(new TransferRequest(TransferMode.REIMPORT, assessorId, destination.getId())).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(fidelitySession), source.getId()).preserveSubjectLabel(true).preserveSessionLabel(true)));
        log.info("IN-10 run:\n{}", run.summary());
        final TransferRunItem subjectItem = run.findItem(subjectId);
        assertNotNull(subjectItem, run.summary());
        assertNull(subjectItem.getDestinationId(), "a subject isn't copied: " + run.summary());
        assertFalse(subjectIds(destination).containsKey(sessionlessSubject.getLabel()), "no subject in the destination");
        final TransferRunItem assessorItem = assertItem(run, assessorId, TransferItemStatus.FAILED, null);
        assertTrue(assessorItem.getMessage() != null && assessorItem.getMessage().contains("not supported for assessors"), run.summary());
        final TransferRunItem sameProject = assertItem(run, id(fidelitySession), TransferItemStatus.FAILED, null);
        assertTrue(sameProject.getMessage() != null && sameProject.getMessage().contains("cannot be the same as the source project"), run.summary());
    }

    /**
     * IN-11, a known gap: every series of a session with Secondary Capture arrives. Today Reimport sends only scan
     * folders named DICOM, and XNAT keeps Secondary Capture in a scan resource labeled {@code secondary}, so that series
     * is left out.
     */
    @Test
    @ExpectedFailure(jiraIssue = SECONDARY_CAPTURE_GAP)
    public void testReimportCarriesSecondaryCapture() {
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(codecsSession), destination.getId()).preserveSubjectLabel(true).preserveSessionLabel(true)));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String copyId = experimentIds(destination).get(codecsSession.getLabel());
        assertNotNull(copyId, run.summary());
        assertEquals(dicomFileCountsByScan(scanFiles(copyId)), dicomFileCountsByScan(scanFiles(id(codecsSession))), "DICOM files per scan, including Secondary Capture");
    }

    /**
     * IN-11, a known gap: a session made only of Secondary Capture reimports. Today the item fails with "No DICOM files
     * found in source experiment ...; nothing to reimport." XNAT stores such a session as {@code xnat:otherDicomSessionData}
     * and leaves it out of the project's experiment listing, so it's looked up by its labels.
     */
    @Test
    @ExpectedFailure(jiraIssue = SECONDARY_CAPTURE_GAP)
    public void testReimportOfOnlySecondaryCapture() {
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                addRequest(new TransferRequest(TransferMode.REIMPORT, secondaryCaptureSessionId, destination.getId()).preserveSubjectLabel(true).preserveSessionLabel(true)));
        log.info("IN-11 Secondary Capture only:\n{}", run.summary());
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        assertNotNull(experimentId(destination, secondaryCaptureSession.getSubject().getLabel(), secondaryCaptureSession.getLabel()), run.summary());
    }

    /** IN-12: a run with one item that succeeds and one that fails ends with Warnings, and the good session arrives. */
    @Test
    public void testPartialFailureEndsWithWarnings() {
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(goodSession), destination.getId()).preserveSubjectLabel(true).preserveSessionLabel(true)).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(labelSessions.get(0)), source.getId())));
        assertRunStatus(run, TransferRunStatus.WARNINGS);
        assertEquals(run.getRun().getPassedCount(), 1, run.summary());
        assertEquals(run.getRun().getFailedCount(), 1, run.summary());
        final String goodCopyId = experimentIds(destination).get(goodSession.getLabel());
        assertNotNull(goodCopyId, "the good session should be in " + destination.getId() + ": " + run.summary());
        assertItem(run, id(goodSession), TransferItemStatus.DONE, goodCopyId);
        assertItem(run, id(labelSessions.get(0)), TransferItemStatus.FAILED, null);
    }

    private MRSession upload(Subject subject, File zip) {
        final MRSession session = new MRSession(source, subject);
        new SessionImportExtension(session, zip);
        mainInterface().createSubject(subject);
        return session;
    }

    /** An experiment's ID by its subject and session labels, or null when there's none. Works for any session type. */
    private String experimentId(Project project, String subjectLabel, String sessionLabel) {
        final Response response = mainQueryBase().queryParam("format", "json").
                get(formatRestUrl("projects", project.getId(), "subjects", subjectLabel, "experiments", sessionLabel));
        return response.statusCode() == 200 ? response.jsonPath().getString("items[0].data_fields.ID") : null;
    }

    private void addTextResource(String resourceUrl, String fileName) {
        mainQueryBase().queryParam("format", "TXT").put(resourceUrl).then().assertThat().statusCode(200);
        mainQueryBase().multiPart("file", fileName, ("Batch Transfer test file " + fileName).getBytes(StandardCharsets.UTF_8)).
                put(resourceUrl + "/files/" + fileName).then().assertThat().statusCode(200);
    }

    private Set<String> resourceLabels(String listingUrl) {
        return new HashSet<>(mainQueryBase().queryParam("format", "json").get(listingUrl).
                then().assertThat().statusCode(200).
                extract().jsonPath().getList("ResultSet.Result.label", String.class));
    }

    private void assertLabels(String subjectLabel, String sessionLabel) {
        assertTrue(idsByLabel(formatRestUrl("projects", destination.getId(), "subjects", subjectLabel, "experiments")).containsKey(sessionLabel),
                "session " + sessionLabel + " under subject " + subjectLabel + "; destination experiments: " + experimentIds(destination).keySet());
    }

    /** The number of files in each scan's DICOM-format resources (including {@code secondary}), by scan folder. */
    private static Map<String, Integer> dicomFileCountsByScan(Map<String, byte[]> files) {
        final Map<String, Integer> counts = new TreeMap<>();
        for (String key : files.keySet()) {
            if (isDicom(files.get(key))) {
                counts.merge(key.split("/")[1], 1, Integer::sum);
            }
        }
        return counts;
    }

}
