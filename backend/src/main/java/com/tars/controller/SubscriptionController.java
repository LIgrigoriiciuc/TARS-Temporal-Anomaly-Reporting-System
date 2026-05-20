package com.tars.controller;

import com.tars.model.Agent;
import com.tars.model.dto.SubscriptionResponseDTO;
import com.tars.model.dto.UpgradeRequestDTO;
import com.tars.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // Current subscription info — shown on subscription page
    @GetMapping
    public ResponseEntity<SubscriptionResponseDTO> getSubscription(HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        return ResponseEntity.ok(subscriptionService.getSubscriptionInfo(agent));
    }

    /**
     * UC-12 — returns Stripe Checkout URL.
     * Frontend redirects agent to this URL.
     */
    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, String>> upgrade(
            @Valid @RequestBody UpgradeRequestDTO dto,
            HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        String checkoutUrl = subscriptionService.createCheckoutSession(
                agent, dto.getPlan(), dto.getBillingCycle()
        );
        // Return URL for frontend to redirect to
        return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
    }

    /**
     * UC-13 — schedules cancellation at period end.
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancel(HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("currentUser");
        subscriptionService.cancelSubscription(agent);
        return ResponseEntity.ok(Map.of("message",
                "Subscription cancelled. Access remains until " +
                        subscriptionService.getOrCreateFreeSubscription(agent).getExpiryDate()));
    }
}