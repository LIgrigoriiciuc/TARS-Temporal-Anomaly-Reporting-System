package com.tars.service;

import com.tars.model.dto.AnomalyGraphDTO;
import com.tars.model.enums.ParadoxRisk;
import com.tars.model.mappers.AnomalyMapper;
import com.tars.repository.AnomalyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final AnomalyRepository anomalyRepository;

    public List<AnomalyGraphDTO> getGraphAnomalies(Long timelineId, ParadoxRisk paradoxRisk,
                                                   Integer yearFrom, Integer yearTo,
                                                   Set<Long> allowedTimelineIds) {
        return anomalyRepository.findForGraph(timelineId, paradoxRisk, yearFrom, yearTo)
                .stream()
                .filter(a -> allowedTimelineIds == null || allowedTimelineIds.contains(a.getTimeline().getId()))
                .map(AnomalyMapper::toGraphDto)
                .collect(Collectors.toList());
    }
}