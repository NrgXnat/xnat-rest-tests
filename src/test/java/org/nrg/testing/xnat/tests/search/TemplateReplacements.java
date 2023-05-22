package org.nrg.testing.xnat.tests.search;

import org.nrg.testing.CommonStringUtils;
import org.nrg.xnat.pogo.Project;

import java.util.HashMap;
import java.util.Map;

public class TemplateReplacements {

    private final Map<String, String> replacements = new HashMap<>();

    TemplateReplacements project(Project project) {
        replacements.put("%PROJECT_ID%", project.getId());
        return this;
    }

    String inject(String input) {
        return CommonStringUtils.replaceEach(input, replacements);
    }

}
