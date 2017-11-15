package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

public final class RemoveAllPrivateTags extends ScriptValidation { // don't extend (already DE6)

    @Override
    public void validateScriptRan(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putWildcardedNonexistenceCheck("(2001,XXXX)");
        root.putWildcardedNonexistenceCheck("(2005,XXXX)");
        addSharedFuncGroupsSequenceChecks(root, true);
        addPerFrameFunctionalGroupsSequenceChecks(root, true);

        validate(dicomMap, new ArrayList<InterfileDicomValidation>());
    }

    @Override
    public void validateScriptDidntRun(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        addSharedFuncGroupsSequenceChecks(root, false);
        addPerFrameFunctionalGroupsSequenceChecks(root, false);

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
        root.putValueEqualCheck("(0018,9012)", "NO");
        root.putValueEqualCheck("(0018,9014)", "NO");
        root.putValueEqualCheck("(0018,9016)", "NONE");
        root.putValueEqualCheck("(0018,9018)", "NO");
        root.putValueEqualCheck("(0018,9033)", "PARTIAL");
        root.putValueEqualCheck("(0018,9035)", "0");
        root.putValueEqualCheck("(0018,9037)", "NONE");
        root.putValueEqualCheck("(0018,980c)", "FREEHAND");
        root.putValueEqualCheck("(0028,0301)", "NO");
        root.putNonexistenceChecks("(0012,0010)");
        root.putValueEqualCheck("(0010,1010)", "300Y");
        root.putValueEqualCheck("(0010,1005)", "NAME^NAME");
        root.putNonexistenceChecks("(0010,1050)");

        return dicomMap;
    }

    private void addSharedFuncGroupsSequenceChecks(DicomObject root, boolean scriptRan) {
        final DicomObject sharedFunctionalGroupsSeqItem = new DicomObject();
        root.putSequenceCheck("(5200,9229)", new DicomSequence(sharedFunctionalGroupsSeqItem));

        sharedFunctionalGroupsSeqItem.putValueEqualCheck("(0018,9180)", "ELECTRIC_FIELD");
        if (scriptRan) {
            sharedFunctionalGroupsSeqItem.putWildcardedNonexistenceCheck("(2005,XXXX)");
        } else {
            sharedFunctionalGroupsSeqItem.putValueEqualCheck("(2005,0014)", "Philips MR Imaging DD 005");
            final DicomObject privateSeqItem = new DicomObject();
            privateSeqItem.putValueEqualCheck("(0018,0089)", "319");
            sharedFunctionalGroupsSeqItem.putSequenceCheck("(2005,140e)", new DicomSequence(privateSeqItem));
        }
    }

    private void addPerFrameFunctionalGroupsSequenceChecks(DicomObject root, boolean scriptRan) {
        final DicomSequence perFrameFunctionalGroupsSequence = new DicomSequence();
        root.putSequenceCheck("(5200,9230)", perFrameFunctionalGroupsSequence);

        for (int i = 0; i < 3; i++) {
            final DicomObject perFrameFunctionalGroupsSeqItem = new DicomObject();
            final DicomObject mrEchoSeqItem = new DicomObject();
            mrEchoSeqItem.putValueEqualCheck("(0018,9082)", "15");
            perFrameFunctionalGroupsSeqItem.putSequenceCheck("(0018,9114)", new DicomSequence(mrEchoSeqItem));
            if (scriptRan) {
                perFrameFunctionalGroupsSeqItem.putWildcardedNonexistenceCheck("(2005,XXXX)");
            } else {
                final DicomObject privateSeqItem = new DicomObject();
                privateSeqItem.putValueEqualCheck("(0018,0085)", "1H");
                perFrameFunctionalGroupsSeqItem.putSequenceCheck("(2005,140f)", new DicomSequence(privateSeqItem));
                perFrameFunctionalGroupsSeqItem.putValueEqualCheck("(2005,0014)", "Philips MR Imaging DD 005");
            }
            perFrameFunctionalGroupsSequence.addItem(perFrameFunctionalGroupsSeqItem);
        }
    }

}
