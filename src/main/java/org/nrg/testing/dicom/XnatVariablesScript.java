package org.nrg.testing.dicom;

import org.nrg.xnat.pogo.experiments.ImagingSession;

public class XnatVariablesScript extends SimplestDicomScriptValidation {

    private String project;
    private String subject;
    private String session;

    public XnatVariablesScript session(ImagingSession session) {
        this.session = session.getLabel();
        subject = session.getSubject().getLabel();
        project = session.getPrimaryProject().getId();
        return this;
    }

    @Override
    protected RootDicomObject generateValidationObject() {
        final RootDicomObject root = new RootDicomObject();

        root.putValueEqualCheck("(0008,1010)", project);
        root.putValueEqualCheck("(0008,1030)", subject);
        root.putValueEqualCheck("(0008,103e)", session);

        return root;
    }

}
