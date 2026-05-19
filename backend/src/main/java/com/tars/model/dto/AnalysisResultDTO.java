package com.tars.model.dto;

import com.tars.model.enums.AnalysisStatus;
import com.tars.model.enums.AnomalyType;
import com.tars.model.enums.ParadoxRisk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What the agent sees after their report is analyzed.
 * No internal IDs, no correlated report references.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultDTO {

    private AnalysisStatus analysisStatus;

    // null while PENDING
    private Boolean confirmed;

    // null if confirmed = false or still pending
    private AnomalyType type;
    private ParadoxRisk paradoxRisk;

    private String explanation;
    private LocalDateTime analyzedAt;
}