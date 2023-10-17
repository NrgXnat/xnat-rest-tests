package org.nrg.testing.dicom;

import java.util.function.Consumer;

public class BlankKnownPhiPrivateElements extends SimplestDicomScriptValidation {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,1040)", "ANATOMY AND NEUROBIOLOGY");
        root.putValueEqualCheck("(0018,9012)", "NO");
        root.putValueEqualCheck("(0018,9025)", "NONE");

        root.putSequenceCheck("(0040,0260)", (item) -> {
            item.putValueEqualCheck("(0008,0100)", "UNDEFINED");
            item.putValueEqualCheck("(0008,0102)", "UNDEFINED");
            item.putValueEqualCheck("(0008,0104)", "UNDEFINED");
            item.putEmptyChecks("(0008,010b)");
        });

        root.putEmptyChecks("(2001,100c)", "(2001,100e)", "(2001,1012)", "(2001,1019)");
        root.putValueEqualCheck("(2001,101c)", "NO");
        root.putEmptyChecks("(2005,101b)", "(2005,101c)", "(2005,101e)", "(2005,101f)", "(2005,1026)");

        root.putSequenceCheck("(2005,1402)", (item) -> {
            item.putEmptyChecks("(0008,0100)", "(0008,0102)", "(0008,0103)", "(0008,0104)", "(0008,010b)", "(0008,010d)");
        });

        final Consumer<DicomObject> innerPrivateSequenceCheck = (innerPrivateItem) -> {
            innerPrivateItem.putValueEqualCheck("(0018,0021)", "SK");
            innerPrivateItem.putEmptyChecks("(2001,1006)");
        };
        root.putSequenceCheck(
                "(5200,9230)",
                (perFrameItem1) -> perFrameItem1.putSequenceCheck("(2005,140f)", innerPrivateSequenceCheck),
                (perFrameItem2) -> perFrameItem2.putSequenceCheck("(2005,140f)", innerPrivateSequenceCheck),
                (perFrameItem3) -> perFrameItem3.putSequenceCheck("(2005,140f)", innerPrivateSequenceCheck)
        );

        return root;
    }

}
