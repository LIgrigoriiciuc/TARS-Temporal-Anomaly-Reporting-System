package com.tars.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.model.*;
import com.tars.model.enums.*;
import com.tars.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class OpenAIServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock AnomalyAnalysisRepository analysisRepository;
    @Mock AnomalyRepository anomalyRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock OpenAIHttpClient openAIHttpClient;
    @Mock AlertService alertService;

    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    OpenAIService openAIService;

    private Agent agentOne;
    private Agent agentTwo;
    private Timeline timeline;
    private ObservationReport report;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(openAIService, "objectMapper", objectMapper);

        agentOne = new Agent();
        ReflectionTestUtils.setField(agentOne, "id", 1L);

        agentTwo = new Agent();
        ReflectionTestUtils.setField(agentTwo, "id", 2L);

        timeline = new Timeline();
        ReflectionTestUtils.setField(timeline, "id", 10L);
        timeline.setName("ALPHA");

        report = ObservationReport.builder()
                .description("Strange loop detected near the origin point")
                .year(2045)
                .keywords("loop, origin")
                .status(ReportStatus.PENDING_ANALYSIS)
                .agent(agentOne)
                .timeline(timeline)
                .build();
        ReflectionTestUtils.setField(report, "id", 99L);

        // analysisRepository.save returns what it receives, with id set
        when(analysisRepository.save(any())).thenAnswer(inv -> {
            AnomalyAnalysis a = inv.getArgument(0);
            if (a.getId() == null) ReflectionTestUtils.setField(a, "id", 1L);
            return a;
        });
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(anomalyRepository.save(any())).thenAnswer(inv -> {
            Anomaly a = inv.getArgument(0);
            if (a.getId() == null) ReflectionTestUtils.setField(a, "id", 100L);
            return a;
        });

        // Default: report found, no historical context, no existing anomalies
        when(reportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(reportRepository.findHistoricalContext(anyLong(), anyInt(), anyInt(), anyLong()))
                .thenReturn(List.of());
        when(anomalyRepository.findByTimelineId(anyLong())).thenReturn(List.of());
        when(anomalyRepository.findByTimelineId(anyLong())).thenReturn(List.of());
    }

    // -------------------------------------------------------------------------
    // 1. Confirmed — new unverified anomaly created
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_confirmed_createsNewUnverifiedAnomaly() throws Exception {
        when(openAIHttpClient.call(anyString())).thenReturn("""
                {
                  "confirmed": true,
                  "type": "LOP",
                  "paradoxRisk": "HIGH",
                  "explanation": "A clear temporal loop was detected.",
                  "contributingReportIds": [12, 45]
                }
                """);

        openAIService.analyzeReport(99L);

        ArgumentCaptor<Anomaly> anomalyCaptor = ArgumentCaptor.forClass(Anomaly.class);
        verify(anomalyRepository).save(anomalyCaptor.capture());
        Anomaly saved = anomalyCaptor.getValue();

        assertThat(saved.getType()).isEqualTo(AnomalyType.LOP);
        assertThat(saved.getParadoxRisk()).isEqualTo(ParadoxRisk.HIGH);
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getYear()).isEqualTo(2045);
        assertThat(saved.getContributingReportIds()).contains("99");

        ArgumentCaptor<AnomalyAnalysis> analysisCaptor = ArgumentCaptor.forClass(AnomalyAnalysis.class);
        // save called twice: once for PENDING, once for COMPLETED
        verify(analysisRepository, times(2)).save(analysisCaptor.capture());
        AnomalyAnalysis completed = analysisCaptor.getAllValues().get(1);
        assertThat(completed.getAnalysisStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.getConfirmed()).isTrue();
        assertThat(completed.getAnomaly()).isNotNull();

        assertThat(report.getStatus()).isEqualTo(ReportStatus.CONFIRMED);
    }

    // -------------------------------------------------------------------------
    // 2. Not confirmed — report rejected, no anomaly
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_notConfirmed_rejectsReportNoAnomaly() throws Exception {
        when(openAIHttpClient.call(anyString())).thenReturn("""
                {
                  "confirmed": false,
                  "type": null,
                  "paradoxRisk": null,
                  "explanation": "No anomaly detected — normal temporal variance.",
                  "contributingReportIds": []
                }
                """);

        openAIService.analyzeReport(99L);

        verify(anomalyRepository, never()).save(any());
        assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);

        ArgumentCaptor<AnomalyAnalysis> captor = ArgumentCaptor.forClass(AnomalyAnalysis.class);
        verify(analysisRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getConfirmed()).isFalse();
        assertThat(captor.getAllValues().get(1).getAnalysisStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    // -------------------------------------------------------------------------
    // 3. Confirmed — 75%+ overlap with existing anomaly, links to it
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_confirmed_overlapsExistingAnomaly_linksInsteadOfCreating() throws Exception {
        when(openAIHttpClient.call(anyString())).thenReturn("""
                {
                  "confirmed": true,
                  "type": "PAR",
                  "paradoxRisk": "CRITICAL",
                  "explanation": "Matches existing causal inversion records.",
                  "contributingReportIds": [12, 45]
                }
                """);

        // Existing verified anomaly — pool [12, 45, 78], intersection with [12, 45] = 100%
        Anomaly existing = Anomaly.builder()
                .type(AnomalyType.PAR)
                .paradoxRisk(ParadoxRisk.CRITICAL)
                .timeline(timeline)
                .year(2043)
                .contributingReportIds("12,45,78")
                .verified(true)
                .build();
        ReflectionTestUtils.setField(existing, "id", 55L);

        when(anomalyRepository.findByTimelineId(10L)).thenReturn(List.of(existing));

        openAIService.analyzeReport(99L);

        // No new anomaly saved
        verify(anomalyRepository, never()).save(argThat(a -> a.getId() == null));

        ArgumentCaptor<AnomalyAnalysis> captor = ArgumentCaptor.forClass(AnomalyAnalysis.class);
        verify(analysisRepository, times(2)).save(captor.capture());
        AnomalyAnalysis completed = captor.getAllValues().get(1);
        assertThat(completed.getAnomaly()).isEqualTo(existing);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CONFIRMED);
    }

    // -------------------------------------------------------------------------
    // 4. Overlaps unverified anomaly + new agent → promoted to verified
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_corroboratesUnverifiedAnomaly_promotesToVerified() throws Exception {
        // Current report submitted by agentTwo
        ReflectionTestUtils.setField(report, "agent", agentTwo);

        when(openAIHttpClient.call(anyString())).thenReturn("""
                {
                  "confirmed": true,
                  "type": "ERO",
                  "paradoxRisk": "HIGH",
                  "explanation": "Erosion pattern matches existing unverified record.",
                  "contributingReportIds": [12, 45]
                }
                """);

        // Unverified anomaly founded by agentOne (report 77)
        Anomaly unverified = Anomaly.builder()
                .type(AnomalyType.ERO)
                .paradoxRisk(ParadoxRisk.HIGH)
                .timeline(timeline)
                .year(2040)
                .contributingReportIds("77,12,45")
                .verified(false)
                .build();
        ReflectionTestUtils.setField(unverified, "id", 66L);

        ObservationReport foundingReport = ObservationReport.builder()
                .agent(agentOne)
                .build();
        ReflectionTestUtils.setField(foundingReport, "id", 77L);

        when(anomalyRepository.findByTimelineId(10L)).thenReturn(List.of(unverified));
        when(reportRepository.findAllById(anySet())).thenReturn(List.of(foundingReport));

        openAIService.analyzeReport(99L);

        assertThat(unverified.isVerified()).isTrue();
        verify(anomalyRepository).save(unverified);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CONFIRMED);
    }

    // -------------------------------------------------------------------------
    // 5. Self-report ID stripped from contributing set on analysis
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_selfReportIdRemovedFromContributingIds() throws Exception {
        // OpenAI includes the current report's own ID (99) — should be stripped
        when(openAIHttpClient.call(anyString())).thenReturn("""
                {
                  "confirmed": true,
                  "type": "DEV",
                  "paradoxRisk": "MEDIUM",
                  "explanation": "Deviation detected.",
                  "contributingReportIds": [99, 12, 45]
                }
                """);

        openAIService.analyzeReport(99L);

        ArgumentCaptor<AnomalyAnalysis> captor = ArgumentCaptor.forClass(AnomalyAnalysis.class);
        verify(analysisRepository, times(2)).save(captor.capture());
        AnomalyAnalysis completed = captor.getAllValues().get(1);

        // Analysis contributingReportIds must not contain 99 (self excluded)
        String contributing = completed.getContributingReportIds();
        assertThat(contributing).doesNotContain("99");
        assertThat(contributing).contains("12");
        assertThat(contributing).contains("45");
    }

    // -------------------------------------------------------------------------
    // 6. Both OpenAI responses unparseable → UNRESOLVED
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_bothParseAttemptsFail_marksUnresolved() throws Exception {
        when(openAIHttpClient.call(anyString())).thenReturn("not json at all %%%");

        openAIService.analyzeReport(99L);

        ArgumentCaptor<AnomalyAnalysis> captor = ArgumentCaptor.forClass(AnomalyAnalysis.class);
        verify(analysisRepository, times(2)).save(captor.capture());
        AnomalyAnalysis final_ = captor.getAllValues().get(1);

        assertThat(final_.getAnalysisStatus()).isEqualTo(AnalysisStatus.UNRESOLVED);
        assertThat(final_.getExplanation()).contains("not parseable");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
        verify(anomalyRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // 7. First response unparseable, second succeeds (retry flow)
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_firstParseFails_retrySucceeds() throws Exception {
        when(openAIHttpClient.call(anyString()))
                .thenReturn("```not valid json```")  // first call fails
                .thenReturn("""
                        {
                          "confirmed": true,
                          "type": "RFT",
                          "paradoxRisk": "CRITICAL",
                          "explanation": "Rift detected on retry.",
                          "contributingReportIds": []
                        }
                        """); // second call succeeds

        openAIService.analyzeReport(99L);

        verify(openAIHttpClient, times(2)).call(anyString());
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CONFIRMED);

        ArgumentCaptor<Anomaly> anomalyCaptor = ArgumentCaptor.forClass(Anomaly.class);
        verify(anomalyRepository).save(anomalyCaptor.capture());
        assertThat(anomalyCaptor.getValue().getType()).isEqualTo(AnomalyType.RFT);
    }

    // -------------------------------------------------------------------------
    // 8. Below 75% overlap — new anomaly created
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_belowOverlapThreshold_createsNewAnomaly() throws Exception {
        when(openAIHttpClient.call(anyString())).thenReturn("""
                {
                  "confirmed": true,
                  "type": "RFT",
                  "paradoxRisk": "CRITICAL",
                  "explanation": "New rift, no match.",
                  "contributingReportIds": [12]
                }
                """);

        // Existing anomaly pool [45, 78] — intersection with [12] = 0%
        Anomaly existing = Anomaly.builder()
                .timeline(timeline)
                .contributingReportIds("45,78")
                .verified(true)
                .build();
        ReflectionTestUtils.setField(existing, "id", 77L);

        when(anomalyRepository.findByTimelineId(10L)).thenReturn(List.of(existing));

        openAIService.analyzeReport(99L);

        ArgumentCaptor<Anomaly> captor = ArgumentCaptor.forClass(Anomaly.class);
        verify(anomalyRepository).save(captor.capture());
        // Saved anomaly is new (id was null before save)
        assertThat(captor.getValue().getType()).isEqualTo(AnomalyType.RFT);
        assertThat(captor.getValue()).isNotEqualTo(existing);
    }

    // -------------------------------------------------------------------------
    // 9. OpenAI API throws exception → analysis marked FAILED
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_openAIThrowsException_marksAsFailed() throws Exception {
        when(openAIHttpClient.call(anyString()))
                .thenThrow(new RuntimeException("OpenAI API quota exceeded or unauthorized: 429"));

        openAIService.analyzeReport(99L);

        ArgumentCaptor<AnomalyAnalysis> captor = ArgumentCaptor.forClass(AnomalyAnalysis.class);
        verify(analysisRepository, times(2)).save(captor.capture());
        AnomalyAnalysis failed = captor.getAllValues().get(1);

        assertThat(failed.getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getExplanation()).contains("technical error");
        verify(anomalyRepository, never()).save(any());
    }
    // -------------------------------------------------------------------------
    // 10. Second agent corroborates → anomaly verified → alert triggered
    // -------------------------------------------------------------------------

    @Test
    void analyzeReport_secondAgentCorroborates_verifiesAnomalyAndTriggersAlert() throws Exception {
        // Current report submitted by agentTwo
        ReflectionTestUtils.setField(report, "agent", agentTwo);

        when(openAIHttpClient.call(anyString())).thenReturn("""
                {
                  "confirmed": true,
                  "type": "RFT",
                  "paradoxRisk": "CRITICAL",
                  "explanation": "Rift confirmed by second observer.",
                  "contributingReportIds": [12, 45]
                }
                """);

        // Unverified anomaly founded by agentOne (report 77) — CRITICAL risk
        Anomaly unverified = Anomaly.builder()
                .type(AnomalyType.RFT)
                .paradoxRisk(ParadoxRisk.CRITICAL)
                .timeline(timeline)
                .year(2044)
                .contributingReportIds("77,12,45")
                .verified(false)
                .build();
        ReflectionTestUtils.setField(unverified, "id", 66L);

        ObservationReport foundingReport = ObservationReport.builder()
                .agent(agentOne)
                .build();
        ReflectionTestUtils.setField(foundingReport, "id", 77L);

        when(anomalyRepository.findByTimelineId(10L)).thenReturn(List.of(unverified));
        when(reportRepository.findAllById(anySet())).thenReturn(List.of(foundingReport));

        openAIService.analyzeReport(99L);

        // Anomaly promoted to verified
        assertThat(unverified.isVerified()).isTrue();
        verify(anomalyRepository).save(unverified);

        // Alert triggered because CRITICAL
        verify(alertService).triggerIfCritical(unverified);

        // Report confirmed
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CONFIRMED);

        // WebSocket pushed to agent
        verify(messagingTemplate).convertAndSend(
                eq("/topic/analysis/" + agentTwo.getId()),
                (Object) any()
        );
    }
}