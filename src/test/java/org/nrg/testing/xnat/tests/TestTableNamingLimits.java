package org.nrg.testing.xnat.tests;

import org.hamcrest.Matchers;
import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.ExpectedFailure;
import org.nrg.testing.util.RandomHelper;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.Users;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.assessors.ManualQC;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.users.User;
import org.nrg.xnat.versions.Xnat_1_8_4;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestTableNamingLimits extends BaseXnatRestTest {

    private User longUsernameUser;
    private XnatInterface longUserInterface;
    private final Project longIdProject = new Project(RandomHelper.randomID(32));
    private final Subject subject = new Subject(longIdProject);
    private final ImagingSession session = new MRSession(longIdProject, subject);
    private final ManualQC manualQC = new ManualQC(longIdProject, subject, session);

    @BeforeClass
    public void setupScenario() {
        final String username = RandomHelper.randomLetters(24);
        longUsernameUser = Users.genericAccount().username(username).password(username);
        mainAdminInterface().createUser(longUsernameUser);
        longUserInterface = interfaceFor(longUsernameUser);
        longUserInterface.createProject(longIdProject);
    }

    @AfterClass(alwaysRun = true)
    public void removeProject() {
        restDriver.deleteProjectSilently(longUsernameUser, longIdProject);
    }

    @Test
    @AddedIn(Xnat_1_8_4.class)
    public void testLongSiteSearch() {
        final String searchXML = longUserInterface.queryBase().urlEncodingEnabled(false).get(formatRestUrl("/search/saved/@" + DataType.MANUAL_QC.getXsiType())).
                then().assertThat().statusCode(200).and().extract().response().asString();
        String cachedSearchId = null;
        for (int i = 0; i < 5; i++) {
            cachedSearchId = longUserInterface.jsonQuery().queryParam("cache", true).queryParam("refresh", true).body(searchXML).post(formatRestUrl("search")).
                    then().assertThat().statusCode(200).and().extract().path("ResultSet.ID"); // try to cache a temporary search table to check XNAT-6784
        }
        longUserInterface.jsonQuery().get(formatRestUrl("/search", cachedSearchId)).then().assertThat().body("ResultSet.Result.label", Matchers.hasItem(manualQC.getLabel()));
    }

}
