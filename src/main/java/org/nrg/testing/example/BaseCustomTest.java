package org.nrg.testing.example;

import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.subinterfaces.XnatFunctionalitySubinterface;

import java.util.Collections;
import java.util.List;

public abstract class BaseCustomTest extends BaseXnatRestTest {

    @Override
    protected List<Class<? extends XnatFunctionalitySubinterface>> additionalRegisteredSubinterfaces() {
        return Collections.singletonList(SchemaOnlyExampleSubinterface.class);
    }

    protected SchemaOnlyExampleSubinterface mainSchemaOnlyExampleSubinterface() {
        return mainInterface().getSubinterface(SchemaOnlyExampleSubinterface.class);
    }

}
