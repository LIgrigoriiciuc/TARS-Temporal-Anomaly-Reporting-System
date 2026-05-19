package com.tars.controller;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.Timeline;
import com.tars.model.dto.*;
import com.tars.model.mappers.ReportMapper;
import com.tars.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    // -------------------------------------------------------------------------
    // UC-05 Submit new report
    // -------------------------------------------------------------------------

    @PostMapping("/submit")
    public ResponseEntity<SubmittedReportResponseDTO> submitReport(
            @Valid @RequestBody SubmitReportRequestDTO dto,
            HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport report = ReportMapper.fromSubmitDto(dto);
        ObservationReport saved = reportService.submitReport(report, agent, dto.getTimelineId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportMapper.toSubmittedDto(saved));
    }

    /**
     * UC-07 — agent resumes a draft and submits it directly.
     * PUT because we're promoting an existing resource (draft → submission).
     */
    @PutMapping("/drafts/{id}/submit")
    public ResponseEntity<SubmittedReportResponseDTO> submitFromDraft(
            @PathVariable Long id,
            @Valid @RequestBody SubmitReportRequestDTO dto,
            HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport submitted = reportService.submitFromDraft(
                id, agent.getId(),
                dto.getDescription(), dto.getYear(),
                dto.getKeywords(), dto.getTimelineId()
        );
        return ResponseEntity.ok(ReportMapper.toSubmittedDto(submitted));
    }

    // -------------------------------------------------------------------------
    // UC-07 Agent report lists
    // -------------------------------------------------------------------------

    /**
     * All submitted reports (pending, confirmed, rejected) — not drafts.
     * Agent polls this list or the single-report endpoint to check analysis status.
     */
    @GetMapping("/submitted")
    public ResponseEntity<List<SubmittedReportResponseDTO>> getSubmittedReports(HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        List<SubmittedReportResponseDTO> reports = reportService.getAgentSubmittedReports(agent.getId())
                .stream()
                .map(ReportMapper::toSubmittedDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(reports);
    }

    /**
     * Single report — used for polling analysis result after submission.
     * Returns the full report with embedded AnalysisResultDTO (null while pending).
     */
    @GetMapping("/submitted/{id}")
    public ResponseEntity<SubmittedReportResponseDTO> getSubmittedReport(
            @PathVariable Long id,
            HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport report = reportService.getAgentReport(id, agent.getId());
        return ResponseEntity.ok(ReportMapper.toSubmittedDto(report));
    }

    // -------------------------------------------------------------------------
    // UC-06 / UC-07 Draft management
    // -------------------------------------------------------------------------

    @PostMapping("/drafts")
    public ResponseEntity<DraftResponseDTO> saveDraft(
            @RequestBody DraftRequestDTO dto,
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

    @GetMapping("/drafts/{id}")
    public ResponseEntity<DraftResponseDTO> getDraft(
            @PathVariable Long id,
            HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        ObservationReport draft = reportService.getDraft(id, agent.getId());
        return ResponseEntity.ok(ReportMapper.toDto(draft));
    }

    @PutMapping("/drafts/{id}")
    public ResponseEntity<DraftResponseDTO> updateDraft(
            @PathVariable Long id,
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

    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable Long id,
            HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        reportService.deleteDraft(id, agent.getId());
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Shared
    // -------------------------------------------------------------------------

    @GetMapping("/timelines")
    public ResponseEntity<List<Timeline>> getTimelines() {
        return ResponseEntity.ok(reportService.getAllTimelines());
    }
}