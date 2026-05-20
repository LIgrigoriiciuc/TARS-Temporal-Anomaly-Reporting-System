package com.tars.model.dto;

import com.tars.model.enums.BillingCycle;
import com.tars.model.enums.PlanType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpgradeRequestDTO {

    @NotNull(message = "Plan is required")
    private PlanType plan;

    @NotNull(message = "Billing cycle is required")
    private BillingCycle billingCycle;
}