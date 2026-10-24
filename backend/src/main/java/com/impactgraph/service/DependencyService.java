package com.impactgraph.service;

import com.impactgraph.dto.request.DependencyRequest;
import com.impactgraph.dto.response.DependencyResponse;
import com.impactgraph.entity.Dependency;
import com.impactgraph.exception.CrossProjectDependencyException;
import com.impactgraph.exception.DuplicateResourceException;
import com.impactgraph.exception.InvalidRequestException;
import com.impactgraph.exception.ResourceNotFoundException;
import com.impactgraph.graph.DependencyGraph;
import com.impactgraph.repository.DependencyRepository;
import com.impactgraph.repository.ServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class DependencyService {

    private final DependencyRepository dependencyRepository;
    private final ServiceRepository serviceRepository;
    private final ProjectService projectService;

    public DependencyService(DependencyRepository dependencyRepository,
                             ServiceRepository serviceRepository,
                             ProjectService projectService) {
        this.dependencyRepository = dependencyRepository;
        this.serviceRepository = serviceRepository;
        this.projectService = projectService;
    }

    public DependencyResponse createDependency(DependencyRequest request) {
        if (request.getSourceServiceId() == null) {
            throw new InvalidRequestException("Source service ID is required");
        }
        if (request.getTargetServiceId() == null) {
            throw new InvalidRequestException("Target service ID is required");
        }

        // Rule 1: Reject self-dependency (A cannot depend on A)
        if (request.getSourceServiceId().equals(request.getTargetServiceId())) {
            throw new InvalidRequestException(
                    "Self-dependency is not allowed: source service and target service cannot be the same (ID: "
                            + request.getSourceServiceId() + ")");
        }

        // Rule 2: Ensure source and target services exist
        com.impactgraph.entity.Service source = serviceRepository.findById(request.getSourceServiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source service with ID " + request.getSourceServiceId() + " not found"));

        com.impactgraph.entity.Service target = serviceRepository.findById(request.getTargetServiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target service with ID " + request.getTargetServiceId() + " not found"));

        // Rule 3: Reject dependencies between services belonging to different projects
        Long sourceProjectId = source.getProject().getId();
        Long targetProjectId = target.getProject().getId();
        if (!Objects.equals(sourceProjectId, targetProjectId)) {
            throw new CrossProjectDependencyException(
                    "Dependencies between services belonging to different projects are not allowed. " +
                            "Source service '" + source.getName() + "' belongs to project " + sourceProjectId +
                            ", but target service '" + target.getName() + "' belongs to project " + targetProjectId + ".");
        }

        // Rule 4: Reject duplicate dependencies for the same source-target pair
        if (dependencyRepository.existsBySourceServiceIdAndTargetServiceId(source.getId(), target.getId())) {
            throw new DuplicateResourceException(
                    "A dependency from source service '" + source.getName() + "' (ID " + source.getId() +
                            ") to target service '" + target.getName() + "' (ID " + target.getId() + ") already exists");
        }

        // Direction Convention: A -> B means A depends on B
        Dependency dependency = new Dependency();
        dependency.setSourceService(source);
        dependency.setTargetService(target);
        dependency.setDependencyType(request.getDependencyType() != null ? request.getDependencyType().trim() : null);
        dependency.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Dependency saved = dependencyRepository.save(dependency);
        return DependencyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DependencyResponse> getAllDependencies(Long projectId, Long sourceServiceId, Long targetServiceId) {
        List<Dependency> list;
        if (projectId != null) {
            projectService.findProjectEntity(projectId);
            list = dependencyRepository.findBySourceServiceProjectId(projectId);
        } else if (sourceServiceId != null) {
            list = dependencyRepository.findBySourceServiceId(sourceServiceId);
        } else if (targetServiceId != null) {
            list = dependencyRepository.findByTargetServiceId(targetServiceId);
        } else {
            list = dependencyRepository.findAll();
        }
        return list.stream().map(DependencyResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DependencyResponse getDependencyById(Long id) {
        Dependency dependency = findDependencyEntity(id);
        return DependencyResponse.from(dependency);
    }

    public DependencyResponse updateDependency(Long id, DependencyRequest request) {
        Dependency dependency = findDependencyEntity(id);

        Long sourceId = (request.getSourceServiceId() != null)
                ? request.getSourceServiceId()
                : dependency.getSourceService().getId();

        Long targetId = (request.getTargetServiceId() != null)
                ? request.getTargetServiceId()
                : dependency.getTargetService().getId();

        if (sourceId.equals(targetId)) {
            throw new InvalidRequestException(
                    "Self-dependency is not allowed: source service and target service cannot be the same (ID: "
                            + sourceId + ")");
        }

        com.impactgraph.entity.Service source = serviceRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source service with ID " + sourceId + " not found"));

        com.impactgraph.entity.Service target = serviceRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Target service with ID " + targetId + " not found"));

        if (!Objects.equals(source.getProject().getId(), target.getProject().getId())) {
            throw new CrossProjectDependencyException(
                    "Dependencies between services belonging to different projects are not allowed. " +
                            "Source service belongs to project " + source.getProject().getId() +
                            ", but target service belongs to project " + target.getProject().getId() + ".");
        }

        // If changing endpoints, verify duplicate does not already exist
        boolean changedEndpoints = !source.getId().equals(dependency.getSourceService().getId()) ||
                !target.getId().equals(dependency.getTargetService().getId());

        if (changedEndpoints && dependencyRepository.existsBySourceServiceIdAndTargetServiceId(source.getId(), target.getId())) {
            throw new DuplicateResourceException(
                    "Another dependency from source service " + source.getId() +
                            " to target service " + target.getId() + " already exists");
        }

        dependency.setSourceService(source);
        dependency.setTargetService(target);
        dependency.setDependencyType(request.getDependencyType() != null ? request.getDependencyType().trim() : null);
        dependency.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Dependency updated = dependencyRepository.save(dependency);
        return DependencyResponse.from(updated);
    }

    public void deleteDependency(Long id) {
        Dependency dependency = findDependencyEntity(id);
        dependencyRepository.delete(dependency);
    }

    @Transactional(readOnly = true)
    public Dependency findDependencyEntity(Long id) {
        return dependencyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency with ID " + id + " not found"));
    }

    /**
     * Builds a {@link DependencyGraph} representation of a project's services and dependencies.
     * <p>
     * Ensures 100% interoperability with the teammate graph engine components
     * (e.g. {@link com.impactgraph.graph.CycleDetector} and {@link com.impactgraph.graph.ImpactAnalyzer}).
     *
     * @param projectId ID of the project to build the graph for
     * @return constructed {@link DependencyGraph}
     */
    @Transactional(readOnly = true)
    public DependencyGraph buildDependencyGraphForProject(Long projectId) {
        projectService.findProjectEntity(projectId);

        List<com.impactgraph.entity.Service> services = serviceRepository.findByProjectId(projectId);
        List<Dependency> dependencies = dependencyRepository.findBySourceServiceProjectId(projectId);

        DependencyGraph.Builder builder = DependencyGraph.builder();
        for (com.impactgraph.entity.Service svc : services) {
            builder.addService(svc.getName());
        }
        for (Dependency dep : dependencies) {
            // Direction convention: source depends on target (A -> B)
            builder.addDependency(dep.getSourceService().getName(), dep.getTargetService().getName());
        }
        return builder.build();
    }
}
