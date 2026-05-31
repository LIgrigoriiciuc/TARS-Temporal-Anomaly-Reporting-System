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
 *
 * If injectionDetected = true, the report was quarantined before analysis.
 * confirmed will be false and explanation will state the reason.
 * All other analysis fields (type, paradoxRisk, anomalyVerified) will be null.
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

    // null if confirmed=false or still pending
    // false = anomaly exists but awaiting a second independent observer
    // true = at least 2 distinct agents confirmed this anomaly
    private Boolean anomalyVerified;

    private LocalDateTime analyzedAt;

    // true if Gemini detected a prompt injection attempt in the report fields.
    // When true, the report is FLAGGED and excluded from all corroboration and historical context.
    // Distinct from a normal rejection — this is a security event, not an analysis verdict.
    private Boolean injectionDetected;
}