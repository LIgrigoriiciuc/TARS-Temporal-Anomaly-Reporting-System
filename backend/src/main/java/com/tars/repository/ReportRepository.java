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
    /**
     * UC-08 — historical context for Gemini prompt.
     * Excludes the submitting agent's own reports — agents cannot self-corroborate.
     */
    @Query("""
        SELECT r FROM ObservationReport r
        WHERE r.timeline.id = :timelineId
        AND r.year BETWEEN :yearFrom AND :yearTo
        AND r.status IN ('CONFIRMED', 'REJECTED', 'PENDING_ANALYSIS')
        AND r.agent.id != :excludeAgentId
        AND (:keyword IS NULL OR LOWER(r.keywords) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY r.year ASC
        """)
    List<ObservationReport> findHistoricalContext(
            @Param("timelineId") Long timelineId,
            @Param("yearFrom") int yearFrom,
            @Param("yearTo") int yearTo,
            @Param("keyword") String keyword,
            @Param("excludeAgentId") Long excludeAgentId
    );

    /**
     * Duplicate check — same agent, same timeline, same year, not a draft.
     * If result is non-empty, block submission with 409.
     */
    @Query("""
        SELECT r FROM ObservationReport r
        WHERE r.agent.id = :agentId
        AND r.timeline.id = :timelineId
        AND r.year = :year
        AND r.status IN ('PENDING_ANALYSIS', 'CONFIRMED')
        """)
    List<ObservationReport> findDuplicateReport(
            @Param("agentId") Long agentId,
            @Param("timelineId") Long timelineId,
            @Param("year") int year
    );
}