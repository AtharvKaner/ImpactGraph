package com.impactgraph.dto.response;

import com.impactgraph.entity.Project;

import java.time.Instant;

public class ProjectResponse {

    private Long id;
    private String name;
    private String description;
    private Instant createdAt;
    private int serviceCount;

    public ProjectResponse() {
    }

    public ProjectResponse(Long id, String name, String description, Instant createdAt, int serviceCount) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.serviceCount = serviceCount;
    }

    public static ProjectResponse from(Project project) {
        if (project == null) return null;
        int count = project.getServices() != null ? project.getServices().size() : 0;
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                count
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getServiceCount() {
        return serviceCount;
    }

    public void setServiceCount(int serviceCount) {
        this.serviceCount = serviceCount;
    }
}
