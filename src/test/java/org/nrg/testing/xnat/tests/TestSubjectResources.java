package org.nrg.testing.xnat.tests;

import com.jayway.restassured.http.ContentType;
import org.nrg.testing.FileIOUtils;
import org.nrg.testing.LegacyComparison;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xdat.bean.XnatSubjectdataBean;
import org.nrg.xdat.bean.base.BaseElement;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.enums.Handedness;
import org.nrg.xnat.pogo.Investigator;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.extensions.subject.SubjectXMLPutExtension;
import org.nrg.xnat.rest.SerializationUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static org.testng.AssertJUnit.assertEquals;

public class TestSubjectResources extends BaseXnatRestTest {

    @BeforeMethod
    public void addSubjectResourceTestProject() {
        mainInterface().createProject(testSpecificProject);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteSubjectResourceProject() {
        restDriver.deleteProjectSilently(mainUser, testSpecificProject);
    }

    @Test
    public void testSubjectResourceQueryCRUD() {
        final Project project = testSpecificProject;

        final Subject subject = new Subject(project, "1").
                group("control").
                src("12").
                pi(new Investigator().firstname("Tim").lastname("Olsen")).
                dob(LocalDate.parse("2000-01-01")).
                gender(Gender.MALE).
                handedness(Handedness.LEFT);

        mainCredentials().given().queryParam("format", "xml").queryParam("req_format", "qs").queryParams(SerializationUtils.serializeToMap(subject)).put(restDriver.subjectUrl(subject)).
                then().assertThat().statusCode(201);

        LegacyComparison.compareBeanXML(
                restDriver.saveBinaryResponseToFile(mainCredentials().given().queryParam("format", "xml").get(restDriver.subjectUrl(subject))),
                getDataFile("qs_subject_v1.xml"),
                Collections.singletonMap(XnatSubjectdataBean.class, Collections.singletonList("project"))
        );
    }

    @Test
    public void testSubjectResourceXMLCrud() throws IOException, SAXException {
        final Project project = testSpecificProject;
        final File subjectXML1 = getDataFile("test_subject_v1.xml");
        final File subjectXML2 = getDataFile("test_subject_v2.xml"); // same as v1, except doesn't have education field
        final File subjectXML3 = getDataFile("test_subject_v3.xml"); // same as v3, except contains additional weight and height fields
        final Map<Class<? extends BaseElement>, List<String>> ignoredFields = Collections.singletonMap(XnatSubjectdataBean.class, Collections.singletonList("project"));

        mainInterface().createProject(project);

        // listing should work even if no subjects
        mainCredentials().given().queryParam("format", "html").get(formatRestUrl("projects", project.getId(), "subjects")).then().assertThat().statusCode(200);

        final Subject subject = new Subject(project).extension(new SubjectXMLPutExtension(restDriver.interfaceFor(mainUser), subjectXML1));
        mainInterface().createSubject(subject);

        compareBeanXML(subjectXML1, subject, ignoredFields);

        // listing should work with subjects too
        mainCredentials().given().queryParam("format", "html").get(formatRestUrl("projects", project.getId(), "subjects")).then().assertThat().statusCode(200);

        // resave with v2 XML but allowDataDeletion=false
        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(FileIOUtils.readFile(subjectXML2)).
                put(restDriver.subjectUrl(subject)).then().assertThat().statusCode(200);

        final LegacyComparison comparison = LegacyComparison.compareObjectsFromFile(
                subjectXML2,
                getSubjectXML(subject),
                ignoredFields
        );
        assertEquals(new ArrayList<String>(), comparison.warnings);
        assertEquals(Collections.singletonList("demographicData/education Mismatch: null 12"), comparison.critical);

        // should still match original
        compareBeanXML(subjectXML1, subject, ignoredFields);

        // resave with v2 XML and allowDataDeletion=true
        mainCredentials().given().queryParam("format", "xml").queryParam("allowDataDeletion", true).contentType(ContentType.XML).body(FileIOUtils.readFile(subjectXML2)).
                put(restDriver.subjectUrl(subject)).then().assertThat().statusCode(200);

        // should no longer match original
        final LegacyComparison comparison2 = LegacyComparison.compareObjectsFromFile(
                subjectXML1,
                getSubjectXML(subject),
                ignoredFields
        );
        assertEquals(new ArrayList<String>(), comparison2.warnings);
        assertEquals(Collections.singletonList("demographicData/education Mismatch: 12 null"), comparison2.critical);

        // should match updated XML
        compareBeanXML(subjectXML2, subject, ignoredFields);

        // resave with v3 XML
        mainCredentials().given().queryParam("format", "xml").contentType(ContentType.XML).body(FileIOUtils.readFile(subjectXML3)).
                put(restDriver.subjectUrl(subject)).then().assertThat().statusCode(200);

        final LegacyComparison comparison3 = LegacyComparison.compareObjectsFromFile(
                subjectXML1,
                getSubjectXML(subject),
                ignoredFields
        );

        assertEquals(new ArrayList<String>(), comparison3.warnings);
        Collections.sort(comparison3.critical);
        assertEquals(Arrays.asList("demographicData/height Mismatch: null 12.0", "demographicData/weight Mismatch: null 12.0"), comparison3.critical);

        compareBeanXML(subjectXML3, subject, ignoredFields);

        mainCredentials().given().queryParam("format", "html").get(formatRestUrl("projects", project.getId(), "subjects")).then().assertThat().statusCode(200);

        mainInterface().deleteSubject(subject);

        // make sure subject deleted
        mainCredentials().given().queryParam("format", "xml").get(restDriver.subjectUrl(subject)).then().assertThat().statusCode(404);
    }

    private File getSubjectXML(Subject subject) {
        return restDriver.saveBinaryResponseToFile(mainCredentials().given().queryParam("format", "xml").get(restDriver.subjectUrl(subject)));
    }

    private void compareBeanXML(File subjectXML, Subject subject, Map<Class<? extends BaseElement>, List<String>> ignoredFields) {
        LegacyComparison.compareBeanXML(
                subjectXML,
                getSubjectXML(subject),
                ignoredFields
        );
    }
}
