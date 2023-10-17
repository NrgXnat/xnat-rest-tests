package org.nrg.testing.dicom;

import java.util.Arrays;
import java.util.List;

public class BlankKnownPhiSeveralElements extends CompositeScriptValidation {

    @Override
    protected List<Class<? extends SimplestDicomScriptValidation>> componentScripts() {
        return Arrays.asList(BlankKnownPhiSingleStandardTag.class, BlankKnownPhiSingleStandardTagpath.class);
    }

}
