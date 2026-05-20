package com.tars.model.dto;

import com.tars.model.enums.AnomalyType;
import com.tars.model.enums.ParadoxRisk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for the timeline graph.
 * X axis = year, Y axis = timelineId, color = paradoxRisk, shape/label = type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyGraphDTO {
    private Long id;
    private AnomalyType type;
    private ParadoxRisk paradoxRisk;
    private Integer year;
    private Long timelineId;
    private String timelineName;
    private boolean verified;
}