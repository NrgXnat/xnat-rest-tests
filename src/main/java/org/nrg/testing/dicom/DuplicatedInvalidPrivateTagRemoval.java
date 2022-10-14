package org.nrg.testing.dicom;

import org.nrg.testing.xnat.versions.XnatTestingVersionManager;
import org.nrg.xnat.versions.Xnat_1_8_6;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public class DuplicatedInvalidPrivateTagRemoval extends ScriptValidation {

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();
        
        root.putValueEqualCheck(0x00290010, "REASONABLE PERSON HEADER1");
        root.putValueEqualCheck(0x00290011, "REASONABLE PERSON HEADER2");
        if (XnatTestingVersionManager.testedVersionFollows(Xnat_1_8_6.class)) {
            // delete does not remove orphan private creator IDs
            root.putValueEqualCheck(0x00290012, "NAUGHTY PERSON");
            root.putValueEqualCheck(0x002900E1, "NAUGHTY PERSON");
        } else {
            root.putNonexistenceChecks(0x00290012, 0x002900E1);
        }
        root.putValueEqualCheck(0x00291008, "MLO");
        root.putValueEqualCheck(0x00291009, "YES");
        root.putValueEqualCheck(0x002911AA, "meow");
        root.putWildcardedNonexistenceCheck("(0029,12XX)");
        root.putWildcardedNonexistenceCheck("(0029,E1XX)");

        root.putValueEqualCheck(0x00490010, "REASONABLE PERSON HEADER1");
        root.putValueEqualCheck(0x00490011, "REASONABLE PERSON HEADER2");
        if (XnatTestingVersionManager.testedVersionFollows(Xnat_1_8_6.class)) {
            // delete does not remove orphan private creator IDs
            root.putValueEqualCheck(0x00490012, "NAUGHTY PERSON");
            root.putValueEqualCheck(0x004900E1, "NAUGHTY PERSON");
        } else {
            root.putNonexistenceChecks(0x00490012, 0x004900E1);
        }
        root.putValueEqualCheck(0x00491008, "MLO");
        root.putValueEqualCheck(0x00491009, "YES");
        root.putValueEqualCheck(0x004911AA, "meow");
        root.putWildcardedNonexistenceCheck("(0049,12XX)");
        root.putWildcardedNonexistenceCheck("(0049,E1XX)");
        
        validate(dicomMap, new ArrayList<>());
    }

    @Override
    public void validateScriptDidntRun(List<File> dicomFiles) {
        throw new UnsupportedOperationException("Only checking that the script did run is currently supported.");
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return new ArrayList<>();
    }

    private Map<File, DicomObject> fixedChecks(List<File> dicomFiles) {
        final DicomObject root = new RootDicomObject();
        assertEquals(372, dicomFiles.size());
        final Map<File, DicomObject> dicomMap = new HashMap<>();

        for (File dicomFile : dicomFiles) {
            dicomMap.put(dicomFile, root);
        }

        root.putValueEqualCheck("(0008,0060)", "CT");

        return dicomMap;
    }

}
