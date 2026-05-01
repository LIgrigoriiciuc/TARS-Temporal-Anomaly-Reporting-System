package com.tars.service;

import com.tars.model.ObservationReport;
import com.tars.model.enums.ReportStatus;
import com.tars.repository.ReportRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;

    public ObservationReport saveAsDraft(ObservationReport draft, Agent agent) {
        if (draft.getDescription() == null && draft.getYear() == null && draft.getKeywords() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trebuie sa completezi macar un camp!");
        }
        draft.setAgent(agent);
        draft.setStatus(ReportStatus.DRAFT);
        return reportRepository.save(draft);
    }

    public List<ObservationReport> getAgentDrafts(Long agentId) {
        return reportRepository.findByAgentIdAndStatus(agentId, ReportStatus.DRAFT);
    }
}
