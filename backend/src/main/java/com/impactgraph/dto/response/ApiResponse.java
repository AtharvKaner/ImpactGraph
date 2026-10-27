package com.impactgraph.dto.response;

import com.impactgraph.entity.Api;

public class ApiResponse {

    private Long id;
    private Long serviceId;
    private String serviceName;
    private String name;
    private String method;
    private String path;
    private String description;

    public ApiResponse() {
    }

    public ApiResponse(Long id, Long serviceId, String serviceName, String name, String method, String path, String description) {
        this.id = id;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.name = name;
        this.method = method;
        this.path = path;
        this.description = description;
    }

    public static ApiResponse from(Api api) {
        if (api == null) return null;
        Long svcId = api.getService() != null ? api.getService().getId() : null;
        String svcName = api.getService() != null ? api.getService().getName() : null;
        return new ApiResponse(
                api.getId(),
                svcId,
                svcName,
                api.getName(),
                api.getMethod(),
                api.getPath(),
                api.getDescription()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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
