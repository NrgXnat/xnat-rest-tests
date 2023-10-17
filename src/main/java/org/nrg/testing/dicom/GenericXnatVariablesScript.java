package org.nrg.testing.dicom;

import org.nrg.xnat.pogo.experiments.ImagingSession;

public abstract class GenericXnatVariablesScript extends SimplestDicomScriptValidation {

    protected String project;
    protected String subject;
    protected String session;

    public GenericXnatVariablesScript session(ImagingSession session) {
        this.session = session.getLabel();
        subject = session.getSubject().getLabel();
        project = session.getPrimaryProject().getId();
        return this;
    }

}
