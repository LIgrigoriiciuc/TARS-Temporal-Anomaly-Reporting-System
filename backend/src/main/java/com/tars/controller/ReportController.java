package com.tars.controller;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.dto.ReportDTO;
import com.tars.model.mappers.ReportMapper;
import com.tars.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // UC-06: Save as draft
    @PostMapping("/drafts")
    public ResponseEntity<ReportDTO> saveDraft(@RequestBody ReportDTO dto, Authentication authentication) {
        Agent agent = (Agent) authentication.getPrincipal();
        ObservationReport draft = ReportMapper.toEntity(dto);
        ObservationReport saved = reportService.saveAsDraft(draft, agent);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportMapper.toDto(saved));
    }

    // UC-07: View all drafts
    @GetMapping("/drafts")
    public ResponseEntity<List<ReportDTO>> getDrafts(Authentication authentication) {
        Agent agent = (Agent) authentication.getPrincipal();
        List<ReportDTO> drafts = reportService.getAgentDrafts(agent.getId())
                .stream()
                .map(ReportMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(drafts);
    }

    // UC-07: Resume a specific draft
    @GetMapping("/drafts/{id}")
    public ResponseEntity<ReportDTO> getDraft(@PathVariable Long id, Authentication authentication) {
        Agent agent = (Agent) authentication.getPrincipal();
        ObservationReport draft = reportService.getDraft(id, agent.getId());
        return ResponseEntity.ok(ReportMapper.toDto(draft));
    }

    // UC-07 A1: Delete draft
    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<Void> deleteDraft(@PathVariable Long id, Authentication authentication) {
        Agent agent = (Agent) authentication.getPrincipal();
        reportService.deleteDraft(id, agent.getId());
        return ResponseEntity.noContent().build();
    }
}
