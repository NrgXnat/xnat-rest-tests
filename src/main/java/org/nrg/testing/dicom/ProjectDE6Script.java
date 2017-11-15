package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ProjectDE6Script extends ProjectDE4Script {

    @Override
    protected void extendScriptRanForDE6(DicomObject dicomObject) {
        dicomObject.putValueEqualCheck("(0020,1200)", "100");
        dicomObject.putValueEqualCheck("(0020,1202)", "50");
        dicomObject.putValueEqualCheck("(2010,0010)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0030)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0052)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0054)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,00A6)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0100)", "GOOD");
        dicomObject.putValueEqualCheck("(2010,0110)", "GOOD");
        dicomObject.putValueEqualCheck("(2200,0003)", "GOOD");
        dicomObject.putValueEqualCheck("(3006,0028)", "Nice");
        dicomObject.putNonexistenceChecks("(2001,100e)");
        dicomObject.putWildcardedNonexistenceCheck("(2001,106x)");
        dicomObject.putValueEqualCheck("(3008,0202)", "NO");
        dicomObject.putValueEqualCheck("(2001,1004)", "UP");
        dicomObject.putValueEqualCheck("(2001,1021)", "Y");
        dicomObject.putValueEqualCheck("(0037,0010)", "INSERTED PRIVATE CREATOR ID");
        dicomObject.putValueEqualCheck("(0037,1000)", "INSERTED NICE");
        dicomObject.putValueEqualCheck("(0037,0011)", "ANOTHER");
        dicomObject.putValueEqualCheck("(0037,1199)", "Meow");
        dicomObject.putValueEqualCheck("(0037,11ee)", "Many Meow");
        dicomObject.putNonexistenceChecks("(0020,9221)");

        final DicomObject performingPhysicianIdSeqItem = new DicomObject();
        performingPhysicianIdSeqItem.putValueEqualCheck("(0008,0080)", "WASHU");
        dicomObject.putSequenceCheck("(0008,1052)", new DicomSequence(performingPhysicianIdSeqItem));

        addOtherPatientIDsSequenceCheck(dicomObject, true);

        dicomObject.putNonexistenceChecks("(0018,0095)", "(0018,9182)");

        addVariousEmbeddedTagChecks(dicomObject, true);

        dicomObject.putValueEqualCheck("(0040,0600)", "(0020,9056)");

        addPrivateSequenceChecks(dicomObject, true);

        dicomObject.putValueNotEqualCheck("(0008,1155)", "");
        dicomObject.putValueEqualCheck("(0008,2142)", "1020");
    }

    @Override
    protected void extendScriptDidntRunForDE6(DicomObject dicomObject) {
        dicomObject.putNonexistenceChecks("(0020,1200)", "(0020,1202)", "(2010,0010)", "(2010,0030)", "(2010,0052)", "(2010,0054)", "(2010,00A6)", "(2010,0100)", "(2010,0110)", "(2200,0003)", "(3006,0028)");
        dicomObject.putValueEqualCheck("(2001,100e)", "N");
        dicomObject.putValueEqualCheck("(2001,1060)", "1");
        dicomObject.putValueEqualCheck("(2001,1061)", "N");
        dicomObject.putValueEqualCheck("(2001,1062)", "N");
        dicomObject.putValueEqualCheck("(2001,1063)", "ELSEWHERE");
        dicomObject.putNonexistenceChecks("(3008,0202)");
        dicomObject.putNonexistenceChecks("(2001,1004)");
        dicomObject.putValueEqualCheck("(2001,1021)", "N");
        dicomObject.putNonexistenceChecks("(0037,0010)", "(0037,1000)", "(0037,0011)", "(0037,1199)", "(0037,11ee)");

        final DicomObject dimensionOrganizationSequenceItem = new DicomObject();
        dimensionOrganizationSequenceItem.putValueEqualCheck("(0020,9164)", "1.3.46.670589.11.5730.5.0.3224.2010071913175879000");
        dicomObject.putSequenceCheck("(0020,9221)", new DicomSequence(dimensionOrganizationSequenceItem));
        dicomObject.putNonexistenceChecks("(0008,1052)");

        addOtherPatientIDsSequenceCheck(dicomObject, false);

        dicomObject.putValueEqualCheck("(0018,0095)", "226");
        dicomObject.putValueStartsWithCheck("(0018,9182)", "26.08020565677");

        addVariousEmbeddedTagChecks(dicomObject, false);

        dicomObject.putNonexistenceChecks("(0040,0600)");

        addPrivateSequenceChecks(dicomObject, false);

        dicomObject.putNonexistenceChecks("(0008,1155)", "(0008,2142)");
    }

    @Override
    protected Map<File, DicomObject> fixedChecks(List<File> dicomFiles) {
        final Map<File, DicomObject> dicomMap = super.fixedChecks(dicomFiles);
        final DicomObject root = dicomMap.values().iterator().next();

        root.putNonexistenceChecks("(2010,00a7)", "(2200,0004)", "(2001,0011)", "(2001,110c)", "(3008,0012)");
        root.putValueEqualCheck("(2001,0010)", "Philips Imaging DD 001");
        root.putNonexistenceChecks("(2001,1030)");
        root.putValueEqualCheck("(0010,0010)", "Watermelon");
        root.putNonexistenceChecks("(0010,2160)");
        root.putNonexistenceChecks("(0018,9098)");
        root.putValueEqualCheck("(0008,1030)", "Fruit_Struct");
        root.putExistenceChecks("(0008,0100)");
        root.putNonexistenceChecks("(0040,100A)");

        final DicomObject dimensionIndexSeqItem0 = new DicomObject();
        dimensionIndexSeqItem0.putValueEqualCheck("(0020,9421)", "Stack ID");
        final DicomObject dimensionIndexSeqItem1 = new DicomObject();
        dimensionIndexSeqItem1.putValueEqualCheck("(0020,9421)", "In-Stack Position Number");
        root.putSequenceCheck("(0020,9222)", new DicomSequence(dimensionIndexSeqItem0, dimensionIndexSeqItem1));

        final DicomObject performedProtocolCodeSeqItem = new DicomObject();
        performedProtocolCodeSeqItem.putValueEqualCheck("(0008,0100)", "UNDEFINED");
        performedProtocolCodeSeqItem.putValueEqualCheck("(0008,0102)", "UNDEFINED");
        performedProtocolCodeSeqItem.putValueEqualCheck("(0008,0104)", "UNDEFINED");
        performedProtocolCodeSeqItem.putValueEqualCheck("(0008,010b)", "N");
        performedProtocolCodeSeqItem.putNonexistenceChecks("(0008,1030)");
        root.putSequenceCheck("(0040,0260)", new DicomSequence(performedProtocolCodeSeqItem));

        root.putWildcardedNonexistenceCheck("(2005,0015)");
        return dicomMap;
    }

    private void addOtherPatientIDsSequenceCheck(DicomObject root, boolean scriptRan) {
        final DicomSequence otherPatientIDsSequence = new DicomSequence();
        root.putSequenceCheck("(0010,1002)", otherPatientIDsSequence);

        for (int i = 0; i < 4; i++) {
            final String hospital = "Hospital_000" + (i + 1);
            final String patientName = "WATERMELON^FRUIT";
            final DicomObject sequenceItem = new DicomObject();
            if (scriptRan) {
                sequenceItem.putNonexistenceChecks("(0010,0010)");
            }
            if (i == 2) {
                final DicomSequence nestedOtherPatientIDs = new DicomSequence();
                final DicomObject nestedOtherIDsItem = new DicomObject();
                nestedOtherIDsItem.putValueEqualCheck("(0010,0021)", hospital);
                if (scriptRan) {
                    nestedOtherIDsItem.putNonexistenceChecks("(0010,0010)");
                } else {
                    nestedOtherIDsItem.putValueEqualCheck("(0010,0010)", patientName);
                }
                sequenceItem.putSequenceCheck("(0010,1002)", nestedOtherPatientIDs);
            } else {
                sequenceItem.putValueEqualCheck("(0010,0021)", hospital);
                if (!scriptRan) sequenceItem.putValueEqualCheck("(0010,0010)", patientName);
            }
            otherPatientIDsSequence.addItem(sequenceItem);
        }
    }

    private void addVariousEmbeddedTagChecks(DicomObject root, boolean scriptRan) {
        final DicomObject mrImagingModSeqItem = new DicomObject();
        mrImagingModSeqItem.putValueEqualCheck("(0018,9020)", "NONE");

        final DicomObject privateTagSeqItem = new DicomObject();
        privateTagSeqItem.putValueEqualCheck("(0018,9183)", "SLICE_AND_FREQ");
        privateTagSeqItem.putValueStartsWithCheck("(0018,9182)", "26.0802056567");

        final DicomObject embeddedMrTimingSeqItem = new DicomObject();
        embeddedMrTimingSeqItem.putValueEqualCheck("(0018,1314)", "90");
        embeddedMrTimingSeqItem.putValueStartsWithCheck("(0018,9182)", "26.0802056567");
        final DicomSequence operatingModeSequence = new DicomSequence();
        embeddedMrTimingSeqItem.putSequenceCheck("(0018,9176)", operatingModeSequence);
        for (int i = 0; i < 3; i++) {
            final DicomObject operatingModeSeqItem = new DicomObject();
            operatingModeSeqItem.putValueEqualCheck("(0018,9178)", (i == 0) ? "IEC_FIRST_LEVEL" : "IEC_NORMAL");
            operatingModeSequence.addItem(operatingModeSeqItem);
        }

        final DicomObject sharedFunctionalGroupsSeqItem = new DicomObject();

        sharedFunctionalGroupsSeqItem.putSequenceCheck("(0018,9006)", new DicomSequence(mrImagingModSeqItem));
        sharedFunctionalGroupsSeqItem.putSequenceCheck("(2005,140e)", new DicomSequence(privateTagSeqItem));
        sharedFunctionalGroupsSeqItem.putSequenceCheck("(0018,9112)", new DicomSequence(embeddedMrTimingSeqItem));
        root.putSequenceCheck("(5200,9229)", new DicomSequence(sharedFunctionalGroupsSeqItem));

        final DicomSequence otherOperatingModeSequence = new DicomSequence();
        for (int i = 0; i < 2; i++) {
            final DicomObject operatingModeSeqItem = new DicomObject();
            operatingModeSeqItem.putValueEqualCheck("(0018,9178)", (i == 0) ? "IEC_NORMAL" : "IEC_FIRST_LEVEL");
            otherOperatingModeSequence.addItem(operatingModeSeqItem);
        }

        final DicomObject rootMrTimingSeqItem = new DicomObject();
        rootMrTimingSeqItem.putSequenceCheck("(0018,9176)", otherOperatingModeSequence);
        root.putSequenceCheck("(0018,9112)", new DicomSequence(rootMrTimingSeqItem));

        final DicomSequence perFrameFunctionalGroupsSequence = new DicomSequence();
        root.putSequenceCheck("(5200,9230)", perFrameFunctionalGroupsSequence);

        final DicomSequence privateSequence = new DicomSequence();
        root.putSequenceCheck("(2005,1402)", privateSequence);

        if (scriptRan) {
            mrImagingModSeqItem.putNonexistenceChecks("(0018,0095)", "(0018,9098)");
            privateTagSeqItem.putNonexistenceChecks("(0018,9098)");
            sharedFunctionalGroupsSeqItem.putNonexistenceChecks("(0018,9182)");
            for (int i = 0; i < 3; i++) {
                final DicomObject perFrameFunctionalGroupsSeqItem = new DicomObject();
                perFrameFunctionalGroupsSeqItem.putNonexistenceChecks("(0018,9114)");
                perFrameFunctionalGroupsSequence.addItem(perFrameFunctionalGroupsSeqItem);
                if (i == 0) {
                    perFrameFunctionalGroupsSeqItem.putWildcardedNonexistenceCheck("(2005,14XX)");
                } else {
                    addEmbeddedPrivateSequenceCheck(perFrameFunctionalGroupsSeqItem, true);
                }
            }
            final DicomObject perFrameFunctionalGroupsSeqItem = new DicomObject();
            perFrameFunctionalGroupsSeqItem.putValueEqualCheck("(0008,0100)", "NICE");
            perFrameFunctionalGroupsSequence.addItem(perFrameFunctionalGroupsSeqItem);
        } else {
            mrImagingModSeqItem.putValueStartsWithCheck("(0018,0095)", "225.514739990");
            mrImagingModSeqItem.putValueStartsWithCheck("(0018,9098)", "127.78717");
            privateTagSeqItem.putValueStartsWithCheck("(0018,9098)", "127.78717");
            sharedFunctionalGroupsSeqItem.putValueStartsWithCheck("(0018,9182)", "25.44334443");
            for (int i = 0; i < 3; i++) {
                final DicomObject perFrameFunctionalGroupsSeqItem = new DicomObject();
                final DicomObject mrEchoSeqItem = new DicomObject();
                mrEchoSeqItem.putValueEqualCheck("(0018,9082)", "15");
                perFrameFunctionalGroupsSeqItem.putSequenceCheck("(0018,9114)", new DicomSequence(mrEchoSeqItem));
                addEmbeddedPrivateSequenceCheck(perFrameFunctionalGroupsSeqItem, false);
                perFrameFunctionalGroupsSequence.addItem(perFrameFunctionalGroupsSeqItem);
            }
            final DicomObject privateSequenceItem = new DicomObject();
            privateSequenceItem.putValueEqualCheck("(0008,010B)", "N");
            privateSequence.addItem(privateSequenceItem);
        }
    }

    private void addEmbeddedPrivateSequenceCheck(DicomObject dicomObject, boolean scriptRan) {
        final DicomObject privateSequenceItem = new DicomObject();
        privateSequenceItem.putValueEqualCheck("(0018,0050)", "1");
        if (scriptRan) {
            privateSequenceItem.putWildcardedNonexistenceCheck("(2005,10aX)");
        } else {
            privateSequenceItem.putValueEqualCheck("(2005,10a1)", "SYN_COCA");
            privateSequenceItem.putValueEqualCheck("(2005,10a8)", "0");
        }
        dicomObject.putSequenceCheck("(2005,140f)", new DicomSequence(privateSequenceItem));
    }

    private void addPrivateSequenceChecks(DicomObject root, boolean scriptRan) {
        final DicomObject privateSeqItem = new DicomObject();
        root.putSequenceCheck("(2001,105f)", new DicomSequence(privateSeqItem));
        if (scriptRan) {
            privateSeqItem.putNonexistenceChecks("(2001,102D)", "(2005,143c)");
        } else {
            privateSeqItem.putValueEqualCheck("(2001,102D)", "3");
            privateSeqItem.putValueEqualCheck("(2005,143c)", "1.7E38");
        }
        privateSeqItem.putValueEqualCheck("(2005,143d)", "1.7E38");
    }

    @Override
    protected List<InterfileDicomValidation> interfileChecksWhen(boolean scriptRan) {
        return (scriptRan) ? Arrays.asList(studyInstanceUidSameCheck, referencedSopInstanceUidSameCheck) : super.interfileChecksWhen(false);
    }

}
