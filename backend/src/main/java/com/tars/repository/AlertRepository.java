package com.tars.repository;

import com.tars.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // All unacknowledged alerts — shown in Supervisor notification panel
    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();

    // Check if alert already exists for this anomaly — avoid duplicates
    Optional<Alert> findByAnomalyId(Long anomalyId);
}