package com.impactgraph.service;

import com.impactgraph.dto.request.ServiceRequest;
import com.impactgraph.dto.response.ServiceResponse;
import com.impactgraph.entity.Project;
import com.impactgraph.exception.DuplicateResourceException;
import com.impactgraph.exception.InvalidRequestException;
import com.impactgraph.exception.ResourceNotFoundException;
import com.impactgraph.repository.DependencyRepository;
import com.impactgraph.repository.ServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final ProjectService projectService;
    private final DependencyRepository dependencyRepository;

    public ServiceService(ServiceRepository serviceRepository,
                          ProjectService projectService,
                          DependencyRepository dependencyRepository) {
        this.serviceRepository = serviceRepository;
        this.projectService = projectService;
        this.dependencyRepository = dependencyRepository;
    }

    public ServiceResponse createService(ServiceRequest request) {
        Project project = projectService.findProjectEntity(request.getProjectId());

        String trimmedName = request.getName().trim();
        if (serviceRepository.existsByProjectIdAndName(project.getId(), trimmedName)) {
            throw new DuplicateResourceException(
                    "Service with name '" + trimmedName + "' already exists in project " + project.getId());
        }

        com.impactgraph.entity.Service service = new com.impactgraph.entity.Service();
        service.setName(trimmedName);
        service.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        service.setProject(project);

        com.impactgraph.entity.Service saved = serviceRepository.save(service);
        return ServiceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> getAllServices(Long projectId) {
        List<com.impactgraph.entity.Service> list;
        if (projectId != null) {
            // Verify project exists to return clean 404 if invalid projectId is passed
            projectService.findProjectEntity(projectId);
            list = serviceRepository.findByProjectId(projectId);
        } else {
            list = serviceRepository.findAll();
        }
        return list.stream().map(ServiceResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceResponse getServiceById(Long id) {
        com.impactgraph.entity.Service service = findServiceEntity(id);
        return ServiceResponse.from(service);
    }

    public ServiceResponse updateService(Long id, ServiceRequest request) {
        com.impactgraph.entity.Service service = findServiceEntity(id);

        Long targetProjectId = (request.getProjectId() != null) ? request.getProjectId() : service.getProject().getId();
        if (!targetProjectId.equals(service.getProject().getId())) {
            Project newProject = projectService.findProjectEntity(targetProjectId);
            // Verify that moving service does not orphan or cross project dependencies
            boolean hasDependencies = !dependencyRepository.findBySourceServiceIdOrTargetServiceId(id, id).isEmpty();
            if (hasDependencies) {
                throw new InvalidRequestException(
                        "Cannot move service to another project while it has active dependencies");
            }
            service.setProject(newProject);
        }

        String trimmedName = request.getName().trim();
        if (serviceRepository.existsByProjectIdAndNameAndIdNot(targetProjectId, trimmedName, id)) {
            throw new DuplicateResourceException(
                    "Another service with name '" + trimmedName + "' already exists in project " + targetProjectId);
        }

        service.setName(trimmedName);
        service.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        com.impactgraph.entity.Service updated = serviceRepository.save(service);
        return ServiceResponse.from(updated);
    }

    public void deleteService(Long id) {
        com.impactgraph.entity.Service service = findServiceEntity(id);
        dependencyRepository.deleteByServiceId(id);
        serviceRepository.delete(service);
    }

    @Transactional(readOnly = true)
    public com.impactgraph.entity.Service findServiceEntity(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service with ID " + id + " not found"));
    }
}
