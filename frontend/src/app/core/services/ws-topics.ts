export const WS_TOPICS = {
  /** UC-08: AI analysis complete. Payload: SubmittedReportResponseDTO */
  analysisResult: (agentId: string) => `/topic/analysis/${agentId}`,

  /** Account deactivated by supervisor. Payload: "TERMINATED" */
  userDeactivated: (userId: string) => `/topic/user-deactivated/${userId}`,

  /** UC-11: HIGH/CRITICAL anomaly alert created. Payload: AlertDTO */
  alertsNew: '/topic/alerts',

  /** UC-11: Alert acknowledged. Payload: alert id (number) */
  alertsAcknowledged: '/topic/alerts/acknowledged',

  /** UC-12: Stripe webhook fired, subscription activated. Payload: SubscriptionDTO */
  subscriptionUpdated: (agentId: string) => `/topic/subscription/${agentId}`,
} as const;
