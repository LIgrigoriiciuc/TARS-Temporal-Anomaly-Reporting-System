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

/**
 * Handles draft lifecycle — UC-06, UC-07.
 * Works only with domain objects. No DTOs here.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final TimelineRepository timelineRepository;

    // UC-06: Save new draft — at least one field must be filled
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

    // UC-07: Update existing draft — verifies ownership, applies changes, saves
    public ObservationReport updateDraft(Long draftId, Long agentId,
                                         String description, Integer year,
                                         String keywords, Long timelineId) {
        ObservationReport existing = getDraft(draftId, agentId);
        existing.setDescription(description);
        existing.setYear(year);
        existing.setKeywords(keywords);
        if (timelineId != null) {
            Timeline timeline = timelineRepository.findById(timelineId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeline not found"));
            existing.setTimeline(timeline);
        }
        return reportRepository.save(existing);
    }

    // UC-07: Get all drafts for an agent
    public List<ObservationReport> getAgentDrafts(Long agentId) {
        return reportRepository.findByAgentIdAndStatus(agentId, ReportStatus.DRAFT);
    }

    // UC-07: Get single draft — verifies ownership
    public ObservationReport getDraft(Long draftId, Long agentId) {
        return reportRepository.findByIdAndAgentId(draftId, agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
    }

    // UC-07 A1: Delete draft — verifies ownership before deleting
    public void deleteDraft(Long draftId, Long agentId) {
        ObservationReport draft = getDraft(draftId, agentId);
        reportRepository.delete(draft);
    }

    // Get all timelines — for dropdown in agent form
    public List<Timeline> getAllTimelines() {
        return timelineRepository.findAll();
    }
}