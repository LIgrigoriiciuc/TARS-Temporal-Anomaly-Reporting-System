package com.tars.service;

import com.tars.model.Anomaly;
import com.tars.model.dto.AnomalyGraphDTO;
import com.tars.repository.AnomalyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final AnomalyRepository anomalyRepository;

    /**
     * Returns all anomalies for the graph.
     * Supervisors see everything.
     * Agents see everything for now — subscription filtering added in iteration 3.
     */
    public List<AnomalyGraphDTO> getAllAnomalies() {
        return anomalyRepository.findAll()
                .stream()
                .map(this::toGraphDto)
                .collect(Collectors.toList());
    }

    /**
     * Anomalies filtered by timeline — used when agent selects a specific lane.
     */
    public List<AnomalyGraphDTO> getAnomaliesByTimeline(Long timelineId) {
        return anomalyRepository.findByTimelineId(timelineId)
                .stream()
                .map(this::toGraphDto)
                .collect(Collectors.toList());
    }

    private AnomalyGraphDTO toGraphDto(Anomaly anomaly) {
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