package com.tars.repository;

import com.tars.model.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {

    // For the graph — all anomalies on a given timeline
    List<Anomaly> findByTimelineId(Long timelineId);

    /**
     * UC-09 / UC-10 — graph query, always verified only.
     * All filter params optional — null means "no filter on this field".
     */
    @Query("""
        SELECT a FROM Anomaly a
        WHERE a.verified = true
        AND (:timelineId IS NULL OR a.timeline.id = :timelineId)
        AND (:paradoxRisk IS NULL OR a.paradoxRisk = :paradoxRisk)
        AND (:yearFrom IS NULL OR a.year >= :yearFrom)
        AND (:yearTo IS NULL OR a.year <= :yearTo)
        ORDER BY a.year ASC
        """)
    List<Anomaly> findForGraph(
            @Param("timelineId") Long timelineId,
            @Param("paradoxRisk") com.tars.model.enums.ParadoxRisk paradoxRisk,
            @Param("yearFrom") Integer yearFrom,
            @Param("yearTo") Integer yearTo
    );
}