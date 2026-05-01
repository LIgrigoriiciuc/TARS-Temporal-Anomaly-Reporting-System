package com.tars.repository;

import com.tars.model.ObservationReport;

@Repository
public interface ReportRepository extends JpaRepository<ObservationReport, Long> {
    List<ObservationReport> findByAgentIdAndStatus(Long agentId, ReportStatus status);
}
