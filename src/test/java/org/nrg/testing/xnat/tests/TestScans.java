package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.DataType;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.pogo.extensions.subject_assessor.SessionImportExtension;
import org.nrg.xnat.versions.Xnat_1_8_6;
import org.testng.annotations.Test;

import static org.nrg.testing.TestGroups.SCANS;
import static org.nrg.testing.TestGroups.VALIDATION;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

@Test(groups = SCANS)
public class TestScans extends BaseXnatRestTest {

    @Test(groups = VALIDATION)
    @AddedIn(Xnat_1_8_6.class)
    public void testImplicitScanTypeChangeValidation() {
        final Project tempProject = registerTempProject();
        final Subject subject = new Subject(tempProject);
        final MRSession session = new MRSession(tempProject, subject);
        session.extension(new SessionImportExtension(session, getDataFile("scan1.zip")));
        mainInterface().createProject(tempProject);
        final Scan initialScan = mainInterface().readScans(tempProject, subject, session).get(0);
        assertEquals(DataType.MR_SCAN.getXsiType(), initialScan.getXsiType());
        mainInterface().queryBase().queryParam("xnat:ctScanData/note","test").put(mainInterface().scanUrl(initialScan))
                .then().assertThat().statusCode(200); // silently failing is not good, but XNAT-7176 is too painful to fix
        final Scan scanPostUpdate = mainInterface().readScans(tempProject, subject, session).get(0);
        assertEquals(DataType.MR_SCAN.getXsiType(), scanPostUpdate.getXsiType());
        assertNull(scanPostUpdate.getNote());
        mainInterface().deleteSubject(tempProject, subject);
    }

}
