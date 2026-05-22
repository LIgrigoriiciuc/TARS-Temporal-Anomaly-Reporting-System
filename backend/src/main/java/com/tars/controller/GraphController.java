package com.tars.controller;

import com.tars.model.Agent;
import com.tars.model.User;
import com.tars.model.dto.AnomalyGraphDTO;
import com.tars.model.dto.TimelineDTO;
import com.tars.model.enums.ParadoxRisk;
import com.tars.service.GraphService;
import com.tars.service.TimelineAccessService;
import com.tars.service.TimelineService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final TimelineService timelineService;
    private final TimelineAccessService timelineAccessService;

    /**
     * UC-09 / UC-10 — graph anomalies, verified only, all filters optional.
     *
     * GET /api/graph/anomalies
     * GET /api/graph/anomalies?timelineId=1
     * GET /api/graph/anomalies?paradoxRisk=CRITICAL
     * GET /api/graph/anomalies?yearFrom=2000&yearTo=2100
     * GET /api/graph/anomalies?timelineId=1&paradoxRisk=HIGH&yearFrom=2040&yearTo=2060
     */
    @GetMapping("/anomalies")
    public ResponseEntity<List<AnomalyGraphDTO>> getAnomalies(
            @RequestParam(required = false) Long timelineId,
            @RequestParam(required = false) ParadoxRisk paradoxRisk,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo) {
        return ResponseEntity.ok(
                graphService.getGraphAnomalies(timelineId, paradoxRisk, yearFrom, yearTo)
        );
    }

    /**
     * All timelines with accessible flag.
     * Agents see lock icon on inaccessible lanes.
     * Supervisors see all as accessible.
     */
    @GetMapping("/timelines")
    public ResponseEntity<List<TimelineDTO>> getTimelines(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user instanceof Agent agent) {
            return ResponseEntity.ok(timelineAccessService.getAllTimelinesForAgent(agent));
        }
        // Supervisor — all timelines accessible
        return ResponseEntity.ok(timelineService.getAllTimelines().stream()
                .map(t -> TimelineDTO.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .description(t.getDescription())
                        .accessible(true)
                        .build())
                .toList());
    }
}