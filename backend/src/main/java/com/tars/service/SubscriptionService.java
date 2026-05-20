package com.tars.service;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.model.billingportal.Configuration;
import com.stripe.param.checkout.SessionCreateParams;
import com.tars.model.Agent;
import com.tars.model.Subscription;
import com.tars.model.dto.SubscriptionResponseDTO;
import com.tars.model.enums.BillingCycle;
import com.tars.model.enums.PlanType;
import com.tars.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    // Stripe price IDs — create these in Stripe dashboard test mode
    // Dashboard → Products → Add product → Add price → copy price ID
    @Value("${stripe.price.pro.monthly}")
    private String proPriceMonthly;

    @Value("${stripe.price.pro.annual}")
    private String proPriceAnnual;

    @Value("${stripe.price.enterprise.monthly}")
    private String enterprisePriceMonthly;

    @Value("${stripe.price.enterprise.annual}")
    private String enterprisePriceAnnual;

    // Plan limits
    public static final int FREE_TIMELINES = 1;
    public static final int PRO_TIMELINES = 5;
    public static final int ENTERPRISE_TIMELINES = Integer.MAX_VALUE;

    public static final int FREE_REPORTS = 20;
    public static final int PRO_REPORTS = 200;
    public static final int ENTERPRISE_REPORTS = -1; // unlimited

    /**
     * UC-12 — creates a Stripe Checkout session and returns the redirect URL.
     */
    @Transactional
    public String createCheckoutSession(Agent agent, PlanType plan, BillingCycle billingCycle) {
        if (plan == PlanType.FREE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upgrade to FREE plan");
        }

        Subscription current = getOrCreateFreeSubscription(agent);
        if (current.getPlan() == PlanType.ENTERPRISE && plan != PlanType.ENTERPRISE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already on ENTERPRISE plan");
        }

        Stripe.apiKey = stripeApiKey;

        try {
            String priceId = resolvePriceId(plan, billingCycle);

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    // Pass agent ID in metadata so webhook knows who to update
                    .putMetadata("agentId", String.valueOf(agent.getId()))
                    .putMetadata("plan", plan.name())
                    .putMetadata("billingCycle", billingCycle.name());

            // Reuse existing Stripe customer if agent has one
            if (current.getStripeCustomerId() != null) {
                paramsBuilder.setCustomer(current.getStripeCustomerId());
            } else {
                paramsBuilder.setCustomerEmail(agent.getEmail());
            }

            Session session = Session.create(paramsBuilder.build());
            log.info("SubscriptionService: checkout session created for agent {}", agent.getId());
            return session.getUrl();

        } catch (Exception e) {
            log.error("SubscriptionService: Stripe error for agent {}: {}", agent.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment service unavailable");
        }
    }

    /**
     * Called by webhook after payment_intent.succeeded.
     * Updates subscription record with new plan and expiry.
     */
    @Transactional
    public void activateSubscription(String stripeSubscriptionId, String stripeCustomerId,
                                     Long agentId, PlanType plan, BillingCycle billingCycle) {
        Subscription subscription = subscriptionRepository.findByAgentId(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

        subscription.setPlan(plan);
        subscription.setBillingCycle(billingCycle);
        subscription.setStartDate(LocalDate.now());
        subscription.setExpiryDate(calculateExpiry(billingCycle));
        subscription.setCancellationScheduled(false);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStripeCustomerId(stripeCustomerId);

        subscriptionRepository.save(subscription);
        log.info("SubscriptionService: plan activated for agent {} → {}", agentId, plan);
    }

    /**
     * UC-13 — schedules cancellation at period end via Stripe API.
     */
    @Transactional
    public void cancelSubscription(Agent agent) {
        Subscription subscription = subscriptionRepository.findByAgentId(agent.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

        if (subscription.getPlan() == PlanType.FREE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active paid subscription to cancel");
        }

        Stripe.apiKey = stripeApiKey;

        try {
            com.stripe.model.Subscription stripeSub =
                    com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());

            com.stripe.param.SubscriptionUpdateParams params =
                    com.stripe.param.SubscriptionUpdateParams.builder()
                            .setCancelAtPeriodEnd(true)
                            .build();

            stripeSub.update(params);

            subscription.setCancellationScheduled(true);
            subscriptionRepository.save(subscription);
            log.info("SubscriptionService: cancellation scheduled for agent {}", agent.getId());

        } catch (Exception e) {
            log.error("SubscriptionService: cancel error for agent {}: {}", agent.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment service unavailable");
        }
    }

    /**
     * Called by webhook when subscription actually expires (customer.subscription.deleted).
     * Reverts plan to FREE.
     */
    @Transactional
    public void revertToFree(String stripeSubscriptionId) {
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            sub.setPlan(PlanType.FREE);
            sub.setBillingCycle(null);
            sub.setExpiryDate(null);
            sub.setCancellationScheduled(false);
            sub.setStripeSubscriptionId(null);
            subscriptionRepository.save(sub);
            log.info("SubscriptionService: agent {} reverted to FREE", sub.getAgent().getId());
        });
    }

    public SubscriptionResponseDTO getSubscriptionInfo(Agent agent) {
        Subscription sub = getOrCreateFreeSubscription(agent);
        return SubscriptionResponseDTO.builder()
                .plan(sub.getPlan())
                .billingCycle(sub.getBillingCycle())
                .startDate(sub.getStartDate())
                .expiryDate(sub.getExpiryDate())
                .cancellationScheduled(sub.isCancellationScheduled())
                .timelinesAllowed(getTimelineLimit(sub.getPlan()))
                .reportsAllowed(getReportLimit(sub.getPlan()))
                .reportsUsed(agent.getMonthlyReportCount())
                .build();
    }

    /**
     * Plan enforcement — called before submitting a report.
     */
    public void enforceReportLimit(Agent agent) {
        Subscription sub = getOrCreateFreeSubscription(agent);
        int limit = getReportLimit(sub.getPlan());
        if (limit != -1 && agent.getMonthlyReportCount() >= limit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Monthly report limit reached. Upgrade your plan to submit more reports.");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public Subscription getOrCreateFreeSubscription(Agent agent) {
        return subscriptionRepository.findByAgentId(agent.getId())
                .orElseGet(() -> {
                    Subscription free = Subscription.builder()
                            .agent(agent)
                            .plan(PlanType.FREE)
                            .startDate(LocalDate.now())
                            .cancellationScheduled(false)
                            .build();
                    return subscriptionRepository.save(free);
                });
    }

    private String resolvePriceId(PlanType plan, BillingCycle billingCycle) {
        return switch (plan) {
            case PRO -> billingCycle == BillingCycle.MONTHLY ? proPriceMonthly : proPriceAnnual;
            case ENTERPRISE -> billingCycle == BillingCycle.MONTHLY ? enterprisePriceMonthly : enterprisePriceAnnual;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid plan");
        };
    }

    private LocalDate calculateExpiry(BillingCycle billingCycle) {
        return billingCycle == BillingCycle.MONTHLY
                ? LocalDate.now().plusMonths(1)
                : LocalDate.now().plusYears(1);
    }

    public int getTimelineLimit(PlanType plan) {
        return switch (plan) {
            case FREE -> FREE_TIMELINES;
            case PRO -> PRO_TIMELINES;
            case ENTERPRISE -> ENTERPRISE_TIMELINES;
        };
    }

    public int getReportLimit(PlanType plan) {
        return switch (plan) {
            case FREE -> FREE_REPORTS;
            case PRO -> PRO_REPORTS;
            case ENTERPRISE -> ENTERPRISE_REPORTS;
        };
    }
}