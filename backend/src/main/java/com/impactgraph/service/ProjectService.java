package com.impactgraph.service;

import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.entity.AppUser;
import com.impactgraph.entity.AuditEventType;
import com.impactgraph.entity.Project;
import com.impactgraph.entity.ProjectMembership;
import com.impactgraph.exception.DuplicateResourceException;
import com.impactgraph.exception.ResourceNotFoundException;
import com.impactgraph.repository.DependencyRepository;
import com.impactgraph.repository.ProjectMembershipRepository;
import com.impactgraph.repository.ProjectRepository;
import com.impactgraph.repository.UserRepository;
import com.impactgraph.security.JwtAuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DependencyRepository dependencyRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final DemoDataSeeder demoDataSeeder;

    @org.springframework.beans.factory.annotation.Autowired
    public ProjectService(ProjectRepository projectRepository,
                          DependencyRepository dependencyRepository,
                          ProjectMembershipRepository membershipRepository,
                          UserRepository userRepository,
                          AuditService auditService,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) DemoDataSeeder demoDataSeeder) {
        this.projectRepository = projectRepository;
        this.dependencyRepository = dependencyRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.demoDataSeeder = demoDataSeeder;
    }

    public ProjectService(ProjectRepository projectRepository,
                          DependencyRepository dependencyRepository,
                          ProjectMembershipRepository membershipRepository,
                          UserRepository userRepository,
                          AuditService auditService) {
        this(projectRepository, dependencyRepository, membershipRepository, userRepository, auditService, null);
    }

    public ProjectResponse createProject(ProjectRequest request) {
        return createProject(request, null, null);
    }

    public ProjectResponse createProject(ProjectRequest request, JwtAuthenticatedUser user, String ipAddress) {
        String trimmedName = request.getName().trim();
        if (projectRepository.existsByName(trimmedName)) {
            throw new DuplicateResourceException("Project with name '" + trimmedName + "' already exists");
        }

        Project project = new Project();
        project.setName(trimmedName);
        project.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Project saved = projectRepository.save(project);

        if (user != null && auditService != null) {
            auditService.logEvent(
                    AuditEventType.PROJECT_CREATED,
                    user.getUserId(), user.getEmail(),
                    toUuid(saved.getId()), toUuid(saved.getId()),
                    "SUCCESS",
                    "Project created: " + saved.getName(),
                    ipAddress
            );
        }

        return ProjectResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(ProjectResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects(JwtAuthenticatedUser user) {
        if (user == null || "ADMIN".equals(user.getRole())) {
            return getAllProjects();
        }

        List<Long> memberProjectIds = membershipRepository.findProjectIdsByUserId(user.getUserId());
        List<Project> memberProjects = projectRepository.findAllById(memberProjectIds);

        // Always include Demo projects so all users have immediate access to demo architectures
        List<Project> demoProjects = projectRepository.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().startsWith("[Demo]"))
                .filter(p -> !memberProjects.contains(p))
                .toList();

        List<Project> combined = new java.util.ArrayList<>(memberProjects);
        combined.addAll(demoProjects);
        return combined.stream().map(ProjectResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id) {
        Project project = findProjectEntity(id);
        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id, JwtAuthenticatedUser user) {
        if (user != null) {
            enforceProjectAccess(user, id);
        }
        return getProjectById(id);
    }

    public ProjectResponse updateProject(Long id, ProjectRequest request) {
        return updateProject(id, request, null, null);
    }

    public ProjectResponse updateProject(Long id, ProjectRequest request, JwtAuthenticatedUser user, String ipAddress) {
        Project project = findProjectEntity(id);

        String trimmedName = request.getName().trim();
        if (projectRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new DuplicateResourceException("Another project with name '" + trimmedName + "' already exists");
        }

        project.setName(trimmedName);
        project.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Project updated = projectRepository.save(project);

        if (user != null && auditService != null) {
            auditService.logEvent(
                    AuditEventType.PROJECT_UPDATED,
                    user.getUserId(), user.getEmail(),
                    toUuid(updated.getId()), toUuid(updated.getId()),
                    "SUCCESS",
                    "Project updated: " + updated.getName(),
                    ipAddress
            );
        }

        return ProjectResponse.from(updated);
    }

    public void deleteProject(Long id) {
        deleteProject(id, null, null);
    }

    public void deleteProject(Long id, JwtAuthenticatedUser user, String ipAddress) {
        Project project = findProjectEntity(id);
        dependencyRepository.deleteByProjectId(id);
        membershipRepository.deleteByUserIdAndProjectId(null, id);
        projectRepository.delete(project);

        if (user != null && auditService != null) {
            auditService.logEvent(
                    AuditEventType.PROJECT_DELETED,
                    user.getUserId(), user.getEmail(),
                    toUuid(id), toUuid(id),
                    "SUCCESS",
                    "Project deleted: " + project.getName(),
                    ipAddress
            );
        }
    }

    @Transactional(readOnly = true)
    public Project findProjectEntity(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project with ID " + id + " not found"));
    }

    public boolean canAccessProject(JwtAuthenticatedUser user, Long projectId) {
        if (user == null || "ADMIN".equals(user.getRole())) {
            return true;
        }
        if (isDemoProject(projectId)) {
            return true;
        }
        return membershipRepository.existsByUserIdAndProjectId(user.getUserId(), projectId);
    }

    public boolean isDemoProject(Long projectId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(p -> p.getName() != null && p.getName().startsWith("[Demo]"))
                .orElse(false);
    }

    public ProjectResponse getOrCreateDemoProject() {
        return projectRepository.findByName(DemoDataSeeder.DEMO_PROJECT_NAME)
                .map(ProjectResponse::from)
                .orElseGet(() -> {
                    if (demoDataSeeder != null) {
                        return ProjectResponse.from(demoDataSeeder.seedDemoProjectIfMissing());
                    }
                    Project p = new Project();
                    p.setName(DemoDataSeeder.DEMO_PROJECT_NAME);
                    p.setDescription("Demonstration Architecture");
                    return ProjectResponse.from(projectRepository.save(p));
                });
    }

    public boolean canAccessProject(JwtAuthenticatedUser user, UUID projectId) {
        return canAccessProject(user, resolveProjectId(projectId));
    }

    public void enforceProjectAccess(JwtAuthenticatedUser user, Long projectId) {
        if (!canAccessProject(user, projectId)) {
            if (auditService != null) {
                auditService.logEvent(
                        AuditEventType.ACCESS_DENIED,
                        user != null ? user.getUserId() : null,
                        user != null ? user.getEmail() : null,
                        toUuid(projectId), null,
                        "FAILURE",
                        "Cross-project access denied",
                        null
                );
            }
            throw new SecurityException("You do not have access to this project");
        }
    }

    public void enforceProjectAccess(JwtAuthenticatedUser user, UUID projectId) {
        enforceProjectAccess(user, resolveProjectId(projectId));
    }

    @Transactional
    public void grantMembership(String userEmail, UUID projectId,
                                 JwtAuthenticatedUser admin, String ipAddress) {
        AppUser targetUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Long projId = resolveProjectId(projectId);
        Project project = projectRepository.findById(projId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (membershipRepository.existsByUserIdAndProjectId(targetUser.getId(), project.getId())) {
            throw new IllegalArgumentException("User is already a member of this project");
        }

        ProjectMembership membership = new ProjectMembership();
        membership.setUser(targetUser);
        membership.setProject(project);
        membership.setGrantedBy(admin != null ? admin.getUserId() : null);
        membershipRepository.save(membership);

        if (auditService != null) {
            auditService.logEvent(
                    AuditEventType.MEMBERSHIP_GRANTED,
                    admin != null ? admin.getUserId() : null,
                    admin != null ? admin.getEmail() : null,
                    toUuid(project.getId()), membership.getId(),
                    "SUCCESS",
                    "Membership granted to " + userEmail,
                    ipAddress
            );
        }
    }

    @Transactional
    public void grantMembership(String userEmail, Long projectId,
                                 JwtAuthenticatedUser admin, String ipAddress) {
        grantMembership(userEmail, toUuid(projectId), admin, ipAddress);
    }

    @Transactional
    public void revokeMembership(String userEmail, UUID projectId,
                                  JwtAuthenticatedUser admin, String ipAddress) {
        AppUser targetUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Long projId = resolveProjectId(projectId);
        Project project = projectRepository.findById(projId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        ProjectMembership membership = membershipRepository.findByUserIdAndProjectId(targetUser.getId(), project.getId())
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

        membershipRepository.deleteByUserIdAndProjectId(targetUser.getId(), project.getId());

        if (auditService != null) {
            auditService.logEvent(
                    AuditEventType.MEMBERSHIP_REVOKED,
                    admin != null ? admin.getUserId() : null,
                    admin != null ? admin.getEmail() : null,
                    toUuid(project.getId()), null,
                    "SUCCESS",
                    "Membership revoked from " + userEmail,
                    ipAddress
            );
        }
    }

    @Transactional
    public void revokeMembership(String userEmail, Long projectId,
                                  JwtAuthenticatedUser admin, String ipAddress) {
        revokeMembership(userEmail, toUuid(projectId), admin, ipAddress);
    }

    @Transactional(readOnly = true)
    public List<MemberInfo> listMembers(Long projectId, JwtAuthenticatedUser user) {
        enforceProjectAccess(user, projectId);
        return membershipRepository.findByProjectId(projectId).stream()
                .map(m -> new MemberInfo(
                        m.getUser().getId(),
                        m.getUser().getEmail(),
                        m.getUser().getDisplayName(),
                        m.getUser().getRole().name(),
                        m.getGrantedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MemberInfo> listMembers(UUID projectId, JwtAuthenticatedUser user) {
        return listMembers(resolveProjectId(projectId), user);
    }

    public Long resolveProjectId(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        return projectId.getLeastSignificantBits();
    }

    public static UUID toUuid(Long id) {
        return id != null ? new UUID(0L, id) : null;
    }

    public record MemberInfo(UUID userId, String email, String displayName,
                             String role, Instant grantedAt) {}
}
