package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.Subscription;
import com.tars.model.enums.BillingCycle;
import com.tars.model.enums.PlanType;
import com.tars.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock TimelineAccessService timelineAccessService;
    @InjectMocks SubscriptionService subscriptionService;

    private Agent agent;
    private Subscription freeSub;
    private Subscription proSub;

    @BeforeEach
    void setUp() {
        agent = new Agent();
        ReflectionTestUtils.setField(agent, "id", 1L);
        agent.setMonthlyReportCount(0);

        freeSub = Subscription.builder()
                .agent(agent)
                .plan(PlanType.FREE)
                .startDate(LocalDate.now())
                .cancellationScheduled(false)
                .build();
        ReflectionTestUtils.setField(freeSub, "id", 1L);

        proSub = Subscription.builder()
                .agent(agent)
                .plan(PlanType.PRO)
                .billingCycle(BillingCycle.MONTHLY)
                .startDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusMonths(1))
                .cancellationScheduled(false)
                .stripeSubscriptionId("sub_test123")
                .stripeCustomerId("cus_test123")
                .build();
        ReflectionTestUtils.setField(proSub, "id", 1L);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.setTimelineAccessService(timelineAccessService);
    }

    @Test
    void getOrCreateFreeSubscription_existingSubscription_returnsIt() {
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(freeSub));

        Subscription result = subscriptionService.getOrCreateFreeSubscription(agent);

        assertThat(result.getPlan()).isEqualTo(PlanType.FREE);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void getOrCreateFreeSubscription_noSubscription_createsFree() {
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.empty());

        Subscription result = subscriptionService.getOrCreateFreeSubscription(agent);

        assertThat(result.getPlan()).isEqualTo(PlanType.FREE);
        verify(subscriptionRepository).save(any());
    }

    @Test
    void enforceReportLimit_freeUnderLimit_passes() {
        agent.setMonthlyReportCount(10);
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(freeSub));

        assertThatNoException().isThrownBy(() -> subscriptionService.enforceReportLimit(agent));
    }

    @Test
    void enforceReportLimit_freeAtLimit_throws403() {
        agent.setMonthlyReportCount(20);
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(freeSub));

        assertThatThrownBy(() -> subscriptionService.enforceReportLimit(agent))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("limit reached");
    }

    @Test
    void enforceReportLimit_proUnderLimit_passes() {
        agent.setMonthlyReportCount(199);
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(proSub));

        assertThatNoException().isThrownBy(() -> subscriptionService.enforceReportLimit(agent));
    }

    @Test
    void enforceReportLimit_proAtLimit_throws403() {
        agent.setMonthlyReportCount(200);
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(proSub));

        assertThatThrownBy(() -> subscriptionService.enforceReportLimit(agent))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("limit reached");
    }

    @Test
    void enforceReportLimit_enterprise_neverBlocks() {
        agent.setMonthlyReportCount(99999);
        Subscription enterpriseSub = Subscription.builder()
                .agent(agent)
                .plan(PlanType.ENTERPRISE)
                .build();
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(enterpriseSub));

        assertThatNoException().isThrownBy(() -> subscriptionService.enforceReportLimit(agent));
    }

    @Test
    void activateSubscription_updatesPlanAndExpiry() {
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(freeSub));

        subscriptionService.activateSubscription(
                "sub_new123", "cus_new123", 1L, PlanType.PRO, BillingCycle.MONTHLY
        );

        assertThat(freeSub.getPlan()).isEqualTo(PlanType.PRO);
        assertThat(freeSub.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(freeSub.getStripeSubscriptionId()).isEqualTo("sub_new123");
        assertThat(freeSub.getStripeCustomerId()).isEqualTo("cus_new123");
        assertThat(freeSub.isCancellationScheduled()).isFalse();
        assertThat(freeSub.getExpiryDate()).isEqualTo(LocalDate.now().plusMonths(1));
        verify(subscriptionRepository).save(freeSub);
    }

    @Test
    void activateSubscription_annual_setsExpiryOneYear() {
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(freeSub));

        subscriptionService.activateSubscription(
                "sub_annual", "cus_123", 1L, PlanType.ENTERPRISE, BillingCycle.ANNUAL
        );

        assertThat(freeSub.getExpiryDate()).isEqualTo(LocalDate.now().plusYears(1));
    }

    @Test
    void activateSubscription_agentNotFound_throws404() {
        when(subscriptionRepository.findByAgentId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.activateSubscription(
                "sub_x", "cus_x", 99L, PlanType.PRO, BillingCycle.MONTHLY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void revertToFree_clearsSubscriptionData() {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_test123"))
                .thenReturn(Optional.of(proSub));

        subscriptionService.revertToFree("sub_test123");

        assertThat(proSub.getPlan()).isEqualTo(PlanType.FREE);
        assertThat(proSub.getBillingCycle()).isNull();
        assertThat(proSub.getExpiryDate()).isNull();
        assertThat(proSub.getStripeSubscriptionId()).isNull();
        assertThat(proSub.isCancellationScheduled()).isFalse();
        verify(subscriptionRepository).save(proSub);
    }

    @Test
    void revertToFree_unknownSubscriptionId_doesNothing() {
        when(subscriptionRepository.findByStripeSubscriptionId("unknown"))
                .thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> subscriptionService.revertToFree("unknown"));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void getTimelineLimit_free_returns1() {
        assertThat(subscriptionService.getTimelineLimit(PlanType.FREE)).isEqualTo(1);
    }

    @Test
    void getTimelineLimit_pro_returns5() {
        assertThat(subscriptionService.getTimelineLimit(PlanType.PRO)).isEqualTo(5);
    }

    @Test
    void getTimelineLimit_enterprise_returnsMaxInt() {
        assertThat(subscriptionService.getTimelineLimit(PlanType.ENTERPRISE))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void getReportLimit_free_returns20() {
        assertThat(subscriptionService.getReportLimit(PlanType.FREE)).isEqualTo(20);
    }

    @Test
    void getReportLimit_enterprise_returnsMinusOne() {
        assertThat(subscriptionService.getReportLimit(PlanType.ENTERPRISE)).isEqualTo(-1);
    }

    @Test
    void getSubscriptionInfo_returnsCorrectDto() {
        agent.setMonthlyReportCount(5);
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(proSub));

        var dto = subscriptionService.getSubscriptionInfo(agent);

        assertThat(dto.getPlan()).isEqualTo(PlanType.PRO);
        assertThat(dto.getReportsUsed()).isEqualTo(5);
        assertThat(dto.getReportsAllowed()).isEqualTo(200);
        assertThat(dto.getTimelinesAllowed()).isEqualTo(5);
        assertThat(dto.isCancellationScheduled()).isFalse();
    }

    @Test
    void createCheckoutSession_upgradeToFree_throws400() {
        assertThatThrownBy(() ->
                subscriptionService.createCheckoutSession(agent, PlanType.FREE, BillingCycle.MONTHLY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot upgrade to FREE");
    }

    @Test
    void createCheckoutSession_alreadyEnterprise_throws400() {
        Subscription enterpriseSub = Subscription.builder()
                .agent(agent).plan(PlanType.ENTERPRISE).build();
        when(subscriptionRepository.findByAgentId(1L)).thenReturn(Optional.of(enterpriseSub));

        assertThatThrownBy(() ->
                subscriptionService.createCheckoutSession(agent, PlanType.PRO, BillingCycle.MONTHLY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Already on ENTERPRISE");
    }
}