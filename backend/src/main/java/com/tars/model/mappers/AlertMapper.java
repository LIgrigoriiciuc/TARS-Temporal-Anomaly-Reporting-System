package com.tars.model.mappers;

import com.tars.model.Alert;
import com.tars.model.dto.AlertDTO;

public class AlertMapper {

    public static AlertDTO toDto(Alert alert) {
        if (alert == null) return null;
        return AlertDTO.builder()
                .id(alert.getId())
                .anomalyId(alert.getAnomaly().getId())
                .anomalyType(alert.getAnomaly().getType())
                .paradoxRisk(alert.getAnomaly().getParadoxRisk())
                .timelineName(alert.getAnomaly().getTimeline() != null
                        ? alert.getAnomaly().getTimeline().getName() : null)
                .year(alert.getAnomaly().getYear())
                .acknowledged(alert.isAcknowledged())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}