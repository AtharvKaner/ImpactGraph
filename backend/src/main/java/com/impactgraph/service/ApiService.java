package com.impactgraph.service;

import com.impactgraph.dto.request.ApiRequest;
import com.impactgraph.dto.response.ApiResponse;
import com.impactgraph.entity.Api;
import com.impactgraph.exception.DuplicateResourceException;
import com.impactgraph.exception.InvalidRequestException;
import com.impactgraph.exception.ResourceNotFoundException;
import com.impactgraph.repository.ApiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ApiService {

    private static final Set<String> ALLOWED_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
    );

    private final ApiRepository apiRepository;
    private final ServiceService serviceService;

    public ApiService(ApiRepository apiRepository, ServiceService serviceService) {
        this.apiRepository = apiRepository;
        this.serviceService = serviceService;
    }

    public ApiResponse createApi(ApiRequest request) {
        com.impactgraph.entity.Service service = serviceService.findServiceEntity(request.getServiceId());

        String method = validateAndNormalizeMethod(request.getMethod());
        String path = validateAndNormalizePath(request.getPath());
        String trimmedName = request.getName().trim();

        if (apiRepository.existsByServiceIdAndMethodAndPath(service.getId(), method, path)) {
            throw new DuplicateResourceException(
                    "API endpoint with method " + method + " and path '" + path + "' already exists for service " + service.getId());
        }

        Api api = new Api();
        api.setName(trimmedName);
        api.setMethod(method);
        api.setPath(path);
        api.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        api.setService(service);

        Api saved = apiRepository.save(api);
        return ApiResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ApiResponse> getAllApis(Long serviceId) {
        List<Api> list;
        if (serviceId != null) {
            // Verify service exists to return 404 on invalid serviceId
            serviceService.findServiceEntity(serviceId);
            list = apiRepository.findByServiceId(serviceId);
        } else {
            list = apiRepository.findAll();
        }
        return list.stream().map(ApiResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiResponse getApiById(Long id) {
        Api api = findApiEntity(id);
        return ApiResponse.from(api);
    }

    public ApiResponse updateApi(Long id, ApiRequest request) {
        Api api = findApiEntity(id);

        com.impactgraph.entity.Service targetService = api.getService();
        if (request.getServiceId() != null && !request.getServiceId().equals(api.getService().getId())) {
            targetService = serviceService.findServiceEntity(request.getServiceId());
            api.setService(targetService);
        }

        String method = validateAndNormalizeMethod(request.getMethod());
        String path = validateAndNormalizePath(request.getPath());
        String trimmedName = request.getName().trim();

        if (apiRepository.existsByServiceIdAndMethodAndPathAndIdNot(targetService.getId(), method, path, id)) {
            throw new DuplicateResourceException(
                    "Another API endpoint with method " + method + " and path '" + path + "' already exists for service " + targetService.getId());
        }

        api.setName(trimmedName);
        api.setMethod(method);
        api.setPath(path);
        api.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Api updated = apiRepository.save(api);
        return ApiResponse.from(updated);
    }

    public void deleteApi(Long id) {
        Api api = findApiEntity(id);
        apiRepository.delete(api);
    }

    @Transactional(readOnly = true)
    public Api findApiEntity(Long id) {
        return apiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API with ID " + id + " not found"));
    }

    private String validateAndNormalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new InvalidRequestException("HTTP method cannot be blank");
        }
        String normalized = method.trim().toUpperCase();
        if (!ALLOWED_METHODS.contains(normalized)) {
            throw new InvalidRequestException(
                    "Invalid HTTP method: '" + method + "'. Allowed methods: " + ALLOWED_METHODS);
        }
        return normalized;
    }

    private String validateAndNormalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidRequestException("API path cannot be blank");
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            throw new InvalidRequestException("API path must start with '/': '" + path + "'");
        }
        return trimmed;
    }
}
