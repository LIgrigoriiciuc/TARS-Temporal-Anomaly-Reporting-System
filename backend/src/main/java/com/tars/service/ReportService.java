package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.ReportSubmittedEvent;
import com.tars.model.Timeline;
import com.tars.model.enums.ReportStatus;
import com.tars.repository.AnomalyAnalysisRepository;
import com.tars.repository.ReportRepository;
import com.tars.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final TimelineRepository timelineRepository;
    private final SubscriptionService subscriptionService;
    private final TimelineAccessService timelineAccessService;
    private final ApplicationEventPublisher eventPublisher;
    @Transactional
    public ObservationReport submitReport(ObservationReport report, Agent agent, Long timelineId) {
        if (report.getDescription() == null || report.getDescription().isBlank() ||
                report.getYear() == null ||
                report.getKeywords() == null || report.getKeywords().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All fields are required to submit a report");
        }
        Timeline timeline = timelineRepository.findById(timelineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeline not found"));
        subscriptionService.enforceReportLimit(agent);
        timelineAccessService.enforceTimelineAccess(agent, timelineId);

        report.setAgent(agent);
        report.setTimeline(timeline);
        report.setStatus(ReportStatus.PENDING_ANALYSIS);

        ObservationReport saved = reportRepository.save(report);
        agent.setMonthlyReportCount(agent.getMonthlyReportCount() + 1);

        // Fire AFTER_COMMIT so OPENAI thread won't start until report is in DB
        eventPublisher.publishEvent(new ReportSubmittedEvent(saved.getId(), agent));

        return saved;
    }

    /**
     * If agent is resuming a draft and submitting it — promotes draft to submission.
     */
    @Transactional
    public ObservationReport submitFromDraft(Long draftId, Long agentId, String description,
                                             Integer year, String keywords, Long timelineId) {
        ObservationReport draft = getDraft(draftId, agentId);

        Timeline timeline = timelineRepository.findById(timelineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeline not found"));

        draft.setDescription(description);
        draft.setYear(year);
        draft.setKeywords(keywords);
        draft.setTimeline(timeline);
        draft.setStatus(ReportStatus.PENDING_ANALYSIS);

        ObservationReport saved = reportRepository.save(draft);

        // Fire AFTER_COMMIT OPENAI thread won't start until report is in DB
        eventPublisher.publishEvent(new ReportSubmittedEvent(saved.getId(), draft.getAgent()));

        return saved;
    }

    public List<ObservationReport> getAgentDrafts(Long agentId) {
        return reportRepository.findByAgentIdAndStatusOrderByIdDesc(agentId, ReportStatus.DRAFT);
    }

    public List<ObservationReport> getAgentSubmittedReports(Long agentId) {
        return reportRepository.findByAgentIdAndStatusInOrderByIdDesc(
                agentId,
                List.of(ReportStatus.PENDING_ANALYSIS, ReportStatus.CONFIRMED, ReportStatus.REJECTED, ReportStatus.FLAGGED)
        );
    }

    public ObservationReport getAgentReport(Long reportId, Long agentId) {
        return reportRepository.findByIdAndAgentId(reportId, agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    @Transactional
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

    @Transactional
    public ObservationReport updateDraft(Long draftId, Long agentId, String description,
                                         Integer year, String keywords, Long timelineId) {
        ObservationReport existing = getDraft(draftId, agentId);
        if (description == null && year == null && keywords == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one field must be filled");
        }
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

    @Transactional
    public void deleteDraft(Long draftId, Long agentId) {
        ObservationReport draft = getDraft(draftId, agentId);
        reportRepository.delete(draft);
    }

    public ObservationReport getDraft(Long draftId, Long agentId) {
        ObservationReport report = reportRepository.findByIdAndAgentId(draftId, agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        if (report.getStatus() != ReportStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report is not a draft");
        }
        return report;
    }

    public List<Timeline> getAllTimelines() {
        return timelineRepository.findAll();
    }
}