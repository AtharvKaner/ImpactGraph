package com.impactgraph.dto.response;

import com.impactgraph.entity.Service;

public class ServiceResponse {

    private Long id;
    private Long projectId;
    private String projectName;
    private String name;
    private String description;
    private int apiCount;

    public ServiceResponse() {
    }

    public ServiceResponse(Long id, Long projectId, String projectName, String name, String description, int apiCount) {
        this.id = id;
        this.projectId = projectId;
        this.projectName = projectName;
        this.name = name;
        this.description = description;
        this.apiCount = apiCount;
    }

    public static ServiceResponse from(Service service) {
        if (service == null) return null;
        Long projId = service.getProject() != null ? service.getProject().getId() : null;
        String projName = service.getProject() != null ? service.getProject().getName() : null;
        int count = service.getApis() != null ? service.getApis().size() : 0;
        return new ServiceResponse(
                service.getId(),
                projId,
                projName,
                service.getName(),
                service.getDescription(),
                count
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

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getApiCount() {
        return apiCount;
    }

    public void setApiCount(int apiCount) {
        this.apiCount = apiCount;
    }
}
