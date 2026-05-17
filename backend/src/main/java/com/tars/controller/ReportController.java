package com.tars.controller;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.Timeline;
import com.tars.model.dto.ReportDTO;
import com.tars.model.mappers.ReportMapper;
import com.tars.repository.TimelineRepository;
import com.tars.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final TimelineRepository timelineRepository;

    // UC-06: Save as draft
    @PostMapping("/drafts")
    public ResponseEntity<ReportDTO> saveDraft(@RequestBody ReportDTO dto, HttpServletRequest request) {
        // Get authenticated agent from request attribute set by JwtFilter
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport draft = ReportMapper.toEntity(dto);
        ObservationReport saved = reportService.saveAsDraft(draft, agent, dto.getTimelineId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportMapper.toDto(saved));
    }

    // UC-07: View all drafts
    @GetMapping("/drafts")
    public ResponseEntity<List<ReportDTO>> getDrafts(HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        List<ReportDTO> drafts = reportService.getAgentDrafts(agent.getId())
                .stream()
                .map(ReportMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(drafts);
    }

    // UC-07: Resume a specific draft
    @GetMapping("/drafts/{id}")
    public ResponseEntity<ReportDTO> getDraft(@PathVariable Long id, HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport draft = reportService.getDraft(id, agent.getId());
        return ResponseEntity.ok(ReportMapper.toDto(draft));
    }

    // UC-07 A1: Delete draft
    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<Void> deleteDraft(@PathVariable Long id, HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        reportService.deleteDraft(id, agent.getId());
        return ResponseEntity.noContent().build();
    }

    // UC-07 update draft
    @PutMapping("/drafts/{id}")
    public ResponseEntity<ReportDTO> updateDraft(@PathVariable Long id,
                                                 @RequestBody ReportDTO dto,
                                                 HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport existing = reportService.getDraft(id, agent.getId());
        existing.setDescription(dto.getDescription());
        existing.setYear(dto.getYear());
        existing.setKeywords(dto.getKeywords());
        if (dto.getTimelineId() != null) {
            Timeline timeline = timelineRepository.findById(dto.getTimelineId())
                    .orElseThrow();
            existing.setTimeline(timeline);
        }
        ObservationReport saved = reportService.saveAsDraft(existing, agent, dto.getTimelineId());
        return ResponseEntity.ok(ReportMapper.toDto(saved));
    }

    // Get all available timelines for dropdown
    @GetMapping("/timelines")
    public ResponseEntity<List<Timeline>> getTimelines() {
        return ResponseEntity.ok(timelineRepository.findAll());
    }
}