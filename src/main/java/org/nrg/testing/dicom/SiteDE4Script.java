package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

import java.io.File;
import java.util.*;

import static org.testng.AssertJUnit.assertEquals;

public class SiteDE4Script extends ScriptValidation {

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putNonexistenceChecks("(0002,0016)", "(0008,0033)");
        root.putValueEqualCheck("(0008,0061)", "MR");
        root.putValueEqualCheck("(0008,1080)", "DIAG");
        root.putValueEqualCheck("(0008,0100)", "Val1");
        root.putValueEqualCheck("(0018,7026)", "2.55\\4.99");
        root.putValueEqualCheck("(0010,1000)", "mr_PDW_TSE_text_from_URL_01204567_0%3D1_b");
        root.putValueNotEqualCheck("(0008,1150)", null);

        for (String wildcardCheck : new String[]{"(0018,901#)", "(0018,905@)", "(0044,000#)"}) {
            root.putWildcardedNonexistenceCheck(wildcardCheck);
        }

        root.putNonexistenceChecks("(0028,3003)");
        root.putValueEqualCheck("(0012,0020)", "Hello");
        root.putValueEqualCheck("(0010,1020)", null);
        root.putValueEqualCheck("(0010,1040)", null);
        root.putValueEqualCheck("(0010,2150)", null);
        root.putNonexistenceChecks("(2001,100f)");
        root.putWildcardedNonexistenceCheck("(2001,108@)");

        extendScriptRanForDE6(root);

        validate(dicomMap, interfileChecksWhen(true));
    }

    @Override
    public void validateScriptDidntRun(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putValueEqualCheck("(0002,0016)", "DicomBrowser");
        root.putValueEqualCheck("(0008,0033)", "131758");
        root.putNonexistenceChecks("(0008,0061)");
        root.putValueEqualCheck("(0008,1080)", null);
        root.putValueEqualCheck("(0008,0100)", null);
        root.putNonexistenceChecks("(0018,7026)");
        root.putValueEqualCheck("(0010,1000)", null);
        root.putNonexistenceChecks("(0008,1150)");
        root.putValueEqualCheck("(0018,9011)", "YES");
        root.putValueEqualCheck("(0018,9015)", "NO");
        root.putValueEqualCheck("(0018,9017)", "NONE");
        root.putValueEqualCheck("(0018,9019)", "0");
        root.putValueEqualCheck("(0018,9058)", "400");
        root.putValueEqualCheck("(0044,0001)", "CHOCOLATE");
        root.putValueEqualCheck("(0044,0002)", "YES");
        root.putValueEqualCheck("(0044,000A)", "RITTER");
        root.putValueEqualCheck("(0044,000b)", "20100101");
        root.putValueEqualCheck("(0028,3003)", "Philips Real World Value Mapping");
        root.putNonexistenceChecks("(0012,0020)");
        root.putValueEqualCheck("(0010,1020)", "1.01");
        root.putValueEqualCheck("(0010,1040)", "10000 Place Street, Cityville");
        root.putNonexistenceChecks("(0010,2150)");
        root.putValueEqualCheck("(2001,100f)", "0");
        root.putValueEqualCheck("(2001,1082)", "3");
        root.putValueEqualCheck("(2001,1084)", "0");
        root.putValueEqualCheck("(2001,1086)", "0");
        root.putValueEqualCheck("(2001,1088)", "1");
        root.putValueEqualCheck("(2001,108a)", "0");

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

        root.putNonexistenceChecks("(0008,0105)");
        root.putValueEqualCheck("(0018,9091)", "0");
        root.putNonexistenceChecks("(0018,9095)", "(0018,909B)", "(0018,909e)", "(0018,9013)");
        root.putValueEqualCheck("(0018,9051)", "BODY");
        root.putValueEqualCheck("(0018,9059)", "NO");
        root.putWildcardedNonexistenceCheck("(0018,93XX)");
        root.putWildcardedNonexistenceCheck("(0018,94XX)");
        root.putValueEqualCheck("(0028,9002)", "0");
        root.putNonexistenceChecks("(0012,0030)", "(0012,0040)", "(0012,0045)", "(0008,2122)", "(0008,2124)");
        root.putNonexistenceChecks("(0029,0010)", "(0029,1019)");
        root.putValueEqualCheck("(2001,0010)", "Philips Imaging DD 001");
        root.putNonexistenceChecks("(2001,1031)");
        root.putValueEqualCheck("(2001,1081)", "1");
        root.putValueEqualCheck("(2001,1083)", "127.787174999999");
        root.putValueEqualCheck("(2001,1085)", "3");
        root.putValueEqualCheck("(2001,1087)", "1H");
        root.putValueEqualCheck("(2001,1089)", "0");
        root.putValueEqualCheck("(2001,108b)", "B");
        root.putNonexistenceChecks("(0018,9042)");

        final DicomObject sharedFunctionalGroupsSeqItem = new DicomObject();
        sharedFunctionalGroupsSeqItem.putExistenceChecks("(0018,9042)");
        root.putSequenceCheck("(5200,9229)", new DicomSequence(sharedFunctionalGroupsSeqItem).disableSizeCheck());

        return dicomMap;
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return scriptRan ? Arrays.asList(studyInstanceUidSameCheck, referencedSopClassUidSameCheck) : Collections.singletonList(studyInstanceUidSameCheck);
    }

}
