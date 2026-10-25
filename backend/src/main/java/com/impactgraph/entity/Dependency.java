package com.impactgraph.entity;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * Domain entity representing a directed dependency between two services.
 * <p>
 * <b>Direction Convention:</b> {@code A -> B} means service {@code A} depends on service {@code B}.
 * <ul>
 *   <li>{@link #getSourceService()}: The service that declares the dependency (source node {@code A}, dependent).</li>
 *   <li>{@link #getTargetService()}: The service that is depended upon (target node {@code B}, dependency).</li>
 * </ul>
 * This matches the convention used by the graph engine ({@link com.impactgraph.graph.DependencyEdge}).
 */
@Entity
@Table(name = "dependencies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dependencies_source_target", columnNames = {"source_service_id", "target_service_id"})
})
public class Dependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_service_id", nullable = false)
    private Service sourceService;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_service_id", nullable = false)
    private Service targetService;

    @Column(name = "dependency_type", length = 50)
    private String dependencyType;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Dependency() {
    }

    public Dependency(Service sourceService, Service targetService, String dependencyType, String description) {
        this.sourceService = sourceService;
        this.targetService = targetService;
        this.dependencyType = dependencyType;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Service getSourceService() {
        return sourceService;
    }

    public void setSourceService(Service sourceService) {
        this.sourceService = sourceService;
    }

    public Service getTargetService() {
        return targetService;
    }

    public void setTargetService(Service targetService) {
        this.targetService = targetService;
    }

    public String getDependencyType() {
        return dependencyType;
    }

    public void setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dependency that = (Dependency) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "id=" + id +
                ", sourceService=" + (sourceService != null ? sourceService.getId() : null) +
                ", targetService=" + (targetService != null ? targetService.getId() : null) +
                ", dependencyType='" + dependencyType + '\'' +
                '}';
    }
}
