package org.nrg.testing.xnat.tests.customforms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomFormConstants {
    public static final String PROJECT_ID_HERE = "PROJECT_ID_HERE";
    public static final String APPEND_FORM_NUMBER = "APPEND_FORM_NUMBER";
    public static final String CUSTOM_FORM_MANAGER_ROLE_NAME = "CustomFormManager";
    public static final List<String> SITE_FORM_TEMPLATES = Arrays.asList("SiteWideProjectForm.json", "SiteWideSubjectForm.json", "SiteWideMRSessionForm.json");

    public static final List<String> PROJECT_FORM_TEMPLATES = Arrays.asList("ProjectSpecificSubjectForm.json", "ProjectSpecificMRSessionForm.json", "ProjectSpecificProjectForm.json");

    public static final String SHARED_SUBJECT_FORM_TEMPLATE = "{\"submission\":{\"data\":{\"zIndex\":10, \"xnatDatatype\":{\"label\":\"Subject\", \"value\":\"xnat:subjectData\"}, \"isThisASiteWideConfiguration\":\"no\", \"xnatProject\":[{\"label\":\"PROJECT1_ID_HERE\", \"value\":\"PROJECT1_ID_HERE\"}, {\"label\":\"PROJECT2_ID_HERE\", \"value\":\"PROJECT2_ID_HERE\"}]}}, \"builder\":{\"display\":\"form\",\"title\":\"FormAPPEND_FORM_NUMBER\", \"settings\":{},\"components\":[{\"label\":\"Text Field APPEND_FORM_NUMBER\", \"key\":\"textFieldAPPEND_FORM_NUMBER\", \"type\":\"textfield\", \"input\":true,\"tableView\":true}]}}";

    public static final String EXCLUSIVE_TO_PROJECT_SUBJECT_FORM = "{\"submission\":{\"data\":{\"zIndex\":10, \"xnatDatatype\":{\"label\":\"Subject\", \"value\":\"xnat:subjectData\"}, \"isThisASiteWideConfiguration\":\"no\", \"xnatProject\":[{\"label\":\"PROJECT_ID_HERE\", \"value\":\"PROJECT_ID_HERE\"}]}}, \"builder\":{\"display\":\"form\", \"title\":\"FormAPPEND_FORM_NUMBER\", \"settings\":{}, \"components\":[{\"label\":\"Text Field APPEND_FORM_NUMBER\", \"key\":\"textFieldAPPEND_FORM_NUMBER\", \"type\":\"textfield\", \"input\":true, \"tableView\":true}]}}";

}
