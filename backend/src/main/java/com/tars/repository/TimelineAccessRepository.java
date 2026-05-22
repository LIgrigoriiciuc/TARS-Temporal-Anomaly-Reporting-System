package com.tars.repository;

import com.tars.model.TimelineAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimelineAccessRepository extends JpaRepository<TimelineAccess, Long> {

    List<TimelineAccess> findByAgentIdAndActiveTrue(Long agentId);

    long countByAgentIdAndActiveTrue(Long agentId);

    Optional<TimelineAccess> findByAgentIdAndTimelineId(Long agentId, Long timelineId);
}