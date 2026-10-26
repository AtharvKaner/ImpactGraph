package com.impactgraph.dto.repoanalysis;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for comparing two Git revisions and calculating downstream change impact.
 */
public class RevisionComparisonRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private String baseRevision; // defaults to HEAD~1

    private String targetRevision; // defaults to HEAD

    private String repositoryUrl;

    private String localPath;

    private String personalAccessToken;

    public RevisionComparisonRequest() {}

    public RevisionComparisonRequest(Long projectId, String baseRevision, String targetRevision) {
        this.projectId = projectId;
        this.baseRevision = baseRevision;
        this.targetRevision = targetRevision;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getBaseRevision() {
        return baseRevision;
    }

    public void setBaseRevision(String baseRevision) {
        this.baseRevision = baseRevision;
    }

    public String getTargetRevision() {
        return targetRevision;
    }

    public void setTargetRevision(String targetRevision) {
        this.targetRevision = targetRevision;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getPersonalAccessToken() {
        return personalAccessToken;
    }

    public void setPersonalAccessToken(String personalAccessToken) {
        this.personalAccessToken = personalAccessToken;
    }
}
