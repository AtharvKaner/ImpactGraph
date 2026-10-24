package com.impactgraph.controller;

import com.impactgraph.dto.request.ServiceRequest;
import com.impactgraph.dto.response.ApiResponse;
import com.impactgraph.dto.response.ServiceResponse;
import com.impactgraph.service.ApiService;
import com.impactgraph.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceService serviceService;
    private final ApiService apiService;

    public ServiceController(ServiceService serviceService, ApiService apiService) {
        this.serviceService = serviceService;
        this.apiService = apiService;
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody ServiceRequest request) {
        ServiceResponse created = serviceService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponse>> getAllServices(
            @RequestParam(required = false) Long projectId) {
        return ResponseEntity.ok(serviceService.getAllServices(projectId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.getServiceById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> updateService(@PathVariable Long id,
                                                         @Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.ok(serviceService.updateService(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        serviceService.deleteService(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/apis")
    public ResponseEntity<List<ApiResponse>> getApisForService(@PathVariable Long id) {
        return ResponseEntity.ok(apiService.getAllApis(id));
    }
}
