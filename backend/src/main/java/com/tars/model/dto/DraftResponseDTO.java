package com.tars.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Includes server-generated fields: id, status, timestamp, timelineName.
 */
@Data
@AllArgsConstructor
public class DraftResponseDTO {
    private Long id;
    private String description;
    private Integer year;
    private String keywords;
    private String status;
    private LocalDateTime timestamp;
    private Long timelineId;
    private String timelineName;
}