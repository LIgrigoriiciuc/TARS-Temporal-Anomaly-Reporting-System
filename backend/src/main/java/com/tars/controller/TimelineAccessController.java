package com.tars.controller;

import com.tars.model.Agent;
import com.tars.model.dto.TimelineDTO;
import com.tars.service.TimelineAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports/subscription/timelines")
@RequiredArgsConstructor
public class TimelineAccessController {

    private final TimelineAccessService timelineAccessService;

    @GetMapping
    public ResponseEntity<List<TimelineDTO>> getTimelines(HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        return ResponseEntity.ok(timelineAccessService.getAllTimelinesForAgent(agent));
    }

    /**
     * Agent picks a timeline to add to their plan.
     * Blocked if plan limit reached.
     */
    @PostMapping("/{timelineId}")
    public ResponseEntity<TimelineDTO> addTimeline(@PathVariable Long timelineId,
                                                   HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        return ResponseEntity.ok(timelineAccessService.addTimeline(agent, timelineId));
    }


}