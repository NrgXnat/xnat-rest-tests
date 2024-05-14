package org.nrg.testing.example;

import org.nrg.testing.xnat.BaseXnatRestTest;
import org.nrg.xnat.subinterfaces.CoreXnatFunctionalitySubinterface;

import java.util.Collections;
import java.util.List;

public abstract class BaseCustomTest extends BaseXnatRestTest {

    @Override
    protected List<Class<? extends CoreXnatFunctionalitySubinterface>> additionalRegisteredSubinterfaces() {
        return Collections.singletonList(SchemaOnlyExampleSubinterface.class);
    }

    protected SchemaOnlyExampleSubinterface mainSchemaOnlyExampleSubinterface() {
        return mainInterface().getSubinterface(SchemaOnlyExampleSubinterface.class);
    }

}
