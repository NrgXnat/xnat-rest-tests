package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.FileIOUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.nrg.xnat.pogo.DataType.*;
import static org.testng.AssertJUnit.assertEquals;

public class TestSearchGeneration extends BaseSearchTest {

    private final Project commonProject = registerTempProject();

    @BeforeClass
    protected void initProject() {
        final Subject subject = new Subject(commonProject);
        new MRSession(commonProject, subject);
        mainInterface().createProject(commonProject);
    }

    @Test
    public void testSearchXmlGenerationProject() {
        assertXmlFromFile("default_project_datatype_search.xml", mainInterface().getDefaultSearch(PROJECT));
    }

    @Test
    public void testSearchXmlGenerationSubject() {
        final Project projectWithoutMrSessions = registerTempProject().addSubject(new Subject());
        mainInterface().createProject(projectWithoutMrSessions);
        assertXmlFromFile(
                "default_project_subject_search.xml",
                mainInterface().getDefaultSearch(projectWithoutMrSessions, SUBJECT),
                projectWithoutMrSessions
        );
        assertXmlFromFile(
                "default_project_subject_with_mr_search.xml",
                mainInterface().getDefaultSearch(commonProject, SUBJECT),
                commonProject
        );
        assertXmlFromFile("default_site_subject_search.xml", mainInterface().getDefaultSearch(SUBJECT));
    }

    @Test
    public void testSearchXmlGenerationMrSession() {
        assertXmlFromFile(
                "default_project_mr_session_search.xml",
                mainInterface().getDefaultSearch(commonProject, MR_SESSION),
                commonProject
        );
        assertXmlFromFile("default_site_mr_session_search.xml", mainInterface().getDefaultSearch(MR_SESSION));
    }

    @Test
    @AddedIn(Xnat_1_8_5.class) // see XNAT-7037
    public void testSearchXmlGenerationMrScan() {
        mainAdminInterface().setupDataType(MR_SCAN);

        assertXmlFromFile(
                "default_project_mr_scan_search.xml",
                mainInterface().getDefaultSearch(commonProject, MR_SCAN),
                commonProject
        );
        assertXmlFromFile("default_site_mr_scan_search.xml", mainInterface().getDefaultSearch(MR_SCAN));
    }

    @Test
    public void testSearchXmlGenerationQc() {
        assertXmlFromFile(
                "default_project_manual_qc_search.xml",
                mainInterface().getDefaultSearch(commonProject, MANUAL_QC),
                commonProject
        );
        assertXmlFromFile("default_site_manual_qc_search.xml", mainInterface().getDefaultSearch(MANUAL_QC));
    }

    @Test
    @TestRequires(plugins = "datasetsPlugin") // no concrete implementation of project assessors currently exist in core XNAT
    public void testSearchXmlGenerationDatasetDefinition() {
        final DataType datasetDefinition = new DataType().xsiType("sets:definition");

        assertXmlFromFile(
                "default_project_dataset_defn_search.xml",
                mainInterface().getDefaultSearch(commonProject, datasetDefinition),
                commonProject
        );
        assertXmlFromFile("default_site_dataset_defn_search.xml", mainInterface().getDefaultSearch(datasetDefinition));
    }

    private void genericizeXmlAndAssertEqual(String expectedXml, XnatSearchDocument searchXml) {
        assertEquals(expectedXml, searchXml.genericizedXml());
    }

    private void assertXmlFromFile(String expectedXmlFileName, XnatSearchDocument searchXml) {
        genericizeXmlAndAssertEqual(
                FileIOUtils.readFile(searchFile(expectedXmlFileName)),
                searchXml
        );
    }

    private void assertXmlFromFile(String expectedXmlFileName, XnatSearchDocument searchXml, Project project) {
        genericizeXmlAndAssertEqual(
                FileIOUtils.readFile(searchFile(expectedXmlFileName)).replace("%PROJECT_ID%", project.getId()),
                searchXml
        );
    }

}
