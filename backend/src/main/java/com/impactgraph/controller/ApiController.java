package com.impactgraph.controller;

import com.impactgraph.dto.request.ApiRequest;
import com.impactgraph.dto.response.ApiResponse;
import com.impactgraph.service.ApiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apis")
public class ApiController {

    private final ApiService apiService;

    public ApiController(ApiService apiService) {
        this.apiService = apiService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse> createApi(@Valid @RequestBody ApiRequest request) {
        ApiResponse created = apiService.createApi(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ApiResponse>> getAllApis(
            @RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(apiService.getAllApis(serviceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getApiById(@PathVariable Long id) {
        return ResponseEntity.ok(apiService.getApiById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateApi(@PathVariable Long id,
                                                 @Valid @RequestBody ApiRequest request) {
        return ResponseEntity.ok(apiService.updateApi(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApi(@PathVariable Long id) {
        apiService.deleteApi(id);
        return ResponseEntity.noContent().build();
    }
}
