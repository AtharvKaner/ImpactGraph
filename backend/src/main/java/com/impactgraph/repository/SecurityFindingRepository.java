package com.impactgraph.repository;

import com.impactgraph.entity.FindingStatus;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.entity.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, UUID> {

    List<SecurityFinding> findByStatus(FindingStatus status);

    List<SecurityFinding> findBySeverity(Severity severity);

    List<SecurityFinding> findByRuleId(String ruleId);

    List<SecurityFinding> findByProjectId(Long projectId);

    Page<SecurityFinding> findByProjectId(Long projectId, Pageable pageable);

    @Query("SELECT f FROM SecurityFinding f WHERE (:status IS NULL OR f.status = :status) " +
           "AND (:severity IS NULL OR f.severity = :severity) " +
           "AND (:ruleId IS NULL OR f.ruleId = :ruleId) " +
           "AND (:projectId IS NULL OR f.projectId = :projectId) " +
           "ORDER BY f.createdAt DESC")
    List<SecurityFinding> filterFindings(
            @Param("status") FindingStatus status,
            @Param("severity") Severity severity,
            @Param("ruleId") String ruleId,
            @Param("projectId") Long projectId);

    long countBySeverity(Severity severity);

    long countByStatus(FindingStatus status);
}
