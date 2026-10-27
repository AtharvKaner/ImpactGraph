package com.impactgraph.repository;

import com.impactgraph.entity.AuditEvent;
import com.impactgraph.entity.AuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByOrderByTimestampDesc(Pageable pageable);

    Page<AuditEvent> findByActorIdOrderByTimestampDesc(UUID actorId, Pageable pageable);

    Page<AuditEvent> findByProjectIdOrderByTimestampDesc(UUID projectId, Pageable pageable);

    Page<AuditEvent> findByEventTypeOrderByTimestampDesc(AuditEventType eventType, Pageable pageable);

    Page<AuditEvent> findByTimestampBetweenOrderByTimestampDesc(Instant start, Instant end, Pageable pageable);

    long countByEventType(AuditEventType eventType);
}
