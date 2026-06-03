package com.tars.service;

import com.tars.model.Alert;
import com.tars.model.Anomaly;
import com.tars.model.Timeline;
import com.tars.model.dto.AlertDTO;
import com.tars.model.enums.AnomalyType;
import com.tars.model.enums.ParadoxRisk;
import com.tars.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks AlertService alertService;

    private Anomaly highRiskAnomaly;
    private Anomaly criticalAnomaly;
    private Anomaly lowRiskAnomaly;
    private Timeline timeline;

    @BeforeEach
    void setUp() {
        timeline = new Timeline();
        ReflectionTestUtils.setField(timeline, "id", 10L);
        timeline.setName("ALPHA");

        highRiskAnomaly = Anomaly.builder()
                .type(AnomalyType.PAR)
                .paradoxRisk(ParadoxRisk.HIGH)
                .timeline(timeline)
                .year(2045)
                .verified(true)
                .build();
        ReflectionTestUtils.setField(highRiskAnomaly, "id", 1L);

        criticalAnomaly = Anomaly.builder()
                .type(AnomalyType.RFT)
                .paradoxRisk(ParadoxRisk.CRITICAL)
                .timeline(timeline)
                .year(2046)
                .verified(true)
                .build();
        ReflectionTestUtils.setField(criticalAnomaly, "id", 2L);

        lowRiskAnomaly = Anomaly.builder()
                .type(AnomalyType.LOP)
                .paradoxRisk(ParadoxRisk.LOW)
                .timeline(timeline)
                .year(2047)
                .verified(true)
                .build();
        ReflectionTestUtils.setField(lowRiskAnomaly, "id", 3L);

        when(alertRepository.save(any())).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            if (a.getId() == null) ReflectionTestUtils.setField(a, "id", 99L);
            return a;
        });
    }

    @Test
    void triggerIfCritical_highRisk_createsAlertAndPushes() {
        when(alertRepository.findByAnomalyId(1L)).thenReturn(Optional.empty());

        alertService.triggerIfCritical(highRiskAnomaly);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAnomaly()).isEqualTo(highRiskAnomaly);
        assertThat(captor.getValue().isAcknowledged()).isFalse();

        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), (Object) any(AlertDTO.class));
    }

    @Test
    void triggerIfCritical_critical_createsAlertAndPushes() {
        when(alertRepository.findByAnomalyId(2L)).thenReturn(Optional.empty());

        alertService.triggerIfCritical(criticalAnomaly);

        verify(alertRepository).save(any());
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), (Object) any(AlertDTO.class));
    }

    @Test
    void triggerIfCritical_lowRisk_doesNothing() {
        alertService.triggerIfCritical(lowRiskAnomaly);

        verify(alertRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void triggerIfCritical_mediumRisk_doesNothing() {
        Anomaly mediumAnomaly = Anomaly.builder()
                .paradoxRisk(ParadoxRisk.MEDIUM)
                .build();
        ReflectionTestUtils.setField(mediumAnomaly, "id", 4L);

        alertService.triggerIfCritical(mediumAnomaly);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void triggerIfCritical_nullParadoxRisk_doesNothing() {
        Anomaly noRisk = Anomaly.builder().build();
        ReflectionTestUtils.setField(noRisk, "id", 5L);

        alertService.triggerIfCritical(noRisk);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void triggerIfCritical_duplicateAlert_skipsCreation() {
        Alert existing = Alert.builder().anomaly(highRiskAnomaly).acknowledged(false).build();
        ReflectionTestUtils.setField(existing, "id", 55L);
        when(alertRepository.findByAnomalyId(1L)).thenReturn(Optional.of(existing));

        alertService.triggerIfCritical(highRiskAnomaly);

        verify(alertRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void getUnacknowledged_returnsUnacknowledgedOnly() {
        Alert a1 = Alert.builder().anomaly(highRiskAnomaly).acknowledged(false).build();
        Alert a2 = Alert.builder().anomaly(criticalAnomaly).acknowledged(false).build();
        ReflectionTestUtils.setField(a1, "id", 1L);
        ReflectionTestUtils.setField(a2, "id", 2L);

        when(alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(a2, a1));

        List<AlertDTO> result = alertService.getUnacknowledged();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getParadoxRisk()).isEqualTo(ParadoxRisk.CRITICAL);
        assertThat(result.get(1).getParadoxRisk()).isEqualTo(ParadoxRisk.HIGH);
    }

    @Test
    void getUnacknowledged_noAlerts_returnsEmptyList() {
        when(alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of());

        assertThat(alertService.getUnacknowledged()).isEmpty();
    }

    @Test
    void acknowledge_setsAcknowledgedTrue() {
        Alert alert = Alert.builder().anomaly(highRiskAnomaly).acknowledged(false).build();
        ReflectionTestUtils.setField(alert, "id", 10L);
        when(alertRepository.findById(10L)).thenReturn(Optional.of(alert));

        AlertDTO result = alertService.acknowledge(10L);

        assertThat(alert.isAcknowledged()).isTrue();
        assertThat(result.isAcknowledged()).isTrue();
        verify(alertRepository).save(alert);
    }

    @Test
    void acknowledge_notFound_throws404() {
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.acknowledge(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }
}