package com.tars.model.dto;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DraftRequestDTO {
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;
    private Integer year;
    private String keywords;
    private Long timelineId;
}