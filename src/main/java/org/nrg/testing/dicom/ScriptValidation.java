package org.nrg.testing.dicom;

import java.io.File;
import java.util.List;
import java.util.Map;

public abstract class ScriptValidation {

    protected final DicomValidator validator = new DicomFileValidator();

    protected void validate(Map<File, DicomObject> dicomMap, List<InterfileDicomValidation> interfileDicomValidations) {
        validator.validate(dicomMap, interfileDicomValidations.toArray(new InterfileDicomValidation[]{}));
    }

    public abstract void validateScriptRan(List<File> dicomFiles);

    public abstract void validateScriptDidntRun(List<File> dicomFiles);

    protected abstract List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan);

}
