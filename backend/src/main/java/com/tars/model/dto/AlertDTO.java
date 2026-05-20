package com.tars.model.dto;

import com.tars.model.enums.AnomalyType;
import com.tars.model.enums.ParadoxRisk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDTO {
    private Long id;
    private Long anomalyId;
    private AnomalyType anomalyType;
    private ParadoxRisk paradoxRisk;
    private String timelineName;
    private Integer year;
    private boolean acknowledged;
    private LocalDateTime createdAt;
}