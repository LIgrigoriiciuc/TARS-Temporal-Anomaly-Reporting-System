package com.tars.model;

import com.tars.model.enums.AnalysisStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "anomaly_analyses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "report")
public class AnomalyAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false, unique = true)
    private ObservationReport report;

    @Enumerated(EnumType.STRING)
    private AnalysisStatus analysisStatus;

    // true = anomaly confirmed, Anomaly record created; false = nothing found
    private Boolean confirmed;

    // Raw Gemini explanation — what it found or why it found nothing
    @Column(columnDefinition = "TEXT")
    private String explanation;

    // Internal only — all report IDs Gemini considered related, never sent to agent
    // Comma-separated e.g. "12,45,78"
    @Column(columnDefinition = "TEXT")
    private String correlatedReportIds;

    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onCreate() {
        analyzedAt = LocalDateTime.now();
    }
}