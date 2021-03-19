package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.TestNgUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.SimpleResourceFileExtension;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;
import org.nrg.xnat.pogo.resources.SubjectAssessorResource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

public class TestFileFiltering extends BaseXnatRestTest {

    private final File louieFile = getDataFile("louie.jpg");

    @BeforeMethod
    public void setupFileFilterTest() {
        mainAdminInterface().createProject(testSpecificProject);
    }

    @AfterMethod(alwaysRun = true)
    public void deleteTestProject() {
        restDriver.deleteProjectSilently(mainAdminUser, testSpecificProject);
    }

    @Test
    public void testFileFiltering() {
        final String content1 = "TEST1";
        final String content2 = "TEST2";
        final String content3 = "TEST3";

        final ImagingSession session = new MRSession("MR1");
        final Resource resource = new SubjectAssessorResource(testSpecificProject, testSpecificSubject, session, "TEST");
        resource.addResourceFile(resourceFile("Louie1.jpg", content1));
        resource.addResourceFile(resourceFile("Louie2.jpg", content1));
        resource.addResourceFile(resourceFile("Louie3.jpg", content2));
        resource.addResourceFile(resourceFile("Louie4.jpg", content2));
        resource.addResourceFile(resourceFile("Louie5.jpg", content3));

        final String resourceFilesUrl = formatXnatUrl(resource.resourceUrl(), "resources", resource.getFolder(), "files");

        mainAdminInterface().createSubjectAssessor(testSpecificProject, testSpecificSubject, session);

        // query all
        mainAdminCredentials().given().queryParam("format", "json").get(resourceFilesUrl).then().assertThat().body("ResultSet.Result", Matchers.hasSize(5));

        // query content2
        mainAdminCredentials().given().queryParam("format", "json").queryParam("file_content", content2).get(resourceFilesUrl).then().assertThat().body("ResultSet.Result", Matchers.hasSize(2));

        // query content3
        mainAdminCredentials().given().queryParam("format", "json").queryParam("file_content", content3).get(resourceFilesUrl).then().assertThat().body("ResultSet.Result", Matchers.hasSize(1));

        // Should not be able to retrieve second file for content3
        mainAdminCredentials().expect().statusCode(404).given().queryParam("file_content", content3).queryParam("index", 1).get(resourceFilesUrl);

        TestNgUtils.assertBinaryFilesEqual(
                louieFile,
                restDriver.saveBinaryResponseToFile(mainAdminCredentials().given().queryParam("file_content", content2).queryParam("index", 1).get(resourceFilesUrl))
        );
    }

    private ResourceFile resourceFile(String name, String content) {
        return new ResourceFile().name(name).content(content).extension(new SimpleResourceFileExtension(louieFile));
    }

}
