package org.nrg.testing.xnat.tests.customforms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import org.apache.commons.io.FileUtils;
import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.testing.xnat.customforms.pojo.AppliesTo;
import org.nrg.testing.xnat.exceptions.ProcessingException;
import org.nrg.testing.xnat.customforms.pojo.CustomFormPojo;
import org.nrg.xnat.interfaces.XnatInterface;
import org.nrg.xnat.pogo.Project;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.testng.AssertJUnit.assertEquals;

public class BaseCustomFormRestTest extends BaseXnatRestTest {

    protected Set<String> createSiteSpecificForms(final XnatInterface xnatInterface, List<String> files, final int statusCode) throws IOException {
        Set<String> generatedFormUUIDs = new HashSet<>();
        for (String file : files) {
            String formUUID = createForm(xnatInterface, file, null, statusCode);
            assertEquals(formUUID, UUID.fromString(formUUID).toString());
            generatedFormUUIDs.add(formUUID);
        }
        return generatedFormUUIDs;
    }

    protected Set<String> createSiteSpecificForms(final XnatInterface xnatInterface, final int statusCode) throws IOException {
        return createSiteSpecificForms(xnatInterface, CustomFormConstants.SITE_FORM_TEMPLATES, statusCode);
    }

    protected Set<String> createProjectSpecificForms(final XnatInterface xnatInterface, final Project project1, final int statusCode) throws IOException {
        Set<String> generatedFormUUIDs = new HashSet<>();
        for (String file : CustomFormConstants.PROJECT_FORM_TEMPLATES) {
            String formUUID = createForm(xnatInterface, file, project1.getId(), statusCode);
            if (statusCode == 201) {
                assertEquals(formUUID, UUID.fromString(formUUID).toString());
                generatedFormUUIDs.add(formUUID);
            }
        }
        return generatedFormUUIDs;
    }


    protected void deleteForms(XnatInterface xnatInterface, final Set<String> generatedFormUUIDs, final int statusCode) throws IOException {
        fetchForms(xnatInterface).stream()
                .filter(form -> generatedFormUUIDs.contains(form.getFormUUID()))
                .forEach(form -> {
                    try {
                        deleteForms(xnatInterface, form, statusCode);
                    } catch (JsonProcessingException jpe) {
                        throw new ProcessingException(jpe.getMessage());
                    }
                });
    }

    protected String createForm(final XnatInterface xnatInterface, final String file, final String projectId, int statusCode) throws IOException {
        return saveFormAndAssert(xnatInterface, file, projectId, statusCode);
    }

    protected List<CustomFormPojo> fetchForms(final XnatInterface xnatInterface) throws IOException {
        final String jsonResponse = getJsonResponse(xnatInterface, "/customforms", 200);
        return objectMapper.readValue(jsonResponse, new TypeReference<List<CustomFormPojo>>(){});
    }

    protected String getJsonResponse(final XnatInterface xnatInterface, final String url, final int statusCode) {
        return xnatInterface
                .requestWithCsrfToken()
                .get(formatXapiUrl(url))
                .then()
                .assertThat()
                .statusCode(statusCode)
                .and()
                .extract()
                .response()
                .asString();
    }

    protected String getJsonResponse(final XnatInterface xnatInterface, final String url, Map<String, String> queryParams, final int statusCode) {
        return xnatInterface
                .requestWithCsrfToken()
                .queryParams(queryParams)
                .get(formatXapiUrl(url))
                .then()
                .assertThat()
                .statusCode(statusCode)
                .and()
                .extract()
                .response()
                .asString();
    }

    protected String deleteForms(final XnatInterface xnatInterface, CustomFormPojo customFormPojo, int statusCode) throws JsonProcessingException {
        return xnatInterface
                .requestWithCsrfToken()
                .contentType(ContentType.JSON)
                .body(customFormPojo.getAppliesToList())
                .delete(formatXapiUrl("customforms"))
                .then()
                .assertThat()
                .statusCode(statusCode)
                .and()
                .extract()
                .response()
                .asString();
    }

    protected String saveFormAndAssert(final XnatInterface xnatInterface, final String file, final String projectId, int statusCode) throws IOException {
        String rawTestString = FileUtils.readFileToString(getDataFile("custom_forms/" + file), "UTF-8");
        rawTestString = rawTestString.replaceAll(CustomFormConstants.APPEND_FORM_NUMBER, String.valueOf(ThreadLocalRandom.current().nextInt()));
        if (null != projectId) {
            rawTestString = rawTestString.replaceAll(CustomFormConstants.PROJECT_ID_HERE, projectId);
        }
        return saveFormAndAssert(xnatInterface, rawTestString, statusCode);
    }

    protected String saveFormAndAssert(final XnatInterface xnatInterface, final String formString, int statusCode)  {
        return xnatInterface
                .requestWithCsrfToken()
                .contentType(ContentType.JSON)
                .body(formString)
                .put(formatXapiUrl("customforms/save"))
                .then()
                .assertThat()
                .statusCode(statusCode)
                .and()
                .extract()
                .response()
                .asString();
    }

    protected String sendRequest(final XnatInterface xnatInterface, final String url, final List<AppliesTo> appliesTo, int statusCode)  {
        return xnatInterface
                .requestWithCsrfToken()
                .contentType(ContentType.JSON)
                .body(appliesTo)
                .post(formatXapiUrl(url))
                .then()
                .assertThat()
                .statusCode(statusCode)
                .and()
                .extract()
                .response()
                .asString();
    }


    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

}
