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
        DraftResponseDTO dto = new DraftResponseDTO(report.getId(), report.getDescription(), report.getYear(),
                report.getKeywords(), report.getStatus().name(), report.getTimestamp(),
                report.getTimeline() != null ? report.getTimeline().getId() : null,
                report.getTimeline() != null ? report.getTimeline().getName() : null);
        if (report.getTimeline() != null) {
            dto.setTimelineId(report.getTimeline().getId());
            dto.setTimelineName(report.getTimeline().getName());
        }
        return dto;
    }
}
