package org.nrg.testing.dicom;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public class ProjectDE4Script extends ScriptValidation {

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putNonexistenceChecks("(0002,0013)", "(0008,002a)");
        root.putValueEqualCheck("(0008,0064)", "WSD");
        root.putValueEqualCheck("(0008,0050)", "REMOVED");
        root.putValueEqualCheck("(0008,1040)", "Dept A");
        root.putValueEqualCheck("(0018,0022)", "1\\2\\3\\4");
        root.putValueEqualCheck("(0010,0021)", "mr_V2_text_from_URL_01204567_0%3D1_b");
        root.putValueNotEqualCheck("(0020,000D)", "1.3.46.670589.11.5730.5.0.1744.2010043012343685002");
        root.putWildcardedNonexistenceCheck("(0018,900X)");
        root.putWildcardedNonexistenceCheck("(0018,903#)");
        root.putWildcardedNonexistenceCheck("(0018,980@)");
        root.putNonexistenceChecks("(0028,0301)");
        root.putValueEqualCheck("(0012,0010)", "Hello");
        root.putValueEqualCheck("(0010,1010)", "");
        root.putValueEqualCheck("(0010,1005)", "");
        root.putValueEqualCheck("(0010,1050)", "");
        root.putNonexistenceChecks("(2001,100e)");
        root.putWildcardedNonexistenceCheck("(2001,106X)");
        extendScriptRanForDE6(root);

        validate(dicomMap, interfileChecksWhen(true));
    }

    @Override
    public void validateScriptDidntRun(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putValueEqualCheck("(0002,0013)", "OFFIS_DCMTK_362");
        root.putValueEqualCheck("(0008,002a)", "20100430130441.40");
        root.putNonexistenceChecks("(0008,0064)");
        root.putValueEqualCheck("(0008,0050)", "20100430");
        root.putValueEqualCheck("(0008,1040)", "ANATOMY AND NEUROBIOLOGY");
        root.putNonexistenceChecks("(0018,0022)", "(0010,0021)");
        root.putValueEqualCheck("(0020,000D)", "1.3.46.670589.11.5730.5.0.1744.2010043012343685002");
        root.putValueEqualCheck("(0018,9004)", "RESEARCH");
        root.putValueEqualCheck("(0018,9005)", "TSE");
        root.putValueEqualCheck("(0018,9008)", "SPIN");
        root.putValueEqualCheck("(0018,9033)", "PARTIAL");
        root.putValueEqualCheck("(0018,9035)", "0");
        root.putValueEqualCheck("(0018,9037)", "NONE");
        root.putValueEqualCheck("(0018,980c)", "FREEHAND");
        root.putValueEqualCheck("(0028,0301)", "NO");
        root.putNonexistenceChecks("(0012,0010)");
        root.putValueEqualCheck("(0010,1010)", "300Y");
        root.putValueEqualCheck("(0010,1005)", "NAME^NAME");
        root.putNonexistenceChecks("(0010,1050)");
        root.putValueEqualCheck("(2001,100e)", "N");
        root.putValueEqualCheck("(2001,1060)", "1");
        root.putValueEqualCheck("(2001,1061)", "N");
        root.putValueEqualCheck("(2001,1062)", "N");
        root.putValueEqualCheck("(2001,1063)", "ELSEWHERE");
        extendScriptDidntRunForDE6(root);

        validate(dicomMap, interfileChecksWhen(false));
    }

    @Override
    protected void extendScriptRanForDE6(DicomObject dicomObject) {}

    @Override
    protected void extendScriptDidntRunForDE6(DicomObject dicomObject) {}

    protected Map<File, DicomObject> fixedChecks(List<File> dicomFiles) {
        final DicomObject root = new RootDicomObject();
        assertEquals(2, dicomFiles.size());
        final Map<File, DicomObject> dicomMap = new HashMap<>();

        for (File dicomFile : dicomFiles) {
            dicomMap.put(dicomFile, root);
        }

        root.putNonexistenceChecks("(0008,0103)");
        root.putNonexistenceChecks("(0018,9000)", "(0018,900a)", "(0018,900f)");
        root.putNonexistenceChecks("(0018,9010)", "(0018,901a)", "(0018,901b)");
        root.putValueEqualCheck("(0018,9011)", "YES");
        root.putValueEqualCheck("(0018,9015)", "NO");
        root.putValueEqualCheck("(0018,9017)", "NONE");
        root.putValueEqualCheck("(0018,9019)", "0");
        root.putValueEqualCheck("(0018,9030)", "0");
        root.putValueEqualCheck("(0018,9032)", "RECTILINEAR");
        root.putValueEqualCheck("(0018,9034)", "UNKNOWN");
        root.putWildcardedNonexistenceCheck("(0018,93XX)");
        root.putValueEqualCheck("(0018,9805)", "10");
        root.putValueEqualCheck("(0018,980b)", "YES");
        root.putValueEqualCheck("(0028,9001)", "1");
        root.putNonexistenceChecks("(0012,0021)", "(0012,0031)", "(0012,0042)", "(0008,2111)", "(0008,2111)", "(0008,2120)");
        root.putNonexistenceChecks("(0029,0010)", "(0029,1018)");
        root.putNonexistenceChecks("(2001,1030)");
        root.putNonexistenceChecks("(0010,2297)");

        return dicomMap;
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return Collections.singletonList(studyInstanceUidSameCheck);
    }

}
