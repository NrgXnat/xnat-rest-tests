package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.search.SearchColumn;
import org.nrg.xnat.pogo.search.SearchField;
import org.nrg.xnat.pogo.search.SearchFieldTypes;
import org.nrg.xnat.pogo.search.SearchResponse;
import org.nrg.xnat.pogo.search.SearchRow;
import org.nrg.xnat.versions.Xnat_1_8_0;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import static org.nrg.testing.TestGroups.PERMISSIONS;
import static org.nrg.testing.TestGroups.SEARCH;
import static org.testng.AssertJUnit.assertTrue;

@AddedIn(Xnat_1_8_0.class)
public class TestSearchPermissions extends BaseSearchPermissionsTest {

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testProjectSearchAccess() {
        final SearchField idField = new SearchField()
                .fieldId("ID")
                .elementName(DataType.PROJECT)
                .type(SearchFieldTypes.STRING)
                .header("ID");
        final SearchColumn idColumn = idField.generateExpectedSearchColumn(true, true);

        final SearchResponse searchResponse = mainInterface().performSearch(mainInterface().getDefaultSearch(DataType.PROJECT));
        assertTrue(searchResponse.getColumns().stream().anyMatch(column -> column.getKey().equals("id")));
        new SearchValidator<Project>(
                (project, row) -> project.getId().equals(row.get(idColumn.getKey()))
        ).validateSearchResponseResultContains(
                searchResponse.getResult(),
                Arrays.asList(publicProject, protectedProject, customUserGroupProject),
                Collections.singletonList(privateProject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testSubjectSearchAccess() {
        new SearchValidator<Subject>(
                new ArrayList<>(),
                mainInterface().getDefaultSearch(customUserGroupProject, DataType.SUBJECT),
                (subject, row) -> subject.getLabel().equals(row.getSubjectLabelInProject(customUserGroupProject))
        ).performAndValidateSearch(customGroupSubject, customGroupSubjectDob, customGroupSubjectAge, publicSubject);

        final SearchValidator<Subject> siteSubjectValidator = new SearchValidator<>(
                (subject, row) -> subject.getLabel().equals(row.get("subject_label"))
        );
        siteSubjectValidator.validateSearchResponseResultContains(
                mainInterface().performSearch(mainInterface().getDefaultSearch(DataType.SUBJECT)).getResult(),
                Arrays.asList(publicSubject, customGroupSubject),
                Arrays.asList(protectedSubject, privateSubject)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testSessionSearchAccess() {
        new SearchValidator<>(
                new ArrayList<>(),
                mainInterface().getDefaultSearch(customUserGroupProject, DataType.MR_SESSION),
                mrMatchesLabel
        ).performAndValidateSearch(customGroupMrSession);

        new SearchValidator<ImagingSession>(
                (session, row) -> session.getLabel().equals(row.get("label"))
        ).validateSearchResponseResultContains(
                mainInterface().performSearch(mainInterface().getDefaultSearch(DataType.PET_SESSION)).getResult(),
                Collections.singletonList(publicPet),
                Collections.singletonList(customGroupPetSession)
        );

        searchAndCheckSessions(
                DataType.MR_SESSION,
                Arrays.asList(publicMr, customGroupMrSession),
                Arrays.asList(privateMr, protectedMr, publicPet, customGroupPetSession)
        );

        searchAndCheckSessions(
                DataType.PET_SESSION,
                Collections.singletonList(publicPet),
                Arrays.asList(privateMr, protectedMr, publicMr, customGroupMrSession, customGroupPetSession)
        );
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    public void testScanSearchAccess() {
        new SearchValidator<>(
                scanMatchesIdAndSession
        ).validateSearchResponseResultContains(
                mainInterface().performSearch(mainInterface().getDefaultSearch(DataType.MR_SCAN)).getResult(),
                Collections.singletonList(publicMrScan),
                Arrays.asList(customGroupMrSessionPetScan, customGroupPetSessionMrScan, customGroupPetSessionPetScan)
        );

        new SearchValidator<>(
                new ArrayList<>(),
                mainInterface().getDefaultSearch(publicProject, DataType.MR_SCAN),
                scanMatchesIdAndSession
        ).performAndValidateSearch(publicMrScan);
    }

    @Test(groups = {SEARCH, PERMISSIONS})
    @ExpectedFailure(jiraIssue = "XNAT-6357")
    public void testScanSearchAccessIncludeCustomUserGroupData() {
        new SearchValidator<>(
                scanMatchesIdAndSession
        ).validateSearchResponseResultContains(
                mainInterface().performSearch(mainInterface().getDefaultSearch(DataType.MR_SCAN)).getResult(),
                Arrays.asList(publicMrScan, customGroupMrSessionMrScan),
                Arrays.asList(customGroupMrSessionPetScan, customGroupPetSessionMrScan, customGroupPetSessionPetScan, protectedMrScan, privateMrScan)
        );

        new SearchValidator<>(
                new ArrayList<>(),
                mainInterface().getDefaultSearch(customUserGroupProject, DataType.MR_SCAN),
                scanMatchesIdAndSession
        ).performAndValidateSearch(customGroupMrSessionMrScan);
    }

    private void searchAndCheckSessions(DataType dataType, List<ImagingSession> sessionsToBeIncluded, List<ImagingSession> sessionsToBeExcluded) {
        new SearchValidator<ImagingSession>(
                (session, row) -> session.getLabel().equals(row.get("label"))
        ).validateSearchResponseResultContains(
                mainInterface().performSearch(mainInterface().getDefaultSearch(dataType)).getResult(),
                sessionsToBeIncluded,
                sessionsToBeExcluded
        );
    }

}
