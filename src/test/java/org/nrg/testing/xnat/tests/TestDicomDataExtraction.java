package org.nrg.testing.xnat.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;
import org.nrg.testing.annotations.Basic;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.enums.TestData;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.scans.CTScan;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.CTSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.rest.SerializationUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

import static org.nrg.testing.TestGroups.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * NOTE: I'm adding these tests because we don't really have any integration tests to check for DICOM extraction into XNAT.
 * The behavior described here is not necessarily "correct", but rather it's what XNAT does. For example, many, many attributes are extracted at the scan (series) level,
 * even though they're fundamentally instance-level attributes [one such example is X-Ray Tube Current (0018,1151) for CT images]. Essentially, I'm adding these tests
 * so we don't lose the DICOM extraction that we have inadvertently; if these tests start to fail because we tackle the issue of modeling DICOM better
 * (and fundamentally change at what level we store a lot of values), then these tests should be retired gladly, and new ones written to reflect the correct behavior.
 *
 * - Charlie, 2018-02-27
 */
@TestRequires(data = {
        TestData.EXTRACTION_DIFFUSION,
        TestData.EXTRACTION_MR,
        TestData.EXTRACTION_CT,
        TestData.EXTRACTION_OPT
})
@Test(groups = {METADATA_EXTRACTION, IMPORTER})
public class TestDicomDataExtraction extends BaseXnatRestTest {

    private static final String STUDY_COMMENTS = "studyComments";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Project testProject = new Project("ScanExtr" + RandomHelper.randomID(8));
    private final Subject testSubject = new Subject(testProject);
    private final MRSession standardMRSession = new MRSession(testProject, testSubject);
    private final MRSession diffusionMR = new MRSession(testProject, testSubject);
    private final CTSession standardCTSession = new CTSession(testProject, testSubject);
    private final ImagingSession optSession = new ImagingSession(testProject, testSubject);

    @BeforeClass
    private void disableAnonAndSetupProject() {
        mainAdminInterface().disableSiteAnonScript();
        new SessionImportExtension(standardMRSession, TestData.EXTRACTION_MR.toFile());
        new SessionImportExtension(diffusionMR, TestData.EXTRACTION_DIFFUSION.toFile());
        new SessionImportExtension(standardCTSession, TestData.EXTRACTION_CT.toFile());
        new SessionImportExtension(optSession, TestData.EXTRACTION_OPT.toFile());
        mainInterface().createProject(testProject);
    }

    @AfterClass(alwaysRun = true)
    private void removeProject() {
        restDriver.deleteProjectSilently(mainAdminUser, testProject);
    }

    @Test(groups = SMOKE)
    @Basic
    public void testMRDicomExtraction() throws IOException {
        checkStudyMatchesAttributes(standardMRSession, "mrSessionAttributes.json");
        checkSeriesMatchesAttributes(new MRScan(standardMRSession, "4"), "mrSeriesAttributes.json");
    }

    /*
    Note: "Tra" orientation is correct from the value in (5200,9229)[0].(0020,9116)[0].(0020,0037), as described by DICOM PS3.3 C.23.3.1.1 and C.7.6.2.1.1
     */
    @Test
    public void testEnhancedMRDiffusionExtraction() throws IOException {
        checkSeriesMatchesAttributes(new MRScan(diffusionMR, "1"), "diffusionScanAttributes.json");
    }

    @Test
    public void testCTDicomExtraction() throws IOException {
        checkSeriesMatchesAttributes(new CTScan(standardCTSession, "3000534"), "ctSeriesAttributes.json");
    }

    @Test
    public void testOPTDicomExtraction() throws IOException {
        checkSeriesMatchesAttributes(new Scan(optSession, "2429001"), "optSeriesAttributes.json");
    }

    private void checkStudyMatchesAttributes(ImagingSession session, String attributesFile) throws IOException {
        final JsonPath studyJsonPath = restDriver.mainInterface().jsonQuery().get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200).and().extract().jsonPath();
        final Map<String, Object> expectedStudyFields = readAttributes(attributesFile);
        if (expectedStudyFields.containsKey(STUDY_COMMENTS)) {
            final Map<String, Object> studyCommentDataFields = studyJsonPath.getMap("items[0].children.find { it.field == 'fields/field' }.items[0].data_fields");
            assertEquals(STUDY_COMMENTS, studyCommentDataFields.get("name"));
            assertEquals(expectedStudyFields.get(STUDY_COMMENTS), studyCommentDataFields.get("field"));
            expectedStudyFields.remove(STUDY_COMMENTS);
        }
        final Map<String, Object> actualStudyDataFields = studyJsonPath.get("items[0].data_fields");
        assertEquals(restDriver.mainInterface().getAccessionNumber(session.getSubject()), actualStudyDataFields.get("subject_ID"));
        assertEquals(session.getSubject().getProject().getId(), actualStudyDataFields.get("project"));
        assertEquals(session.getLabel(), actualStudyDataFields.get("label"));
        assertEquals(session.getAccessionNumber(), actualStudyDataFields.get("ID"));
        assertTrue(actualStudyDataFields.entrySet().containsAll(expectedStudyFields.entrySet()));
    }

    private void checkSeriesMatchesAttributes(Scan scan, String attributesFile) throws IOException {
        final Map<String, Object> expectedSeriesFields = readAttributes(attributesFile);
        final Map<String, Object> actualSeriesDataFields = restDriver.mainInterface().jsonQuery().get(mainInterface().scanUrl(scan)).then().assertThat().statusCode(200).and().extract().path("items[0].data_fields");
        assertEquals(scan.getSession().getAccessionNumber(), actualSeriesDataFields.get("image_session_ID"));
        assertTrue(actualSeriesDataFields.entrySet().containsAll(expectedSeriesFields.entrySet()));
    }

    private Map<String, Object> readAttributes(String attributesFile) throws IOException {
        return objectMapper.readValue(getDataFile(String.format("dicom_extraction/%s", attributesFile)), SerializationUtils.MAP_TYPE_REF);
    }

}
