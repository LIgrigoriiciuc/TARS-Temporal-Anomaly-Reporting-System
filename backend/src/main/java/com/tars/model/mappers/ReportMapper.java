package com.tars.model.mappers;

import com.tars.model.Anomaly;
import com.tars.model.AnomalyAnalysis;
import com.tars.model.ObservationReport;
import com.tars.model.dto.*;

public class ReportMapper {

    public static ObservationReport toEntity(DraftRequestDTO dto) {
        if (dto == null) return null;
        ObservationReport report = new ObservationReport();
        report.setDescription(dto.getDescription());
        report.setYear(dto.getYear());
        report.setKeywords(dto.getKeywords());
        return report;
    }

    public static DraftResponseDTO toDto(ObservationReport report) {
        if (report == null) return null;
        return new DraftResponseDTO(
                report.getId(),
                report.getDescription(),
                report.getYear(),
                report.getKeywords(),
                report.getStatus().name(),
                report.getTimestamp(),
                report.getTimeline() != null ? report.getTimeline().getId() : null,
                report.getTimeline() != null ? report.getTimeline().getName() : null
        );
    }

    // -------------------------------------------------------------------------
    // Submit + analysis mapping (iteration 2)
    // -------------------------------------------------------------------------

    public static ObservationReport fromSubmitDto(SubmitReportRequestDTO dto) {
        if (dto == null) return null;
        ObservationReport report = new ObservationReport();
        report.setDescription(dto.getDescription());
        report.setYear(dto.getYear());
        report.setKeywords(dto.getKeywords());
        return report;
    }

    public static SubmittedReportResponseDTO toSubmittedDto(ObservationReport report) {
        if (report == null) return null;
        return SubmittedReportResponseDTO.builder()
                .id(report.getId())
                .description(report.getDescription())
                .year(report.getYear())
                .keywords(report.getKeywords())
                .status(report.getStatus())
                .timestamp(report.getTimestamp())
                .timelineId(report.getTimeline() != null ? report.getTimeline().getId() : null)
                .timelineName(report.getTimeline() != null ? report.getTimeline().getName() : null)
                .analysis(toAnalysisDto(report.getAnalysis()))
                .build();
    }

    private static AnalysisResultDTO toAnalysisDto(AnomalyAnalysis analysis) {
        if (analysis == null) return null;

        // type and paradoxRisk live on Anomaly, not on AnomalyAnalysis
        // anomaly is null when confirmed=false or still pending
        Anomaly anomaly = analysis.getAnomaly();

        return AnalysisResultDTO.builder()
                .analysisStatus(analysis.getAnalysisStatus())
                .confirmed(analysis.getConfirmed())
                .explanation(analysis.getExplanation())
                .analyzedAt(analysis.getAnalyzedAt())
                .type(anomaly != null ? anomaly.getType() : null)
                .paradoxRisk(anomaly != null ? anomaly.getParadoxRisk() : null)
                .anomalyVerified(anomaly != null ? anomaly.isVerified() : null)
                .build();
    }
}