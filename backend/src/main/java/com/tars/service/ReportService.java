package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.Timeline;
import com.tars.model.enums.ReportStatus;
import com.tars.repository.AnomalyAnalysisRepository;
import com.tars.repository.ReportRepository;
import com.tars.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tars.model.enums.ReportStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final TimelineRepository timelineRepository;
    private final AnomalyAnalysisRepository analysisRepository;
    private final GeminiService geminiService;
    private final SubscriptionService subscriptionService;

    // -------------------------------------------------------------------------
    // UC-05 Submit Report
    // -------------------------------------------------------------------------

    /**
     * Saves the report as PENDING_ANALYSIS, commits, then fires async Gemini analysis.
     * The @Transactional here ensures the report is fully committed to DB
     * before GeminiService (running in another thread) tries to load it.
     */
    @Transactional
    public ObservationReport submitReport(ObservationReport report, Agent agent, Long timelineId) {
        Timeline timeline = timelineRepository.findById(timelineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeline not found"));

        // Plan enforcement — check monthly report limit
        subscriptionService.enforceReportLimit(agent);

        // Duplicate check — same agent, same timeline, same year
        List<ObservationReport> duplicates = reportRepository.findDuplicateReport(
                agent.getId(), timelineId, report.getYear()
        );
        if (!duplicates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an active report for this timeline and year");
        }

        report.setAgent(agent);
        report.setTimeline(timeline);
        report.setStatus(ReportStatus.PENDING_ANALYSIS);

        ObservationReport saved = reportRepository.save(report);

        // Increment monthly report count
        agent.setMonthlyReportCount(agent.getMonthlyReportCount() + 1);

        // Fire and forget — agent gets HTTP response immediately
        // GeminiService.analyzeReport() runs in its own thread + transaction
        geminiService.analyzeReport(saved.getId());

        return saved;
    }

    /**
     * If agent is resuming a draft and submitting it — promotes draft to submission.
     * Deletes nothing; status change IS the promotion.
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
        geminiService.analyzeReport(saved.getId());

        return saved;
    }

    // -------------------------------------------------------------------------
    // UC-07 Agent report lists
    // -------------------------------------------------------------------------

    public List<ObservationReport> getAgentDrafts(Long agentId) {
        return reportRepository.findByAgentIdAndStatus(agentId, ReportStatus.DRAFT);
    }

    /**
     * All submitted reports — pending, confirmed, rejected.
     * Excludes drafts.
     */
    public List<ObservationReport> getAgentSubmittedReports(Long agentId) {
        return reportRepository.findByAgentIdAndStatusIn(
                agentId,
                List.of(ReportStatus.PENDING_ANALYSIS, ReportStatus.CONFIRMED, ReportStatus.REJECTED)
        );
    }

    /**
     * Single report — used for polling analysis result.
     * Agent can only access their own reports.
     */
    public ObservationReport getAgentReport(Long reportId, Long agentId) {
        return reportRepository.findByIdAndAgentId(reportId, agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    // -------------------------------------------------------------------------
    // UC-06 Draft management (unchanged from iteration 1)
    // -------------------------------------------------------------------------

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