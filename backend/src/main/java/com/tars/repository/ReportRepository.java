package com.tars.repository;

import com.tars.model.ObservationReport;
import com.tars.model.enums.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<ObservationReport, Long> {
    List<ObservationReport> findByAgentIdAndStatus(Long agentId, ReportStatus status);
    Optional<ObservationReport> findByIdAndAgentId(Long id, Long agentId);
}
