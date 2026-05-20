package com.tars.model.dto;

import com.tars.model.enums.BillingCycle;
import com.tars.model.enums.PlanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponseDTO {
    private PlanType plan;
    private BillingCycle billingCycle;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private boolean cancellationScheduled;
    private int timelinesAllowed;
    private int reportsAllowed;      // -1 = unlimited
    private int reportsUsed;
}