package org.nrg.testing.dicom;

public class LateAnonClientLikeScript extends GenericXnatVariablesScript {

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,0008)", AnonConstants.ANON2_IMAGE_TYPE);
        root.putNonexistenceChecks("(0010,1010)", "(0044,000a)");
        root.putValueEqualCheck("(0012,0020)", project);
        root.putValueEqualCheck("(0012,0021)", subject);
        root.putValueEqualCheck("(0012,0030)", session);
        root.putValueEqualCheck("(0012,0062)", "YES");
        root.putValueEqualCheck("(0044,000b)", "20100101");
        root.putValueEqualCheck("(0044,0002)", "YES");
        root.putNonexistenceChecks("(2001,1010)", "(2005,144f)");
        root.putValueEqualCheck("(2001,1012)", "N");
        root.putValueEqualCheck("(2005,144e)", "N");

        return root;
    }

}
