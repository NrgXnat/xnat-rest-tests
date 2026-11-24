package org.nrg.testing.xnat.tests.batchtransfer;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.PluginRequirement;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.dicom.ProjectScript;
import org.nrg.testing.dicom.SiteScript;
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
import org.nrg.xnat.pogo.transfer.ManifestRow;
import org.nrg.xnat.pogo.transfer.ManifestRowStatus;
import org.nrg.xnat.pogo.transfer.ManifestValidationRequest;
import org.nrg.xnat.pogo.transfer.ManifestValidationResult;
import org.nrg.xnat.pogo.transfer.TransferItemStatus;
import org.nrg.xnat.pogo.transfer.TransferMode;
import org.nrg.xnat.pogo.transfer.TransferRequest;
import org.nrg.xnat.pogo.transfer.TransferRunDetail;
import org.nrg.xnat.pogo.transfer.TransferRunItem;
import org.nrg.xnat.pogo.transfer.TransferRunStatus;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_10_1;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.nrg.testing.TestGroups.ANONYMIZATION;
import static org.nrg.xnat.enums.DicomEditVersion.DE_6;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Anonymization on Batch Transfer's Reimport (NrgXnat/batch-transfer-plugin#38, section 7.1): which scripts run, in
 * what order, and on which copy.
 *
 * <p>The source session is {@code ANON_2}, uploaded with the site-wide script off, so it keeps two planted tags: the
 * site script removes {@code (0044,0001)=CHOCOLATE} and the anonymizing destination's project script removes
 * {@code (0044,000A)=RITTER}. Each script also writes probe tags that show it ran and in which order:
 *
 * <pre>
 *   Tag                                Site-wide   Custom      Project
 *   (0008,1010) StationName            BT site     BT custom   BT project
 *   (0008,1040) InstitutionalDeptName  BT site     BT custom   -
 *   (0018,1030) ProtocolName           -           BT custom   -
 * </pre>
 *
 * <p>The class changes the site-wide script, and puts back the script and its on/off setting it found when it ends.
 * Every Reimport gets its own destination labels, so the tests don't depend on each other.
 */
@TestRequires(users = 2, data = TestData.ANON_2,
        specificPluginRequirements = @PluginRequirement(pluginId = PluginRegistry.BATCH_TRANSFER_ID, minimumSupportedVersion = "2.0.0"))
@AddedIn(Xnat_1_10_1.class)
@Test(groups = ANONYMIZATION)
public class TestBatchTransferAnonymization extends BaseBatchTransferTest {

    private static final String SITE = "BT site";
    private static final String CUSTOM = "BT custom";
    private static final String PROJECT = "BT project";
    /** Passes the plugin's checks, then fails on any PatientName shorter than 999 characters. */
    private static final String DATA_ERROR_SCRIPT = "version \"6.3\"\n(0008,0080) := substring[(0010,0010), 0, 999]\n";
    private static final String MAP_UID_SCRIPT = "version \"6.3\"\n(0020,000D) := mapUID[(0020,000D)]\n";

    private final AnonScript siteScript = XnatObjectUtils.anonScriptFromFile(DE_6, "bt_site_anon_probe.das");
    private final AnonScript projectScript = XnatObjectUtils.anonScriptFromFile(DE_6, "bt_project_anon_probe.das");
    private final String customScript = XnatObjectUtils.anonScriptFromFile(DE_6, "bt_custom_probe.das").getContents();
    private final String valuesScript = XnatObjectUtils.anonScriptFromFile(DE_6, "bt_values.das").getContents();
    private final File mrSessionZip = getDataFile("mr_1.zip");

    private final Project source = new Project();
    private final Project anonDestination = new Project().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);     // project script
    private final Project plainDestination = new Project().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE);    // no project script
    private final Project untouchedDestination = new Project().prearchiveCode(PrearchiveCode.AUTO_ARCHIVE); // must stay empty

    private User member;        // member of plainDestination (PERM-03)
    private User collaborator;  // collaborator on untouchedDestination (AN-10)

    private Subject anonSubject;
    private MRSession anonSession;          // ANON_2, uploaded with the site script off
    private Subject valuesSubject;
    private MRSession valuesSession;        // mr_1, uploaded with the site script off (AN-05's second session)
    private MRSession siteAnonSession;      // mr_1, uploaded with the probe site script on (AN-07)

    private Map<String, String> anonSourceDigests;
    private Map<String, Attributes> anonSourceHeaders;     // by SOPInstanceUID
    private boolean perImportAnon;

    @BeforeClass
    public void setUpProjectsAndSiteScript() {
        saveSiteAnonSettings();
        perImportAnon = mainInterface().readTransferCapabilities().isPerImportAnon();

        member = getGenericUser();
        collaborator = getGenericUser();
        source.addCollaborator(member).addCollaborator(collaborator);
        plainDestination.addMember(member);
        untouchedDestination.addCollaborator(collaborator);
        for (Project project : allProjects()) {
            mainInterface().createProject(project);
        }
        mainInterface().setProjectAnonScript(anonDestination, projectScript);
        mainInterface().enableProjectAnonScript(anonDestination);

        // Sources with the planted tags intact.
        mainAdminInterface().disableSiteAnonScript();
        anonSubject = new Subject(source);
        anonSession = new MRSession(source, anonSubject);
        new SessionImportExtension(anonSession, TestData.ANON_2.toFile());
        mainInterface().createSubject(anonSubject);
        valuesSubject = new Subject(source);
        valuesSession = new MRSession(source, valuesSubject);
        new SessionImportExtension(valuesSession, mrSessionZip);
        mainInterface().createSubject(valuesSubject);

        // The probe site script for the rest of the class; AN-07's source is uploaded with it.
        mainAdminInterface().setSiteAnonScript(siteScript);
        mainAdminInterface().enableSiteAnonScript();
        final Subject siteAnonSubject = new Subject(source);
        siteAnonSession = new MRSession(source, siteAnonSubject);
        new SessionImportExtension(siteAnonSession, mrSessionZip);
        mainInterface().createSubject(siteAnonSubject);

        anonSourceDigests = digests(scanFiles(id(anonSession)));
        anonSourceHeaders = bySopInstanceUid(headers(id(anonSession)));
    }

    @AfterClass(alwaysRun = true)
    public void restoreSiteScriptAndDeleteProjects() {
        try {
            restoreSiteAnonSettings();
        } finally {
            for (Project project : Arrays.asList(anonDestination, plainDestination, untouchedDestination, source)) {
                restDriver.deleteProjectSilently(mainAdminUser, project);
            }
        }
    }

    /** AN-01: a Reimport runs the site-wide script and then the destination's project script, on the copy only. */
    @Test
    @Basic
    public void testReimportRunsTheSiteAndProjectScripts() {
        final String copyId = reimportAndCheck(anonDestination, "AN01", null, null);
        new SiteScript().validateScriptRan(dicomFiles(copyId));
        new ProjectScript().validateScriptRan(dicomFiles(copyId));
        assertProbes(copyId, PROJECT, SITE, null);
        assertSourceUnchanged();
    }

    /** AN-02: a Reimport into a project without a project script runs only the site-wide script. */
    @Test
    public void testReimportWithoutAProjectScriptRunsOnlyTheSiteScript() {
        final String copyId = reimportAndCheck(plainDestination, "AN02", null, null);
        new SiteScript().validateScriptRan(dicomFiles(copyId));
        new ProjectScript().validateScriptDidntRun(dicomFiles(copyId));
        assertProbes(copyId, SITE, SITE, null);
    }

    /** AN-03: a custom script runs after the site-wide script and before the project script. */
    @Test
    public void testCustomScriptRunsBetweenTheSiteAndProjectScripts() {
        requirePerImportAnon();
        final String copyId = reimportAndCheck(anonDestination, "AN03", customScript, null);
        new SiteScript().validateScriptRan(dicomFiles(copyId));
        new ProjectScript().validateScriptRan(dicomFiles(copyId));
        assertProbes(copyId, PROJECT, CUSTOM, CUSTOM);
        assertSourceUnchanged();
    }

    /** AN-04: in replace mode, only the custom script runs: neither the site-wide nor the project script. */
    @Test
    public void testReplaceModeRunsOnlyTheCustomScript() {
        requirePerImportAnon();
        final String copyId = reimportAndCheck(anonDestination, "AN04", customScript, true);
        new SiteScript().validateScriptDidntRun(dicomFiles(copyId));
        new ProjectScript().validateScriptDidntRun(dicomFiles(copyId));
        assertProbes(copyId, CUSTOM, CUSTOM, CUSTOM);
    }

    /** AN-11: replace mode without a custom script is ignored, so the site-wide and project scripts still run. */
    @Test
    public void testReplaceModeWithoutAScriptIsIgnored() {
        final String copyId = reimportAndCheck(anonDestination, "AN11", null, true);
        new SiteScript().validateScriptRan(dicomFiles(copyId));
        new ProjectScript().validateScriptRan(dicomFiles(copyId));
        assertProbes(copyId, PROJECT, SITE, null);
    }

    /**
     * AN-05: each session gets its own manifest values. An empty value is a warning when the manifest is checked, is
     * accepted at submit, and writes an empty tag.
     */
    @Test
    public void testEachSessionGetsItsOwnValues() {
        requirePerImportAnon();
        final String manifestCsv = "source_subject_label,source_session_label,pid,site\n" +
                anonSubject.getLabel() + "," + anonSession.getLabel() + ",AN05_PID_A,AN05_SITE_A\n" +
                valuesSubject.getLabel() + "," + valuesSession.getLabel() + ",AN05_PID_B,\n";
        final ManifestValidationResult manifest = mainInterface().validateTransferManifest(new ManifestValidationRequest().
                sourceProject(source.getId()).
                manifestCsv(manifestCsv).
                anonScript(valuesScript));
        assertTrue(manifest.isRequiredPresent(), "missing columns: " + manifest.getMissingColumns());
        assertTrue(manifest.getScriptBinding().isBound(), "unbound: " + manifest.getScriptBinding().getUnbound());
        final ManifestRow rowA = manifestRow(manifest, anonSession);
        final ManifestRow rowB = manifestRow(manifest, valuesSession);
        assertTrue(rowA.getValueWarnings().isEmpty(), "warnings for the full row: " + rowA.getValueWarnings());
        assertFalse(rowB.getValueWarnings().isEmpty(), "the empty site value is a warning");

        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                anonScript(valuesScript).
                addRequest(reimportRequest(rowA.getResolvedId(), plainDestination, "AN05A").csvValues(rowA.getCsvValues())).
                addRequest(reimportRequest(rowB.getResolvedId(), plainDestination, "AN05B").csvValues(rowB.getCsvValues())));
        assertRunStatus(run, TransferRunStatus.COMPLETED);

        for (Attributes header : headers(copyId(plainDestination, "AN05A"))) {
            assertEquals(header.getString(Tag.PatientID), "AN05_PID_A");
            assertEquals(header.getString(Tag.InstitutionName), "AN05_SITE_A");
        }
        for (Attributes header : headers(copyId(plainDestination, "AN05B"))) {
            assertEquals(header.getString(Tag.PatientID), "AN05_PID_B");
            assertTrue(header.contains(Tag.InstitutionName), "the empty value writes the tag");
            assertEquals(header.getString(Tag.InstitutionName, ""), "", "the tag is empty");
        }
    }

    /**
     * AN-07: the site-wide script runs again on a source that was uploaded with it. With an idempotent script, the
     * copy's headers match the source's.
     */
    @Test
    public void testSiteScriptRunsAgainOnReimport() {
        final String copyId = reimportAndCheck(siteAnonSession, plainDestination, "AN07", null, null);
        final Map<String, Attributes> sourceHeaders = bySopInstanceUid(headers(id(siteAnonSession)));
        final List<Attributes> copyHeaders = headers(copyId);
        assertEquals(copyHeaders.size(), sourceHeaders.size());
        for (Attributes copy : copyHeaders) {
            final Attributes original = sourceHeaders.get(copy.getString(Tag.SOPInstanceUID));
            assertNotNull(original, "the copy's instances are the source's");
            for (int tag : new int[]{Tag.StationName, Tag.InstitutionalDepartmentName, Tag.PatientName, Tag.PatientID, Tag.StudyDescription, Tag.StudyInstanceUID, Tag.SeriesInstanceUID}) {
                assertEquals(copy.getString(tag), original.getString(tag), "tag " + Integer.toHexString(tag));
            }
            assertEquals(copy.getString(Tag.StationName), SITE, "the source was uploaded with the probe site script");
        }
    }

    /**
     * AN-08: the routing labels become the XNAT labels exactly, and the session's patient fields match its anonymized
     * headers.
     */
    @Test
    public void testRoutingLabelsAndPatientFields() {
        requirePerImportAnon();
        final Map<String, String> values = new HashMap<>();
        values.put("pid", "AN08_PID");
        values.put("site", "AN08_SITE");
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                anonScript(valuesScript).
                addRequest(new TransferRequest(TransferMode.REIMPORT, id(anonSession), plainDestination.getId()).
                        destinationSubjectLabel("AN08_SUBJ").
                        destinationSessionLabel("AN08_SESS").
                        csvValues(values)));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String copyId = copyId(plainDestination, "AN08_SESS");
        assertItem(run, id(anonSession), TransferItemStatus.DONE, copyId);
        assertTrue(idsByLabel(formatRestUrl("projects", plainDestination.getId(), "subjects", "AN08_SUBJ", "experiments")).containsKey("AN08_SESS"),
                "the session is under the routed subject label");

        final Attributes header = headers(copyId).get(0);
        assertEquals(header.getString(Tag.PatientID), "AN08_PID");
        final Map<String, Object> fields = mainQueryBase().queryParam("format", "json").get(formatRestUrl("experiments", copyId)).
                then().assertThat().statusCode(200).
                extract().jsonPath().getMap("items[0].data_fields");
        assertEquals(fields.get("dcmPatientId"), header.getString(Tag.PatientID), "dcmPatientId");
        assertEquals(fields.get("dcmPatientName"), header.getString(Tag.PatientName), "dcmPatientName");
    }

    /** AN-09: the plugin's script checks refuse a batch at submit and leave the destination untouched. */
    @Test
    public void testScriptChecksAtSubmitLeaveTheDestinationUntouched() {
        requirePerImportAnon();
        final Map<String, String> quoted = new HashMap<>();
        quoted.put("pid", "AN09\"QUOTE");
        quoted.put("site", "AN09");
        final String valueError = mainInterface().launchBatchTransferExpectingRejection(new BatchTransferPayload().
                sourceProject(source.getId()).
                anonScript(valuesScript).
                addRequest(reimportRequest(id(anonSession), untouchedDestination, "AN09").csvValues(quoted)), 400);
        assertTrue(valueError.contains("Value for 'pid'") && valueError.contains("outside the allowed set"), valueError);

        final String restricted = mainInterface().launchBatchTransferExpectingRejection(new BatchTransferPayload().
                sourceProject(source.getId()).
                anonScript(MAP_UID_SCRIPT).
                addRequest(reimportRequest(id(anonSession), untouchedDestination, "AN09")), 400);
        assertTrue(restricted.contains("restricted command 'mapUID'"), restricted);
        assertUntouched();
    }

    /** AN-10: a collaborator on the destination can't apply a custom script: a 403 at submit, and nothing transferred. */
    @Test
    public void testCollaboratorCannotApplyACustomScript() {
        requirePerImportAnon();
        final String error = interfaceFor(collaborator).launchBatchTransferExpectingRejection(new BatchTransferPayload().
                sourceProject(source.getId()).
                anonScript(customScript).
                addRequest(reimportRequest(id(anonSession), untouchedDestination, "AN10")), 403);
        assertTrue(error.contains("edit access to project " + untouchedDestination.getId()), error);
        assertUntouched();
    }

    /** PERM-03: a member of the destination can apply a custom script. */
    @Test
    public void testMemberCanApplyACustomScript() {
        requirePerImportAnon();
        final TransferRunDetail run = runTransfer(interfaceFor(member), new BatchTransferPayload().
                sourceProject(source.getId()).
                anonScript(customScript).
                addRequest(reimportRequest(id(anonSession), plainDestination, "PERM03")));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String copyId = copyId(plainDestination, "PERM03");
        assertItem(run, id(anonSession), TransferItemStatus.DONE, copyId);
        assertProbes(copyId, CUSTOM, CUSTOM, CUSTOM);
    }

    /** AN-12: a script that passes the plugin's checks but fails on the data fails the item and leaves nothing behind. */
    @Test
    public void testScriptThatFailsOnTheDataLeavesNothingBehind() {
        requirePerImportAnon();
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                anonScript(DATA_ERROR_SCRIPT).
                addRequest(reimportRequest(id(anonSession), untouchedDestination, "AN12")));
        assertRunStatus(run, TransferRunStatus.FAILED);
        final TransferRunItem item = assertItem(run, id(anonSession), TransferItemStatus.FAILED, null);
        assertTrue(item.getMessage() != null && item.getMessage().contains("could not be applied"), run.summary());
        assertUntouched();
    }

    private List<Project> allProjects() {
        return Arrays.asList(source, anonDestination, plainDestination, untouchedDestination);
    }

    private void requirePerImportAnon() {
        if (!perImportAnon) {
            throw new SkipException("This XNAT build doesn't take a custom Reimport script (capabilities.per_import_anon is false).");
        }
    }

    private TransferRequest reimportRequest(String itemId, Project destination, String label) {
        return new TransferRequest(TransferMode.REIMPORT, itemId, destination.getId()).
                destinationSubjectLabel(label).
                destinationSessionLabel(label);
    }

    private String reimportAndCheck(Project destination, String label, String anonScript, Boolean replace) {
        return reimportAndCheck(anonSession, destination, label, anonScript, replace);
    }

    /** Reimports one session under the given subject and session label, checks it arrived, and returns the copy's ID. */
    private String reimportAndCheck(MRSession session, Project destination, String label, String anonScript, Boolean replace) {
        final TransferRunDetail run = runTransfer(new BatchTransferPayload().
                sourceProject(source.getId()).
                anonScript(anonScript).
                anonReplacePipeline(replace).
                addRequest(reimportRequest(id(session), destination, label)));
        assertRunStatus(run, TransferRunStatus.COMPLETED);
        final String copyId = copyId(destination, label);
        assertItem(run, id(session), TransferItemStatus.DONE, copyId);
        return copyId;
    }

    private String copyId(Project destination, String label) {
        final String copyId = experimentIds(destination).get(label);
        assertNotNull(copyId, "a session labeled " + label + " in " + destination.getId());
        return copyId;
    }

    /** Checks the probe tags in every file of a copy. A null expectation means the value the source has. */
    private void assertProbes(String copyId, String stationName, String departmentName, String protocolName) {
        for (Attributes copy : headers(copyId)) {
            final Attributes original = anonSourceHeaders.get(copy.getString(Tag.SOPInstanceUID));
            assertNotNull(original, "the copy's instances are the source's");
            assertEquals(copy.getString(Tag.StationName), expected(stationName, original, Tag.StationName), "StationName (0008,1010)");
            assertEquals(copy.getString(Tag.InstitutionalDepartmentName), expected(departmentName, original, Tag.InstitutionalDepartmentName), "InstitutionalDepartmentName (0008,1040)");
            assertEquals(copy.getString(Tag.ProtocolName), expected(protocolName, original, Tag.ProtocolName), "ProtocolName (0018,1030)");
        }
    }

    private static String expected(String value, Attributes original, int tag) {
        return value != null ? value : original.getString(tag);
    }

    /** The source keeps its planted tags and its files, byte for byte. */
    private void assertSourceUnchanged() {
        assertEquals(digests(scanFiles(id(anonSession))), anonSourceDigests, "the source's files are unchanged");
        new SiteScript().validateScriptDidntRun(dicomFiles(id(anonSession)));
        new ProjectScript().validateScriptDidntRun(dicomFiles(id(anonSession)));
    }

    private void assertUntouched() {
        assertEquals(experimentIds(untouchedDestination), new HashMap<String, String>(), "experiments in " + untouchedDestination.getId());
        assertEquals(prearchiveEntries(untouchedDestination), 0, "prearchive entries for " + untouchedDestination.getId());
    }

    private ManifestRow manifestRow(ManifestValidationResult manifest, MRSession session) {
        for (ManifestRow row : manifest.getRows()) {
            if (session.getLabel().equals(row.getSourceSessionLabel())) {
                assertEquals(row.getStatus(), ManifestRowStatus.MATCHED, "row " + row.getRowNumber());
                return row;
            }
        }
        throw new AssertionError("no manifest row for " + session.getLabel());
    }

    private static Map<String, Attributes> bySopInstanceUid(List<Attributes> headers) {
        final Map<String, Attributes> byUid = new HashMap<>();
        for (Attributes header : headers) {
            byUid.put(header.getString(Tag.SOPInstanceUID), header);
        }
        return byUid;
    }

}
