package com.impactgraph.repository;

import com.impactgraph.entity.ProjectMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {

    boolean existsByUserIdAndProjectId(UUID userId, Long projectId);

    Optional<ProjectMembership> findByUserIdAndProjectId(UUID userId, Long projectId);

    List<ProjectMembership> findByUserId(UUID userId);

    List<ProjectMembership> findByProjectId(Long projectId);

    void deleteByUserIdAndProjectId(UUID userId, Long projectId);

    @Query("SELECT pm.project.id FROM ProjectMembership pm WHERE pm.user.id = :userId")
    List<Long> findProjectIdsByUserId(@Param("userId") UUID userId);
}
