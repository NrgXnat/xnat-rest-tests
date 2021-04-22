package org.nrg.testing.dicom;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SimplestDicomScriptValidation extends ScriptValidation {

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        validate(dicomFiles, generateValidationObject(), true);
    }

    @Override
    public void validateScriptDidntRun(List<File> dicomFiles) {
        final RootDicomObject root = generateValidationObjectForNonrunningScript();
        if (root != null) {
            validate(dicomFiles, root, false);
        }
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return new ArrayList<>();
    }

    protected RootDicomObject generateValidationObjectForNonrunningScript() {
        return null;
    }

    protected abstract RootDicomObject generateValidationObject();

    private void validate(List<File> dicomFiles, RootDicomObject root, boolean ran) {
        final Map<File, DicomObject> dicomMap = new HashMap<>();
        for (File dicomFile : dicomFiles) {
            dicomMap.put(dicomFile, root);
        }
        validate(dicomMap, interfileChecksWhen(ran));
    }

}
