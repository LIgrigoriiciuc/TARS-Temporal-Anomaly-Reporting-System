package com.tars.model.dto;

import jakarta.validation.constraints.Max;
import lombok.Data;

/**
 * Request DTO — what Angular sends when creating or updating a draft.
 * No id, no status, no timestamp — those are server-side concerns.
 */
@Data
public class DraftRequestDTO {
    @Max(1000)
    private String description;
    private Integer year;
    private String keywords;
    private Long timelineId;
}