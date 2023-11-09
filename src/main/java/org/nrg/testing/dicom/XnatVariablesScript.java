package org.nrg.testing.dicom;

public class XnatVariablesScript extends GenericXnatVariablesScript {

    @Override
    protected void validation(RootDicomObject root) {
        root.putValueEqualCheck("(0008,1010)", project);
        root.putValueEqualCheck("(0008,1030)", subject);
        root.putValueEqualCheck("(0008,103e)", session);
    }

}
