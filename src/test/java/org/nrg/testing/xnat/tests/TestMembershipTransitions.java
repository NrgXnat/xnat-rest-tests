package org.nrg.testing.xnat.tests;

import io.restassured.response.Response;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.nrg.testing.annotations.TestRequires;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.enums.Accessibility;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.pogo.users.UserGroups;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.testng.AssertJUnit.*;

/**
 * Checks what a user can do immediately after their project membership changes. XNAT keeps permissions in caches that
 * are updated from asynchronous group events, and a role change (e.g. member to owner) implicitly removes the user from
 * the project's other groups without a removal event of its own. Every check here is made once, straight after the
 * change, with no waiting: a failure means the user was served stale permissions.
 *
 * <p>Unless a test checks access before adding the user, the user's first request comes after they were added, so
 * XNAT builds their per-project cache entry from the database rather than from the add event. The two paths can
 * disagree, so keep that ordering in mind when adding checks.</p>
 */
@Test(groups = PERMISSIONS)
public class TestMembershipTransitions extends BaseXnatRestTest {

    private static final Matcher<Integer> DENIED = Matchers.oneOf(403, 404);
    private static final String MR = DataType.MR_SESSION.getXsiType();
    private static final int BURST_PROJECTS = 20;

    @Test
    @TestRequires(users = 1)
    public void testAddMember() {
        final User user = getGenericUser();
        final ProjectData data = createProjectWithData(Accessibility.PRIVATE);

        assertNoAccess(user, data, "before being added"); // so the add event updates an existing cache entry
        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.MEMBER);
        final String when = "after being added as a member";
        assertCanRead(user, data, when);
        final ImagingSession created = assertCanCreate(user, data.project, when);
        assertStatus(when + ": DELETE own MR session", deleteSession(user, created), Matchers.is(403));
    }

    @Test
    @TestRequires(users = 1)
    public void testPromoteMemberToOwner() {
        final User user = getGenericUser();
        final ProjectData data = createProjectWithData(Accessibility.PRIVATE);

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.MEMBER);
        final ImagingSession created = assertCanCreate(user, data.project, "as a member");
        assertStatus("as a member: DELETE own MR session", deleteSession(user, created), Matchers.is(403));

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.OWNER);
        assertStatus("after member -> owner: DELETE MR session", deleteSession(user, created), Matchers.is(200));
    }

    @Test
    @TestRequires(users = 1)
    public void testPromoteToOwnerThenRemove() {
        final User user = getGenericUser();
        final ProjectData data = createProjectWithData(Accessibility.PRIVATE);

        assertNoAccess(user, data, "before being added"); // so the add event updates an existing cache entry
        promoteToOwnerThenRemove(user, data);
    }

    @Test
    @TestRequires(users = 1)
    public void testPromoteToOwnerThenRemoveFirstSeenAsMember() {
        final User user = getGenericUser();
        final ProjectData data = createProjectWithData(Accessibility.PRIVATE);

        promoteToOwnerThenRemove(user, data);
    }

    private void promoteToOwnerThenRemove(User user, ProjectData data) {
        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.MEMBER);
        assertCanRead(user, data, "as a member");
        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.OWNER);
        assertCanRead(user, data, "after member -> owner");

        mainAdminInterface().removeUserFromProject(user, data.project, UserGroups.OWNER);
        assertNoAccess(user, data, "after member -> owner -> removed");
    }

    @Test
    @TestRequires(users = 1)
    public void testDemoteOwnerToMemberThenRemove() {
        final User user = getGenericUser();
        final ProjectData data = createProjectWithData(Accessibility.PRIVATE);

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.OWNER);
        assertCanRead(user, data, "as an owner");

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.MEMBER);
        final String demoted = "after owner -> member";
        assertCanRead(user, data, demoted);
        assertStatus(demoted + ": DELETE MR session", deleteSession(user, data.session), Matchers.is(403));

        mainAdminInterface().removeUserFromProject(user, data.project, UserGroups.MEMBER);
        assertNoAccess(user, data, "after owner -> member -> removed");
    }

    @Test
    @TestRequires(users = 1)
    public void testMemberToCollaboratorThenRemove() {
        final User user = getGenericUser();
        final ProjectData data = createProjectWithData(Accessibility.PRIVATE);

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.MEMBER);
        assertCanRead(user, data, "as a member");
        assertTrue("as a member: " + MR + " should be createable", readCreateable(user).contains(MR));

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.COLLABORATOR);
        final String collaborator = "after member -> collaborator";
        assertCanRead(user, data, collaborator);
        assertStatus(collaborator + ": PUT subject", putSubject(user, new Subject(data.project)), Matchers.is(403));
        assertFalse(collaborator + ": " + MR + " should no longer be createable", readCreateable(user).contains(MR));

        mainAdminInterface().removeUserFromProject(user, data.project, UserGroups.COLLABORATOR);
        assertNoAccess(user, data, "after member -> collaborator -> removed");
    }

    @Test
    @TestRequires(users = 1)
    public void testReAddMember() {
        final User user = getGenericUser();
        final ProjectData data = createProjectWithData(Accessibility.PRIVATE);

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.MEMBER);
        assertCanRead(user, data, "as a member");
        mainAdminInterface().removeUserFromProject(user, data.project, UserGroups.MEMBER);
        assertNoAccess(user, data, "after member -> removed");

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.MEMBER);
        final String when = "after member -> removed -> member";
        assertCanRead(user, data, when);
        assertCanCreate(user, data.project, when);
    }

    @Test
    @TestRequires(users = 1)
    public void testProjectDeletedWhileMember() {
        final User user = getGenericUser();
        final ProjectData data = createProjectWithData(Accessibility.PRIVATE);

        mainAdminInterface().setUserProjectRole(user, data.project, UserGroups.MEMBER);
        assertCanRead(user, data, "as a member");

        mainAdminInterface().deleteProject(data.project);
        final String when = "after the project was deleted";
        assertStatus(when + ": GET project record", readProjectRecord(user, data.project), Matchers.is(404));
        assertFalse(when + ": project should not be in the user's project list", listProjectIds(user).contains(data.project.getId()));
        assertFalse(when + ": " + MR + " should not be createable", readCreateable(user).contains(MR));
    }

    @Test
    @TestRequires(users = 1)
    public void testNonMemberAccessToProtectedAndPublicProjects() {
        final User user = getGenericUser();
        final ProjectData protectedData = createProjectWithData(Accessibility.PROTECTED);
        final ProjectData publicData = createProjectWithData(Accessibility.PUBLIC);

        final String protectedProject = "non-member on a protected project";
        assertStatus(protectedProject + ": GET project record", readProjectRecord(user, protectedData.project), Matchers.is(200));
        assertStatus(protectedProject + ": GET subjects", readSubjects(user, protectedData.project), Matchers.is(403));
        assertStatus(protectedProject + ": GET experiments", readExperiments(user, protectedData.project), Matchers.is(403));

        final String publicProject = "non-member on a public project";
        final Response subjects = readSubjects(user, publicData.project);
        assertStatus(publicProject + ": GET subjects", subjects, Matchers.is(200));
        assertEquals(publicProject + ": subject labels", Collections.singletonList(publicData.subject.getLabel()), subjects.jsonPath().getList("ResultSet.Result.label", String.class));
    }

    @Test
    @TestRequires(users = 1)
    public void testBurstOfMembershipChanges() {
        final User user = getGenericUser();
        final List<Project> projects = new ArrayList<>();
        for (int i = 0; i < BURST_PROJECTS; i++) {
            projects.add(createProject(Accessibility.PRIVATE));
        }
        final Set<String> allIds = projects.stream().map(Project::getId).collect(Collectors.toSet());
        assertEquals("before being added: burst projects in the user's project list", Collections.emptySet(), listedAmong(user, allIds));

        for (Project project : projects) {
            mainAdminInterface().setUserProjectRole(user, project, UserGroups.MEMBER);
        }
        assertEquals("after " + BURST_PROJECTS + " adds: burst projects in the user's project list", allIds, listedAmong(user, allIds));

        final List<Project> kept = new ArrayList<>();
        final List<Project> removed = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            (i % 2 == 0 ? removed : kept).add(projects.get(i));
        }
        for (Project project : removed) {
            mainAdminInterface().removeUserFromProject(user, project, UserGroups.MEMBER);
        }

        final String when = "after " + BURST_PROJECTS + " adds and " + removed.size() + " removals";
        final Set<String> keptIds = kept.stream().map(Project::getId).collect(Collectors.toSet());
        assertEquals(when + ": burst projects in the user's project list", keptIds, listedAmong(user, allIds));

        final List<String> mismatches = new ArrayList<>();
        for (Project project : kept) {
            collectMismatch(mismatches, "kept " + project.getId() + " GET project record", readProjectRecord(user, project), Matchers.is(200));
            collectMismatch(mismatches, "kept " + project.getId() + " GET subjects", readSubjects(user, project), Matchers.is(200));
        }
        for (Project project : removed) {
            collectMismatch(mismatches, "removed " + project.getId() + " GET project record", readProjectRecord(user, project), DENIED);
            collectMismatch(mismatches, "removed " + project.getId() + " GET subjects", readSubjects(user, project), DENIED);
        }
        assertTrue(when + ": unexpected status codes: " + mismatches, mismatches.isEmpty());
        assertTrue(when + ": " + MR + " should still be createable", readCreateable(user).contains(MR));
    }

    private static class ProjectData {
        private final Project project;
        private final Subject subject;
        private final ImagingSession session;

        private ProjectData(Project project, Subject subject, ImagingSession session) {
            this.project = project;
            this.subject = subject;
            this.session = session;
        }
    }

    private Project createProject(Accessibility accessibility) {
        final Project project = registerTempProject().accessibility(accessibility);
        mainAdminInterface().createProject(project);
        return project;
    }

    private ProjectData createProjectWithData(Accessibility accessibility) {
        final Project project = createProject(accessibility);
        final Subject subject = new Subject(project);
        final ImagingSession session = new MRSession(project, subject);
        mainAdminInterface().createSubject(subject);
        return new ProjectData(project, subject, session);
    }

    // Both run every check before failing, so one stale cache doesn't hide the state of the others.
    private void assertCanRead(User user, ProjectData data, String when) {
        final List<String> mismatches = new ArrayList<>();
        collectMismatch(mismatches, "GET project record", readProjectRecord(user, data.project), Matchers.is(200));
        collectMismatch(mismatches, "GET subjects", readSubjects(user, data.project), Matchers.is(200));
        collectMismatch(mismatches, "GET MR session", readSession(user, data.session), Matchers.is(200));
        if (!listProjectIds(user).contains(data.project.getId())) {
            mismatches.add("project missing from the user's project list");
        }
        assertTrue(when + ": expected read access, but: " + mismatches, mismatches.isEmpty());
    }

    private void assertNoAccess(User user, ProjectData data, String when) {
        final List<String> mismatches = new ArrayList<>();
        collectMismatch(mismatches, "GET project record", readProjectRecord(user, data.project), DENIED);
        collectMismatch(mismatches, "GET subjects", readSubjects(user, data.project), DENIED);
        collectMismatch(mismatches, "GET MR session", readSession(user, data.session), DENIED);
        if (listProjectIds(user).contains(data.project.getId())) {
            mismatches.add("project still in the user's project list");
        }
        if (readCreateable(user).contains(MR)) {
            mismatches.add(MR + " still createable");
        }
        assertTrue(when + ": expected no access, but: " + mismatches, mismatches.isEmpty());
    }

    /**
     * Checks that MR sessions are createable for the user, then creates a subject and MR session in the project as the user.
     * @return the created session
     */
    private ImagingSession assertCanCreate(User user, Project project, String when) {
        assertTrue(when + ": " + MR + " should be createable", readCreateable(user).contains(MR));
        final Subject subject = new Subject(project);
        final ImagingSession session = new MRSession(project, subject);
        try {
            interfaceFor(user).createSubject(subject);
        } catch (AssertionError e) {
            throw new AssertionError(when + ": PUT subject and MR session failed: " + e.getMessage(), e);
        }
        return session;
    }

    private Response readProjectRecord(User user, Project project) {
        return restDriver.queryBaseFor(user).queryParam("format", "json").get(mainAdminInterface().projectUrl(project));
    }

    private Response readSubjects(User user, Project project) {
        return restDriver.queryBaseFor(user).queryParam("format", "json").get(mainAdminInterface().projectSubjectsUrl(project));
    }

    private Response readExperiments(User user, Project project) {
        return restDriver.queryBaseFor(user).queryParam("format", "json").get(mainAdminInterface().projectExperimentsUrl(project));
    }

    private Response readSession(User user, ImagingSession session) {
        return restDriver.queryBaseFor(user).queryParam("format", "json").get(mainAdminInterface().subjectAssessorUrl(session));
    }

    private Response putSubject(User user, Subject subject) {
        return restDriver.queryBaseFor(user).put(mainAdminInterface().subjectUrl(subject));
    }

    private Response deleteSession(User user, ImagingSession session) {
        return restDriver.queryBaseFor(user).queryParam("removeFiles", true).delete(mainAdminInterface().subjectAssessorUrl(session));
    }

    // listProjects and readCreateableDataTypes both require a 200, so an auth failure can't pass as "absent"
    private List<String> listProjectIds(User user) {
        return interfaceFor(user).listProjects().stream().map(Project::getId).collect(Collectors.toList());
    }

    private Set<String> listedAmong(User user, Set<String> projectIds) {
        return listProjectIds(user).stream().filter(projectIds::contains).collect(Collectors.toSet());
    }

    private List<String> readCreateable(User user) {
        return interfaceFor(user).readCreateableDataTypes();
    }

    private static void assertStatus(String check, Response response, Matcher<Integer> expected) {
        assertThat(check + " (response: " + abbreviate(response) + ")", response.statusCode(), expected);
    }

    private static void collectMismatch(List<String> mismatches, String check, Response response, Matcher<Integer> expected) {
        if (!expected.matches(response.statusCode())) {
            mismatches.add(check + " returned " + response.statusCode() + " (expected " + expected + ")");
        }
    }

    private static String abbreviate(Response response) {
        final String body = response.asString().replaceAll("\\s+", " ");
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

}
