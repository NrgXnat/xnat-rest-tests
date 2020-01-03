package org.nrg.testing;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xnat.pogo.experiments.ImagingSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CatalogRequest {

    private List<String> sessions = new ArrayList<>();
    private List<String> projectIds = new ArrayList<>();
    @JsonProperty("scan_formats")
    private List<String> scanResourceLabels = new ArrayList<>();
    @JsonProperty("scan_types")
    private List<String> scanTypes = new ArrayList<>();
    private List<String> resources = new ArrayList<>();
    private List<String> assessors = new ArrayList<>();
    private List<String> options = new ArrayList<>();

    public List<String> getSessions() {
        return sessions;
    }

    public void setSessions(List<String> sessions) {
        this.sessions = sessions;
    }

    public List<String> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<String> projectIds) {
        this.projectIds = projectIds;
    }

    public List<String> getScanResourceLabels() {
        return scanResourceLabels;
    }

    public void setScanResourceLabels(List<String> scanResourceLabels) {
        this.scanResourceLabels = scanResourceLabels;
    }

    public List<String> getScanTypes() {
        return scanTypes;
    }

    public void setScanTypes(List<String> scanTypes) {
        this.scanTypes = scanTypes;
    }

    public List<String> getResources() {
        return resources;
    }

    public void setResources(List<String> resources) {
        this.resources = resources;
    }

    public List<String> getAssessors() {
        return assessors;
    }

    public void setAssessors(List<String> assessors) {
        this.assessors = assessors;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public CatalogRequest addSession(String session) {
        insertNonnull(sessions, session);
        return this;
    }

    public CatalogRequest addProjectId(String projectId) {
        insertNonnull(projectIds, projectId);
        return this;
    }

    public CatalogRequest addScanResourceLabel(String label) {
        insertNonnull(scanResourceLabels, label);
        return this;
    }

    public CatalogRequest addScanType(String type) {
        insertNonnull(scanTypes, type);
        return this;
    }

    public CatalogRequest addResource(String resource) {
        insertNonnull(resources, resource);
        return this;
    }

    public CatalogRequest addAssessor(String assessor) {
        insertNonnull(assessors, assessor);
        return this;
    }

    public CatalogRequest addOption(String option) {
        insertNonnull(options, option);
        return this;
    }

    private <X> void insertNonnull(List<X> list, X item) {
        if (item != null) list.add(item);
    }

    public static String expectedSessionFormat(ImagingSession session) {
        return StringUtils.join(Arrays.asList(
                session.getPrimaryProject().getId(),
                session.getSubject().getLabel(),
                session.getLabel(),
                session.getAccessionNumber()
        ), ":");
    }

}
