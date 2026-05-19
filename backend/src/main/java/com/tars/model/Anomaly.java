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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "anomalies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "analyses")
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AnomalyType type;

    @Enumerated(EnumType.STRING)
    private ParadoxRisk paradoxRisk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeline_id", nullable = false)
    private Timeline timeline;

    // Year of the first confirmed report — graph X axis
    private Integer year;

    /**
     * All analyses that have been linked to this anomaly.
     * First one created it, subsequent ones were matched via 75% overlap check.
     */
    @OneToMany(mappedBy = "anomaly", fetch = FetchType.LAZY)
    @Builder.Default
    private List<AnomalyAnalysis> analyses = new ArrayList<>();

    /**
     * Union of all contributingReportIds across all linked analyses.
     * Grows as new analyses are linked — used for overlap check on future reports.
     * Comma-separated, internal only.
     */
    @Column(columnDefinition = "TEXT")
    private String contributingReportIds;

    private LocalDateTime detectedAt;

    @PrePersist
    protected void onCreate() {
        detectedAt = LocalDateTime.now();
    }
}