package com.tars.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Table(name = "timeline_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "timeline_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"agent", "timeline"})
public class TimelineAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeline_id", nullable = false)
    private Timeline timeline;

    private LocalDate grantedAt;
    private boolean active;

    @PrePersist
    protected void onCreate() {
        grantedAt = LocalDate.now();
    }
}