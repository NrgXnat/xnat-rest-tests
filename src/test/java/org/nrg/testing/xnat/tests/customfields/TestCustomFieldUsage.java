package org.nrg.testing.xnat.tests.customfields;

import org.nrg.testing.annotations.AddedIn;
import org.nrg.testing.annotations.TestedApiSpec;
import org.nrg.testing.annotations.TestedApiSpecs;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.CustomFieldScope;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.ImagingSession;
import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.experiments.SessionAssessor;
import org.nrg.xnat.pogo.experiments.assessors.QC;
import org.nrg.xnat.pogo.experiments.scans.MRScan;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.nrg.xnat.rest.NotFoundException;
import org.nrg.xnat.versions.Xnat_1_8_5;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.http.Method.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@AddedIn(Xnat_1_8_5.class)
public class TestCustomFieldUsage extends BaseXnatRestTest {
    private Project project;
    private Subject subject;
    private ImagingSession session;
    private Scan scan;
    private SessionAssessor qc;

    @BeforeMethod
    public void initProject() {
        project = new Project();
        subject = new Subject(project);
        session = new MRSession(project, subject);
        scan = new MRScan(session, "1");
        qc = new QC(project, subject, session);
        mainInterface().createProject(project);
    }

    @AfterMethod(alwaysRun = true)
    public void teardownProject() {
        restDriver.deleteProjectSilently(mainUser, project);
    }

    @Test
    public void testPossibleFields() {
        final Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("abc", "abc123");
        innerMap.put("abcint", 5);
        final Map<String, Object> complexFields = new HashMap<>();
        complexFields.put("str", "str123");
        complexFields.put("int", 100);
        complexFields.put("float", 100.5);
        complexFields.put("nullVal", null);
        complexFields.put("arr", Arrays.asList("1", "2", "3"));
        complexFields.put("obj", innerMap);
        final CustomFieldScope scope = new CustomFieldScope().project(project);
        assertEquals(complexFields, mainInterface().setCustomFields(scope, complexFields));
        assertEquals(complexFields, mainInterface().readCustomFields(scope));
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProject() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project)
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/subjects/{subject}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/subjects/{subject}/fields/{fieldName}")
    })
    public void testFieldsLifecycleSubjectId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        subject(subject.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabelExperimentLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel()).
                        experiment(session.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabelExperimentId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel()).
                        experiment(session.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectIdExperimentLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber()).
                        experiment(session.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectIdExperimentId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber()).
                        experiment(session.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsExperimentLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        experiment(session.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsExperimentId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        experiment(session.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/experiments/{experiment}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/experiments/{experiment}/fields/{fieldName}")
    })
    public void testFieldsLifecycleExperimentId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        experiment(session.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/scans/{scan}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/scans/{scan}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabelExperimentLabelScan() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel()).
                        experiment(session.getLabel()).
                        scan(scan)
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/scans/{scan}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/scans/{scan}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabelExperimentIdScan() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel()).
                        experiment(session.getAccessionNumber()).
                        scan(scan)
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/scans/{scan}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/scans/{scan}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectIdExperimentLabelScan() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber()).
                        experiment(session.getLabel()).
                        scan(scan)
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/scans/{scan}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/scans/{scan}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectIdExperimentIdScan() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber()).
                        experiment(session.getAccessionNumber()).
                        scan(scan)
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/scans/{scan}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/scans/{scan}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsExperimentLabelScan() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        experiment(session.getLabel()).
                        scan(scan)
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/scans/{scan}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/scans/{scan}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsExperimentIdScan() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        experiment(session.getAccessionNumber()).
                        scan(scan)
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/experiments/{experiment}/scans/{scan}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/experiments/{experiment}/scans/{scan}/fields/{fieldName}")
    })
    public void testFieldsLifecycleExperimentIdScan() {
        testFieldLifecycle(
                new CustomFieldScope().
                        experiment(session.getAccessionNumber()).
                        scan(scan)
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabelExperimentLabelAssessorLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel()).
                        experiment(session.getLabel()).
                        assessor(qc.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabelExperimentLabelAssessorId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel()).
                        experiment(session.getLabel()).
                        assessor(qc.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabelExperimentIdAssessorLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel()).
                        experiment(session.getAccessionNumber()).
                        assessor(qc.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectLabelExperimentIdAssessorId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getLabel()).
                        experiment(session.getAccessionNumber()).
                        assessor(qc.getAccessionNumber())
        );
    }


    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectIdExperimentLabelAssessorLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber()).
                        experiment(session.getLabel()).
                        assessor(qc.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectIdExperimentLabelAssessorId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber()).
                        experiment(session.getLabel()).
                        assessor(qc.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectIdExperimentIdAssessorLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber()).
                        experiment(session.getAccessionNumber()).
                        assessor(qc.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/subjects/{subject}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsSubjectIdExperimentIdAssessorId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        subject(subject.getAccessionNumber()).
                        experiment(session.getAccessionNumber()).
                        assessor(qc.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsExperimentLabelAssessorLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        experiment(session.getLabel()).
                        assessor(qc.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsExperimentLabelAssessorId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        experiment(session.getLabel()).
                        assessor(qc.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsExperimentIdAssessorLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        experiment(session.getAccessionNumber()).
                        assessor(qc.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/projects/{project}/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleProjectsExperimentIdAssessorId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        project(project).
                        experiment(session.getAccessionNumber()).
                        assessor(qc.getAccessionNumber())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleExperimentIdAssessorLabel() {
        testFieldLifecycle(
                new CustomFieldScope().
                        experiment(session.getAccessionNumber()).
                        assessor(qc.getLabel())
        );
    }

    @Test
    @TestedApiSpecs({
            @TestedApiSpec(method = {GET, PUT},    url = "/xapi/custom-fields/experiments/{experiment}/assessors/{assessor}/fields"),
            @TestedApiSpec(method = {GET, DELETE}, url = "/xapi/custom-fields/experiments/{experiment}/assessors/{assessor}/fields/{fieldName}")
    })
    public void testFieldsLifecycleExperimentIdAssessorId() {
        testFieldLifecycle(
                new CustomFieldScope().
                        experiment(session.getAccessionNumber()).
                        assessor(qc.getAccessionNumber())
        );
    }

    /**
     * This runs a generic test to exercise the full functionality of the custom-fields API. The following operations are checked,
     * given a specific scope:
     *   1) Retrieve fields initially and verify the result is empty
     *   2) Set fields and verify the API returns the same value as was provided
     *   3) Retrieve fields and verify they have been updated
     *   4) Set a new field and verify the API returns a *merged* JSON field structure
     *   5) Retrieve fields and verify they have been updated
     *   6) Retrieve a subset of fields and verify only the subset is returned
     *   7) Retrieve a subset of fields (including specifying a nonexistent field) and verify only the subset is returned
     *   8) Retrieve an individual field
     *   9) Attempt to retrieve a specific nonexistent field and get a 404
     *   10) Set an existing field and verify the API returns a JSON with an updated value, but other fields untouched
     *   11) Retrieve fields and verify they have been updated
     *   12) Delete a field and verify the API returns the rest of the fields
     *   13) Attempt to delete a nonexistent field
     *   14) Retrieve the fields one last time to verify all is as expected
     */
    private void testFieldLifecycle(CustomFieldScope fieldScope) {
        final Map<String, Object> emptyMap = new HashMap<>();
        final String firstKey = "KEY1";
        final String firstVal = "VAL 001";
        final String secondKey = "key2";
        final Integer secondVal = 1000;
        final String secondValOverwrite = "new value";
        final Map<String, Object> firstFields = Collections.singletonMap(firstKey, firstVal);
        final Map<String, Object> secondFields = Collections.singletonMap(secondKey, secondVal);
        final Map<String, Object> mergedFields = new HashMap<>(firstFields);
        mergedFields.put(secondKey, secondVal);
        final Map<String, Object> fieldsPostOverwrite = new HashMap<>(firstFields);
        fieldsPostOverwrite.put(secondKey, secondValOverwrite);

        assertEquals(emptyMap, mainInterface().readCustomFields(fieldScope)); // 1)
        assertEquals(firstFields, mainInterface().setCustomFields(fieldScope, firstFields)); // 2)
        assertEquals(firstFields, mainInterface().readCustomFields(fieldScope)); // 3)
        assertEquals(mergedFields, mainInterface().setCustomFields(fieldScope, secondFields)); // 4)
        assertEquals(mergedFields, mainInterface().readCustomFields(fieldScope)); // 5)
        assertEquals(secondFields, mainInterface().readCustomFields(fieldScope, Collections.singletonList(secondKey))); // 6)
        assertEquals(secondFields, mainInterface().readCustomFields(fieldScope, Arrays.asList(secondKey, "DNE"))); // 7)
        assertEquals(firstVal, mainInterface().readCustomField(fieldScope, firstKey)); // 8)

        try {
            mainInterface().readCustomField(fieldScope, "DNE"); // 9)
            fail("Should have thrown 404");
        } catch (NotFoundException expected) {}

        assertEquals(fieldsPostOverwrite, mainInterface().setCustomFields(fieldScope, Collections.singletonMap(secondKey, secondValOverwrite))); // 10)
        assertEquals(fieldsPostOverwrite, mainInterface().readCustomFields(fieldScope)); // 11)
        assertEquals(firstFields, mainInterface().deleteCustomField(fieldScope, secondKey)); // 12)
        assertEquals(firstFields, mainInterface().deleteCustomField(fieldScope, "DOESNOTEXIST")); // 13)
        assertEquals(firstFields, mainInterface().readCustomFields(fieldScope)); // 14)
    }

}
