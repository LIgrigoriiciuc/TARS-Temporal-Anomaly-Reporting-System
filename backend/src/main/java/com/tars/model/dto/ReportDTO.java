package com.tars.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportDTO {
    private Long id;
    private String description;
    private Integer year;
    private String keywords;
    private String status;
    private LocalDateTime timestamp;
}
