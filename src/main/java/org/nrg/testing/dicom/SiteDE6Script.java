package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SiteDE6Script extends SiteDE4Script {

    @Override
    protected void extendScriptRanForDE6(DicomObject dicomObject) {
        dicomObject.putValueEqualCheck("(0020,1204)", "100");
        dicomObject.putValueEqualCheck("(0020,1206)", "50");
        dicomObject.putValueEqualCheck("(2010,0040)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0050)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0060)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0080)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,00A8)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0140)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0150)", "GOOD");
        dicomObject.putValueEqualCheck("(2200,0005)", "GOOD");
        dicomObject.putValueEqualCheck("(3006,0088)", "Nice");
        dicomObject.putValueEqualCheck("(300A,000E)", "TSE");
        dicomObject.putNonexistenceChecks("(2005,1402)");
        dicomObject.putValueEqualCheck("(2001,1006)", "YAH");
        dicomObject.putValueEqualCheck("(2001,1024)", "Y");
        dicomObject.putValueEqualCheck("(0039,0010)", "INSERTED PRIVATE CREATOR ID");
        dicomObject.putValueEqualCheck("(0039,1001)", "COOL");
        dicomObject.putValueEqualCheck("(0039,0011)", "ANOTHER");
        dicomObject.putValueEqualCheck("(0039,1199)", "Meow");
        dicomObject.putValueEqualCheck("(0039,11ee)", "Much Meow");
        dicomObject.putNonexistenceChecks("(0008,1110)");

        final DicomObject opIdSeqItem = new DicomObject();
        opIdSeqItem.putValueEqualCheck("(0008,0080)", "WUSTL");
        dicomObject.putSequenceCheck("(0008,1072)", new DicomSequence(opIdSeqItem));

        dicomObject.putNonexistenceChecks("(0018,9035)");
        checkSharedFuncGroupsSeq(dicomObject, true);
        checkPerFrameFunctionalGroupsSequence(dicomObject, true);

        dicomObject.putNonexistenceChecks("(0018,9180)");
        checkInterventionDrugInfoSequence(dicomObject, true);

        dicomObject.putValueEqualCheck("(0040,1002)", "(0020,9056)");
        checkEmbeddedPatientId(dicomObject, true);

        checkPrivateSequence(dicomObject, true);
        dicomObject.putValueNotEqualCheck("(0008,1195)", "");
        dicomObject.putValueEqualCheck("(0008,2143)", "102030");
    }

    @Override
    protected void extendScriptDidntRunForDE6(DicomObject dicomObject) {
        dicomObject.putNonexistenceChecks("(0020,1204)", "(0020,1206)", "(2010,0040)", "(2010,0050)", "(2010,0060)", "(2010,0080)", "(2010,00A8)",
                "(2010,0140)", "(2010,0150)", "(2200,0005)", "(3006,0088)", "(300A,000E)");

        final DicomObject privateSeqItem = new DicomObject();
        privateSeqItem.putValueEqualCheck("(0008,0100)", "");
        privateSeqItem.putValueEqualCheck("(0008,010b)", "N");
        dicomObject.putSequenceCheck("(2005,1402)", new DicomSequence(privateSeqItem));

        dicomObject.putNonexistenceChecks("(2001,1006)");
        dicomObject.putValueEqualCheck("(2001,1024)", "N");
        dicomObject.putNonexistenceChecks("(0039,0010)", "(0039,1001)", "(0039,0011)", "(0039,1199)", "(0039,11ee)");

        final DicomObject referencedStudySeqItem = new DicomObject();
        referencedStudySeqItem.putValueEqualCheck("(0008,1150)", "1.2.840.10008.3.1.2.3.1");
        referencedStudySeqItem.putValueEqualCheck("(0008,1155)", "1.3.46.670589.11.5730.5.0.1744.2010043012343685002");
        dicomObject.putSequenceCheck("(0008,1110)", new DicomSequence(referencedStudySeqItem));

        dicomObject.putNonexistenceChecks("(0008,1072)");
        dicomObject.putValueEqualCheck("(0018,9035)", "0");
        checkSharedFuncGroupsSeq(dicomObject, false);
        checkPerFrameFunctionalGroupsSequence(dicomObject, false);

        dicomObject.putValueEqualCheck("(0018,9180)", "DB_DT");
        checkInterventionDrugInfoSequence(dicomObject, false);

        dicomObject.putNonexistenceChecks("(0040,1002)");
        checkEmbeddedPatientId(dicomObject, false);

        checkPrivateSequence(dicomObject, false);
        dicomObject.putNonexistenceChecks("(0008,1195)", "(0008,2143)");
    }

    @Override
    protected Map<File, DicomObject> fixedChecks(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = super.fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putNonexistenceChecks("(2010,00A9)", "(2200,0006)", "(3008,0066)", "(2001,0011)", "(2001,110c)");

        root.putValueEqualCheck("(2005,0014)", "Philips MR Imaging DD 005");
        root.putValueEqualCheck("(0008,0080)", "BU SCHOOL OF MEDICINE");
        root.putNonexistenceChecks("(0010,2160)");

        final DicomObject mrTimingAndParamsSeqItem = new DicomObject();
        final DicomObject operatingModeSeqItem0 = new DicomObject();
        operatingModeSeqItem0.putValueEqualCheck("(0018,9177)", "RF");
        final DicomObject operatingModeSeqItem1 = new DicomObject();
        operatingModeSeqItem1.putValueEqualCheck("(0018,9177)", "GRADIENT");
        mrTimingAndParamsSeqItem.putSequenceCheck("(0018,9176)", new DicomSequence(operatingModeSeqItem0, operatingModeSeqItem1));
        root.putSequenceCheck("(0018,9112)", new DicomSequence(mrTimingAndParamsSeqItem));
        root.putNonexistenceChecks("(0040,100A)");

        return dicomMap;
    }

    private void checkEmbeddedPatientId(DicomObject root, boolean scriptRan) {
        root.putValueEqualCheck("(0010,0020)", "Watermelon_MR1");

        final List<String> patientIds = Arrays.asList("PATIENT_0001", "PERSON_0001", "PATIENT_0003", "PERSON_0004");
        final DicomSequence otherPatientIdsSequence = new DicomSequence();
        for (int i = 0; i < 4; i++) {
            final DicomObject otherPatientIdsSeqItem = new DicomObject();
            if (i == 2) {
                final DicomObject nestedSeqItem = new DicomObject();
                nestedSeqItem.putValueEqualCheck("(0010,0021)", "Hospital_0003");
                if (scriptRan) {
                    nestedSeqItem.putNonexistenceChecks("(0010,0020)");
                } else {
                    nestedSeqItem.putValueEqualCheck("(0010,0020)", patientIds.get(i));
                }
                otherPatientIdsSeqItem.putSequenceCheck("(0010,1002)", new DicomSequence(nestedSeqItem));
            } else {
                otherPatientIdsSeqItem.putValueEqualCheck("(0010,0021)", String.format("Hospital_000%d", (i + 1)));
                if (scriptRan) {
                    otherPatientIdsSeqItem.putNonexistenceChecks("(0010,0020)");
                } else {
                    otherPatientIdsSeqItem.putValueEqualCheck("(0010,0020)", patientIds.get(i));
                }
            }
            otherPatientIdsSequence.addItem(otherPatientIdsSeqItem);
        }
    }

    private void checkSharedFuncGroupsSeq(DicomObject root, boolean scriptRan) {
        final DicomObject functionalGroupsSeqItem = new DicomObject();
        final DicomSequence sharedFunctionalGroupsSequence = new DicomSequence(functionalGroupsSeqItem);
        root.putSequenceCheck("(5200,9229)", sharedFunctionalGroupsSequence);
        final DicomObject privateSeqItem = new DicomObject();
        final DicomObject mrTimingAndParamsSeqItem = new DicomObject();
        final DicomSequence operatingModeSequence = new DicomSequence();
        mrTimingAndParamsSeqItem.putSequenceCheck("(0018,9176)", operatingModeSequence);
        for (String type : new String[]{"STATIC FIELD", "RF", "GRADIENT"}) {
            final DicomObject operatingModeSeqItem = new DicomObject();
            operatingModeSeqItem.putValueEqualCheck("(0018,9177)", type);
            operatingModeSequence.addItem(operatingModeSeqItem);
        }
        mrTimingAndParamsSeqItem.putValueEqualCheck("(0018,9180)", "DB_DT");
        functionalGroupsSeqItem.putSequenceCheck("(0018,9112)", new DicomSequence(mrTimingAndParamsSeqItem));
        if (scriptRan) {
            privateSeqItem.putNonexistenceChecks("(0018,9035)", "(0018,9180)");
            final DicomObject injectedFunctionalGroupsSeqItem = new DicomObject();
            injectedFunctionalGroupsSeqItem.putValueEqualCheck("(0008,0100)", "NICE");
            sharedFunctionalGroupsSequence.addItem(injectedFunctionalGroupsSeqItem);
        } else {
            functionalGroupsSeqItem.putValueEqualCheck("(0018,9180)", "ELECTRIC_FIELD");
            privateSeqItem.putValueEqualCheck("(0018,9035)", "0");
        }
        functionalGroupsSeqItem.putSequenceCheck("(2005,140e)", new DicomSequence(privateSeqItem));
    }

    private void checkPerFrameFunctionalGroupsSequence(DicomObject root, boolean scriptRan) {
        final DicomSequence perFrameFunctionalGroupsSequence = new DicomSequence();

        for (int i = 0; i < 3; i++) {
            final DicomObject perFrameFunctionalGroupsSeqItem = new DicomObject();
            perFrameFunctionalGroupsSeqItem.putNonexistenceChecks("(0008,1030)");
            final DicomObject frameContentSeqItem = new DicomObject();
            if (scriptRan) {
                frameContentSeqItem.putNonexistenceChecks("(0020,9056)", "(0018,9152)");
            } else {
                frameContentSeqItem.putValueEqualCheck("(0020,9056)", "1");
                final DicomObject mrMetaboliteSeqItem = new DicomObject();
                mrMetaboliteSeqItem.putValueEqualCheck("(0018,9080)", "WATER");
                perFrameFunctionalGroupsSeqItem.putSequenceCheck("(0018,9152)", new DicomSequence(mrMetaboliteSeqItem));
            }
            if (i == 1) {
                if (scriptRan) {
                    perFrameFunctionalGroupsSeqItem.putNonexistenceChecks("(2005,0014)");
                    perFrameFunctionalGroupsSeqItem.putWildcardedNonexistenceCheck("(2005,14XX)");
                } else {
                    final DicomObject privateSeqItem = new DicomObject();
                    privateSeqItem.putValueEqualCheck("(0018,0085)", "1H");
                    perFrameFunctionalGroupsSeqItem.putSequenceCheck("(2005,140f)", new DicomSequence(privateSeqItem));
                }
            }
            perFrameFunctionalGroupsSeqItem.putSequenceCheck("(0020,9111)", new DicomSequence(frameContentSeqItem));
            perFrameFunctionalGroupsSequence.addItem(perFrameFunctionalGroupsSeqItem);
        }
        root.putSequenceCheck("(5200,9230)", perFrameFunctionalGroupsSequence);
    }

    private void checkInterventionDrugInfoSequence(DicomObject root, boolean scriptRan) {
        final DicomSequence interventionDrugInfoSequence = new DicomSequence();
        final DicomObject interventionDrugInfoSeqItem0 = new DicomObject();
        interventionDrugInfoSeqItem0.putValueEqualCheck("(0018,0028)", "150000");
        interventionDrugInfoSeqItem0.putValueEqualCheck("(0018,0034)", "CHOCOLATE");
        interventionDrugInfoSequence.addItem(interventionDrugInfoSeqItem0);
        if (!scriptRan) {
            final DicomObject interventionDrugInfoSeqItem1 = new DicomObject();
            interventionDrugInfoSeqItem1.putValueEqualCheck("(0018,0028)", "100000");
            interventionDrugInfoSeqItem1.putValueEqualCheck("(0018,0034)", "CHOCOLATE");
        }
        root.putSequenceCheck("(0018,0026)", interventionDrugInfoSequence);
    }

    private void checkPrivateSequence(DicomObject root, boolean scriptRan) {
        final DicomObject privateSeqItem = new DicomObject();
        if (scriptRan) {
            privateSeqItem.putNonexistenceChecks("(2001,1032)", "(2005,143e)");
        } else {
            privateSeqItem.putValueEqualCheck("(2001,1032)", "0");
            privateSeqItem.putValueEqualCheck("(2005,143e)", "1.7E38");
        }
        privateSeqItem.putValueEqualCheck("(2001,1036)", "PARALLEL");
        root.putSequenceCheck("(2001,105f)", new DicomSequence(privateSeqItem));
    }

}
