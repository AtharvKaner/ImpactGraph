package com.impactgraph.repository;

import com.impactgraph.entity.AnalysisRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, UUID> {

    List<AnalysisRun> findByProjectIdOrderByTimestampDesc(Long projectId);

    Page<AnalysisRun> findAllByOrderByTimestampDesc(Pageable pageable);

    Optional<AnalysisRun> findFirstByCommitSha(String commitSha);
    
    boolean existsByDeliveryId(String deliveryId);

    Optional<AnalysisRun> findFirstByDeliveryId(String deliveryId);
}
