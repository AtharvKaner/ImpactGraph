package com.impactgraph.repository;

import com.impactgraph.entity.Api;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiRepository extends JpaRepository<Api, Long> {

    List<Api> findByServiceId(Long serviceId);

    List<Api> findByServiceProjectId(Long projectId);

    boolean existsByServiceIdAndMethodAndPath(Long serviceId, String method, String path);

    boolean existsByServiceIdAndMethodAndPathAndIdNot(Long serviceId, String method, String path, Long id);
}
