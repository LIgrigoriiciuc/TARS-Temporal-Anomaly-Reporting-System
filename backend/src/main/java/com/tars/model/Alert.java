package com.tars.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "anomaly")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One anomaly triggers at most one alert
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anomaly_id", nullable = false, unique = true)
    private Anomaly anomaly;

    private boolean acknowledged;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}