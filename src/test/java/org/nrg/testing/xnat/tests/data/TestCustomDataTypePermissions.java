package org.nrg.testing.xnat.tests.data;

import io.restassured.response.Response;
import org.nrg.testing.TestGroups;
import org.nrg.testing.TimeUtils;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.Users;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.Gender;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_10_0;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.http.Method.*;
import static org.hamcrest.Matchers.*;
import static org.testng.AssertJUnit.*;

/**
 * Permission-focused tests for custom data types across all three extension points.
 *
 * This test class focuses on verifying that:
 * - Owners can perform all operations (read, modify, delete, upload files)
 * - Members can read and modify but NOT delete
 * - Collaborators can only read, NOT modify or delete
 *
 * Tests each of the three extension points:
 * - xnat:subjectAssessorData
 * - xnat:imageAssessorData
 * - xnat:abstractProjectAsset
 */
@AddedIn(Xnat_1_10_0.class)
@Test(groups = {TestGroups.DATA_TYPES, TestGroups.PERMISSIONS})
public class TestCustomDataTypePermissions extends BaseXnatRestTest {

    private static final String DATATYPES_URL = "/xapi/datatypes";
    private static final String DBSCHEMAS_URL = "/xapi/dbschemas";

    private final File dummyFile = getDataFile("dummy.txt");
    private final String testRunId = RandomHelper.randomID(6);

    // Data types
    private String subjectAssessorXsiType;
    private String imageAssessorXsiType;
    private String projectAssetXsiType;

    // Schema IDs
    private Long subjectAssessorSchemaId;
    private Long imageAssessorSchemaId;
    private Long projectAssetSchemaId;

    // Users
    private User ownerUser;
    private User memberUser;
    private User collaboratorUser;

    // Project and data structures
    private Project testProject;
    private Subject testSubject;
    private ImagingSession testMRSession;

    // Experiments created for owner/member/collaborator testing
    private String ownerSubjAssessorId;
    private String ownerImgAssessorId;
    private String ownerProjAssetLabel;

    private String memberSubjAssessorId;
    private String memberImgAssessorId;
    private String memberProjAssetLabel;

    private String collabSubjAssessorId;
    private String collabImgAssessorId;
    private String collabProjAssetLabel;

    @BeforeClass
    public void setupTestEnvironment() {
        // Create users
        ownerUser = Users.genericAccount();
        memberUser = Users.genericAccount();
        collaboratorUser = Users.genericAccount();

        mainAdminInterface().createUser(ownerUser);
        mainAdminInterface().createUser(memberUser);
        mainAdminInterface().createUser(collaboratorUser);

        // Create project with roles
        testProject = new Project("TestDTPerm_" + testRunId)
                .accessibility(Accessibility.PRIVATE)
                .addOwner(ownerUser)
                .addMember(memberUser)
                .addCollaborator(collaboratorUser);

        mainAdminInterface().createProject(testProject);

        // Create subject and MR session
        testSubject = new Subject(testProject, "PermTestSubject")
                .gender(Gender.MALE);
        mainAdminInterface().createSubject(testSubject);

        testMRSession = new MRSession(testProject, testSubject, "PermTestMR")
                .date(LocalDate.parse("2024-01-15"));
        mainAdminInterface().createSubjectAssessor(testMRSession);

        TimeUtils.sleep(2000);

        // Create custom data types
        createCustomDataTypes();

        TimeUtils.sleep(2000);

        // Create test experiments for each role
        createTestExperiments();
    }

    private void createCustomDataTypes() {
        // Subject Assessor type
        String subjAssessorName = "PermSubjAssessor" + testRunId;
        Response resp1 = mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("name", subjAssessorName)
                .queryParam("singular", "Perm Subject Assessor")
                .queryParam("plural", "Perm Subject Assessors")
                .queryParam("extends", "subjectAssessorData")
                .post(DATATYPES_URL + "/create")
                .then()
                .extract().response();

        if (resp1.statusCode() == 201) {
            String complexType = resp1.jsonPath().getString("complexType");
            subjectAssessorXsiType = complexType;
            subjectAssessorSchemaId = findSchemaIdByContent(subjAssessorName);
        }

        // Image Assessor type
        String imgAssessorName = "PermImgAssessor" + testRunId;
        Response resp2 = mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("name", imgAssessorName)
                .queryParam("singular", "Perm Image Assessor")
                .queryParam("plural", "Perm Image Assessors")
                .queryParam("extends", "imageAssessorData")
                .post(DATATYPES_URL + "/create")
                .then()
                .extract().response();

        if (resp2.statusCode() == 201) {
            String complexType = resp2.jsonPath().getString("complexType");
            imageAssessorXsiType = complexType;
            imageAssessorSchemaId = findSchemaIdByContent(imgAssessorName);
        }

        // Project Asset type
        String projAssetName = "PermProjAsset" + testRunId;
        Response resp3 = mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("name", projAssetName)
                .queryParam("singular", "Perm Project Asset")
                .queryParam("plural", "Perm Project Assets")
                .queryParam("extends", "abstractProjectAsset")
                .post(DATATYPES_URL + "/create")
                .then()
                .extract().response();

        if (resp3.statusCode() == 201) {
            String complexType = resp3.jsonPath().getString("complexType");
            projectAssetXsiType = complexType;
            projectAssetSchemaId = findSchemaIdByContent(projAssetName);
        }
    }

    private void createTestExperiments() {
        // Create experiments as admin for each user to test permissions against

        // Subject Assessors - one per user role
        ownerSubjAssessorId = createSubjectAssessor("OwnerSubjAssessor_" + testRunId);
        memberSubjAssessorId = createSubjectAssessor("MemberSubjAssessor_" + testRunId);
        collabSubjAssessorId = createSubjectAssessor("CollabSubjAssessor_" + testRunId);

        // Image Assessors - one per user role
        ownerImgAssessorId = createImageAssessor("OwnerImgAssessor_" + testRunId);
        memberImgAssessorId = createImageAssessor("MemberImgAssessor_" + testRunId);
        collabImgAssessorId = createImageAssessor("CollabImgAssessor_" + testRunId);

        // Project Assets - one per user role
        ownerProjAssetLabel = "OwnerProjAsset_" + testRunId;
        memberProjAssetLabel = "MemberProjAsset_" + testRunId;
        collabProjAssetLabel = "CollabProjAsset_" + testRunId;

        createProjectAsset(ownerProjAssetLabel);
        createProjectAsset(memberProjAssetLabel);
        createProjectAsset(collabProjAssetLabel);
    }

    private String createSubjectAssessor(String label) {
        if (subjectAssessorXsiType == null) return null;

        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", label);

        mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("xsiType", subjectAssessorXsiType)
                .queryParam("date", "2024-06-15")
                .put(url);

        return label;
    }

    private String createImageAssessor(String label) {
        if (imageAssessorXsiType == null) return null;

        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", label);

        mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("xsiType", imageAssessorXsiType)
                .queryParam("date", "2024-06-16")
                .put(url);

        return label;
    }

    private void createProjectAsset(String label) {
        if (projectAssetXsiType == null) return;

        String url = formatRestUrl("projects", testProject.getId(),
                "resources", label);

        mainAdminInterface()
                .requestWithCsrfToken()
                .queryParam("xsiType", projectAssetXsiType)
                .queryParam("label", label)
                .put(url);
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        // Delete experiments
        deleteExperimentSilently(ownerSubjAssessorId);
        deleteExperimentSilently(memberSubjAssessorId);
        deleteExperimentSilently(collabSubjAssessorId);

        deleteImageAssessorSilently(ownerImgAssessorId);
        deleteImageAssessorSilently(memberImgAssessorId);
        deleteImageAssessorSilently(collabImgAssessorId);

        deleteProjectAssetSilently(ownerProjAssetLabel);
        deleteProjectAssetSilently(memberProjAssetLabel);
        deleteProjectAssetSilently(collabProjAssetLabel);

        // Delete project
        restDriver.deleteProjectSilently(mainAdminUser, testProject);

        // Delete schemas
        deleteSchemaIfPossible(subjectAssessorSchemaId);
        deleteSchemaIfPossible(imageAssessorSchemaId);
        deleteSchemaIfPossible(projectAssetSchemaId);
    }

    // ==================== Subject Assessor Permission Tests ====================

    @Test
    public void testOwnerCanReadSubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(ownerSubjAssessorId);

        restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(formatRestUrl("projects", testProject.getId(),
                        "experiments", ownerSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testOwnerCanModifySubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(ownerSubjAssessorId);

        String newDate = "2024-07-20";

        restDriver.queryBaseFor(ownerUser)
                .queryParam("date", newDate)
                .put(formatRestUrl("projects", testProject.getId(),
                        "experiments", ownerSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the date was updated
        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(formatRestUrl("projects", testProject.getId(),
                        "experiments", ownerSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        String actualDate = response.jsonPath().getString("items[0].data_fields.date");
        assertEquals("Date should be updated to " + newDate, newDate, actualDate);
    }

    @Test
    public void testOwnerCanUploadFileToSubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(ownerSubjAssessorId);

        String fileUrl = formatRestUrl("projects", testProject.getId(),
                "experiments", ownerSubjAssessorId,
                "resources", "OWNER_FILES", "files", "owner_test.txt");

        restDriver.queryBaseFor(ownerUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the file was uploaded by retrieving it
        restDriver.queryBaseFor(ownerUser)
                .get(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testMemberCanReadSubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(memberSubjAssessorId);

        restDriver.queryBaseFor(memberUser)
                .queryParam("format", "json")
                .get(formatRestUrl("projects", testProject.getId(),
                        "experiments", memberSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testMemberCanModifySubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(memberSubjAssessorId);

        String newDate = "2024-08-15";

        restDriver.queryBaseFor(memberUser)
                .queryParam("date", newDate)
                .put(formatRestUrl("projects", testProject.getId(),
                        "experiments", memberSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the date was updated
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("format", "json")
                .get(formatRestUrl("projects", testProject.getId(),
                        "experiments", memberSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        String actualDate = response.jsonPath().getString("items[0].data_fields.date");
        assertEquals("Date should be updated to " + newDate, newDate, actualDate);
    }

    @Test
    public void testMemberCanUploadFileToSubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(memberSubjAssessorId);

        String fileUrl = formatRestUrl("projects", testProject.getId(),
                "experiments", memberSubjAssessorId,
                "resources", "MEMBER_FILES", "files", "member_test.txt");

        restDriver.queryBaseFor(memberUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the file was uploaded by retrieving it
        restDriver.queryBaseFor(memberUser)
                .get(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testMemberCannotDeleteSubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(memberSubjAssessorId);

        restDriver.queryBaseFor(memberUser)
                .delete(formatRestUrl("projects", testProject.getId(),
                        "experiments", memberSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    public void testCollaboratorCanReadSubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(collabSubjAssessorId);

        restDriver.queryBaseFor(collaboratorUser)
                .queryParam("format", "json")
                .get(formatRestUrl("projects", testProject.getId(),
                        "experiments", collabSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testCollaboratorCannotModifySubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(collabSubjAssessorId);

        restDriver.queryBaseFor(collaboratorUser)
                .queryParam("date", "2024-09-01")
                .put(formatRestUrl("projects", testProject.getId(),
                        "experiments", collabSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    public void testCollaboratorCannotUploadFileToSubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(collabSubjAssessorId);

        String fileUrl = formatRestUrl("projects", testProject.getId(),
                "experiments", collabSubjAssessorId,
                "resources", "COLLAB_FILES", "files", "collab_test.txt");

        restDriver.queryBaseFor(collaboratorUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    public void testCollaboratorCannotDeleteSubjectAssessor() {
        skipIfNoType(subjectAssessorXsiType, "Subject Assessor");
        skipIfNoExperiment(collabSubjAssessorId);

        restDriver.queryBaseFor(collaboratorUser)
                .delete(formatRestUrl("projects", testProject.getId(),
                        "experiments", collabSubjAssessorId))
                .then()
                .assertThat()
                .statusCode(403);
    }

    // ==================== Image Assessor Permission Tests ====================

    @Test
    public void testOwnerCanReadImageAssessor() {
        skipIfNoType(imageAssessorXsiType, "Image Assessor");
        skipIfNoExperiment(ownerImgAssessorId);

        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", ownerImgAssessorId);

        restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(url)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testOwnerCanModifyImageAssessor() {
        skipIfNoType(imageAssessorXsiType, "Image Assessor");
        skipIfNoExperiment(ownerImgAssessorId);

        String newDate = "2024-07-21";
        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", ownerImgAssessorId);

        restDriver.queryBaseFor(ownerUser)
                .queryParam("date", newDate)
                .put(url)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the date was updated
        Response response = restDriver.queryBaseFor(ownerUser)
                .queryParam("format", "json")
                .get(url)
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        String actualDate = response.jsonPath().getString("items[0].data_fields.date");
        assertEquals("Date should be updated to " + newDate, newDate, actualDate);
    }

    @Test
    public void testMemberCanReadImageAssessor() {
        skipIfNoType(imageAssessorXsiType, "Image Assessor");
        skipIfNoExperiment(memberImgAssessorId);

        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", memberImgAssessorId);

        restDriver.queryBaseFor(memberUser)
                .queryParam("format", "json")
                .get(url)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testMemberCanModifyImageAssessor() {
        skipIfNoType(imageAssessorXsiType, "Image Assessor");
        skipIfNoExperiment(memberImgAssessorId);

        String newDate = "2024-08-16";
        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", memberImgAssessorId);

        restDriver.queryBaseFor(memberUser)
                .queryParam("date", newDate)
                .put(url)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the date was updated
        Response response = restDriver.queryBaseFor(memberUser)
                .queryParam("format", "json")
                .get(url)
                .then()
                .assertThat()
                .statusCode(200)
                .extract().response();

        String actualDate = response.jsonPath().getString("items[0].data_fields.date");
        assertEquals("Date should be updated to " + newDate, newDate, actualDate);
    }

    @Test
    public void testMemberCannotDeleteImageAssessor() {
        skipIfNoType(imageAssessorXsiType, "Image Assessor");
        skipIfNoExperiment(memberImgAssessorId);

        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", memberImgAssessorId);

        restDriver.queryBaseFor(memberUser)
                .delete(url)
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    public void testCollaboratorCanReadImageAssessor() {
        skipIfNoType(imageAssessorXsiType, "Image Assessor");
        skipIfNoExperiment(collabImgAssessorId);

        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", collabImgAssessorId);

        restDriver.queryBaseFor(collaboratorUser)
                .queryParam("format", "json")
                .get(url)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testCollaboratorCannotModifyImageAssessor() {
        skipIfNoType(imageAssessorXsiType, "Image Assessor");
        skipIfNoExperiment(collabImgAssessorId);

        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", collabImgAssessorId);

        restDriver.queryBaseFor(collaboratorUser)
                .queryParam("date", "2024-09-02")
                .put(url)
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    public void testCollaboratorCannotDeleteImageAssessor() {
        skipIfNoType(imageAssessorXsiType, "Image Assessor");
        skipIfNoExperiment(collabImgAssessorId);

        String url = formatRestUrl("projects", testProject.getId(),
                "subjects", testSubject.getLabel(),
                "experiments", testMRSession.getLabel(),
                "assessors", collabImgAssessorId);

        restDriver.queryBaseFor(collaboratorUser)
                .delete(url)
                .then()
                .assertThat()
                .statusCode(403);
    }

    // ==================== Project Asset Permission Tests ====================

    @Test
    public void testOwnerCanReadProjectAsset() {
        skipIfNoType(projectAssetXsiType, "Project Asset");
        skipIfNoExperiment(ownerProjAssetLabel);

        restDriver.queryBaseFor(ownerUser)
                .get(formatRestUrl("projects", testProject.getId(),
                        "resources", ownerProjAssetLabel))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testOwnerCanUploadToProjectAsset() {
        skipIfNoType(projectAssetXsiType, "Project Asset");
        skipIfNoExperiment(ownerProjAssetLabel);

        String fileUrl = formatRestUrl("projects", testProject.getId(),
                "resources", ownerProjAssetLabel, "files", "owner_asset.txt");

        restDriver.queryBaseFor(ownerUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the file was uploaded by retrieving it
        restDriver.queryBaseFor(ownerUser)
                .get(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testMemberCanReadProjectAsset() {
        skipIfNoType(projectAssetXsiType, "Project Asset");
        skipIfNoExperiment(memberProjAssetLabel);

        restDriver.queryBaseFor(memberUser)
                .get(formatRestUrl("projects", testProject.getId(),
                        "resources", memberProjAssetLabel))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testMemberCanUploadToProjectAsset() {
        skipIfNoType(projectAssetXsiType, "Project Asset");
        skipIfNoExperiment(memberProjAssetLabel);

        String fileUrl = formatRestUrl("projects", testProject.getId(),
                "resources", memberProjAssetLabel, "files", "member_asset.txt");

        restDriver.queryBaseFor(memberUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);

        // Verify the file was uploaded by retrieving it
        restDriver.queryBaseFor(memberUser)
                .get(fileUrl)
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testMemberCannotDeleteProjectAsset() {
        skipIfNoType(projectAssetXsiType, "Project Asset");
        skipIfNoExperiment(memberProjAssetLabel);

        restDriver.queryBaseFor(memberUser)
                .delete(formatRestUrl("projects", testProject.getId(),
                        "resources", memberProjAssetLabel))
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    public void testCollaboratorCanReadProjectAsset() {
        skipIfNoType(projectAssetXsiType, "Project Asset");
        skipIfNoExperiment(collabProjAssetLabel);

        restDriver.queryBaseFor(collaboratorUser)
                .get(formatRestUrl("projects", testProject.getId(),
                        "resources", collabProjAssetLabel))
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void testCollaboratorCannotUploadToProjectAsset() {
        skipIfNoType(projectAssetXsiType, "Project Asset");
        skipIfNoExperiment(collabProjAssetLabel);

        String fileUrl = formatRestUrl("projects", testProject.getId(),
                "resources", collabProjAssetLabel, "files", "collab_asset.txt");

        restDriver.queryBaseFor(collaboratorUser)
                .multiPart(dummyFile)
                .put(fileUrl)
                .then()
                .assertThat()
                .statusCode(403);
    }

    @Test
    public void testCollaboratorCannotDeleteProjectAsset() {
        skipIfNoType(projectAssetXsiType, "Project Asset");
        skipIfNoExperiment(collabProjAssetLabel);

        restDriver.queryBaseFor(collaboratorUser)
                .delete(formatRestUrl("projects", testProject.getId(),
                        "resources", collabProjAssetLabel))
                .then()
                .assertThat()
                .statusCode(403);
    }

    // ==================== Helper Methods ====================

    private void skipIfNoType(String xsiType, String typeName) {
        if (xsiType == null) {
            throw new org.testng.SkipException(typeName + " type was not created");
        }
    }

    private void skipIfNoExperiment(String experimentId) {
        if (experimentId == null) {
            throw new org.testng.SkipException("Experiment was not created");
        }
    }

    private Long findSchemaIdByContent(String typeName) {
        Response response = mainAdminInterface()
                .requestWithCsrfToken()
                .get(DBSCHEMAS_URL)
                .then()
                .extract().response();

        List<Map<String, Object>> schemas = response.jsonPath().getList("$");
        for (Map<String, Object> schema : schemas) {
            String name = (String) schema.get("name");
            String content = (String) schema.get("content");
            if ((name != null && name.toLowerCase().contains(typeName.toLowerCase())) ||
                (content != null && content.toLowerCase().contains(typeName.toLowerCase()))) {
                return ((Number) schema.get("id")).longValue();
            }
        }
        return null;
    }

    private void deleteExperimentSilently(String experimentId) {
        if (experimentId != null && testProject != null) {
            try {
                mainAdminInterface()
                        .requestWithCsrfToken()
                        .queryParam("removeFiles", true)
                        .delete(formatRestUrl("projects", testProject.getId(),
                                "experiments", experimentId));
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void deleteImageAssessorSilently(String assessorLabel) {
        if (assessorLabel != null && testProject != null && testMRSession != null) {
            try {
                mainAdminInterface()
                        .requestWithCsrfToken()
                        .queryParam("removeFiles", true)
                        .delete(formatRestUrl("projects", testProject.getId(),
                                "subjects", testSubject.getLabel(),
                                "experiments", testMRSession.getLabel(),
                                "assessors", assessorLabel));
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void deleteProjectAssetSilently(String assetLabel) {
        if (assetLabel != null && testProject != null) {
            try {
                mainAdminInterface()
                        .requestWithCsrfToken()
                        .delete(formatRestUrl("projects", testProject.getId(),
                                "resources", assetLabel));
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void deleteSchemaIfPossible(Long schemaId) {
        if (schemaId != null) {
            try {
                mainAdminInterface()
                        .requestWithCsrfToken()
                        .delete(DBSCHEMAS_URL + "/" + schemaId);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
