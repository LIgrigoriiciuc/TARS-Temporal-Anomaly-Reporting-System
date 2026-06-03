package com.tars.service;

import com.tars.model.Anomaly;
import com.tars.model.Timeline;
import com.tars.model.dto.AnomalyGraphDTO;
import com.tars.model.enums.AnomalyType;
import com.tars.model.enums.ParadoxRisk;
import com.tars.repository.AnomalyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock AnomalyRepository anomalyRepository;
    @InjectMocks GraphService graphService;

    private Timeline alpha;
    private Timeline beta;
    private Anomaly anomaly1;
    private Anomaly anomaly2;

    @BeforeEach
    void setUp() {
        alpha = new Timeline();
        ReflectionTestUtils.setField(alpha, "id", 1L);
        alpha.setName("ALPHA");

        beta = new Timeline();
        ReflectionTestUtils.setField(beta, "id", 2L);
        beta.setName("BETA");

        anomaly1 = Anomaly.builder()
                .type(AnomalyType.PAR)
                .paradoxRisk(ParadoxRisk.CRITICAL)
                .timeline(alpha)
                .year(2045)
                .verified(true)
                .build();
        ReflectionTestUtils.setField(anomaly1, "id", 1L);

        anomaly2 = Anomaly.builder()
                .type(AnomalyType.LOP)
                .paradoxRisk(ParadoxRisk.HIGH)
                .timeline(beta)
                .year(2060)
                .verified(true)
                .build();
        ReflectionTestUtils.setField(anomaly2, "id", 2L);
    }

    // -------------------------------------------------------------------------
    // getGraphAnomalies — no filters
    // -------------------------------------------------------------------------

    @Test
    void getGraphAnomalies_noFilters_returnsAll() {
        when(anomalyRepository.findForGraph(null, null, null, null))
                .thenReturn(List.of(anomaly1, anomaly2));

        List<AnomalyGraphDTO> result = graphService.getGraphAnomalies(null, null, null, null, null);

        assertThat(result).hasSize(2);
        verify(anomalyRepository).findForGraph(null, null, null, null);
    }

    @Test
    void getGraphAnomalies_noFilters_emptyRepo_returnsEmptyList() {
        when(anomalyRepository.findForGraph(null, null, null, null))
                .thenReturn(List.of());

        assertThat(graphService.getGraphAnomalies(null, null, null, null, null)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Filters passed through to repository correctly
    // -------------------------------------------------------------------------

    @Test
    void getGraphAnomalies_timelineFilter_passedToRepo() {
        when(anomalyRepository.findForGraph(1L, null, null, null))
                .thenReturn(List.of(anomaly1));

        List<AnomalyGraphDTO> result = graphService.getGraphAnomalies(1L, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTimelineId()).isEqualTo(1L);
        verify(anomalyRepository).findForGraph(1L, null, null, null);
    }

    @Test
    void getGraphAnomalies_paradoxRiskFilter_passedToRepo() {
        when(anomalyRepository.findForGraph(null, ParadoxRisk.CRITICAL, null, null))
                .thenReturn(List.of(anomaly1));

        List<AnomalyGraphDTO> result = graphService.getGraphAnomalies(null, ParadoxRisk.CRITICAL, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParadoxRisk()).isEqualTo(ParadoxRisk.CRITICAL);
    }

    @Test
    void getGraphAnomalies_yearRangeFilter_passedToRepo() {
        when(anomalyRepository.findForGraph(null, null, 2040, 2050))
                .thenReturn(List.of(anomaly1));

        List<AnomalyGraphDTO> result = graphService.getGraphAnomalies(null, null, 2040, 2050, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getYear()).isEqualTo(2045);
    }

    @Test
    void getGraphAnomalies_allFilters_passedToRepo() {
        when(anomalyRepository.findForGraph(1L, ParadoxRisk.CRITICAL, 2040, 2050))
                .thenReturn(List.of(anomaly1));

        List<AnomalyGraphDTO> result = graphService.getGraphAnomalies(1L, ParadoxRisk.CRITICAL, 2040, 2050, null);

        assertThat(result).hasSize(1);
        verify(anomalyRepository).findForGraph(1L, ParadoxRisk.CRITICAL, 2040, 2050);
    }

    // -------------------------------------------------------------------------
    // DTO mapping
    // -------------------------------------------------------------------------

    @Test
    void getGraphAnomalies_mapsFieldsCorrectly() {
        when(anomalyRepository.findForGraph(null, null, null, null))
                .thenReturn(List.of(anomaly1));

        AnomalyGraphDTO dto = graphService.getGraphAnomalies(null, null, null, null, null).get(0);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getType()).isEqualTo(AnomalyType.PAR);
        assertThat(dto.getParadoxRisk()).isEqualTo(ParadoxRisk.CRITICAL);
        assertThat(dto.getYear()).isEqualTo(2045);
        assertThat(dto.getTimelineId()).isEqualTo(1L);
        assertThat(dto.getTimelineName()).isEqualTo("ALPHA");
        assertThat(dto.isVerified()).isTrue();
    }

    @Test
    void getGraphAnomalies_nullTimeline_doesNotThrow() {
        Anomaly noTimeline = Anomaly.builder()
                .type(AnomalyType.DEV)
                .paradoxRisk(ParadoxRisk.MEDIUM)
                .year(2030)
                .verified(true)
                .build();
        ReflectionTestUtils.setField(noTimeline, "id", 3L);

        when(anomalyRepository.findForGraph(null, null, null, null))
                .thenReturn(List.of(noTimeline));

        AnomalyGraphDTO dto = graphService.getGraphAnomalies(null, null, null, null, null).get(0);

        assertThat(dto.getTimelineId()).isNull();
        assertThat(dto.getTimelineName()).isNull();
    }

    // -------------------------------------------------------------------------
    // allowedTimelineIds filter
    // -------------------------------------------------------------------------

    @Test
    void getGraphAnomalies_allowedTimelineIds_filtersOutNonMatching() {
        when(anomalyRepository.findForGraph(null, null, null, null))
                .thenReturn(List.of(anomaly1, anomaly2));

        // Only timeline 1 (ALPHA) is allowed
        List<AnomalyGraphDTO> result = graphService.getGraphAnomalies(null, null, null, null, Set.of(1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTimelineId()).isEqualTo(1L);
    }

    @Test
    void getGraphAnomalies_allowedTimelineIds_emptySet_returnsNothing() {
        when(anomalyRepository.findForGraph(null, null, null, null))
                .thenReturn(List.of(anomaly1, anomaly2));

        List<AnomalyGraphDTO> result = graphService.getGraphAnomalies(null, null, null, null, Set.of());

        assertThat(result).isEmpty();
    }
}