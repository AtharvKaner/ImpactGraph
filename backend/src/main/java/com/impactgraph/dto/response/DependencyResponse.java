package com.impactgraph.dto.response;

import com.impactgraph.entity.Dependency;

public class DependencyResponse {

    private Long id;
    private Long projectId;
    private Long sourceServiceId;
    private String sourceServiceName;
    private Long targetServiceId;
    private String targetServiceName;
    private String dependencyType;
    private String description;
    private String direction;

    public DependencyResponse() {
    }

    public DependencyResponse(Long id, Long projectId, Long sourceServiceId, String sourceServiceName,
                              Long targetServiceId, String targetServiceName, String dependencyType,
                              String description, String direction) {
        this.id = id;
        this.projectId = projectId;
        this.sourceServiceId = sourceServiceId;
        this.sourceServiceName = sourceServiceName;
        this.targetServiceId = targetServiceId;
        this.targetServiceName = targetServiceName;
        this.dependencyType = dependencyType;
        this.description = description;
        this.direction = direction;
    }

    public static DependencyResponse from(Dependency dependency) {
        if (dependency == null) return null;
        Long srcId = dependency.getSourceService() != null ? dependency.getSourceService().getId() : null;
        String srcName = dependency.getSourceService() != null ? dependency.getSourceService().getName() : null;
        Long tgtId = dependency.getTargetService() != null ? dependency.getTargetService().getId() : null;
        String tgtName = dependency.getTargetService() != null ? dependency.getTargetService().getName() : null;
        Long projId = dependency.getSourceService() != null && dependency.getSourceService().getProject() != null
                ? dependency.getSourceService().getProject().getId() : null;
        String arrow = (srcName != null && tgtName != null) ? (srcName + " -> " + tgtName) : null;

        return new DependencyResponse(
                dependency.getId(),
                projId,
                srcId,
                srcName,
                tgtId,
                tgtName,
                dependency.getDependencyType(),
                dependency.getDescription(),
                arrow
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getSourceServiceId() {
        return sourceServiceId;
    }

    public void setSourceServiceId(Long sourceServiceId) {
        this.sourceServiceId = sourceServiceId;
    }

    public String getSourceServiceName() {
        return sourceServiceName;
    }

    public void setSourceServiceName(String sourceServiceName) {
        this.sourceServiceName = sourceServiceName;
    }

    public Long getTargetServiceId() {
        return targetServiceId;
    }

    public void setTargetServiceId(Long targetServiceId) {
        this.targetServiceId = targetServiceId;
    }

    public String getTargetServiceName() {
        return targetServiceName;
    }

    public void setTargetServiceName(String targetServiceName) {
        this.targetServiceName = targetServiceName;
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

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
