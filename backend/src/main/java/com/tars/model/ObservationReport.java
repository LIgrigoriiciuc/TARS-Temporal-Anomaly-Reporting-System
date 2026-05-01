package com.tars.model;

import com.tars.model.enums.ReportStatus;

@Entity
@Table(name = "observation_reports")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ObservationReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;
    private Integer year;
    private String keywords;

    private LocalDateTime timestamp = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;
}