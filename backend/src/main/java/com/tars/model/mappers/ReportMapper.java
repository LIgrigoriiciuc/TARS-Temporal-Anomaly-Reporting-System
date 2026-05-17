package com.tars.model.mappers;

import com.tars.model.ObservationReport;
import com.tars.model.dto.DraftRequestDTO;
import com.tars.model.dto.DraftResponseDTO;

/**
 * Maps between ObservationReport entity and its two DTOs.
 * toEntity: DraftRequestDTO (request) → ObservationReport
 * toDto:    ObservationReport → DraftResponseDTO (response)
 */
public class ReportMapper {

    // Request → Entity (used in controller before calling service)
    public static ObservationReport toEntity(DraftRequestDTO dto) {
        if (dto == null) return null;
        ObservationReport report = new ObservationReport();
        report.setDescription(dto.getDescription());
        report.setYear(dto.getYear());
        report.setKeywords(dto.getKeywords());
        return report;
    }

    // Entity → Response (used in controller after service returns)
    public static DraftResponseDTO toDto(ObservationReport report) {
        if (report == null) return null;
        DraftResponseDTO dto = new DraftResponseDTO();
        dto.setId(report.getId());
        dto.setDescription(report.getDescription());
        dto.setYear(report.getYear());
        dto.setKeywords(report.getKeywords());
        dto.setStatus(report.getStatus().name());
        dto.setTimestamp(report.getTimestamp());
        if (report.getTimeline() != null) {
            dto.setTimelineId(report.getTimeline().getId());
            dto.setTimelineName(report.getTimeline().getName());
        }
        return dto;
    }
}
