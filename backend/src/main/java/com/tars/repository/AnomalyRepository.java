package com.tars.repository;

import com.tars.model.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {
    // For the graph — all anomalies on a given timeline
    List<Anomaly> findByTimelineId(Long timelineId);
}
