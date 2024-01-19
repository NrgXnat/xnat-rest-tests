package org.nrg.testing.dicom;

import java.util.Arrays;
import java.util.List;

public class BlankValuesSeveralElements extends CompositeScriptValidation {

    @Override
    protected List<Class<? extends SimplestDicomScriptValidation>> componentScripts() {
        return Arrays.asList(BlankValuesSingleStandardTag.class, BlankValuesSingleStandardTagpath.class);
    }

}
