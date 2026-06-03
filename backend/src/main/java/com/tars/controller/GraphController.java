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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final TimelineService timelineService;
    private final TimelineAccessService timelineAccessService;

    @GetMapping("/anomalies")
    public ResponseEntity<List<AnomalyGraphDTO>> getAnomalies(
            @RequestParam(required = false) Long timelineId,
            @RequestParam(required = false) ParadoxRisk paradoxRisk,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        // Agents: restrict to their accessible timeline IDs
        Set<Long> allowedIds = null;
        if (user instanceof Agent agent) {
            allowedIds = timelineAccessService.getAllTimelinesForAgent(agent)
                    .stream()
                    .filter(TimelineDTO::isAccessible)
                    .map(TimelineDTO::getId)
                    .collect(Collectors.toSet());
            // If filtering by a specific timeline, check access
            if (timelineId != null && !allowedIds.contains(timelineId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to this timeline");
            }
        }
        return ResponseEntity.ok(
                graphService.getGraphAnomalies(timelineId, paradoxRisk, yearFrom, yearTo, allowedIds)
        );
    }

    @GetMapping("/timelines")
    public ResponseEntity<List<TimelineDTO>> getTimelines(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user instanceof Agent agent) {
            return ResponseEntity.ok(timelineAccessService.getAllTimelinesForAgent(agent));
        }
        // Supervisor: all timelines accessible
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