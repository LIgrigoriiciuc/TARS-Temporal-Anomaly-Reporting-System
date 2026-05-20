package com.tars.repository;

import com.tars.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByAgentId(Long agentId);

    // Used by Stripe webhook to find subscription by Stripe's own ID
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}