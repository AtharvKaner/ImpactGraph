package com.impactgraph.dto.repoanalysis;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for importing and analyzing a software repository.
 * Supports public/private GitHub repositories and secure local workspace directories.
 */
public class RepositoryImportRequest {

    private String sourceType = "GITHUB"; // GITHUB or LOCAL

    private String repositoryUrl;

    private String localPath;

    private String branch;

    /**
     * Optional personal access token for private GitHub repositories.
     * Never persisted to the database, never written to log files,
     * and never embedded in URLs.
     */
    private String personalAccessToken;

    private String projectName;

    private String description;

    public RepositoryImportRequest() {}

    public RepositoryImportRequest(String sourceType, String repositoryUrl, String localPath,
                                   String branch, String personalAccessToken, String projectName) {
        this.sourceType = sourceType;
        this.repositoryUrl = repositoryUrl;
        this.localPath = localPath;
        this.branch = branch;
        this.personalAccessToken = personalAccessToken;
        this.projectName = projectName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
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

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getPersonalAccessToken() {
        return personalAccessToken;
    }

    public void setPersonalAccessToken(String personalAccessToken) {
        this.personalAccessToken = personalAccessToken;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
