package com.tars.model.mappers;

import com.tars.model.Anomaly;
import com.tars.model.dto.AnomalyGraphDTO;

public class AnomalyMapper {

    public static AnomalyGraphDTO toGraphDto(Anomaly anomaly) {
        if (anomaly == null) return null;
        return AnomalyGraphDTO.builder()
                .id(anomaly.getId())
                .type(anomaly.getType())
                .paradoxRisk(anomaly.getParadoxRisk())
                .year(anomaly.getYear())
                .timelineId(anomaly.getTimeline() != null ? anomaly.getTimeline().getId() : null)
                .timelineName(anomaly.getTimeline() != null ? anomaly.getTimeline().getName() : null)
                .verified(anomaly.isVerified())
                .build();
    }
}