// ─────────────────────────────────────────────────────────────────────────────
// WebSocket topic constants
// Keep in sync with server-side pushes (see service-layer comments in Spring).
// ─────────────────────────────────────────────────────────────────────────────

export const WS_TOPICS = {
  /** UC-08: pushed by GeminiService after AI analysis completes.
   *  Payload: SubmittedReportResponseDTO (same shape as GET /api/reports/submitted/{id}) */
  analysisResult: (agentId: string) => `/topic/analysis/${agentId}`,

  /** Pushed by supervisor action when an agent account is deactivated.
   *  Payload: none (presence of message is the signal) */
  userDeactivated: (userId: string) => `/topic/user-deactivated/${userId}`,
} as const;
