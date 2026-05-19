package com.tars.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubmitReportRequestDTO {

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @NotNull(message = "Year is required")
    private Integer year;

    @NotNull(message = "Timeline is required")
    private Long timelineId;

    // Optional — agent may not have keywords yet
    private String keywords;
}
