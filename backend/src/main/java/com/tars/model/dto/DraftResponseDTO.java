package com.tars.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Response DTO — what backend returns to Angular for draft data.
 * Includes server-generated fields: id, status, timestamp, timelineName.
 */
@Data
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