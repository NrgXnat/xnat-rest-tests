package org.nrg.testing.xnat.tests.batchtransfer;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.xnat.XnatObjectUtils;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.AnonScript;
import org.nrg.xnat.pogo.PluginRegistry;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.pogo.transfer.BatchTransferPayload;
import org.nrg.xnat.pogo.transfer.TransferItemStatus;
import org.nrg.xnat.pogo.transfer.TransferMode;
import org.nrg.xnat.pogo.transfer.TransferRequest;
import org.nrg.xnat.pogo.transfer.TransferRunDetail;
import org.nrg.xnat.pogo.transfer.TransferRunItem;
import org.nrg.xnat.pogo.transfer.TransferRunStatus;
import org.nrg.xnat.versions.Xnat_1_10_1;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.nrg.testing.TestGroups.ANONYMIZATION;
import static org.nrg.xnat.enums.DicomEditVersion.DE_6;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Batch Transfer's Reimport compared with XNAT's own upload of the same data (NrgXnat/batch-transfer-plugin#38,
 * section 7.2). For each case, the reference is a DICOM zip import into a project set up like the destination, with the
 * importer parameters the plugin's Reimport sends. The two sessions are compared file by file, paired by SOP Instance
 * UID, and by their scans, resources, session fields and labels. Any difference fails the test.
 *
 * <p>The source is {@code ANON_2}, uploaded with the site-wide script off. The destinations' project script is
 * {@code bt_project_anon_probe.das}, which removes the planted {@code (0044,000A)} tag and sets {@code StationName}, and
 * the site-wide script, where a case turns it on, is {@code bt_site_anon_probe.das}. Neither reads the project or the
 * labels, so the two paths can run in different projects. The class puts back the site-wide script and its on/off
 * setting it found when it ends.
 */
@Slf4j
@TestRequires(data = TestData.ANON_2,
        specificPluginRequirements = @PluginRequirement(pluginId = PluginRegistry.BATCH_TRANSFER_ID, minimumSupportedVersion = "2.0.0"))
@AddedIn(Xnat_1_10_1.class)
@Test(groups = ANONYMIZATION)
public class TestBatchTransferParity extends BaseBatchTransferTest {

    private static final int ARCHIVE_WAIT_SECONDS = 120;
    private static final String RECORDED_SCRIPT = "<script ID>";
    private static final List<String> SESSION_FIELDS = Arrays.asList("modality", "date", "time", "UID", "scanner", "dcmPatientName", "dcmPatientId", "dcmPatientBirthDate");

    private final AnonScript projectScript = XnatObjectUtils.anonScriptFromFile(DE_6, "bt_project_anon_probe.das");
    private final AnonScript otherProjectScript = XnatObjectUtils.anonScriptFromFile(DE_6, "bt_custom_probe.das");
    private final AnonScript siteScript = XnatObjectUtils.anonScriptFromFile(DE_6, "bt_site_anon_probe.das");
    private final File anonZip = TestData.ANON_2.toFile();

    private final Project source = new Project();
    private final Map<String, Project> references = new LinkedHashMap<>();
    private final Map<String, Project> destinations = new LinkedHashMap<>();
    private String sourceSessionId;

    @BeforeClass
    public void setUpProjects() {
        saveSiteAnonSettings();
        mainAdminInterface().disableSiteAnonScript();
        mainInterface().createProject(source);
        final Subject subject = new Subject(source);
        final MRSession session = new MRSession(source, subject);
        new SessionImportExtension(session, anonZip);
        mainInterface().createSubject(subject);
        sourceSessionId = id(session);

        // Each case gets a reference project and a destination set up the same way, except where the case says otherwise.
        setUpPair("PA01", PrearchiveCode.AUTO_ARCHIVE, projectScript, projectScript);
        setUpPair("PA02", PrearchiveCode.AUTO_ARCHIVE, projectScript, projectScript);
        setUpPair("PA03", PrearchiveCode.AUTO_ARCHIVE, projectScript, null);
        setUpPair("PA04", PrearchiveCode.AUTO_ARCHIVE, projectScript, otherProjectScript);
        setUpPair("PA05", PrearchiveCode.MANUAL, projectScript, projectScript);
        setUpPair("PA06", PrearchiveCode.AUTO_ARCHIVE, null, null);
    }

    @AfterClass(alwaysRun = true)
    public void restoreSiteScriptAndDeleteProjects() {
        try {
            restoreSiteAnonSettings();
        } finally {
            final List<Project> projects = new ArrayList<>(destinations.values());
            projects.addAll(references.values());
            projects.add(source);
            for (Project project : projects) {
                try {
                    clearPrearchive(project);
                } catch (Exception | Error e) {
                    log.warn("Couldn't clear the prearchive of {}", project.getId(), e);
                }
                restDriver.deleteProjectSilently(mainAdminUser, project);
            }
        }
    }

    /** PA-01: with only the project script, a Reimport matches XNAT's own upload. */
    @Test
    public void testProjectScriptOnly() {
        mainAdminInterface().disableSiteAnonScript();
        final String referenceId = uploadReference("PA01", "PA01", true);
        final String copyId = reimport("PA01", "PA01", null, null);
        assertParity(referenceId, copyId);
    }

    /** PA-02: with the site-wide and project scripts, a Reimport matches XNAT's own upload. */
    @Test
    public void testSiteAndProjectScripts() {
        useSiteScript();
        final String referenceId = uploadReference("PA02", "PA02", true);
        final String copyId = reimport("PA02", "PA02", null, null);
        assertParity(referenceId, copyId);
    }

    /**
     * PA-03: a custom script on a Reimport gives the same result as that script set as the project script. The importer's
     * inline script and the project script are interchangeable, except that XNAT records only the project script in
     * (0012,0064); see {@link #normalizeRecords}.
     */
    @Test
    public void testCustomScriptMatchesTheSameProjectScript() {
        requirePerImportAnon();
        mainAdminInterface().disableSiteAnonScript();
        final String referenceId = uploadReference("PA03", "PA03", true);
        final String copyId = reimport("PA03", "PA03", projectScript.getContents(), null);
        assertParity(referenceId, copyId, true);
    }

    /**
     * PA-04: in replace mode, only the custom script runs. The destination has the site-wide script on and its own,
     * different project script, and the copy still matches an upload where only that script ran as the project script.
     */
    @Test
    public void testReplaceModeRunsOnlyTheCustomScript() {
        requirePerImportAnon();
        mainAdminInterface().disableSiteAnonScript();
        final String referenceId = uploadReference("PA04", "PA04", true);
        useSiteScript();
        final String copyId = reimport("PA04", "PA04", projectScript.getContents(), true);
        assertParity(referenceId, copyId, true);
    }

    /**
     * PA-05: into a project that sends uploads to the prearchive, a Reimport waits there as READY and isn't archived; its
     * item is Done with no copy to name. Archived through the core API, it matches XNAT's own upload archived the same
     * way.
     */
    @Test
    public void testManualPrearchiveDestination() {
        mainAdminInterface().disableSiteAnonScript();
        uploadReference("PA05", "PA05", false);
        final String referenceId = archiveTheOnlyPrearchiveSession(references.get("PA05"), "PA05");

        final TransferRunDetail run = runTransfer(reimportPayload("PA05", "PA05", null, null));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final TransferRunItem item = assertItem(run, sourceSessionId, TransferItemStatus.DONE, null);
        assertNull(item.getDestinationId(), "nothing is archived yet, so the item names no copy: " + run.summary());
        assertEquals(experimentIds(destinations.get("PA05")), Collections.emptyMap(), "nothing archived in the destination");
        final String copyId = archiveTheOnlyPrearchiveSession(destinations.get("PA05"), "PA05");
        assertParity(referenceId, copyId);
    }

    /**
     * PA-06: with no labels given and Preserve off, a Reimport derives the same subject and session labels from the
     * DICOM as XNAT's own upload does.
     */
    @Test
    public void testDerivedLabels() {
        mainAdminInterface().disableSiteAnonScript();
        uploadReference("PA06", null, true);
        final String referenceId = awaitTheOnlyExperiment(references.get("PA06"));
        final TransferRunDetail run = runTransfer(reimportPayload("PA06", null, null, null));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String copyId = awaitTheOnlyExperiment(destinations.get("PA06"));
        assertItem(run, sourceSessionId, TransferItemStatus.DONE, copyId);

        assertEquals(onlyLabel(experimentIds(destinations.get("PA06"))), onlyLabel(experimentIds(references.get("PA06"))), "session label");
        assertEquals(onlyLabel(subjectIds(destinations.get("PA06"))), onlyLabel(subjectIds(references.get("PA06"))), "subject label");
        assertParity(referenceId, copyId);
    }

    private void setUpPair(String caseId, PrearchiveCode prearchiveCode, AnonScript referenceScript, AnonScript destinationScript) {
        references.put(caseId, createProject(prearchiveCode, referenceScript));
        destinations.put(caseId, createProject(prearchiveCode, destinationScript));
    }

    private Project createProject(PrearchiveCode prearchiveCode, AnonScript projectAnonScript) {
        final Project project = new Project().prearchiveCode(prearchiveCode);
        mainInterface().createProject(project);
        if (projectAnonScript != null) {
            mainInterface().setProjectAnonScript(project, projectAnonScript);
            mainInterface().enableProjectAnonScript(project);
        }
        return project;
    }

    private void useSiteScript() {
        mainAdminInterface().setSiteAnonScript(siteScript);
        mainAdminInterface().enableSiteAnonScript();
    }

    /**
     * XNAT's own DICOM zip import of the source data into the case's reference project, with the importer parameters
     * the plugin's Reimport sends. Returns the archived session's ID, or null when the project keeps uploads in the
     * prearchive.
     */
    private String uploadReference(String caseId, String label, boolean autoArchive) {
        final Project project = references.get(caseId);
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("import-handler", "DICOM-zip");
        params.put("Ignore-Unparsable", "true");
        params.put("rename", "true");
        params.put("project", project.getId());
        params.put("action", "commit");
        if (autoArchive) {
            params.put("AA", "true");
        }
        if (label != null) {
            params.put("subject", label);
            params.put("session", label);
        }
        try {
            mainQueryBase().queryParams(params).
                    multiPart("file", anonZip.getName(), Files.readAllBytes(anonZip.toPath()), "application/zip").
                    post(formatRestUrl("services", "import")).
                    then().assertThat().statusCode(200);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return autoArchive && label != null ? awaitExperiment(project, label) : null;
    }

    private BatchTransferPayload reimportPayload(String caseId, String label, String anonScript, Boolean replace) {
        final TransferRequest request = new TransferRequest(TransferMode.REIMPORT, sourceSessionId, destinations.get(caseId).getId());
        if (label != null) {
            request.destinationSubjectLabel(label).destinationSessionLabel(label);
        }
        return new BatchTransferPayload().sourceProject(source.getId()).anonScript(anonScript).anonReplacePipeline(replace).addRequest(request);
    }

    /** Reimports the source into the case's destination under the label, checks it arrived, and returns the copy's ID. */
    private String reimport(String caseId, String label, String anonScript, Boolean replace) {
        final TransferRunDetail run = runTransfer(reimportPayload(caseId, label, anonScript, replace));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String copyId = awaitExperiment(destinations.get(caseId), label);
        assertItem(run, sourceSessionId, TransferItemStatus.DONE, copyId);
        return copyId;
    }

    /** Waits for the project's one prearchive session to be READY, archives it through the core API, and returns its ID. */
    private String archiveTheOnlyPrearchiveSession(Project project, String label) {
        final StopWatch stopWatch = TimeUtils.launchStopWatch();
        while (true) {
            final List<Map<String, Object>> rows = ownPrearchiveRows(project);
            if (rows.size() == 1 && "READY".equals(rows.get(0).get("status"))) {
                mainInterface().archiveSession(String.valueOf(rows.get(0).get("url")));
                return awaitExperiment(project, label);
            }
            assertTrue(rows.size() <= 1, "at most one prearchive session in " + project.getId() + ": " + rows);
            TimeUtils.checkStopWatch(stopWatch, ARCHIVE_WAIT_SECONDS, "No READY prearchive session in " + project.getId() + ": " + rows);
            TimeUtils.sleep(1000);
        }
    }

    private String awaitExperiment(Project project, String label) {
        final StopWatch stopWatch = TimeUtils.launchStopWatch();
        while (true) {
            final String id = experimentIds(project).get(label);
            if (id != null) {
                return id;
            }
            TimeUtils.checkStopWatch(stopWatch, ARCHIVE_WAIT_SECONDS, "No session " + label + " archived in " + project.getId());
            TimeUtils.sleep(1000);
        }
    }

    private String awaitTheOnlyExperiment(Project project) {
        final StopWatch stopWatch = TimeUtils.launchStopWatch();
        while (true) {
            final Map<String, String> ids = experimentIds(project);
            if (ids.size() == 1) {
                return ids.values().iterator().next();
            }
            TimeUtils.checkStopWatch(stopWatch, ARCHIVE_WAIT_SECONDS, "Expected one session in " + project.getId() + ", found " + ids);
            TimeUtils.sleep(1000);
        }
    }

    private static String onlyLabel(Map<String, String> idsByLabel) {
        assertEquals(idsByLabel.size(), 1, "one entry expected: " + idsByLabel);
        return idsByLabel.keySet().iterator().next();
    }

    private void assertParity(String referenceId, String copyId) {
        assertParity(referenceId, copyId, false);
    }

    /**
     * Checks the copy matches the reference.
     *
     * @param inlineScriptOnCopy Whether the copy's script came inline with the Reimport while the reference's ran as a
     *                           project script. See {@link #normalizeRecords}.
     */
    private void assertParity(String referenceId, String copyId, boolean inlineScriptOnCopy) {
        final List<String> differences = differences(referenceId, copyId, inlineScriptOnCopy);
        assertTrue(differences.isEmpty(), "differences between the reference " + referenceId + " and the copy " + copyId + ":\n  " + String.join("\n  ", differences));
    }

    /**
     * Every difference between the reference session and the copy: files, scans and their resources, and session fields.
     * No header tag is left out. The one that differs by design, (0012,0064), is prepared first by
     * {@link #normalizeRecords}.
     */
    private List<String> differences(String referenceId, String copyId, boolean inlineScriptOnCopy) {
        final List<String> differences = new ArrayList<>();

        final Map<String, Attributes> referenceFiles = bySopInstanceUid(dicomOnly(scanFiles(referenceId)));
        final Map<String, Attributes> copyFiles = bySopInstanceUid(dicomOnly(scanFiles(copyId)));
        log.info("De-identification records (script IDs): reference {} has {}, copy {} has {}", referenceId,
                deidentificationRecords(referenceFiles), copyId, deidentificationRecords(copyFiles));
        referenceFiles.values().forEach(file -> normalizeRecords(file, inlineScriptOnCopy));
        copyFiles.values().forEach(file -> normalizeRecords(file, inlineScriptOnCopy));
        if (!referenceFiles.keySet().equals(copyFiles.keySet())) {
            differences.add("SOP Instance UIDs: reference " + referenceFiles.keySet() + ", copy " + copyFiles.keySet());
        }
        for (Map.Entry<String, Attributes> reference : referenceFiles.entrySet()) {
            final Attributes copy = copyFiles.get(reference.getKey());
            if (copy == null) {
                continue;
            }
            for (String difference : headerDifferences(reference.getValue(), copy)) {
                differences.add(reference.getKey() + " " + difference);
            }
            if (!Arrays.equals(pixelData(reference.getValue()), pixelData(copy))) {
                differences.add(reference.getKey() + " pixel data");
            }
        }

        final Map<String, String> referenceScans = scans(referenceId);
        final Map<String, String> copyScans = scans(copyId);
        if (!referenceScans.equals(copyScans)) {
            differences.add("scans and resources: reference " + referenceScans + ", copy " + copyScans);
        }

        final Map<String, Object> referenceFields = sessionFields(referenceId);
        final Map<String, Object> copyFields = sessionFields(copyId);
        for (String field : SESSION_FIELDS) {
            if (!String.valueOf(referenceFields.get(field)).equals(String.valueOf(copyFields.get(field)))) {
                differences.add("session " + field + ": reference " + referenceFields.get(field) + ", copy " + copyFields.get(field));
            }
        }
        return differences;
    }

    /**
     * Prepares (0012,0064), the De-identification Method Code Sequence, for comparison. XNAT adds an item for each script
     * it records, with the script's database ID as the CodeValue. That ID differs between any two projects, even for the
     * same script, so it's masked; the items' number and meaning are still compared. A script sent inline as the
     * importer's {@code Anon-Script} isn't recorded at all: {@code GradualDicomImporter} applies it with
     * {@code record = false}, since it has no database ID. So where the copy's script came inline and the reference's
     * ran as a project script, the sequence is left out of the comparison.
     */
    private static void normalizeRecords(Attributes file, boolean inlineScriptOnCopy) {
        if (inlineScriptOnCopy) {
            file.remove(Tag.DeidentificationMethodCodeSequence);
            return;
        }
        final Sequence sequence = file.getSequence(Tag.DeidentificationMethodCodeSequence);
        if (sequence != null) {
            for (Attributes item : sequence) {
                item.setString(Tag.CodeValue, VR.SH, RECORDED_SCRIPT);
            }
        }
    }

    /** The CodeValues of the De-identification Method Code Sequence items across a session's files. */
    private static Set<String> deidentificationRecords(Map<String, Attributes> files) {
        final Set<String> codes = new TreeSet<>();
        for (Attributes file : files.values()) {
            final Sequence sequence = file.getSequence(Tag.DeidentificationMethodCodeSequence);
            if (sequence != null) {
                for (Attributes item : sequence) {
                    codes.add(item.getString(Tag.CodeValue));
                }
            }
        }
        return codes;
    }

    /**
     * Each scan's type and series description, and each of its resources' label, format and file count, by scan ID. Two
     * requests: the scans, and every scan's resources, which XNAT lists with the scan's ID as {@code cat_id}.
     */
    private Map<String, String> scans(String sessionId) {
        final Map<String, Set<String>> resourcesByScan = new TreeMap<>();
        for (Map<String, Object> resource : listing(formatRestUrl("experiments", sessionId, "scans", "ALL", "resources"))) {
            resourcesByScan.computeIfAbsent(String.valueOf(resource.get("cat_id")), scanId -> new TreeSet<>()).
                    add(resource.get("label") + "/" + resource.get("format") + "/" + resource.get("file_count"));
        }
        final Map<String, String> scans = new TreeMap<>();
        for (Map<String, Object> row : listing(formatRestUrl("experiments", sessionId, "scans"))) {
            final String scanId = String.valueOf(row.get("ID"));
            // Every scan has at least its DICOM resource, so an empty list means the resources weren't matched to it.
            assertTrue(resourcesByScan.containsKey(scanId), "no resources listed for scan " + scanId + " of " + sessionId + ": " + resourcesByScan);
            scans.put(scanId, row.get("type") + " | " + row.get("series_description") + " | " + resourcesByScan.get(scanId));
        }
        return scans;
    }

    private List<Map<String, Object>> listing(String url) {
        return mainQueryBase().queryParam("format", "json").get(url).
                then().assertThat().statusCode(200).
                extract().jsonPath().getList("ResultSet.Result");
    }

    private Map<String, Object> sessionFields(String sessionId) {
        final Response response = mainQueryBase().queryParam("format", "json").get(formatRestUrl("experiments", sessionId));
        response.then().assertThat().statusCode(200);
        return response.jsonPath().getMap("items[0].data_fields");
    }

}
