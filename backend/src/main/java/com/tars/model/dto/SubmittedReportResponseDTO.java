package com.tars.model.dto;

import com.tars.model.enums.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Returned to the agent after submission and via WebSocket on analysis completion.
 * status = FLAGGED means the report was quarantined due to a prompt injection attempt.
 * In that case, analysis.injectionDetected = true and analysis.explanation states the reason.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmittedReportResponseDTO {

    private Long id;
    private String description;
    private Integer year;
    private String keywords;
    private ReportStatus status;
    private LocalDateTime timestamp;

    private Long timelineId;
    private String timelineName;

    // null while status = PENDING_ANALYSIS, populated once Gemini finishes
    private AnalysisResultDTO analysis;
}