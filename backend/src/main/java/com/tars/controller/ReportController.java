package com.tars.controller;

import com.tars.service.ReportService;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('AGENT')")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @PostMapping("/drafts")
    public ResponseEntity<ReportDTO> saveDraft(@RequestBody ReportDTO dto, @AuthenticationPrincipal User agent) {
        ObservationReport report = ReportMapper.toEntity(dto);
        ObservationReport saved = reportService.saveAsDraft(report, (Agent) agent);
        return ResponseEntity.ok(ReportMapper.toDto(saved));
    }

    @GetMapping("/drafts")
    public ResponseEntity<List<ReportDTO>> getMyDrafts(@AuthenticationPrincipal User agent) {
        List<ObservationReport> drafts = reportService.getAgentDrafts(agent.getId());
        return ResponseEntity.ok(drafts.stream().map(ReportMapper::toDto).toList());
    }
}
