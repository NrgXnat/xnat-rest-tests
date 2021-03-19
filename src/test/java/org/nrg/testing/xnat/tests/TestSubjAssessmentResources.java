package org.nrg.testing.xnat.tests;

import com.jayway.restassured.http.ContentType;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.LegacyComparison;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xdat.bean.XnatMrscandataBean;
import org.nrg.xdat.bean.XnatMrsessiondataBean;
import org.nrg.xdat.bean.base.BaseElement;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SubjectAssessorXMLExtension;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.testng.AssertJUnit.assertEquals;

public class TestSubjAssessmentResources extends BaseXnatRestTest {

    private Project currentProject;
    private Subject subject1;

    @BeforeMethod
    public void setupCurrentProject() {
        currentProject = new Project();
        subject1 = new Subject(currentProject, "1");
        mainInterface().createProject(currentProject);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteCurrentProject() {
        restDriver.deleteProjectSilently(mainUser, currentProject);
    }

    @Test
    public void testDateFormatValidation() {
        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(readDataFile("test_expt_bad_date.xml")).
                post(experimentsUrl()).then().assertThat().statusCode(400);
    }

    @Test
    public void testIntegerFormatValidation() {
        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(readDataFile("test_expt_bad_int.xml")).
                post(experimentsUrl()).then().assertThat().statusCode(400);
    }

    // @Test Not supported yet
    public void testURIFormatValidation() {
        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(readDataFile("test_expt_bad_URI.xml")).
                post(experimentsUrl()).then().assertThat().statusCode(400); // contains space in URI => invalid

        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(readDataFile("test_expt_bad_URI2.xml")).
                post(experimentsUrl()).then().assertThat().statusCode(400); // contains path outside project => invalid

        final MRSession uriWithRelativePath = new MRSession(currentProject, subject1).extension(new SubjectAssessorXMLExtension(getDataFile("test_expt_bad_URI3.xml")));
        final MRSession uriWithWindowsSlash = new MRSession(currentProject, subject1).extension(new SubjectAssessorXMLExtension(getDataFile("test_expt_bad_URI4.xml")));

        mainInterface().createSubjectAssessor(uriWithRelativePath);
        mainInterface().createSubjectAssessor(uriWithWindowsSlash);

        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(readDataFile("test_expt_bad_URI5.xml")).
                post(experimentsUrl()).then().assertThat().statusCode(400); // contains parent directory syntax => invalid
    }

    @Test
    public void testLengthValidation() {
        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(readDataFile("test_expt_bad_length.xml")).
                post(experimentsUrl()).then().assertThat().statusCode(400);
    }

    @Test
    public void testInvalidSubjectValidation() {
        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(readDataFile("test_expt_v1.xml")).
                post(formatRestUrl("projects", currentProject.getId(), "subjects", "INVALIDSUBJECT", "experiments")).then().assertThat().statusCode(404);
    }

    @Test
    public void testSubjectAssessmentXMLCRUD() throws IOException, SAXException {
        final File experimentV1 = getDataFile("test_expt_v1.xml");
        final File experimentV2 = getDataFile("test_expt_v2.xml");
        final ImagingSession session = new MRSession(currentProject, subject1).extension(new SubjectAssessorXMLExtension(experimentV1));

        mainCredentials().given().queryParam("format", "html").get(experimentsUrl()).then().assertThat().statusCode(200);
        mainInterface().createSubjectAssessor(session);

        final Map<Class<? extends BaseElement>, List<String>> fieldsToIgnore = new HashMap<>();
        fieldsToIgnore.put(XnatMrsessiondataBean.class, Collections.singletonList("project"));
        fieldsToIgnore.put(XnatMrscandataBean.class, Collections.singletonList("project"));

        LegacyComparison.compareBeanXML(
                experimentV1,
                restDriver.saveBinaryResponseToFile(mainCredentials().queryParam("format", "xml").get(mainInterface().subjectAssessorUrl(session))),
                fieldsToIgnore
        );

        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(FileIOUtils.readFile(experimentV2)).
                put(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(200);

        mainCredentials().given().queryParam("format", "html").get(experimentsUrl()).then().assertThat().statusCode(200);

        LegacyComparison.compareBeanXML(
                experimentV2,
                restDriver.saveBinaryResponseToFile(mainCredentials().queryParam("format", "xml").get(mainInterface().subjectAssessorUrl(session))),
                fieldsToIgnore
        );

        final LegacyComparison comparison = LegacyComparison.compareObjectsFromFile(
                experimentV1,
                restDriver.saveBinaryResponseToFile(mainCredentials().queryParam("format", "xml").get(mainInterface().subjectAssessorUrl(session))),
                fieldsToIgnore
        );

        assertEquals(new ArrayList<String>(), comparison.warnings);
        assertEquals(Collections.singletonList("mrSessionData/coil Mismatch: test test2"), comparison.critical);

        TimeUtils.sleep(1000); // cache update
        mainInterface().deleteSubjectAssessor(session);

        mainCredentials().given().queryParam("format", "html").get(experimentsUrl()).then().assertThat().statusCode(200);

        mainCredentials().given().queryParam("format", "xml").get(mainInterface().subjectAssessorUrl(session)).then().assertThat().statusCode(404);
    }

    private String experimentsUrl() {
        return formatRestUrl("projects", currentProject.getId(), "subjects", subject1.getLabel(), "experiments");
    }

}
