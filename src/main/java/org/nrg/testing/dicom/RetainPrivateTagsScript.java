package org.nrg.testing.dicom;

import org.nrg.testing.dicom.values.DicomSequence;

public class RetainPrivateTagsScript extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putExistenceChecks("(2001,0010)", "(2001,100C)");
        root.putExistenceChecks("(2005,0010)", "(2005,1012)");
        root.putNonexistenceChecks("(2005,0011)", "(2005,0012)", "(2005,0013)", "(2005,0014)");

        final DicomObject perFrameFunctionalGroupsSequenceItem1= new DicomObject();
        perFrameFunctionalGroupsSequenceItem1.putNonexistenceChecks( "(2001,0014)", "(2001,140F)");
        final DicomObject perFrameFunctionalGroupsSequenceItem2= new DicomObject();
        perFrameFunctionalGroupsSequenceItem2.putNonexistenceChecks( "(2001,0014)", "(2001,140F)");
        final DicomObject perFrameFunctionalGroupsSequenceItem3= new DicomObject();
        perFrameFunctionalGroupsSequenceItem3.putNonexistenceChecks( "(2001,0014)", "(2001,140F)");

        root.putSequenceCheck( "(5200,9230)", new DicomSequence( perFrameFunctionalGroupsSequenceItem1,
                perFrameFunctionalGroupsSequenceItem2,
                perFrameFunctionalGroupsSequenceItem3));

        return root;
    }

}
