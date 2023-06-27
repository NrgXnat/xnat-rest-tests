package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.enums.PrearchiveCode;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Investigator;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.search.ComparisonType;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.search.XdatCriteria;
import org.nrg.xnat.pogo.search.XnatSearchDocument;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.pogo.users.UserGroup;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.nrg.testing.TestGroups.SEARCH;

/**
 * The endpoint at /data/projects is particularly esoteric. There are 4 backend implementations
 * that are configured to handle the request, depending on the particular query parameters passed
 * to the call. These tests are designed to accomplish the following:
 *   1) Reasonable behavior with respect to security
 *   2) Status quo functioning of this endpoint in other regards
 * That is to say, this API is weird, and I am writing these tests to make sure it doesn't change out from under
 * us unintentionally, but the behavior I'm "asserting" is not great.
 */
public class TestDynamicSearchProjects extends BaseDynamicSearchTest {

    private static final String CREATE = "create";
    private static final String EDIT = "edit";
    private static final String READ = "read";
    private static final String DELETE = "delete";
    private static final String ALL_DATA_OVERRIDE = "allDataOverride";
    private static final String RESTRICT = "restrict";
    private static final String CREATABLE_TYPES = "creatableTypes";
    private static final String DATA_TYPE = "dataType";
    private static final String DATA_TYPE_KEBAB_CASE = "data-type";
    private static final String ACCESSIBLE = "accessible";
    private static final String DATA_ACCESS = "data";
    private static final String ID = "ID";
    private static final String SECONDARY_ID = "secondary_ID";
    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final String OWNER = "owner";
    private static final String MEMBER = "member";
    private static final String COLLABORATOR = "collaborator";

    private final DynamicSearchResultValidator<Project> defaultProjectHandlerValidator = new DynamicSearchResultValidator<Project>()
            .uniquenessKey(ID)
            .objectMappingFunction(
                    (project) -> {
                        final Investigator pi = project.getPi();
                        final Map<String, String> asMap = new HashMap<>();
                        asMap.put("pi_firstname", pi == null ? "" : pi.getFirstname());
                        asMap.put(SECONDARY_ID, project.getRunningTitle());
                        asMap.put("pi_lastname", pi == null ? "" : pi.getLastname());
                        asMap.put(NAME, project.getTitle());
                        asMap.put(DESCRIPTION, nullFallback(project.getDescription()));
                        asMap.put(ID, project.getId());
                        asMap.put("URI", "/data/projects/" + project.getId());
                        return asMap;
                    }
            );

    private final DynamicSearchResultValidator<Project> permissionsProjectHandlerValidator = new DynamicSearchResultValidator<Project>()
            .uniquenessKey(ID.toLowerCase())
            .objectMappingFunction(
                    (project) -> {
                        final Map<String, String> asMap = new HashMap<>();
                        asMap.put(ID.toLowerCase(), project.getId());
                        asMap.put(SECONDARY_ID.toLowerCase(), project.getRunningTitle());
                        return asMap;
                    }
            );

    private final DynamicSearchResultValidator<Project> creatableTypesHandlerValidator = new DynamicSearchResultValidator<Project>()
            .uniquenessKey(ID.toLowerCase())
            .objectMappingFunction(
                    (project) -> {
                        final Map<String, String> asMap = new HashMap<>();
                        asMap.put(SECONDARY_ID.toLowerCase(), project.getRunningTitle());
                        asMap.put(NAME, project.getTitle());
                        asMap.put(DESCRIPTION, nullFallback(project.getDescription()));
                        asMap.put(ID.toLowerCase(), project.getId());
                        return asMap;
                    }
            );
    
    private DynamicSearchResultValidator<Project> mainUserFilterableProjectsValidator;
    
    @BeforeClass(groups = {SEARCH, PERMISSIONS})
    private void initValidator() {
        mainUserFilterableProjectsValidator = filterableProjectsValidatorFor(mainUser);
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearch() {
        defaultProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(new HashMap<>()),
                detectableProjects,
                Collections.singletonList(privateProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchAllDataOverride() {
        defaultProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(ALL_DATA_OVERRIDE, "true")), // doesn't work for regular users
                detectableProjects,
                Collections.singletonList(privateProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchRestrict() {
        for (String permission : Arrays.asList(EDIT, DELETE)) {
            defaultProjectHandlerValidator.validateDynamicSearchResponse(
                    queryProjects(Collections.singletonMap(RESTRICT, permission)),
                    Collections.singletonList(ownerProject),
                    Arrays.asList(publicProject, protectedProject, privateProject, memberProject, collaboratorProject, customUserGroupProject)
            );
        }
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testAdminDynamicProjectSearchAllDataOverride() {
        defaultProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(ALL_DATA_OVERRIDE, "true"), mainAdminUser),
                allProjects,
                new ArrayList<>()
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchPermissionsSubject() {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(DATA_TYPE, DataType.SUBJECT.getXsiType());
        queryParams.put(PERMISSIONS, READ);
        permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Arrays.asList(publicProject, customUserGroupProject, ownerProject, memberProject, collaboratorProject),
                Arrays.asList(protectedProject, privateProject)
        );

        for (String permission : Arrays.asList(CREATE, EDIT)) {
            queryParams.put(PERMISSIONS, permission);
            permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                    queryProjects(queryParams),
                    Arrays.asList(ownerProject, memberProject),
                    Arrays.asList(protectedProject, privateProject, publicProject, customUserGroupProject, collaboratorProject)
            );
        }

        queryParams.put(PERMISSIONS, DELETE);
        permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Collections.singletonList(ownerProject),
                Arrays.asList(protectedProject, privateProject, publicProject, customUserGroupProject, memberProject, collaboratorProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchPermissionsMrSessions() {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(DATA_TYPE, DataType.MR_SESSION.getXsiType());
        queryParams.put(PERMISSIONS, READ);
        permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Arrays.asList(publicProject, customUserGroupProject, ownerProject, memberProject, collaboratorProject),
                Arrays.asList(protectedProject, privateProject)
        );

        for (String permission : Arrays.asList(CREATE, EDIT)) {
            queryParams.put(PERMISSIONS, permission);
            permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                    queryProjects(queryParams),
                    Arrays.asList(ownerProject, memberProject, customUserGroupProject),
                    Arrays.asList(protectedProject, privateProject, publicProject, collaboratorProject)
            );
        }

        queryParams.put(PERMISSIONS, DELETE);
        permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Collections.singletonList(ownerProject),
                Arrays.asList(protectedProject, privateProject, publicProject, customUserGroupProject, memberProject, collaboratorProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchPermissionsPetSessions() {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(DATA_TYPE, DataType.PET_SESSION.getXsiType());
        queryParams.put(PERMISSIONS, READ);
        permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Arrays.asList(publicProject, ownerProject, memberProject, collaboratorProject),
                Arrays.asList(protectedProject, privateProject, customUserGroupProject)
        );

        for (String permission : Arrays.asList(CREATE, EDIT)) {
            queryParams.put(PERMISSIONS, permission);
            permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                    queryProjects(queryParams),
                    Arrays.asList(ownerProject, memberProject),
                    Arrays.asList(protectedProject, privateProject, publicProject, collaboratorProject, customUserGroupProject)
            );
        }

        queryParams.put(PERMISSIONS, DELETE);
        permissionsProjectHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Collections.singletonList(ownerProject),
                Arrays.asList(protectedProject, privateProject, publicProject, customUserGroupProject, memberProject, collaboratorProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchCreatableTypes() {
        creatableTypesHandlerValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(CREATABLE_TYPES, "true")),
                Arrays.asList(ownerProject, memberProject, customUserGroupProject),
                Arrays.asList(publicProject, protectedProject, privateProject, collaboratorProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchCreatableTypesAllDataAdmin() {
        creatableTypesHandlerValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(CREATABLE_TYPES, "true"), mainAdminUser),
                allProjects,
                new ArrayList<>()
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchCreatableTypesWithDataTypes() {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(CREATABLE_TYPES, "true");
        queryParams.put(DATA_TYPE_KEBAB_CASE, DataType.SUBJECT.getXsiType());
        creatableTypesHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Arrays.asList(ownerProject, memberProject),
                Arrays.asList(publicProject, protectedProject, privateProject, collaboratorProject, customUserGroupProject)
        );

        queryParams.put(DATA_TYPE_KEBAB_CASE, DataType.MR_SESSION.getXsiType());
        creatableTypesHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Arrays.asList(ownerProject, memberProject, customUserGroupProject),
                Arrays.asList(publicProject, protectedProject, privateProject, collaboratorProject)
        );

        queryParams.put(DATA_TYPE_KEBAB_CASE, DataType.PET_SESSION.getXsiType());
        creatableTypesHandlerValidator.validateDynamicSearchResponse(
                queryProjects(queryParams),
                Arrays.asList(ownerProject, memberProject),
                Arrays.asList(publicProject, protectedProject, privateProject, collaboratorProject, customUserGroupProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchFilterableProjectsAccessible() {
        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(ACCESSIBLE, "true")),
                Arrays.asList(ownerProject, memberProject, collaboratorProject, customUserGroupProject, protectedProject, publicProject),
                Collections.singletonList(privateProject)
        );

        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(ACCESSIBLE, "false")),
                Arrays.asList(protectedProject, publicProject),
                Arrays.asList(ownerProject, memberProject, collaboratorProject, customUserGroupProject, privateProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchFilterableProjectsAccessibleAdmin() {
        for (String paramVal : Arrays.asList("true", "false")) { // we are removing the mainAdminUser from the project
            filterableProjectsValidatorFor(mainAdminUser).validateDynamicSearchResponse(
                    queryProjects(Collections.singletonMap(ACCESSIBLE, paramVal), mainAdminUser),
                    allProjects,
                    new ArrayList<>()
            );
        }
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchFilterableProjectsDataWritable() {
        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(DATA_ACCESS, "writable")),
                Arrays.asList(ownerProject, memberProject),
                Arrays.asList(privateProject, collaboratorProject, customUserGroupProject, protectedProject, publicProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchFilterableProjectsDataReadable() {
        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(DATA_ACCESS, "readable")),
                Arrays.asList(ownerProject, memberProject, collaboratorProject, customUserGroupProject, publicProject),
                Arrays.asList(privateProject, protectedProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchFilterableProjectsPrearcCode() {
        new DynamicSearchResultValidator<Project>()
                .uniquenessCompositeKeys(mainUserFilterableProjectsValidator.getUniquenessCompositeKeys())
                .keysToIgnore(mainUserFilterableProjectsValidator.getKeysToIgnore())
                .multiObjectMappingFunction(
                        (project) -> {
                            final List<Map<String, String>> maps = mainUserFilterableProjectsValidator.getMultiObjectMappingFunction().apply(project);
                            final PrearchiveCode prearchiveCode = project.getPrearchiveCode();

                            for (Map<String, String> asMap : maps) {
                                asMap.put("proj_quarantine", "0");
                                asMap.put("proj_prearchive_code", String.valueOf((prearchiveCode != null ? prearchiveCode : PrearchiveCode.AUTO_ARCHIVE).getCode()));
                            }

                            return maps;
                        }
                ).validateDynamicSearchResponse(
                        queryProjects(Collections.singletonMap("prearc_code", "true")),
                        Arrays.asList(ownerProject, memberProject, collaboratorProject, customUserGroupProject, publicProject, protectedProject),
                        Collections.singletonList(privateProject)
                );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    @ExpectedFailure(jiraIssue = "XNAT-7800")
    public void testStandardDynamicProjectSearchFilterableProjectsOwner() {
        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(OWNER, "true")),
                Collections.singletonList(ownerProject),
                Arrays.asList(memberProject, privateProject, collaboratorProject, customUserGroupProject, protectedProject, publicProject)
        );

        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(OWNER, "false")),
                Arrays.asList(protectedProject, publicProject, memberProject, collaboratorProject, customUserGroupProject),
                Arrays.asList(ownerProject, privateProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    @ExpectedFailure(jiraIssue = "XNAT-7803")
    public void testStandardDynamicProjectSearchFilterableProjectsMember() {
        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(MEMBER, "true")),
                Collections.singletonList(memberProject),
                Arrays.asList(ownerProject, privateProject, collaboratorProject, customUserGroupProject, protectedProject, publicProject)
        );

        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(MEMBER, "false")),
                Arrays.asList(protectedProject, publicProject, ownerProject, collaboratorProject, customUserGroupProject),
                Arrays.asList(memberProject, privateProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    @ExpectedFailure(jiraIssue = "XNAT-7803")
    public void testStandardDynamicProjectSearchFilterableProjectsCollaborator() {
        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(COLLABORATOR, "true")),
                Collections.singletonList(collaboratorProject),
                Arrays.asList(ownerProject, privateProject, memberProject, customUserGroupProject, protectedProject, publicProject)
        );

        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap(COLLABORATOR, "false")),
                Arrays.asList(protectedProject, publicProject, ownerProject, memberProject, customUserGroupProject),
                Arrays.asList(collaboratorProject, privateProject)
        );
    }

    /**
     * The time passed by activeSince gets truncated to second precision, so technically protectedProject may be indeterminate on whether it's included or not
     * (if the time for it with millis precision is x.000). We've spaced out all project creations by a full second, so we should get reliable results
     * except for that boundary case that we will ignore.
     */
    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchFilterableProjectsActiveSince() {
        final XnatSearchDocument workflowSearch = mainAdminInterface().getDefaultSearch(DataType.WORKFLOW);
        workflowSearch.addSearchCriterion(
                new XdatCriteria()
                        .schemaField(DataType.WORKFLOW.getXsiType() + "." + "EXTERNALID")
                        .comparisonType(ComparisonType.EQUALS)
                        .value(protectedProject.getId())
        );
        final String protectedProjectTime = mainAdminInterface().performSearch(workflowSearch)
                .getResult()
                .stream()
                .map(row -> row.get("launch_time"))
                .max(String::compareTo)
                .orElseThrow(RuntimeException::new);

        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap("activeSince", protectedProjectTime)),
                Arrays.asList(customUserGroupProject, ownerProject, memberProject, collaboratorProject),
                Arrays.asList(privateProject, publicProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    @TestRequires(users = 1)
    public void testStandardDynamicProjectSearchFilterableProjectsLastAccess() {
        final Project accessed = registerTempProject().accessibility(Accessibility.PUBLIC);
        final Project notAccessed = registerTempProject().accessibility(Accessibility.PUBLIC);
        interfaceFor(insertUser).createProject(accessed);
        interfaceFor(insertUser).createProject(notAccessed);
        final User newUser = getGenericUser();
        interfaceFor(newUser)
                .queryBase()
                .queryParam("format", "html")
                .get(mainInterface().projectUrl(accessed))
                .then()
                .assertThat()
                .statusCode(200);

        filterableProjectsValidatorFor(newUser).validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap("recent", "true"), newUser),
                Collections.singletonList(accessed),
                Collections.singletonList(notAccessed)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testStandardDynamicProjectSearchFilterableProjectsFavorite() {
        final Project favoriteProject = registerTempProject().accessibility(Accessibility.PUBLIC);
        interfaceFor(insertUser).createProject(favoriteProject);
        mainQueryBase()
                .put(formatRestUrl("/users/favorites/Project", favoriteProject.getId()))
                .then()
                .assertThat()
                .statusCode(200);

        mainUserFilterableProjectsValidator.validateDynamicSearchResponse(
                queryProjects(Collections.singletonMap("favorite", "true")),
                Collections.singletonList(favoriteProject),
                allProjects
        );
    }

    // TODO: query parameter "admin"

    private SearchResponse queryProjects(Map<String, String> queryParams, User authUser) {
        return queryDynamicSearchEndpoint(
                formatRestUrl("projects"),
                queryParams,
                authUser
        );
    }

    private SearchResponse queryProjects(Map<String, String> queryParams) {
        return queryProjects(queryParams, mainUser);
    }

    private DynamicSearchResultValidator<Project> filterableProjectsValidatorFor(User user) {
        final int userId = mainAdminInterface().readUser(user.getUsername()).getId();

        return new DynamicSearchResultValidator<Project>()
                .uniquenessKey(ID.toLowerCase())
                .objectMappingFunction(
                        (project) -> {
                            final UserGroup expectedRole = project.findAccessLevelFor(user);
                            final List<Investigator> allProjectInvestigators = new ArrayList<>();
                            if (project.getPi() != null) {
                                allProjectInvestigators.add(project.getPi());
                            }
                            if (project.getInvestigators() != null) {
                                allProjectInvestigators.addAll(project.getInvestigators());
                            }

                            final Map<String, String> asMap = new HashMap<>();
                            asMap.put("project_access", project.getAccessibility().toString());
                            asMap.put("quarantine_status", "active");
                            asMap.put("project_access_img", "/@WEBAPPimages/" + (project.getAccessibility() == Accessibility.PUBLIC ? "globe.gif" : "key.gif")); // lol
                            asMap.put(DESCRIPTION, nullFallback(project.getDescription()));
                            asMap.put("insert_user", insertUser.getUsername());
                            asMap.put("user_role_" + userId, expectedRole == null ? "" : expectedRole.pluralName()); // ?
                            asMap.put("project_invs", allProjectInvestigators
                                    .stream()
                                    .map(Investigator::getFullName)
                                    .collect(Collectors.joining(" <br/> ")) // :|
                            );
                            asMap.put(SECONDARY_ID.toLowerCase(), project.getRunningTitle());
                            asMap.put(NAME, project.getTitle());
                            asMap.put("pi", project.getPi() == null ? "" : project.getPi().getNameFirstLast());
                            asMap.put(ID.toLowerCase(), project.getId());
                            return asMap;
                        }
                ).keysToIgnore(
                        Arrays.asList(
                                "insert_date", "last_accessed_" + userId
                        )
                );
    }

}
