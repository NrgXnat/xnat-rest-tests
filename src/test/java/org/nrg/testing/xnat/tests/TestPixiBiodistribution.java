package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.users.User;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@TestRequires(plugins="PIXIPlugin")
public class TestPixiBiodistribution extends BaseXnatRestTest {

    private Project project;
    private String originalDateFormat;
    private String originalDateTimeFormat;
    private String originalDatetimeFormatSeconds;

    private static final String UI_DATE_FORMAT_PREF = "uiDateFormat";
    private static final String UI_DATE_TIME_FORMAT_PREF = "uiDateTimeFormat";
    private static final String UI_DATE_TIME_SECONDS_FORMAT_PREF = "uiDateTimeSecondsFormat";

    private static final String BASE_BIODISTRIBUTION_CSV_FILE_LOCATION = "pixi/biodistribution" +
            "/biod_test_working_all_columns_data.csv";
    private static final String REPEATING_DATA_BIODISTRIBUTION_CSV_FILE_LOCATION = "pixi/biodistribution/" +
            "biod_test_repeat_data.csv";
    private static final String LIMITED_BIODISTRIBUTION_DATA_CSV_FILE_LOCATION = "pixi/biodistribution/" +
            "biod_test_empty_columns.csv";

    @BeforeMethod
    public void setup() {
        originalDateFormat = mainAdminInterface().readSiteConfigPreference(UI_DATE_FORMAT_PREF);
        originalDateTimeFormat = mainAdminInterface().readSiteConfigPreference(UI_DATE_TIME_FORMAT_PREF);
        originalDatetimeFormatSeconds = mainAdminInterface().readSiteConfigPreference(UI_DATE_TIME_SECONDS_FORMAT_PREF);
        mainAdminInterface().postSiteConfigProperty(UI_DATE_FORMAT_PREF, "MM/dd/yyyy");
        mainAdminInterface().postSiteConfigProperty(UI_DATE_TIME_FORMAT_PREF, "MM/dd/yyyy HH:mm:ss");
        mainAdminInterface().postSiteConfigProperty(UI_DATE_TIME_SECONDS_FORMAT_PREF,"MM/dd/yyyy HH:mm:ss.SSS");
        project = new Project();
    }

    @AfterMethod
    public void teardown() {
        restDriver.deleteProjectSilently(mainAdminUser, project);
        mainAdminInterface().postSiteConfigProperty(UI_DATE_FORMAT_PREF, originalDateFormat);
        mainAdminInterface().postSiteConfigProperty(UI_DATE_TIME_FORMAT_PREF, originalDateTimeFormat);
        mainAdminInterface().postSiteConfigProperty(UI_DATE_TIME_SECONDS_FORMAT_PREF, originalDatetimeFormatSeconds);
    }

    @Test
    public void testBasicBiodistributionUpload() {
        mainInterface().createProject(project);

        String cachePath = setupFileForUpload(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, mainUser);
        Map<String, String> createdBiodistributionElements = mainInterface()
                .uploadBiodistributionData(project.getId(), cachePath, "throw_error");
        assert(createdBiodistributionElements.size()==3);

        getBiodNamesFromCsv(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, createdBiodistributionElements);
    }

    @Test
    public void testSucceedForAlreadyCreatedSubjects() {
        Subject m1 = new Subject(project, "m1");
        Subject m2 = new Subject(project, "m2");
        Subject m3 = new Subject(project, "m3");
        mainInterface().createProject(project);
        mainInterface().getAccessionNumber(m1);
        mainInterface().getAccessionNumber(m2);
        mainInterface().getAccessionNumber(m3);

        String cachePath = setupFileForUpload(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, mainUser);
        Map<String, String> createdBiodistributionElements = mainInterface()
                .uploadBiodistributionData(project.getId(), cachePath, "throw_error");

        assert(createdBiodistributionElements.size()==3);
        getBiodNamesFromCsv(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, createdBiodistributionElements);

    }

    @Test
    public void testFailureRepeatingData() {
        mainInterface().createProject(project);
        String cachePath = setupFileForUpload(REPEATING_DATA_BIODISTRIBUTION_CSV_FILE_LOCATION, mainUser);
        mainInterface().failDataFormattingBiodistributionData(project.getId(), cachePath, "throw_error");
    }

    @Test
    public void testAlreadyCreatedDataThrowError() {
        mainInterface().createProject(project);

        performInitialUploadForAlreadyCreatedTests("throw_error");

        String secondCachePath = setupFileForUpload(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, mainUser);
        mainInterface().failDataFormattingBiodistributionData(project.getId(), secondCachePath, "throw_error");
    }

    @Test
    public void testAlreadyCreatedDataIgnoreMatching() {
        mainInterface().createProject(project);

        performInitialUploadForAlreadyCreatedTests("ignore_matching");

        String secondCachePath = setupFileForUpload(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, mainUser);
        Map<String, String> createdBiodistributionsAfterSecondUpload =
                mainInterface().uploadBiodistributionData(project.getId(), secondCachePath, "ignore_matching");

        assert (createdBiodistributionsAfterSecondUpload.size() == 2);
        getBiodNamesFromCsv(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, createdBiodistributionsAfterSecondUpload);

    }

    @Test
    public void testAlreadyCreatedDataOverwriteMatching() {
        mainInterface().createProject(project);

        performInitialUploadForAlreadyCreatedTests("upload_overwrite");

        String secondCachePath = setupFileForUpload(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, mainUser);
        Map<String, String> createdBiodistributionsAfterSecondUpload =
                mainInterface().uploadBiodistributionData(project.getId(), secondCachePath, "upload_overwrite");

        assert (createdBiodistributionsAfterSecondUpload.size() == 3);
        getBiodNamesFromCsv(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, createdBiodistributionsAfterSecondUpload);
    }

    @Test
    @TestRequires(users = 3)
    public void testBiodistributionUploadPermissions() {
        User unauthorizedUser = getGenericUser();
        User memberLevelUser = getGenericUser();
        User ownerLevelUser = getGenericUser();

        project.addMember(memberLevelUser);
        project.addOwner(ownerLevelUser);
        mainInterface().createProject(project);

        String unauthorizedCachePath = setupFileForUpload(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, unauthorizedUser);
        interfaceFor(unauthorizedUser).failAuthorizationBiodistributionData(project.getId(), unauthorizedCachePath,"throw_error");

        String memberCachePath = setupFileForUpload(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, memberLevelUser);
        interfaceFor(unauthorizedUser).failAuthorizationBiodistributionData(project.getId(), memberCachePath,"throw_error");

        String ownerCachePath = setupFileForUpload(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, ownerLevelUser);
        Map<String, String> createdBiodistributionElements = interfaceFor(ownerLevelUser)
                .uploadBiodistributionData(project.getId(), ownerCachePath, "throw_error");
        assert(createdBiodistributionElements.size()==3);
        getBiodNamesFromCsv(BASE_BIODISTRIBUTION_CSV_FILE_LOCATION, createdBiodistributionElements);
    }

    private String setupFileForUpload(String fileName, User user) {
        //need to upload the file to the user's cache first
        File biodFile = getDataFile(fileName);
        final String currentTimeForCache = Long.toString(System.currentTimeMillis());
        final String cacheUrl = String.format(
                "/opt/data/cache/resources/%s/files/%s", currentTimeForCache, biodFile.getName());
        interfaceFor(user).queryBase().multiPart(biodFile).put(formatRestUrl(cacheUrl)).then().assertThat()
                .statusCode(200);

        //need to extract the relative path for the file within the user's cache to use it for biod uploader
        return currentTimeForCache + "/" + biodFile.getName();
    }

    private void performInitialUploadForAlreadyCreatedTests(String dataOverlapHandling) {
        String cachePath = setupFileForUpload(LIMITED_BIODISTRIBUTION_DATA_CSV_FILE_LOCATION, mainUser);
        //uploading data for the first time here. this is very basic data
        Map<String, String> createdBiodistributionElements = mainInterface()
                .uploadBiodistributionData(project.getId(), cachePath, dataOverlapHandling);
        assert(createdBiodistributionElements.size()==1);

        getBiodNamesFromCsv(LIMITED_BIODISTRIBUTION_DATA_CSV_FILE_LOCATION, createdBiodistributionElements);
    }

    private void getBiodNamesFromCsv(String csvPath, Map<String, String> createdBiodistributionElements) {
        try {
            Set<String> allSubjectNames = Files.readAllLines(Paths.get("src/test/resources/data", csvPath))
                    .stream().skip(1) //skip header
                    .map(l -> l.split(",")[0])//get subject names (will always be column 0 for test csvs)
                    .collect(Collectors.toSet());
            List<String> allInputBiodistributionNames = new ArrayList<>();
            for(String subj : allSubjectNames) {
                allInputBiodistributionNames.add(subj + "_Biod"); //convert from subj label to biod label
            }
            assert(Objects.requireNonNull(allInputBiodistributionNames).containsAll(createdBiodistributionElements.values()));
        } catch (IOException e) {
            assert(false);
        }
    }
}
