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

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/drafts")
    public ResponseEntity<DraftResponseDTO> saveDraft(@RequestBody DraftRequestDTO dto,
                                                      HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport draft = ReportMapper.toEntity(dto);
        ObservationReport saved = reportService.saveAsDraft(draft, agent, dto.getTimelineId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportMapper.toDto(saved));
    }

    @GetMapping("/drafts")
    public ResponseEntity<List<DraftResponseDTO>> getDrafts(HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        List<DraftResponseDTO> drafts = reportService.getAgentDrafts(agent.getId())
                .stream()
                .map(ReportMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(drafts);
    }
    //resume a draft
    @GetMapping("/drafts/{id}")
    public ResponseEntity<DraftResponseDTO> getDraft(@PathVariable Long id, HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport draft = reportService.getDraft(id, agent.getId());
        return ResponseEntity.ok(ReportMapper.toDto(draft));
    }

    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<Void> deleteDraft(@PathVariable Long id, HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        reportService.deleteDraft(id, agent.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/drafts/{id}")
    public ResponseEntity<DraftResponseDTO> updateDraft(@PathVariable Long id, @RequestBody DraftRequestDTO dto,
                                                        HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport updated = reportService.updateDraft(
                id, agent.getId(),
                dto.getDescription(), dto.getYear(),
                dto.getKeywords(), dto.getTimelineId()
        );
        return ResponseEntity.ok(ReportMapper.toDto(updated));
    }

    @GetMapping("/timelines")
    public ResponseEntity<List<Timeline>> getTimelines() {
        return ResponseEntity.ok(reportService.getAllTimelines());
    }
}