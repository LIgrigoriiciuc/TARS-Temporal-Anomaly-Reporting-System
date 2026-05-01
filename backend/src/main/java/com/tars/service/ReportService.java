package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.enums.ReportStatus;
import com.tars.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    // UC-06: Save as draft
    public ObservationReport saveAsDraft(ObservationReport draft, Agent agent) {
        // UC-06 PRE-2: at least one field must be filled
        if (draft.getDescription() == null && draft.getYear() == null && draft.getKeywords() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one field must be filled");
        }
        draft.setAgent(agent);
        draft.setStatus(ReportStatus.DRAFT);
        return reportRepository.save(draft);
    }

    // UC-07: Get all drafts for agent
    public List<ObservationReport> getAgentDrafts(Long agentId) {
        return reportRepository.findByAgentIdAndStatus(agentId, ReportStatus.DRAFT);
    }

    // UC-07: Get single draft (verifies ownership)
    public ObservationReport getDraft(Long draftId, Long agentId) {
        return reportRepository.findByIdAndAgentId(draftId, agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
    }

    // UC-07 A1: Delete draft
    public void deleteDraft(Long draftId, Long agentId) {
        ObservationReport draft = getDraft(draftId, agentId);
        reportRepository.delete(draft);
    }
}
