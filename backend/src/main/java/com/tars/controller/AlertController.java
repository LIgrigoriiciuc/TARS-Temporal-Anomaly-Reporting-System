package com.tars.controller;

import com.tars.model.dto.AlertDTO;
import com.tars.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    // All unacknowledged alerts are shown on Supervisor login
    @GetMapping
    public ResponseEntity<List<AlertDTO>> getUnacknowledged() {
        return ResponseEntity.ok(alertService.getUnacknowledged());
    }

    // Supervisor acknowledges an alert after viewing the anomaly
    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<AlertDTO> acknowledge(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.acknowledge(id));
    }
}