package com.tars.model;

import com.tars.model.enums.AnomalyType;
import com.tars.model.enums.ParadoxRisk;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "anomalies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "analysis")
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Joins back to the analysis that produced this anomaly
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false, unique = true)
    private AnomalyAnalysis analysis;

    @Enumerated(EnumType.STRING)
    private AnomalyType type;

    @Enumerated(EnumType.STRING)
    private ParadoxRisk paradoxRisk;

    // Timeline for graph Y axis
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeline_id", nullable = false)
    private Timeline timeline;

    // Year for graph X axis
    private Integer year;

    // Internal only — the report IDs Gemini identified as direct contributors
    // Subset of AnomalyAnalysis.correlatedReportIds, never sent to agent
    @Column(columnDefinition = "TEXT")
    private String contributingReportIds;

    private LocalDateTime detectedAt;

    @PrePersist
    protected void onCreate() {
        detectedAt = LocalDateTime.now();
    }
}