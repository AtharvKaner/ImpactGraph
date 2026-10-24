package com.impactgraph.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ApiRequest {

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    @NotBlank(message = "API name cannot be blank")
    @Size(max = 100, message = "API name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "HTTP method cannot be blank")
    @Pattern(regexp = "(?i)^(GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD)$", message = "HTTP method must be a valid method (GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD)")
    private String method;

    @NotBlank(message = "Path cannot be blank")
    @Pattern(regexp = "^/.*", message = "Path must start with '/'")
    @Size(max = 255, message = "Path cannot exceed 255 characters")
    private String path;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    public ApiRequest() {
    }

    public ApiRequest(Long serviceId, String name, String method, String path, String description) {
        this.serviceId = serviceId;
        this.name = name;
        this.method = method;
        this.path = path;
        this.description = description;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
