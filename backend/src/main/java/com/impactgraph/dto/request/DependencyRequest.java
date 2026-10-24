package com.impactgraph.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DependencyRequest {

    @NotNull(message = "Source service ID is required")
    private Long sourceServiceId;

    @NotNull(message = "Target service ID is required")
    private Long targetServiceId;

    @Size(max = 50, message = "Dependency type cannot exceed 50 characters")
    private String dependencyType;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    public DependencyRequest() {
    }

    public DependencyRequest(Long sourceServiceId, Long targetServiceId, String dependencyType, String description) {
        this.sourceServiceId = sourceServiceId;
        this.targetServiceId = targetServiceId;
        this.dependencyType = dependencyType;
        this.description = description;
    }

    public Long getSourceServiceId() {
        return sourceServiceId;
    }

    public void setSourceServiceId(Long sourceServiceId) {
        this.sourceServiceId = sourceServiceId;
    }

    public Long getTargetServiceId() {
        return targetServiceId;
    }

    public void setTargetServiceId(Long targetServiceId) {
        this.targetServiceId = targetServiceId;
    }

    public String getDependencyType() {
        return dependencyType;
    }

    public void setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
