package org.nrg.testing.dicom;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.dcm4che3.data.Tag.ReferencedSOPClassUID;
import static org.dcm4che3.data.Tag.ReferencedSOPInstanceUID;
import static org.dcm4che3.data.Tag.StudyInstanceUID;

public abstract class ScriptValidation {

    protected final DicomValidator validator = new DicomFileValidator();
    protected final InterfileDicomValidation studyInstanceUidSameCheck = new FixedTagValue(StudyInstanceUID);
    protected final InterfileDicomValidation referencedSopInstanceUidSameCheck = new FixedTagValue(ReferencedSOPInstanceUID);
    protected final InterfileDicomValidation referencedSopClassUidSameCheck = new FixedTagValue(ReferencedSOPClassUID);

    protected void validate(Map<File, DicomObject> dicomMap, List<InterfileDicomValidation> interfileDicomValidations) {
        validator.validate(dicomMap, interfileDicomValidations.toArray(new InterfileDicomValidation[]{}));
    }

    public abstract void validateScriptRan(List<File> dicomFiles);

    public abstract void validateScriptDidntRun(List<File> dicomFiles);

    protected abstract void extendScriptRanForDE6(DicomObject dicomObject);

    protected abstract void extendScriptDidntRunForDE6(DicomObject dicomObject);

    protected abstract List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan);

}
