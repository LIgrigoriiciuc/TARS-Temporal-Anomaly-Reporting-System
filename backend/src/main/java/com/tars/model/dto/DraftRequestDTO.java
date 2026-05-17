package com.tars.model.dto;

import lombok.Data;

/**
 * Request DTO — what Angular sends when creating or updating a draft.
 * No id, no status, no timestamp — those are server-side concerns.
 */
@Data
public class DraftRequestDTO {
    private String description;
    private Integer year;
    private String keywords;
    private Long timelineId;
}