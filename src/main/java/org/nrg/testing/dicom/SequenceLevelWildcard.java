package org.nrg.testing.dicom;

public enum SequenceLevelWildcard {

    ASTERISK(true, true),
    DOT(false, false),
    PLUS(false, true);

    final boolean includesRootLevel;
    final boolean extendsBeyondOneSequenceLevel;

    SequenceLevelWildcard(boolean includesRootLevel, boolean extendsBeyondOneSequenceLevel) {
        this.includesRootLevel = includesRootLevel;
        this.extendsBeyondOneSequenceLevel = extendsBeyondOneSequenceLevel;
    }

}
