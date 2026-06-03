package com.tars.service;

import com.tars.model.ReportSubmittedEvent;
import com.tars.model.enums.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReportAnalysisListener {

    private final GeminiService geminiService;
    private final SubscriptionService subscriptionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportSubmitted(ReportSubmittedEvent event) {
        PlanType plan = subscriptionService
                .getOrCreateFreeSubscription(event.agent())
                .getPlan();
        if (plan == PlanType.ENTERPRISE) {
            geminiService.analyzeReportPriority(event.reportId());
        } else {
            geminiService.analyzeReport(event.reportId());
        }
    }
}