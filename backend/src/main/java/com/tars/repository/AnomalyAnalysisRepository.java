package com.tars.repository;

import com.tars.model.AnomalyAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnomalyAnalysisRepository extends JpaRepository<AnomalyAnalysis, Long> {
    Optional<AnomalyAnalysis> findByReportId(Long reportId);
}
