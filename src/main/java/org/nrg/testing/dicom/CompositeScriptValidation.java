package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomValue;

import java.util.List;
import java.util.Map;

public abstract class CompositeScriptValidation extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject rootDicomObject = new RootDicomObject();
        for (Class<? extends SimplestDicomScriptValidation> componentScript : componentScripts()) {
            try {
                final RootDicomObject componentScriptDicom = componentScript.newInstance().generateValidationObject();
                for (Map.Entry<DicomTag, DicomValue> entry : componentScriptDicom.getDicomMap().entrySet()) {
                    rootDicomObject.getDicomMap().put(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to compose anonymization check: ", e);
            }
        }
        return rootDicomObject;
    }

    protected abstract List<Class<? extends SimplestDicomScriptValidation>> componentScripts();

}
