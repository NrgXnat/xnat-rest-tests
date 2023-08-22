package org.nrg.testing.xnat.tests;

import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.nrg.testing.TestGroups;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.versions.Xnat_1_8_9_1;
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
