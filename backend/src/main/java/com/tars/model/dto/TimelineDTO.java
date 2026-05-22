package com.tars.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineDTO {
    private Long id;
    private String name;
    private String description;
    // true = agent has access, false = locked (shown with lock icon on frontend)
    private boolean accessible;
}