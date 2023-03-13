package org.nrg.testing.xnat.tests.customfields;

import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.pogo.CustomFieldScope;

import java.util.Collections;

public class BaseCustomFieldsRestTest extends BaseXnatRestTest {
    protected void verifyGet403(CustomFieldScope fieldScope) {
        expect403(() -> mainInterface().readCustomFields(fieldScope));
    }

    protected void verifyGet404(CustomFieldScope fieldScope) {
        expect404(() -> mainInterface().readCustomFields(fieldScope));
    }

    protected void verifyGetIndividual403(CustomFieldScope fieldScope, final String fieldKey) {
        expect403(() -> mainInterface().readCustomField(fieldScope, fieldKey));
    }

    protected void verifyGetIndividual404(CustomFieldScope fieldScope, final String fieldKey) {
        expect404(() -> mainInterface().readCustomField(fieldScope, fieldKey));
    }

    protected void verifyPut403(CustomFieldScope fieldScope) {
        expect403(() -> mainInterface().setCustomFields(fieldScope, Collections.singletonMap("BAD", "VAL")));
    }

    protected void verifyPut404(CustomFieldScope fieldScope) {
        expect404(() -> mainInterface().setCustomFields(fieldScope, Collections.singletonMap("BAD", "VAL")));
    }

    protected void verifyDelete403(CustomFieldScope fieldScope, final String fieldKey) {
        expect403(() -> mainInterface().deleteCustomField(fieldScope, fieldKey));
    }

    protected void verifyDelete404(CustomFieldScope fieldScope, final String fieldKey) {
        expect404(() -> mainInterface().deleteCustomField(fieldScope, fieldKey));
    }
}
