package com.tars.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportDTO {
    private Long id;

    @Size(max = 2000, message = "Description too long")
    private String description;

    private Integer year;
    private String keywords;
    private String status;
    private LocalDateTime timestamp;
    private Long timelineId;
    private String timelineName;
}
