package com.impactgraph.service;

import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.entity.Project;
import com.impactgraph.exception.DuplicateResourceException;
import com.impactgraph.exception.ResourceNotFoundException;
import com.impactgraph.repository.DependencyRepository;
import com.impactgraph.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DependencyRepository dependencyRepository;

    public ProjectService(ProjectRepository projectRepository, DependencyRepository dependencyRepository) {
        this.projectRepository = projectRepository;
        this.dependencyRepository = dependencyRepository;
    }

    public ProjectResponse createProject(ProjectRequest request) {
        String trimmedName = request.getName().trim();
        if (projectRepository.existsByName(trimmedName)) {
            throw new DuplicateResourceException("Project with name '" + trimmedName + "' already exists");
        }

        Project project = new Project();
        project.setName(trimmedName);
        project.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Project saved = projectRepository.save(project);
        return ProjectResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(ProjectResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id) {
        Project project = findProjectEntity(id);
        return ProjectResponse.from(project);
    }

    public ProjectResponse updateProject(Long id, ProjectRequest request) {
        Project project = findProjectEntity(id);

        String trimmedName = request.getName().trim();
        if (projectRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new DuplicateResourceException("Another project with name '" + trimmedName + "' already exists");
        }

        project.setName(trimmedName);
        project.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Project updated = projectRepository.save(project);
        return ProjectResponse.from(updated);
    }

    public void deleteProject(Long id) {
        Project project = findProjectEntity(id);
        dependencyRepository.deleteByProjectId(id);
        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public Project findProjectEntity(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project with ID " + id + " not found"));
    }
}
