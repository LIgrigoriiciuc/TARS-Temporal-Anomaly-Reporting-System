package com.tars.controller;

import com.tars.model.Timeline;
import com.tars.model.User;
import com.tars.model.dto.AnomalyGraphDTO;
import com.tars.service.GraphService;
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

    /**
     * UC-09 — all anomalies for the graph.
     * Both Agent and Supervisor can call this.
     * Subscription filtering (iteration 3) will narrow results for Agents.
     */
    @GetMapping("/anomalies")
    public ResponseEntity<List<AnomalyGraphDTO>> getAnomalies(HttpServletRequest request) {
        return ResponseEntity.ok(graphService.getAllAnomalies());
    }

    /**
     * Anomalies for a specific timeline lane — used when user clicks/selects a lane.
     */
    @GetMapping("/anomalies/timeline/{timelineId}")
    public ResponseEntity<List<AnomalyGraphDTO>> getAnomaliesByTimeline(
            @PathVariable Long timelineId) {
        return ResponseEntity.ok(graphService.getAnomaliesByTimeline(timelineId));
    }

    /**
     * All timelines — frontend needs these to render Y axis lanes.
     * Replaces the old /api/reports/timelines endpoint which was Agent-only.
     */
    @GetMapping("/timelines")
    public ResponseEntity<List<Timeline>> getTimelines() {
        return ResponseEntity.ok(timelineService.getAllTimelines());
    }
}