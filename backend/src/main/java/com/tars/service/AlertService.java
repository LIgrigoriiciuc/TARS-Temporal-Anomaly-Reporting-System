package com.tars.service;

import com.tars.model.Alert;
import com.tars.model.Anomaly;
import com.tars.model.dto.AlertDTO;
import com.tars.model.enums.ParadoxRisk;
import com.tars.model.mappers.AlertMapper;
import com.tars.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Called from GeminiService after a confirmed anomaly is saved.
     * Triggers if paradoxRisk is HIGH or CRITICAL.
     * Guards against duplicates — one alert per anomaly.
     */
    @Transactional
    public void triggerIfCritical(Anomaly anomaly) {
        if (anomaly.getParadoxRisk() == null) return;
        if (anomaly.getParadoxRisk() != ParadoxRisk.HIGH
                && anomaly.getParadoxRisk() != ParadoxRisk.CRITICAL) return;

        // Don't create duplicate alert if one already exists for this anomaly
        if (alertRepository.findByAnomalyId(anomaly.getId()).isPresent()) {
            log.info("AlertService: alert already exists for anomaly {}", anomaly.getId());
            return;
        }

        Alert alert = Alert.builder()
                .anomaly(anomaly)
                .acknowledged(false)
                .build();
        alert = alertRepository.save(alert);

        log.info("AlertService: alert created for anomaly {} paradoxRisk={}",
                anomaly.getId(), anomaly.getParadoxRisk());

        // Push to all supervisors immediately via WebSocket
        // Supervisors subscribe to /topic/alerts
        AlertDTO dto = AlertMapper.toDto(alert);
        try {
            messagingTemplate.convertAndSend("/topic/alerts", dto);
        } catch (Exception e) {
            log.warn("AlertService: WebSocket push failed for alert {}: {}", alert.getId(), e.getMessage());
        }
    }

    public List<AlertDTO> getUnacknowledged() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc()
                .stream()
                .map(AlertMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AlertDTO acknowledge(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        alert.setAcknowledged(true);
        Alert saved = alertRepository.save(alert);
        messagingTemplate.convertAndSend("/topic/alerts/acknowledged", alertId);
        return AlertMapper.toDto(alertRepository.save(alert));
    }
}