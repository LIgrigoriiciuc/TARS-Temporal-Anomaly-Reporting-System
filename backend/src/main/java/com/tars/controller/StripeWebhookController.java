package com.tars.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.tars.model.enums.BillingCycle;
import com.tars.model.enums.PlanType;
import com.tars.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final SubscriptionService subscriptionService;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    /**
     * Stripe calls this after payment events.
     * Must be public (no JWT) — Stripe doesn't send cookies.
     * Signature verification replaces authentication.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("StripeWebhook: invalid signature — possible spoofed request");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        log.info("StripeWebhook: received event {}", event.getType());

        switch (event.getType()) {

            case "checkout.session.completed" -> {
                // Payment succeeded — activate subscription
                Optional<StripeObject> stripeObject = event.getDataObjectDeserializer().getObject();
                if (stripeObject.isPresent()) {
                    Session session = (Session) stripeObject.get();
                    Long agentId = Long.parseLong(session.getMetadata().get("agentId"));
                    PlanType plan = PlanType.valueOf(session.getMetadata().get("plan"));
                    BillingCycle billingCycle = BillingCycle.valueOf(session.getMetadata().get("billingCycle"));

                    subscriptionService.activateSubscription(
                            session.getSubscription(),
                            session.getCustomer(),
                            agentId,
                            plan,
                            billingCycle
                    );
                    log.info("StripeWebhook: subscription activated for agent {}", agentId);
                }
            }

            case "customer.subscription.deleted" -> {
                // Billing cycle ended after cancellation — revert to FREE
                Optional<StripeObject> stripeObject = event.getDataObjectDeserializer().getObject();
                if (stripeObject.isPresent()) {
                    com.stripe.model.Subscription sub = (com.stripe.model.Subscription) stripeObject.get();
                    subscriptionService.revertToFree(sub.getId());
                    log.info("StripeWebhook: subscription {} deleted, reverted to FREE", sub.getId());
                }
            }

            default -> log.info("StripeWebhook: unhandled event type {}", event.getType());
        }

        // Always return 200 — Stripe retries on non-200 responses
        return ResponseEntity.ok("Received");
    }
}