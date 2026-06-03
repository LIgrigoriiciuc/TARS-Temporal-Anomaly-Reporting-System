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
    List<ObservationReport> findByAgentIdAndStatusOrderByIdDesc(Long agentId, ReportStatus status);

    List<ObservationReport> findByAgentIdAndStatusInOrderByIdDesc(Long agentId, List<ReportStatus> statuses);
    /**
     * historical context for Gemini prompt.
     * Excludes only the current report, all other reports including same agent's
     * other reports are included so Gemini has full context.
     */
    @Query("""
    SELECT r FROM ObservationReport r
    WHERE r.timeline.id = :timelineId
    AND r.year BETWEEN :yearFrom AND :yearTo
    AND r.status IN ('CONFIRMED', 'REJECTED', 'PENDING_ANALYSIS')
    AND r.id != :excludeReportId
    ORDER BY r.year ASC
    """)
    List<ObservationReport> findHistoricalContext(
            @Param("timelineId") Long timelineId,
            @Param("yearFrom") int yearFrom,
            @Param("yearTo") int yearTo,
            @Param("excludeReportId") Long excludeReportId
    );
}