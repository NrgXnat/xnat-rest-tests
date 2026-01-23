package org.nrg.testing.xnat.tests.data;

import io.restassured.response.Response;
import org.nrg.testing.CommonStringUtils;
import org.nrg.testing.TestGroups;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.HardDependency;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.Users;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Share;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.pogo.users.UserGroups;
import org.nrg.xnat.versions.Xnat_1_10_0;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.http.Method.*;
import static org.hamcrest.Matchers.*;
import static org.testng.AssertJUnit.*;

/**
 * Comprehensive integration tests for custom data type lifecycle.
 *
 * Tests the full lifecycle of custom data types created via the DataTypesApi:
 * - Creating custom data types for each extension point
 * - Creating experiments/assets of those types
 * - Uploading and deleting files
 * - Sharing experiments between projects
 * - Modifying experiment fields (date)
 * - Permission testing (owner vs member vs collaborator)
 * - Schema deletion protection when experiments exist
 * - Deleting experiments
 *
 * Extension points tested:
 * - xnat:subjectAssessorData
 * - xnat:imageAssessorData
 * - xnat:abstractProjectAsset
 */
@AddedIn(Xnat_1_10_0.class)
@Test(groups = {TestGroups.DATA_TYPES, TestGroups.DB_SCHEMAS, TestGroups.PERMISSIONS})
public class TestCustomDataTypeLifecycle extends BaseXnatRestTest {

    // API URLs
    private static final String DATATYPES_URL = "/xapi/datatypes";
    private static final String DBSCHEMAS_URL = "/xapi/dbschemas";

    // Test data
    private final File dummyFile = getDataFile("dummy.txt");
    private final String testRunId = RandomHelper.randomID(6);

    // Custom data type names - unique per test run to avoid conflicts
    private String subjectAssessorTypeName;
    private String subjectAssessorXsiType;
    private String imageAssessorTypeName;
    private String imageAssessorXsiType;
    private String projectAssetTypeName;
    private String projectAssetXsiType;

    // Schema IDs for cleanup
    private Long subjectAssessorSchemaId;
    private Long imageAssessorSchemaId;
    private Long projectAssetSchemaId;

    // Test users
    private User ownerUser;
    private User memberUser;
    private User collaboratorUser;

    // Test projects
    private Project primaryProject;
    private Project secondaryProject;

    // Test subjects and sessions
    private Subject testSubject;
    private ImagingSession testMRSession;

    // Experiment IDs created during tests
    private String subjectAssessorExptId;
    private String imageAssessorExptId;
    private String projectAssetId;

    @BeforeClass
    public void setupTestEnvironment() {
        // Generate unique type names
        subjectAssessorTypeName = "TestSubjAssessor" + testRunId;
        imageAssessorTypeName = "TestImgAssessor" + testRunId;
        projectAssetTypeName = "TestProjAsset" + testRunId;

        // Create test users
        ownerUser = Users.genericAccount();
        memberUser = Users.genericAccount();
        collaboratorUser = Users.genericAccount();

        mainAdminInterface().createUser(ownerUser);
        mainAdminInterface().createUser(memberUser);
        mainAdminInterface().createUser(collaboratorUser);

        // Create test projects with different user roles
        primaryProject = new Project("TestDT_Primary_" + testRunId)
                .accessibility(Accessibility.PRIVATE)
                .addOwner(ownerUser)
                .addMember(memberUser)
                .addCollaborator(collaboratorUser);

        secondaryProject = new Project("TestDT_Secondary_" + testRunId)
                .accessibility(Accessibility.PRIVATE)
                .addOwner(ownerUser)
                .addMember(memberUser)
                .addCollaborator(collaboratorUser);

        mainAdminInterface().createProject(primaryProject);
        mainAdminInterface().createProject(secondaryProject);

        // Create test subject
        testSubject = new Subject(primaryProject, "TestSubject1")
                .gender(Gender.MALE);
        mainAdminInterface().createSubject(testSubject);

        // Create test MR session for image assessor testing
        testMRSession = new MRSession(primaryProject, testSubject, "TestMR1")
                .date(LocalDate.parse("2024-01-15"));
        mainAdminInterface().createSubjectAssessor(testMRSession);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupTestEnvironment() {
        // Delete experiments first
        deleteExperimentSilently(subjectAssessorExptId);
        deleteExperimentSilently(imageAssessorExptId);
        deleteProjectAssetSilently(projectAssetId);

        // Delete projects (which cleans up subjects, sessions, etc.)
        restDriver.deleteProjectSilently(mainAdminUser, primaryProject);
        restDriver.deleteProjectSilently(mainAdminUser, secondaryProject);

        // Delete schemas (only if no experiments remain)
        deleteSchemaIfEmpty(subjectAssessorSchemaId);
        deleteSchemaIfEmpty(imageAssessorSchemaId);
        deleteSchemaIfEmpty(projectAssetSchemaId);
    }

    // ==================== Data Type Creation Tests ====================

    @Test(priority = 1)
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/create")
    public void test01_CreateSubjectAssessorDataType() {
        Response response = mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("name", subjectAssessorTypeName)
                .queryParam("singular", "Test Subject Assessor")
                .queryParam("plural", "Test Subject Assessors")
                .queryParam("extends", "subjectAssessorData")
                .post(DATATYPES_URL + "/create")
                .then()
                .assertThat()
                .statusCode(201)
                .body("complexType", notNullValue())
                .extract().response();

        String complexType = response.jsonPath().getString("complexType");
        assertNotNull("Complex type should be returned", complexType);

        // Extract xsiType from complex type (format: prefix:complexType)
        subjectAssessorXsiType = complexType.contains(":") ? complexType : "custom:" + complexType;

        // Find the schema ID for later tests
        subjectAssessorSchemaId = findSchemaIdByTypeName(subjectAssessorTypeName);
    }

    @Test(priority = 2)
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/create")
    public void test02_CreateImageAssessorDataType() {
        Response response = mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("name", imageAssessorTypeName)
                .queryParam("singular", "Test Image Assessor")
                .queryParam("plural", "Test Image Assessors")
                .queryParam("extends", "imageAssessorData")
                .post(DATATYPES_URL + "/create")
                .then()
                .assertThat()
                .statusCode(201)
                .body("complexType", notNullValue())
                .extract().response();

        String complexType = response.jsonPath().getString("complexType");
        imageAssessorXsiType = complexType.contains(":") ? complexType : "custom:" + complexType;

        imageAssessorSchemaId = findSchemaIdByTypeName(imageAssessorTypeName);
    }

    @Test(priority = 3)
    @TestedApiSpec(method = POST, url = "/xapi/datatypes/create")
    public void test03_CreateProjectAssetDataType() {
        Response response = mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("name", projectAssetTypeName)
                .queryParam("singular", "Test Project Asset")
                .queryParam("plural", "Test Project Assets")
                .queryParam("extends", "abstractProjectAsset")
                .post(DATATYPES_URL + "/create")
                .then()
                .assertThat()
                .statusCode(201)
                .body("complexType", notNullValue())
                .extract().response();

        String complexType = response.jsonPath().getString("complexType");
        projectAssetXsiType = complexType.contains(":") ? complexType : "custom:" + complexType;

        projectAssetSchemaId = findSchemaIdByTypeName(projectAssetTypeName);

    }

    // ==================== Experiment Creation Tests ====================

    @Test(priority = 10)
    @HardDependency("test01_CreateSubjectAssessorDataType")
    public void test10_CreateSubjectAssessorExperiment() {
        assumeNotNull("Subject assessor xsiType should be set", subjectAssessorXsiType);

        String label = "SubjAssessor_" + testRunId;
        String url = formatRestUrl("projects", primaryProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", label);

        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("xsiType", subjectAssessorXsiType)
                .queryParam("date", "2024-06-15")
                .put(url)
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().response();

        // Get the experiment ID
        subjectAssessorExptId = getExperimentId(primaryProject, label);
        assertNotNull("Subject assessor experiment should be created", subjectAssessorExptId);
    }

    @Test(priority = 11)
    @HardDependency("test02_CreateImageAssessorDataType")
    public void test11_CreateImageAssessorExperiment() {
        assumeNotNull("Image assessor xsiType should be set", imageAssessorXsiType);

        String label = "ImgAssessor_" + testRunId;
        String url = formatRestUrl("projects", primaryProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", label);

        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("xsiType", imageAssessorXsiType)
                .queryParam("date", "2024-06-16")
                .put(url)
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().response();

        imageAssessorExptId = getImageAssessorId(primaryProject, testMRSession, label);
        assertNotNull("Image assessor experiment should be created", imageAssessorExptId);
    }

    @Test(priority = 12)
    @HardDependency("test03_CreateProjectAssetDataType")
    public void test12_CreateProjectAssetExperiment() {
        assumeNotNull("Project asset xsiType should be set", projectAssetXsiType);

        String label = "ProjAsset_" + testRunId;
        String url = formatRestUrl("projects", primaryProject.getId(),
                "resources", label);

        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("xsiType", projectAssetXsiType)
                .queryParam("label", label)
                .put(url)
                .then()
                .assertThat()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                .extract().response();

        projectAssetId = label;
    }

    // ==================== File Upload Tests ====================

    @Test(priority = 20)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test20_UploadFileToSubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        String fileUrl = formatRestUrl("experiments", subjectAssessorExptId,
                "resources", "TEST_RESOURCE", "files", "test1.txt");

        restDriver.queryBaseFor(ownerUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify file exists
        restDriver.queryBaseFor(ownerUser)
                .get(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 21)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test21_UploadFileToImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        String fileUrl = formatRestUrl("experiments", imageAssessorExptId,
                "resources", "TEST_RESOURCE", "files", "test1.txt");

        restDriver.queryBaseFor(ownerUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify file exists
        restDriver.queryBaseFor(ownerUser)
                .get(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 22)
    @HardDependency("test12_CreateProjectAssetExperiment")
    public void test22_UploadFileToProjectAsset() {
        assumeNotNull("Project asset should exist", projectAssetId);

        String fileUrl = formatRestUrl("projects", primaryProject.getId(),
                "resources", projectAssetId, "files", "test1.txt");

        restDriver.queryBaseFor(ownerUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify file exists by retrieving it
        restDriver.queryBaseFor(ownerUser)
                .get(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    // ==================== Permission Tests - Owner ====================

    @Test(priority = 30)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test30_OwnerCanReadSubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 31)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test31_OwnerCanModifySubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        String newDate = "2024-07-20";

        // Modify the date field
        restDriver.queryBaseFor(ownerUser)
                .queryParam("date", newDate)
                .put(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the date was updated
        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        String actualDate = response.jsonPath().getString("items[0].data_fields.date");
        assertEquals("Date should be updated to " + newDate, newDate, actualDate);
    }

    @Test(priority = 32)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test32_OwnerCanReadImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 33)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test33_OwnerCanModifyImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        String newDate = "2024-07-21";

        restDriver.queryBaseFor(ownerUser)
                .queryParam("date", newDate)
                .put(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the date was updated
        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        String actualDate = response.jsonPath().getString("items[0].data_fields.date");
        assertEquals("Date should be updated to " + newDate, newDate, actualDate);
    }

    // ==================== Permission Tests - Member ====================

    @Test(priority = 40)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test40_MemberCanReadSubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        restDriver.queryBaseFor(memberUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 41)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test41_MemberCanModifySubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        String newDate = "2024-08-15";

        // Members should be able to edit
        restDriver.queryBaseFor(memberUser)
                .queryParam("date", newDate)
                .put(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the date was updated
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        String actualDate = response.jsonPath().getString("items[0].data_fields.date");
        assertEquals("Date should be updated to " + newDate, newDate, actualDate);
    }

    @Test(priority = 42)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test42_MemberCannotDeleteSubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        // Members should NOT be able to delete
        restDriver.queryBaseFor(memberUser)
                .delete(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test(priority = 43)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test43_MemberCanReadImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        restDriver.queryBaseFor(memberUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 44)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test44_MemberCanModifyImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        String newDate = "2024-08-16";

        restDriver.queryBaseFor(memberUser)
                .queryParam("date", newDate)
                .put(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the date was updated
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        String actualDate = response.jsonPath().getString("items[0].data_fields.date");
        assertEquals("Date should be updated to " + newDate, newDate, actualDate);
    }

    @Test(priority = 45)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test45_MemberCannotDeleteImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        restDriver.queryBaseFor(memberUser)
                .delete(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    // ==================== Permission Tests - Collaborator ====================

    @Test(priority = 50)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test50_CollaboratorCanReadSubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        restDriver.queryBaseFor(collaboratorUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 51)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test51_CollaboratorCannotModifySubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        // Collaborators should NOT be able to modify
        restDriver.queryBaseFor(collaboratorUser)
                .queryParam("date", "2024-09-01")
                .put(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test(priority = 52)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test52_CollaboratorCannotDeleteSubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        restDriver.queryBaseFor(collaboratorUser)
                .delete(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test(priority = 53)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test53_CollaboratorCanReadImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        restDriver.queryBaseFor(collaboratorUser)
                .queryParam("format", "json")
                .get(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 54)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test54_CollaboratorCannotModifyImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        restDriver.queryBaseFor(collaboratorUser)
                .queryParam("date", "2024-09-02")
                .put(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test(priority = 55)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test55_CollaboratorCannotDeleteImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        restDriver.queryBaseFor(collaboratorUser)
                .delete(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test(priority = 56)
    @HardDependency("test12_CreateProjectAssetExperiment")
    public void test56_CollaboratorCannotModifyProjectAsset() {
        assumeNotNull("Project asset should exist", projectAssetId);

        // Collaborators should NOT be able to upload files to the asset
        String fileUrl = formatRestUrl("projects", primaryProject.getId(),
                "resources", projectAssetId, "files", "collaborator_test.txt");

        restDriver.queryBaseFor(collaboratorUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(403);
    }

    // ==================== Sharing Tests ====================

    @Test(priority = 60)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    public void test60_ShareSubjectAssessorToSecondaryProject() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        // First share the subject
        String shareSubjectUrl = formatRestUrl("projects", primaryProject.getId(),
                "subjects", testSubject.getLabel(),
                "projects", secondaryProject.getId());

        restDriver.queryBaseFor(ownerUser)
                .queryParam("label", "SharedSubject_" + testRunId)
                .put(shareSubjectUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Then share the experiment
        String shareExptUrl = formatRestUrl("experiments", subjectAssessorExptId,
                "projects", secondaryProject.getId());

        restDriver.queryBaseFor(ownerUser)
                .queryParam("label", "SharedSubjAssessor_" + testRunId)
                .put(shareExptUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the experiment is accessible in secondary project
        restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(formatRestUrl("projects", secondaryProject.getId(),
                        "experiments", "SharedSubjAssessor_" + testRunId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 61)
    @HardDependency("test11_CreateImageAssessorExperiment")
    public void test61_ShareImageAssessorToSecondaryProject() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        // Share the image assessor
        String shareUrl = formatRestUrl("experiments", imageAssessorExptId,
                "projects", secondaryProject.getId());

        restDriver.queryBaseFor(ownerUser)
                .queryParam("label", "SharedImgAssessor_" + testRunId)
                .put(shareUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    // ==================== File Delete Tests ====================

    @Test(priority = 70)
    @HardDependency("test20_UploadFileToSubjectAssessor")
    public void test70_DeleteFileFromSubjectAssessor() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        String fileUrl = formatRestUrl("experiments", subjectAssessorExptId,
                "resources", "TEST_RESOURCE", "files", "test1.txt");

        restDriver.queryBaseFor(ownerUser)
                .delete(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify file is deleted
        restDriver.queryBaseFor(ownerUser)
                .get(fileUrl)
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test(priority = 71)
    @HardDependency("test21_UploadFileToImageAssessor")
    public void test71_DeleteFileFromImageAssessor() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        String fileUrl = formatRestUrl("experiments", imageAssessorExptId,
                "resources", "TEST_RESOURCE", "files", "test1.txt");

        restDriver.queryBaseFor(ownerUser)
                .delete(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    // ==================== Schema Deletion Protection Tests ====================

    @Test(priority = 80)
    @HardDependency("test10_CreateSubjectAssessorExperiment")
    @TestedApiSpec(method = DELETE, url = "/xapi/dbschemas/{id}")
    public void test80_CannotDeleteSchemaWithExperiments() {
        assumeNotNull("Schema ID should exist", subjectAssessorSchemaId);
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        // Attempt to delete schema while experiments exist - should fail
        mainAdminInterface()
                .requestWithCsrfToken()
                .delete(DBSCHEMAS_URL + "/" + subjectAssessorSchemaId)
                .then()
                .assertThat()
                .statusCode(500)
                .body("error", containsString("experiments"));
    }

    // ==================== Experiment Deletion Tests ====================

    @Test(priority = 90)
    @HardDependency("test60_ShareSubjectAssessorToSecondaryProject")
    public void test90_UnshareSubjectAssessorFromSecondaryProject() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        // Unshare from secondary project
        String unshareUrl = formatRestUrl("experiments", subjectAssessorExptId,
                "projects", secondaryProject.getId());

        restDriver.queryBaseFor(ownerUser)
                .delete(unshareUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test(priority = 91)
    @HardDependency("test90_UnshareSubjectAssessorFromSecondaryProject")
    public void test91_DeleteSubjectAssessorExperiment() {
        assumeNotNull("Subject assessor experiment should exist", subjectAssessorExptId);

        // Owner should be able to delete
        restDriver.queryBaseFor(ownerUser)
                .queryParam("removeFiles", true)
                .delete(formatRestUrl("experiments", subjectAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);

        subjectAssessorExptId = null; // Mark as deleted
    }

    @Test(priority = 92)
    @HardDependency("test61_ShareImageAssessorToSecondaryProject")
    public void test92_DeleteImageAssessorExperiment() {
        assumeNotNull("Image assessor experiment should exist", imageAssessorExptId);

        // Unshare first
        String unshareUrl = formatRestUrl("experiments", imageAssessorExptId,
                "projects", secondaryProject.getId());

        restDriver.queryBaseFor(ownerUser)
                .delete(unshareUrl);

        // Then delete
        restDriver.queryBaseFor(ownerUser)
                .queryParam("removeFiles", true)
                .delete(formatRestUrl("experiments", imageAssessorExptId))
                .then()
                .assertThat()
                .statusCode(200);

        imageAssessorExptId = null;
    }

    @Test(priority = 93)
    @HardDependency("test12_CreateProjectAssetExperiment")
    public void test93_DeleteProjectAsset() {
        assumeNotNull("Project asset should exist", projectAssetId);

        String deleteUrl = formatRestUrl("projects", primaryProject.getId(),
                "resources", projectAssetId);

        restDriver.queryBaseFor(ownerUser)
                .delete(deleteUrl)
                .then()
                .assertThat()
                .statusCode(200);

        projectAssetId = null;
    }

    // ==================== Schema Deletion After Experiments Deleted ====================

    @Test(priority = 100)
    @HardDependency("test91_DeleteSubjectAssessorExperiment")
    @TestedApiSpec(method = DELETE, url = "/xapi/dbschemas/{id}")
    public void test100_CanDeleteSchemaAfterExperimentsDeleted() {
        assumeNotNull("Schema ID should exist", subjectAssessorSchemaId);

        // Now that experiments are deleted, schema deletion should succeed
        mainAdminInterface()
                .requestWithCsrfToken()
                .delete(DBSCHEMAS_URL + "/" + subjectAssessorSchemaId)
                .then()
                .assertThat()
                .statusCode(200);

        subjectAssessorSchemaId = null;
    }

    // ==================== Helper Methods ====================

    private Long findSchemaIdByTypeName(String typeName) {
        Response response = mainAdminInterface()
                .requestWithCsrfToken()
                .get(DBSCHEMAS_URL)
                .then()
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");
        for (Map<String, Object> schema : schemas) {
            String name = (String) schema.get("name");
            if (name != null && name.toLowerCase().contains(typeName.toLowerCase())) {
                return ((Number) schema.get("id")).longValue();
            }
        }
        return null;
    }

    private String getExperimentId(Project project, String label) {
        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(formatRestUrl("projects", project.getId(), "experiments", label))
                .then()
                .extract().response();

        if (response.statusCode() == 200) {
            return response.jsonPath().getString("items[0].data_fields.ID");
        }
        return null;
    }

    private String getImageAssessorId(Project project, ImagingSession session, String label) {
        String url = formatRestUrl("projects", project.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", session.getLabel(),
                "assessors", label);

        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(url)
                .then()
                .extract().response();

        if (response.statusCode() == 200) {
            return response.jsonPath().getString("items[0].data_fields.ID");
        }
        return label; // Fallback to label if ID not found
    }

    private void deleteExperimentSilently(String experimentId) {
        if (experimentId != null) {
            try {
                mainAdminInterface()
                        .requestWithCsrfToken()
                        .queryParam("removeFiles", true)
                        .delete(formatRestUrl("experiments", experimentId));
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void deleteProjectAssetSilently(String assetId) {
        if (assetId != null && primaryProject != null) {
            try {
                mainAdminInterface()
                        .requestWithCsrfToken()
                        .delete(formatRestUrl("projects", primaryProject.getId(),
                                "resources", assetId));
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void deleteSchemaIfEmpty(Long schemaId) {
        if (schemaId != null) {
            try {
                mainAdminInterface()
                        .requestWithCsrfToken()
                        .delete(DBSCHEMAS_URL + "/" + schemaId);
            } catch (Exception e) {
                // Ignore - schema may have experiments
            }
        }
    }

    private void assumeNotNull(String message, Object value) {
        if (value == null) {
            throw new org.testng.SkipException(message);
        }
    }
}
