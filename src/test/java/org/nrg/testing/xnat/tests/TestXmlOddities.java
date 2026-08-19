package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import io.restassured.http.Method;
import org.hamcrest.Matchers;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.versions.Xnat_1_8_9_1;
import org.nrg.xnat.versions.Xnat_1_10_1;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertTrue;

@Test(groups = TestGroups.XML)
@AddedIn(Xnat_1_8_9_1.class)
public class TestXmlOddities extends BaseXnatRestTest {

    private final Project project = registerTempProject();
    private final Subject subject = new Subject(project);
    private final MRSession session = new MRSession(project, subject);
    private final String XML_CONTENT = readDataFile("qc_with_prov.xml");
    private static final String GROUP_BEFORE = "xmlRoundTripBefore";
    private static final String GROUP_AFTER = "xmlRoundTripAfter";
    private static final String TIMESTAMP_SPACE = "2020-01-01 01:02:03";
    private static final String TIMESTAMP_T = "2020-01-01T01:02:03";
    private static final String TIMESTAMP_T_Z = "2020-01-01T01:02:03Z";

    @BeforeClass(groups = TestGroups.XML)
    private void setupSharedProject() {
        mainInterface().createProject(project);
    }

    public void testXmlTimestampSpaceCreate() {
        testXmlTimestamp(true, TIMESTAMP_SPACE);
    }

    public void testXmlTimestampTCreate() {
        testXmlTimestamp(true, TIMESTAMP_T);
    }

    public void testXmlTimestampTZCreate() {
        testXmlTimestamp(true, TIMESTAMP_T_Z);
    }

    public void testXmlTimestampSpaceEdit() {
        testXmlTimestamp(false, TIMESTAMP_SPACE);
    }

    public void testXmlTimestampTEdit() {
        testXmlTimestamp(false, TIMESTAMP_T);
    }

    public void testXmlTimestampTZEdit() {
        testXmlTimestamp(false, TIMESTAMP_T_Z);
    }

    private void testXmlTimestamp(boolean createWithTimestamp, String serializedTimestamp) {
        final QC qc = new QC(project, subject, session);
        if (!createWithTimestamp) {
            mainInterface().createSessionAssessor(qc);
        }
        final String realizedXmlContent = XML_CONTENT
                .replace("%ID%", createWithTimestamp ? "" : qc.getAccessionNumber())
                .replace("%PROJECT%", project.getId())
                .replace("%LABEL%", qc.getLabel())
                .replace("%SESSION_ID%", session.getAccessionNumber())
                .replace("%TIMESTAMP%", serializedTimestamp);
        putXml(qc, realizedXmlContent);

        assertTrue(
                mainInterface()
                        .xmlQuery()
                        .get(mainInterface().sessionAssessorUrl(qc))
                        .asString()
                        .contains(TIMESTAMP_T)
        );
    }

    /**
     * The upload endpoint validates before doing any archive work, and the validator must reject DOCTYPE
     * declarations up front rather than processing them.
     */
    @AddedIn(Xnat_1_10_1.class)
    @TestedApiSpec(method = Method.PUT, url = "/xapi/archive/upload/xml")
    public void testXmlUploadRejectsDoctype() {
        mainQueryBase()
                .contentType(ContentType.XML)
                .queryParam("allowDataDeletion", false)
                .body(readDataFile("doctype_upload.xml"))
                .put(formatXapiUrl("archive", "upload", "xml"))
                .then()
                .assertThat()
                // Not pinned to 400: XapiRestControllerAdvice currently discards the explicit status whenever an
                // exception is passed, so this surfaces as a 500. Any error status means the upload was refused.
                .statusCode(Matchers.greaterThanOrEqualTo(400))
                .body(Matchers.containsString("DOCTYPE is disallowed"));
    }

    /**
     * The rejection test above never reaches schema resolution, since a DOCTYPE is refused while scanning the prolog.
     * This one does, and it's the only test in the suite that does: the /data endpoints parse with SAXReader, which
     * performs no schema validation at all, so nothing else here loads the schema set.
     *
     * A failure mentioning {@code schema_reference} means the parser's allowed protocols no longer cover wherever XNAT
     * resolved its schemas to. Uses its own subject so the shared one is untouched, and round-trips XNAT's own
     * serialization rather than a hand-written fixture, which keeps element ordering out of the picture.
     */
    @TestedApiSpec(method = Method.PUT, url = "/xapi/archive/upload/xml")
    public void testXmlUploadRoundTripsSubject() {
        final Subject roundTripped = new Subject(project).group(GROUP_BEFORE);
        mainInterface().createSubject(roundTripped);

        final String exported = mainInterface()
                .xmlQuery()
                .get(mainInterface().subjectUrl(roundTripped))
                .asString();

        mainQueryBase()
                .contentType(ContentType.XML)
                .queryParam("allowDataDeletion", false)
                .body(exported.replace(GROUP_BEFORE, GROUP_AFTER))
                .put(formatXapiUrl("archive", "upload", "xml"))
                .then()
                .assertThat()
                .statusCode(200);

        // Confirms the document was applied rather than merely accepted.
        assertTrue(
                mainInterface()
                        .xmlQuery()
                        .get(mainInterface().subjectUrl(roundTripped))
                        .asString()
                        .contains(GROUP_AFTER)
        );
    }

    private void putXml(QC qc, String xmlBody) {
        mainQueryBase()
                .contentType(ContentType.XML)
                .body(xmlBody)
                .put(mainInterface().sessionAssessorUrl(qc))
                .then()
                .assertThat()
                .statusCode(Matchers.oneOf(200, 201));
    }

}
