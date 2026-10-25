package com.impactgraph.service;

import com.impactgraph.dto.ProjectRequest;
import com.impactgraph.dto.ProjectResponse;
import com.impactgraph.entity.*;
import com.impactgraph.repository.ProjectMembershipRepository;
import com.impactgraph.repository.ProjectRepository;
import com.impactgraph.repository.UserRepository;
import com.impactgraph.security.JwtAuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Project management and membership service with authorization enforcement.
 * <p>
 * Authorization rules:
 * - ADMIN can access and manage all projects.
 * - DEVELOPER can only access projects they are a member of.
 * - Project membership is checked on every request.
 * </p>
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMembershipRepository membershipRepository,
                          UserRepository userRepository,
                          AuditService auditService) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Check if the user can access a specific project.
     * ADMINs can access all projects; DEVELOPERs only their memberships.
     */
    public boolean canAccessProject(JwtAuthenticatedUser user, UUID projectId) {
        if ("ADMIN".equals(user.getRole())) {
            return true;
        }
        return membershipRepository.existsByUserIdAndProjectId(user.getUserId(), projectId);
    }

    /**
     * Enforce project access, throwing SecurityException on violation.
     */
    public void enforceProjectAccess(JwtAuthenticatedUser user, UUID projectId) {
        if (!canAccessProject(user, projectId)) {
            auditService.logEvent(
                    AuditEventType.ACCESS_DENIED,
                    user.getUserId(), user.getEmail(),
                    projectId, null,
                    "FAILURE",
                    "Cross-project access denied",
                    null
            );
            throw new SecurityException("You do not have access to this project");
        }
    }

    /**
     * Create a new project. Only ADMINs can create projects.
     */
    @Transactional
    public ProjectResponse createProject(ProjectRequest request, JwtAuthenticatedUser user, String ipAddress) {
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project = projectRepository.save(project);

        auditService.logEvent(
                AuditEventType.PROJECT_CREATED,
                user.getUserId(), user.getEmail(),
                project.getId(), project.getId(),
                "SUCCESS",
                "Project created: " + project.getName(),
                ipAddress
        );

        return toResponse(project);
    }

    /**
     * List projects accessible to the current user.
     */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(JwtAuthenticatedUser user) {
        if ("ADMIN".equals(user.getRole())) {
            return projectRepository.findAll().stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }

        List<UUID> projectIds = membershipRepository.findProjectIdsByUserId(user.getUserId());
        return projectRepository.findAllById(projectIds).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a single project by ID, with access check.
     */
    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, JwtAuthenticatedUser user) {
        enforceProjectAccess(user, projectId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        return toResponse(project);
    }

    /**
     * Update a project. Admin only.
     */
    @Transactional
    public ProjectResponse updateProject(UUID projectId, ProjectRequest request,
                                          JwtAuthenticatedUser user, String ipAddress) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project = projectRepository.save(project);

        auditService.logEvent(
                AuditEventType.PROJECT_UPDATED,
                user.getUserId(), user.getEmail(),
                project.getId(), project.getId(),
                "SUCCESS",
                "Project updated: " + project.getName(),
                ipAddress
        );

        return toResponse(project);
    }

    /**
     * Delete a project. Admin only.
     */
    @Transactional
    public void deleteProject(UUID projectId, JwtAuthenticatedUser user, String ipAddress) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        projectRepository.delete(project);

        auditService.logEvent(
                AuditEventType.PROJECT_DELETED,
                user.getUserId(), user.getEmail(),
                projectId, projectId,
                "SUCCESS",
                "Project deleted: " + project.getName(),
                ipAddress
        );
    }

    /**
     * Grant project membership to a user. Admin only.
     */
    @Transactional
    public void grantMembership(String userEmail, UUID projectId,
                                 JwtAuthenticatedUser admin, String ipAddress) {
        AppUser targetUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (membershipRepository.existsByUserIdAndProjectId(targetUser.getId(), projectId)) {
            throw new IllegalArgumentException("User is already a member of this project");
        }

        ProjectMembership membership = new ProjectMembership();
        membership.setUser(targetUser);
        membership.setProject(project);
        membership.setGrantedBy(admin.getUserId());
        membershipRepository.save(membership);

        auditService.logEvent(
                AuditEventType.MEMBERSHIP_GRANTED,
                admin.getUserId(), admin.getEmail(),
                projectId, membership.getId(),
                "SUCCESS",
                "Membership granted to " + userEmail,
                ipAddress
        );
    }

    /**
     * Revoke project membership. Admin only.
     */
    @Transactional
    public void revokeMembership(String userEmail, UUID projectId,
                                  JwtAuthenticatedUser admin, String ipAddress) {
        AppUser targetUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        membershipRepository.findByUserIdAndProjectId(targetUser.getId(), projectId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));

        membershipRepository.deleteByUserIdAndProjectId(targetUser.getId(), projectId);

        auditService.logEvent(
                AuditEventType.MEMBERSHIP_REVOKED,
                admin.getUserId(), admin.getEmail(),
                projectId, null,
                "SUCCESS",
                "Membership revoked from " + userEmail,
                ipAddress
        );
    }

    /**
     * List members of a project. Admins and project members can view.
     */
    @Transactional(readOnly = true)
    public List<MemberInfo> listMembers(UUID projectId, JwtAuthenticatedUser user) {
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

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    /**
     * Simple record for returning member information.
     */
    public record MemberInfo(UUID userId, String email, String displayName,
                              String role, java.time.Instant grantedAt) {}
}
