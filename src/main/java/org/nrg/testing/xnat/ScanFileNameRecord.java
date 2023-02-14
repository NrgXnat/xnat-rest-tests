package org.nrg.testing.xnat;

import org.nrg.xnat.pogo.experiments.Scan;
import org.nrg.xnat.pogo.resources.Resource;
import org.nrg.xnat.pogo.resources.ResourceFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.assertEquals;

public class ScanFileNameRecord {

    private String scanId; // really, label
    private Map<String, Set<String>> resources;

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public Map<String, Set<String>> getResources() {
        return resources;
    }

    public void setResources(Map<String, Set<String>> resources) {
        this.resources = resources;
    }

    public void identifySelfAndValidate(List<Scan> scans) {
        final Scan correspondingScan = scans
                .stream()
                .filter(scan -> scanId.equals(scan.getId()))
                .findFirst()
                .orElseThrow(RuntimeException::new);
        assertEquals(
                resources.size(),
                correspondingScan.getScanResources().size()
        );
        for (Map.Entry<String, Set<String>> resourceEntry : resources.entrySet()) {
            final Resource actualResource = correspondingScan
                    .getScanResources()
                    .stream()
                    .filter(resource -> resourceEntry.getKey().equals(resource.getFolder()))
                    .findFirst()
                    .orElseThrow(RuntimeException::new);
            assertEquals(
                    resourceEntry.getValue(),
                    actualResource.getResourceFiles().stream().map(ResourceFile::getName).collect(Collectors.toSet())
            );
        }
    }

}