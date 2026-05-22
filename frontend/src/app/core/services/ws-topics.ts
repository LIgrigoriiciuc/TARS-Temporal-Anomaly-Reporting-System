// ─────────────────────────────────────────────────────────────────────────────
// WebSocket topic constants
// Keep in sync with server-side pushes (see service-layer comments in Spring).
// ─────────────────────────────────────────────────────────────────────────────

export const WS_TOPICS = {
  /** UC-08: pushed by GeminiService after AI analysis completes.
   *  Payload: SubmittedReportResponseDTO */
  analysisResult: (agentId: string) => `/topic/analysis/${agentId}`,

  /** Pushed by AdminService when an agent account is deactivated.
   *  Payload: "TERMINATED" string */
  userDeactivated: (userId: string) => `/topic/user-deactivated/${userId}`,

  /** UC-11: pushed by AlertService when a HIGH/CRITICAL anomaly alert is created.
   *  Payload: AlertDTO */
  alertsNew: '/topic/alerts',

  /** UC-11: pushed by AlertService when an alert is acknowledged.
   *  Payload: alert id (number) */
  alertsAcknowledged: '/topic/alerts/acknowledged',
} as const;
