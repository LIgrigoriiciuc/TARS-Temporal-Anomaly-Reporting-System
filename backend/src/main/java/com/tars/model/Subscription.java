package com.tars.model;

import com.tars.model.enums.BillingCycle;
import com.tars.model.enums.PlanType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "agent")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false, unique = true)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    private PlanType plan;

    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;

    private LocalDate startDate;
    private LocalDate expiryDate;

    // true = scheduled to revert to FREE at period end
    private boolean cancellationScheduled;

    // Stripe subscription ID — needed for cancel API call
    private String stripeSubscriptionId;

    // Stripe customer ID — needed for subsequent checkouts
    private String stripeCustomerId;
}