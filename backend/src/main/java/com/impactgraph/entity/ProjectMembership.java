package com.impactgraph.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Links a user to a project, representing project membership.
 * Controls which projects a DEVELOPER can access.
 * ADMINs can access all projects but membership is still tracked.
 */
@Entity
@Table(name = "project_membership",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "project_id"}))
public class ProjectMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, updatable = false)
    private Instant grantedAt;

    /**
     * Who granted membership (admin user ID). Null for system-created memberships.
     */
    @Column
    private UUID grantedBy;

    @PrePersist
    protected void onCreate() {
        grantedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public Instant getGrantedAt() { return grantedAt; }

    public UUID getGrantedBy() { return grantedBy; }
    public void setGrantedBy(UUID grantedBy) { this.grantedBy = grantedBy; }
}
