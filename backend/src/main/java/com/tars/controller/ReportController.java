package com.tars.controller;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.Timeline;
import com.tars.model.dto.DraftRequestDTO;
import com.tars.model.dto.DraftResponseDTO;
import com.tars.model.mappers.ReportMapper;
import com.tars.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles all report/draft HTTP endpoints.
 * Responsibility: map request DTOs → entities, call service, map entities → response DTOs.
 * No business logic here — that belongs in ReportService.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // UC-06: Save new draft
    @PostMapping("/drafts")
    public ResponseEntity<DraftResponseDTO> saveDraft(@RequestBody DraftRequestDTO dto,
                                                      HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport draft = ReportMapper.toEntity(dto);         // map request → entity
        ObservationReport saved = reportService.saveAsDraft(draft, agent, dto.getTimelineId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportMapper.toDto(saved)); // map entity → response
    }

    // UC-07: View all drafts
    @GetMapping("/drafts")
    public ResponseEntity<List<DraftResponseDTO>> getDrafts(HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        List<DraftResponseDTO> drafts = reportService.getAgentDrafts(agent.getId())
                .stream()
                .map(ReportMapper::toDto)                              // map each entity → response
                .collect(Collectors.toList());
        return ResponseEntity.ok(drafts);
    }

    // UC-07: Resume a specific draft
    @GetMapping("/drafts/{id}")
    public ResponseEntity<DraftResponseDTO> getDraft(@PathVariable Long id,
                                                     HttpServletRequest request) {
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

    // UC-07: Update existing draft
    @PutMapping("/drafts/{id}")
    public ResponseEntity<DraftResponseDTO> updateDraft(@PathVariable Long id,
                                                        @RequestBody DraftRequestDTO dto,
                                                        HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport updated = reportService.updateDraft(
                id, agent.getId(),
                dto.getDescription(), dto.getYear(),
                dto.getKeywords(), dto.getTimelineId()
        );
        return ResponseEntity.ok(ReportMapper.toDto(updated));
    }

    // Available timelines for agent dropdown
    @GetMapping("/timelines")
    public ResponseEntity<List<Timeline>> getTimelines() {
        return ResponseEntity.ok(reportService.getAllTimelines());
    }
}