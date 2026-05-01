package com.tars.model.mappers;

import com.tars.model.ObservationReport;
import com.tars.model.dto.ReportDTO;

public class ReportMapper {
    public static ReportDTO toDto(ObservationReport report) {
        if (report == null) return null;

        ReportDTO dto = new ReportDTO();
        dto.setId(report.getId());
        dto.setDescription(report.getDescription());
        dto.setYear(report.getYear());
        dto.setKeywords(report.getKeywords());
        dto.setStatus(report.getStatus().name());
        dto.setTimestamp(report.getTimestamp());
        return dto;
    }

    public static ObservationReport toEntity(ReportDTO dto) {
        if (dto == null) return null;

        ObservationReport report = new ObservationReport();
        report.setId(dto.getId());
        report.setDescription(dto.getDescription());
        report.setYear(dto.getYear());
        report.setKeywords(dto.getKeywords());
        // DRAFT by default in Service
        return report;
    }
}