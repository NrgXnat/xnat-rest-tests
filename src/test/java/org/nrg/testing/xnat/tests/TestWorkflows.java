package org.nrg.testing.xnat.tests;

import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.Project;
import org.nrg.xnat.pogo.Subject;
import org.nrg.xnat.pogo.experiments.sessions.MRSession;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public class TestWorkflows extends BaseXnatRestTest {

    private Project adminProject;
    private Subject adminSubject;
    private MRSession adminExp;

    @BeforeMethod(alwaysRun = true)
    public void setupWorkflowTest() {
        adminExp     = new MRSession().date(LocalDate.parse("1999-12-12"));
        adminSubject = new Subject().label("workflow_subj_1").addExperiment(adminExp);
        adminProject = new Project().addSubject(adminSubject);

        restDriver.createProject(mainAdminUser, adminProject);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownWorkflowTest() {
        restDriver.deleteProjectSilently(mainAdminUser, adminProject);
    }

    @Test
    public void testModifyWorkflowStatus() {
        putWorkflow("Queued", true);
        putWorkflow("Complete", false); // Change the status to "Complete"
        assertEquals("Complete", mainAdminCredentials().given().queryParams(workflowGetParams()).get(restDriver.formatRestUrl("workflows")).jsonPath().getString("items.get(0).data_fields.status")); // Verify that the status was successfully changed
    }

    @Test
    public void testGetWorkflowByWorkflowId() {
        putWorkflow("Queued", true);
        final String workflowId = mainAdminCredentials().given().queryParams(workflowGetParams()).get(restDriver.formatRestUrl("workflows")).jsonPath().getString("items.get(0).data_fields.wrk_workflowData_id");
        mainAdminCredentials().expect().statusCode(200).when().get(restDriver.formatRestUrl("workflows", workflowId));
    }

    @Test
    public void testWorkflowUserPrivileges() {
        putWorkflow("Queued", true);
        mainCredentials().expect().statusCode(403).given().queryParams(workflowQueryParams("Complete", false)).when().put(restDriver.formatRestUrl("workflows"));
    }

    private void putWorkflow(String status, boolean includeDataType) {
        mainAdminCredentials().expect().statusCode(200).given().queryParams(workflowQueryParams(status, includeDataType)).put(restDriver.formatRestUrl("workflows"));
    }

    private Map<String, Object> workflowQueryParams(String status, boolean includeDataType) {
        final Map<String, Object> params = new HashMap<>();
        if (includeDataType) {
            params.put("wrk:workflowData/data_type", adminExp.getDataType().getXsiType());
        }
        params.put("wrk:workflowData/status", status);
        params.put("wrk:workflowData/pipeline_name", "WORKFLOW_TEST");
        params.put("wrk:workflowData/launch_time", "2013-05-20%2013:51:25.089");
        params.put("wrk:workflowData/id", adminExp.getAccessionNumber());
        return params;
    }

    private Map<String, Object> workflowGetParams() {
        final Map<String, Object> params = new HashMap<>();
        params.put("format", "json");
        params.put("wrk:workflowData/pipeline_name", "WORKFLOW_TEST");
        params.put("wrk:workflowData/launch_time", "2013-05-20%2013:51:25.089");
        params.put("wrk:workflowData/id", adminExp.getAccessionNumber());
        return params;
    }

}
