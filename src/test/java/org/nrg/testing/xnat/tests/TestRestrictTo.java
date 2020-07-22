package org.nrg.testing.xnat.tests;

import org.nrg.testing.annotations.TestRequires;
import org.testng.annotations.Test;

@TestRequires(plugins = "xnat-test-harness-plugin")
public class TestRestrictTo extends BasePermissionComparisonTest {

    public TestRestrictTo() {
        super(true, "restrictTo");
    }

    // @Project tests

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectReadClosedXnat() {
        new ProjectTest().operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectReadOpenXnat() {
        new ProjectTest().operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectEditClosedXnat() {
        new ProjectTest().operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectEditOpenXnat() {
        new ProjectTest().operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectDeleteClosedXnat() {
        new ProjectTest().operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectDeleteOpenXnat() {
        new ProjectTest().operation(Operation.DELETE).openXnat(true).run();
    }

    // @Subject tests

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectReadClosedXnat() {
        new SubjectTest().operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectReadOpenXnat() {
        new SubjectTest().operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectEditClosedXnat() {
        new SubjectTest().operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectEditOpenXnat() {
        new SubjectTest().operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectDeleteClosedXnat() {
        new SubjectTest().operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectDeleteOpenXnat() {
        new SubjectTest().operation(Operation.DELETE).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectReadClosedXnat() {
        new SubjectTest().project(true).operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectReadOpenXnat() {
        new SubjectTest().project(true).operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectEditClosedXnat() {
        new SubjectTest().project(true).operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectEditOpenXnat() {
        new SubjectTest().project(true).operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectDeleteClosedXnat() {
        new SubjectTest().project(true).operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectDeleteOpenXnat() {
        new SubjectTest().project(true).operation(Operation.DELETE).openXnat(true).run();
    }

    // @Experiment tests

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectAssessorReadClosedXnat() {
        new SubjectAssessorTest().operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectAssessorReadOpenXnat() {
        new SubjectAssessorTest().operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectAssessorEditClosedXnat() {
        new SubjectAssessorTest().operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectAssessorEditOpenXnat() {
        new SubjectAssessorTest().operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectAssessorDeleteClosedXnat() {
        new SubjectAssessorTest().operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectAssessorDeleteOpenXnat() {
        new SubjectAssessorTest().operation(Operation.DELETE).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectAssessorReadClosedXnat() {
        new SubjectAssessorTest().project(true).operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectAssessorReadOpenXnat() {
        new SubjectAssessorTest().project(true).operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectAssessorEditClosedXnat() {
        new SubjectAssessorTest().project(true).operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectAssessorEditOpenXnat() {
        new SubjectAssessorTest().project(true).operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectAssessorDeleteClosedXnat() {
        new SubjectAssessorTest().project(true).operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectAssessorDeleteOpenXnat() {
        new SubjectAssessorTest().project(true).operation(Operation.DELETE).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectSubjectAssessorReadClosedXnat() {
        new SubjectAssessorTest().subject(true).operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectSubjectAssessorReadOpenXnat() {
        new SubjectAssessorTest().subject(true).operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectSubjectAssessorEditClosedXnat() {
        new SubjectAssessorTest().subject(true).operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectSubjectAssessorEditOpenXnat() {
        new SubjectAssessorTest().subject(true).operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectSubjectAssessorDeleteClosedXnat() {
        new SubjectAssessorTest().subject(true).operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectSubjectAssessorDeleteOpenXnat() {
        new SubjectAssessorTest().subject(true).operation(Operation.DELETE).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectSubjectAssessorReadClosedXnat() {
        new SubjectAssessorTest().subject(true).project(true).operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectSubjectAssessorReadOpenXnat() {
        new SubjectAssessorTest().subject(true).project(true).operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectSubjectAssessorEditClosedXnat() {
        new SubjectAssessorTest().subject(true).project(true).operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectSubjectAssessorEditOpenXnat() {
        new SubjectAssessorTest().subject(true).project(true).operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectSubjectAssessorDeleteClosedXnat() {
        new SubjectAssessorTest().subject(true).project(true).operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectSubjectAssessorDeleteOpenXnat() {
        new SubjectAssessorTest().subject(true).project(true).operation(Operation.DELETE).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSessionAssessorReadClosedXnat() {
        new SessionAssessorTest().operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSessionAssessorReadOpenXnat() {
        new SessionAssessorTest().operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSessionAssessorEditClosedXnat() {
        new SessionAssessorTest().operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSessionAssessorEditOpenXnat() {
        new SessionAssessorTest().operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSessionAssessorDeleteClosedXnat() {
        new SessionAssessorTest().operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSessionAssessorDeleteOpenXnat() {
        new SessionAssessorTest().operation(Operation.DELETE).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSessionAssessorReadClosedXnat() {
        new SessionAssessorTest().project(true).operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSessionAssessorReadOpenXnat() {
        new SessionAssessorTest().project(true).operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSessionAssessorEditClosedXnat() {
        new SessionAssessorTest().project(true).operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSessionAssessorEditOpenXnat() {
        new SessionAssessorTest().project(true).operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSessionAssessorDeleteClosedXnat() {
        new SessionAssessorTest().project(true).operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSessionAssessorDeleteOpenXnat() {
        new SessionAssessorTest().project(true).operation(Operation.DELETE).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectSessionAssessorReadClosedXnat() {
        new SessionAssessorTest().subject(true).operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectSessionAssessorReadOpenXnat() {
        new SessionAssessorTest().subject(true).operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectSessionAssessorEditClosedXnat() {
        new SessionAssessorTest().subject(true).operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectSessionAssessorEditOpenXnat() {
        new SessionAssessorTest().subject(true).operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToSubjectSessionAssessorDeleteClosedXnat() {
        new SessionAssessorTest().subject(true).operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToSubjectSessionAssessorDeleteOpenXnat() {
        new SessionAssessorTest().subject(true).operation(Operation.DELETE).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectSessionAssessorReadClosedXnat() {
        new SessionAssessorTest().subject(true).project(true).operation(Operation.READ).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectSessionAssessorReadOpenXnat() {
        new SessionAssessorTest().subject(true).project(true).operation(Operation.READ).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectSessionAssessorEditClosedXnat() {
        new SessionAssessorTest().subject(true).project(true).operation(Operation.EDIT).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectSessionAssessorEditOpenXnat() {
        new SessionAssessorTest().subject(true).project(true).operation(Operation.EDIT).openXnat(true).run();
    }

    @Test
    @TestRequires(closedXnat = true)
    public void testRestrictToProjectSubjectSessionAssessorDeleteClosedXnat() {
        new SessionAssessorTest().subject(true).project(true).operation(Operation.DELETE).openXnat(false).run();
    }

    @Test
    @TestRequires(openXnat = true)
    public void testRestrictToProjectSubjectSessionAssessorDeleteOpenXnat() {
        new SessionAssessorTest().subject(true).project(true).operation(Operation.DELETE).openXnat(true).run();
    }

}
