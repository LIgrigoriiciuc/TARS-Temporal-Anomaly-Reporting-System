package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.ObservationReport;
import com.tars.model.Timeline;
import com.tars.model.enums.ReportStatus;
import com.tars.repository.ReportRepository;
import com.tars.repository.AnomalyAnalysisRepository;
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

    @Mock
    private AnomalyAnalysisRepository analysisRepository;

    @Mock
    private OpenAIService openAIService;

    @InjectMocks
    private ReportService reportService;

    private Agent agent;
    private ObservationReport draft;
    private Timeline timeline;

    @BeforeEach
    void setUp() {
        agent = new Agent();
        org.springframework.test.util.ReflectionTestUtils.setField(agent, "id", 1L);

        timeline = new Timeline();
        timeline.setId(1L);
        timeline.setName("ALPHA");

        draft = new ObservationReport();
        draft.setDescription("Temporal disturbance detected");
        draft.setYear(2024);
        draft.setKeywords("cooper,anomaly");
    }

    @Test
    void saveAsDraft_Success() {
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.saveAsDraft(draft, agent, null);
        assertEquals(ReportStatus.DRAFT, result.getStatus());
        assertEquals(agent, result.getAgent());
        verify(reportRepository).save(draft);
    }

    @Test
    void saveAsDraft_WithTimeline_Success() {
        when(timelineRepository.findById(1L)).thenReturn(Optional.of(timeline));
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.saveAsDraft(draft, agent, 1L);
        assertEquals(timeline, result.getTimeline());
        assertEquals(ReportStatus.DRAFT, result.getStatus());
    }

    @Test
    void saveAsDraft_AllFieldsNull_ThrowsBadRequest() {
        ObservationReport empty = new ObservationReport();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.saveAsDraft(empty, agent, null));
        assertEquals(400, ex.getStatusCode().value());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void saveAsDraft_InvalidTimeline_ThrowsNotFound() {
        when(timelineRepository.findById(99L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.saveAsDraft(draft, agent, 99L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getAgentDrafts_ReturnsDrafts() {
        draft.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByAgentIdAndStatus(1L, ReportStatus.DRAFT))
                .thenReturn(List.of(draft));
        List<ObservationReport> result = reportService.getAgentDrafts(1L);
        assertEquals(1, result.size());
        assertEquals(draft, result.get(0));
    }

    @Test
    void getDraft_Success() {
        org.springframework.test.util.ReflectionTestUtils.setField(draft, "id", 1L);
        draft.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(draft));
        ObservationReport result = reportService.getDraft(1L, 1L);
        assertEquals(draft, result);
    }

    @Test
    void getDraft_NotFound_ThrowsNotFound() {
        when(reportRepository.findByIdAndAgentId(99L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.getDraft(99L, 1L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void deleteDraft_Success() {
        org.springframework.test.util.ReflectionTestUtils.setField(draft, "id", 1L);
        draft.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(draft));
        doNothing().when(reportRepository).delete(draft);
        assertDoesNotThrow(() -> reportService.deleteDraft(1L, 1L));
        verify(reportRepository).delete(draft);
    }

    @Test
    void deleteDraft_NotOwned_ThrowsNotFound() {
        when(reportRepository.findByIdAndAgentId(1L, 2L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.deleteDraft(1L, 2L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void saveAsDraft_OnlyDescription_Success() {
        ObservationReport onlyDesc = new ObservationReport();
        onlyDesc.setDescription("Something happened");
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.saveAsDraft(onlyDesc, agent, null);
        assertEquals(ReportStatus.DRAFT, result.getStatus());
    }

    @Test
    void updateDraft_Success_NoTimeline() {
        ObservationReport existing = new ObservationReport();
        existing.setId(1L);
        existing.setDescription("old description");
        existing.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.updateDraft(1L, 1L, "new description", 2025, "keyword1", null);
        assertEquals("new description", result.getDescription());
        assertEquals(2025, result.getYear());
        assertEquals("keyword1", result.getKeywords());
        assertNull(result.getTimeline());
        verify(reportRepository).save(existing);
    }

    @Test
    void updateDraft_Success_WithTimeline() {
        ObservationReport existing = new ObservationReport();
        existing.setId(1L);
        existing.setStatus(ReportStatus.DRAFT);
        Timeline timeline = new Timeline();
        timeline.setId(1L);
        timeline.setName("ALPHA");
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(existing));
        when(timelineRepository.findById(1L)).thenReturn(Optional.of(timeline));
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.updateDraft(1L, 1L, "description", 2024, "keyword", 1L);
        assertEquals(timeline, result.getTimeline());
        verify(timelineRepository).findById(1L);
    }

    @Test
    void updateDraft_AllFieldsNull_ThrowsBadRequest() {
        ObservationReport existing = new ObservationReport();
        existing.setId(1L);
        existing.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(existing));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.updateDraft(1L, 1L, null, null, null, null));
        assertEquals(400, ex.getStatusCode().value());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void updateDraft_NotFound_ThrowsNotFound() {
        when(reportRepository.findByIdAndAgentId(99L, 1L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.updateDraft(99L, 1L, "description", 2024, "keyword", null));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void updateDraft_InvalidTimeline_ThrowsNotFound() {
        ObservationReport existing = new ObservationReport();
        existing.setId(1L);
        existing.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(existing));
        when(timelineRepository.findById(99L)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportService.updateDraft(1L, 1L, "description", 2024, "keyword", 99L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void saveAsDraft_OnlyYear_Success() {
        ObservationReport onlyYear = new ObservationReport();
        onlyYear.setYear(2999);
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.saveAsDraft(onlyYear, agent, null);
        assertNotNull(result);
        assertEquals(ReportStatus.DRAFT, result.getStatus());
        assertEquals(2999, result.getYear());
    }

    @Test
    void saveAsDraft_OnlyKeywords_Success() {
        ObservationReport onlyKeywords = new ObservationReport();
        onlyKeywords.setKeywords("tars,wormhole");
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.saveAsDraft(onlyKeywords, agent, null);
        assertNotNull(result);
        assertEquals(ReportStatus.DRAFT, result.getStatus());
        assertEquals("tars,wormhole", result.getKeywords());
    }

    @Test
    void updateDraft_OnlyYear_Success() {
        ObservationReport existing = new ObservationReport();
        existing.setId(1L);
        existing.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.updateDraft(1L, 1L, null, 3025, null, null);
        assertNull(result.getDescription());
        assertEquals(3025, result.getYear());
        assertNull(result.getKeywords());
    }

    @Test
    void updateDraft_OnlyKeywords_Success() {
        ObservationReport existing = new ObservationReport();
        existing.setId(1L);
        existing.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByIdAndAgentId(1L, 1L)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ObservationReport result = reportService.updateDraft(1L, 1L, null, null, "singularity", null);
        assertNull(result.getDescription());
        assertNull(result.getYear());
        assertEquals("singularity", result.getKeywords());
    }

    @Test
    void getAllTimelines_ReturnsTimelineList() {
        Timeline t1 = new Timeline(); t1.setId(1L); t1.setName("ALPHA_TIMELINE");
        Timeline t2 = new Timeline(); t2.setId(2L); t2.setName("BETA_TIMELINE");
        when(timelineRepository.findAll()).thenReturn(List.of(t1, t2));
        List<Timeline> result = reportService.getAllTimelines();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("ALPHA_TIMELINE", result.get(0).getName());
        assertEquals("BETA_TIMELINE", result.get(1).getName());
        verify(timelineRepository, times(1)).findAll();
    }
}