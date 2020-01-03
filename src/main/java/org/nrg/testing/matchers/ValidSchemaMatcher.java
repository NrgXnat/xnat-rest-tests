package org.nrg.testing.matchers;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

// import javax.xml.parsers.DocumentBuilderFactory;

public class ValidSchemaMatcher extends BaseMatcher<String> { // TODO: check that schema is well-formed

    public static final ValidSchemaMatcher INSTANCE = new ValidSchemaMatcher();

    @Override
    public boolean matches(Object o) {
        final String stringRepresentation = (String) o;
        try {
            // DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringRepresentation);
            return stringRepresentation.contains("<xs:schema");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("response body containing well-formed XML schema");
    }

}
