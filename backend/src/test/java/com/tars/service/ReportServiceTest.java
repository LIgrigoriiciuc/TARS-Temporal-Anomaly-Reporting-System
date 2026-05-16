package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.Timeline;
import com.tars.model.enums.ReportStatus;
import com.tars.repository.ReportRepository;
import com.tars.repository.TimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private TimelineRepository timelineRepository;

    @InjectMocks
    private ReportService reportService;

    private Agent agent;
    private ObservationReport draft;
    private Timeline timeline;

    @BeforeEach
    void setUp() {
        agent = new Agent();
        agent.setId(1L);

        timeline = new Timeline();
        timeline.setId(1L);
        timeline.setName("ALPHA");

        draft = new ObservationReport();
        draft.setDescription("Temporal disturbance detected");
        draft.setYear(2024);
        draft.setKeywords("cooper,anomaly");
    }

    // UC-06: Save as draft — happy path
    @Test
    void saveAsDraft_Success() {
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ObservationReport result = reportService.saveAsDraft(draft, agent, null);

        assertEquals(ReportStatus.DRAFT, result.getStatus());
        assertEquals(agent, result.getAgent());
        verify(reportRepository).save(draft);
    }

    // UC-06: Save draft with timeline
    @Test
    void saveAsDraft_WithTimeline_Success() {
        when(timelineRepository.findById(1L)).thenReturn(Optional.of(timeline));
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ObservationReport result = reportService.saveAsDraft(draft, agent, 1L);

        assertEquals(timeline, result.getTimeline());
        assertEquals(ReportStatus.DRAFT, result.getStatus());
    }

    // UC-06 PRE-2: All fields null — should throw
    @Test
    void saveAsDraft_AllFieldsNull_ThrowsBadRequest() {
        ObservationReport empty = new ObservationReport();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.saveAsDraft(empty, agent, null));

        assertEquals(400, ex.getStatusCode().value());
        verify(reportRepository, never()).save(any());
    }

    // UC-06: Timeline not found
    @Test
    void saveAsDraft_InvalidTimeline_ThrowsNotFound() {
        when(timelineRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.saveAsDraft(draft, agent, 99L));

        assertEquals(404, ex.getStatusCode().value());
    }

    // UC-07: Get drafts for agent
    @Test
    void getAgentDrafts_ReturnsDrafts() {
        draft.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByAgentIdAndStatus(1L, ReportStatus.DRAFT))
                .thenReturn(List.of(draft));

        List<ObservationReport> result = reportService.getAgentDrafts(1L);

        assertEquals(1, result.size());
        assertEquals(draft, result.get(0));
    }

    // UC-07: Get single draft — happy path
    @Test
    void getDraft_Success() {
        draft.setId(1L);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(draft));

        ObservationReport result = reportService.getDraft(1L, 1L);

        assertEquals(draft, result);
    }

    // UC-07: Draft not found or wrong owner
    @Test
    void getDraft_NotFound_ThrowsNotFound() {
        when(reportRepository.findByIdAndAgentId(99L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.getDraft(99L, 1L));

        assertEquals(404, ex.getStatusCode().value());
    }

    // UC-07 A1: Delete draft
    @Test
    void deleteDraft_Success() {
        draft.setId(1L);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(draft));
        doNothing().when(reportRepository).delete(draft);

        assertDoesNotThrow(() -> reportService.deleteDraft(1L, 1L));
        verify(reportRepository).delete(draft);
    }

    // UC-07 A1: Delete draft not owned by agent
    @Test
    void deleteDraft_NotOwned_ThrowsNotFound() {
        when(reportRepository.findByIdAndAgentId(1L, 2L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.deleteDraft(1L, 2L));

        assertEquals(404, ex.getStatusCode().value());
    }

    // Only description filled — should still save
    @Test
    void saveAsDraft_OnlyDescription_Success() {
        ObservationReport onlyDesc = new ObservationReport();
        onlyDesc.setDescription("Something happened");
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ObservationReport result = reportService.saveAsDraft(onlyDesc, agent, null);

        assertEquals(ReportStatus.DRAFT, result.getStatus());
    }
}
