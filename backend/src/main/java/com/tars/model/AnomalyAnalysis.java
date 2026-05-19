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
@ToString(exclude = {"report", "anomaly"})
public class AnomalyAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false, unique = true)
    private ObservationReport report;

    @Enumerated(EnumType.STRING)
    private AnalysisStatus analysisStatus;

    private Boolean confirmed;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    // IDs we pulled from DB and sent to Gemini as context — our selection
    @Column(columnDefinition = "TEXT")
    private String correlatedReportIds;

    // IDs Gemini picked as directly causing/defining the anomaly — Gemini's selection
    // Excludes the current report's own ID
    @Column(columnDefinition = "TEXT")
    private String contributingReportIds;

    /**
     * Null if confirmed=false.
     * Set to an existing Anomaly if 75% overlap detected,
     * or to a newly created Anomaly if confirmed=true and no overlap.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anomaly_id", nullable = true)
    private Anomaly anomaly;

    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onCreate() {
        analyzedAt = LocalDateTime.now();
    }
}