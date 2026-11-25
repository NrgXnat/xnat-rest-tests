package org.nrg.testing.xnat.tests;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.nrg.xnat.importer.importers.DicomZipRequest;

/** A DICOM-zip upload with an inline anonymization script, sent as the importer's {@code Anon-Script} parameter. */
class DicomZipRequestWithScript extends DicomZipRequest {
    @JsonProperty("Anon-Script")
    private final String anonScript;

    DicomZipRequestWithScript(final String anonScript) {
        this.anonScript = anonScript;
    }
}
