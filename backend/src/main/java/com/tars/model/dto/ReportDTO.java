package com.tars.model.dto;

@Data
public class ReportDTO {
    private Long id; // null for new draft
    private String description;
    private Integer year;
    private String keywords;
    // no Agent here, but from SecurityContext on backend
}
