package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.Timeline;
import com.tars.model.enums.ReportStatus;
import com.tars.repository.ReportRepository;
import com.tars.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final TimelineRepository timelineRepository;

    public ObservationReport saveAsDraft(ObservationReport draft, Agent agent, Long timelineId) {
        if (draft.getDescription() == null && draft.getYear() == null && draft.getKeywords() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one field must be filled");
        }
        if (timelineId != null) {
            Timeline timeline = timelineRepository.findById(timelineId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeline not found"));
            draft.setTimeline(timeline);
        }
        draft.setAgent(agent);
        draft.setStatus(ReportStatus.DRAFT);
        return reportRepository.save(draft);
    }

    public List<ObservationReport> getAgentDrafts(Long agentId) {
        return reportRepository.findByAgentIdAndStatus(agentId, ReportStatus.DRAFT);
    }

    public ObservationReport getDraft(Long draftId, Long agentId) {
        return reportRepository.findByIdAndAgentId(draftId, agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
    }

    public void deleteDraft(Long draftId, Long agentId) {
        ObservationReport draft = getDraft(draftId, agentId);
        reportRepository.delete(draft);
    }
}