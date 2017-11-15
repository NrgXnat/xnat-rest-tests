package org.nrg.testing.dicom;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public final class PrivateTagWhitelist extends ScriptValidation { // don't extend (already DE6)

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putWildcardedNonexistenceCheck("(2001,XXXX)");
        root.putWildcardedNonexistenceCheck("(2005,12XX)");
        root.putWildcardedNonexistenceCheck("(2005,14XX)");
        root.putNonexistenceChecks("(2005,1031)");

        validate(dicomMap, new ArrayList<InterfileDicomValidation>());
    }

    @Override
    public void validateScriptDidntRun(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putValueEqualCheck("(2005,1034)", "Y");
        root.putValueEqualCheck("(2005,1201)", "0");

        validate(dicomMap, new ArrayList<InterfileDicomValidation>());
    }

    @Override
    protected void extendScriptRanForDE6(DicomObject dicomObject) {}

    @Override
    protected void extendScriptDidntRunForDE6(DicomObject dicomObject) {}

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return new ArrayList<>();
    }

    private Map<File, DicomObject> fixedChecks(List<File> dicomFiles) {
        final DicomObject root = new RootDicomObject();
        assertEquals(2, dicomFiles.size());
        final Map<File, DicomObject> dicomMap = new HashMap<>();

        for (File dicomFile : dicomFiles) {
            dicomMap.put(dicomFile, root);
        }

        root.putValueEqualCheck("(2005,1035)", "PIXEL");
        root.putValueEqualCheck("(2005,1327)", "REAL");
        root.putValueEqualCheck("(2005,1339)", "0\\0");

        return dicomMap;
    }

}
