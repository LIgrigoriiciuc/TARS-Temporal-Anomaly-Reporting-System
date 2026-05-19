package com.tars.repository;

import com.tars.model.ObservationReport;
import com.tars.model.enums.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<ObservationReport, Long> {

    List<ObservationReport> findByAgentIdAndStatus(Long agentId, ReportStatus status);

    // All submitted (non-draft) reports for an agent — for the agent's report history list
    List<ObservationReport> findByAgentIdAndStatusIn(Long agentId, List<ReportStatus> statuses);

    Optional<ObservationReport> findByIdAndAgentId(Long id, Long agentId);

    /**
     * UC-08 step 1 — fetch historical context to send to Gemini.
     * Finds confirmed/rejected reports on the same timeline within a year window,
     * optionally matching any of the submitted keywords.
     *
     * Called before building the Gemini prompt so the AI has relevant history.
     * Results are NOT filtered by agent — the whole DB is context, agents don't see these IDs.
     *
     * yearFrom/yearTo = submitted year ± 50 (configurable in GeminiService)
     * keyword matching is loose — LIKE on the combined keywords string
     */
    @Query("""
        SELECT r FROM ObservationReport r
        WHERE r.timeline.id = :timelineId
        AND r.year BETWEEN :yearFrom AND :yearTo
        AND r.status IN ('CONFIRMED', 'REJECTED','PENDING_ANALYSIS')
        AND (:keyword IS NULL OR LOWER(r.keywords) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY r.year ASC
        """)
    List<ObservationReport> findHistoricalContext(
            @Param("timelineId") Long timelineId,
            @Param("yearFrom") int yearFrom,
            @Param("yearTo") int yearTo,
            @Param("keyword") String keyword
    );
}