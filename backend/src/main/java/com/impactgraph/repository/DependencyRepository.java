package com.impactgraph.repository;

import com.impactgraph.entity.Dependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DependencyRepository extends JpaRepository<Dependency, Long> {

    List<Dependency> findBySourceServiceProjectId(Long projectId);

    List<Dependency> findBySourceServiceId(Long sourceServiceId);

    List<Dependency> findByTargetServiceId(Long targetServiceId);

    List<Dependency> findBySourceServiceIdOrTargetServiceId(Long sourceServiceId, Long targetServiceId);

    boolean existsBySourceServiceIdAndTargetServiceId(Long sourceServiceId, Long targetServiceId);

    Optional<Dependency> findBySourceServiceIdAndTargetServiceId(Long sourceServiceId, Long targetServiceId);

    @Modifying
    @Query("DELETE FROM Dependency d WHERE d.sourceService.id = :serviceId OR d.targetService.id = :serviceId")
    void deleteByServiceId(@Param("serviceId") Long serviceId);

    @Modifying
    @Query("DELETE FROM Dependency d WHERE d.sourceService.project.id = :projectId OR d.targetService.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
