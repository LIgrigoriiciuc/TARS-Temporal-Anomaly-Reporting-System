package com.tars.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "agents")
@DiscriminatorValue("Agent")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@ToString(exclude = "subscription")
public class Agent extends User {

    private Integer monthlyReportCount = 0;

    @OneToOne(mappedBy = "agent", fetch = FetchType.LAZY, optional = true)
    private Subscription subscription;
}