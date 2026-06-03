# TARS — Use Cases

## UC-01: System Login
| | |
|---|---|
| **Primary actors** | Agent, Supervisor |
| **Secondary actors** | Database |
| **Description** | User logs into TARS using email and password to access role-based functionality. |
| **Trigger** | User opens the application without an active session. |

**Preconditions**
- PRE-1. User has a registered account in the system.
- PRE-2. User knows their email and password.
- PRE-3. Application and database are running.

**Postconditions**
- POST-1. User is authenticated with an active JWT session (HttpOnly cookie).
- POST-2. User is redirected to the role-specific dashboard (`/supervisor` or `/agent`).

**Normal Flow**
1. TARS displays the login screen with email and password fields.
2. User enters their email address.
3. User enters their password.
4. User clicks `[INITIALIZE_LINK]`.
5. TARS validates credentials against the database — checks email existence, BCrypt password match, and account status.
6. TARS generates a JWT token and sets it as an HttpOnly, SameSite=Lax cookie valid for 8 hours.
7. TARS redirects the user to the role-specific dashboard based on the `role` field in the response.

**Alternative Flows**
- A1 Invalid Credentials: User enters wrong email or password. Backend returns 401; TARS displays "Invalid credentials" and remains on the login screen.
- A2 Account Inactive: User account has status `INACTIVE`. Backend returns 403; TARS displays "ACCOUNT_TERMINATED // Access denied".

**Exceptions**
- E1 Service Unavailable: Any unexpected error (database timeout, server failure, network issue) returns a non-401/403 status code. TARS displays "SYSTEM_UNAVAILABLE // Retry later".

---

## UC-02: Logout
| | |
|---|---|
| **Primary actors** | Agent, Supervisor |
| **Secondary actors** | Redis (denylist store) |
| **Description** | Authenticated user securely terminates their active session. TARS invalidates the JWT token server-side and redirects to the login screen. |
| **Trigger** | User clicks the Logout button from any page in the application. |

**Preconditions**
- PRE-1. User is authenticated with an active JWT session.

**Postconditions**
- POST-1. JWT token is added to the Redis denylist, invalidated server-side.
- POST-2. JWT cookie is cleared from the browser (MaxAge=0).
- POST-3. `localStorage` is cleared and user is redirected to `/login`.

**Normal Flow**
1. User clicks the Logout button.
2. Angular calls `POST /api/auth/logout` with `withCredentials: true`.
3. Backend extracts the JWT from cookies, calculates remaining TTL, and adds it to the Redis denylist.
4. Backend sets the JWT cookie to empty with `MaxAge=0`, deleting it from the browser.
5. Angular `tap()` clears `localStorage` and navigates to `/login`.

**Alternative Flows**
- A1 Session Expiry: After 8 hours the JWT cookie expires naturally; the next request is rejected and the user is redirected to the login screen.

**Exceptions**
- E1 Redis Connection Failure: `tokenDenylistService.blacklistToken()` throws; the exception is caught internally in `AuthService.logout()`, the error is logged as `DENYLIST_FAILURE`, and execution continues — the cookie is still cleared. Token remains valid server-side until natural expiry.
- E2 Network Error: HTTP request fails to reach the server. Angular `catchError()` triggers, clears `localStorage`, and redirects to `/login` regardless.

---

## UC-03: Create User Account
| | |
|---|---|
| **Primary actors** | Supervisor |
| **Secondary actors** | Database, SMTP Server |
| **Description** | Supervisor creates a new user account and assigns a role (Agent or Supervisor). Upon creation, the new user receives their login credentials by email. |
| **Trigger** | Supervisor fills in the New Personnel Entry form on the dashboard. |

**Preconditions**
- PRE-1. Supervisor is authenticated.
- PRE-2. The email address to be assigned does not already exist in the system.

**Postconditions**
- POST-1. New account is saved to the database with the assigned role and ACTIVE status.
- POST-2. Login credentials are sent to the new user's email address asynchronously.

**Normal Flow**
1. TARS displays the user list and the New Personnel Entry form with name, email, password, and role fields.
2. Supervisor fills in all fields and clicks `ENLIST`.
3. TARS validates all fields (`@Valid` on `UserRequestDTO`).
4. TARS creates the account with status ACTIVE and saves it to the database.
5. TARS asynchronously sends an email to the new user containing their login credentials.
6. TARS reloads the user list, showing the new entry.

**Alternative Flows**
- A1 Validation Error: Supervisor submits with an empty field, invalid email format, or a password under 6 characters. Backend returns 400; TARS displays the relevant field error and does not create the account.
- A2 Duplicate Email: Supervisor submits an email already registered in the system. Backend returns 409; TARS displays "Email already registered".

**Exceptions**
- E1 SMTP Server Down: Account is created successfully in the database, but the credentials email fails to send. The error is caught silently, logged as `CRITICAL MAIL ERROR`, and account creation is not rolled back.

---

## UC-04: Deactivate User Account
| | |
|---|---|
| **Primary actors** | Supervisor |
| **Secondary actors** | Database, Redis |
| **Description** | Supervisor deactivates an existing user account. The user's active session is invalidated immediately and they can no longer log in, but all their data is preserved. |
| **Trigger** | Supervisor clicks `TERMINATE_ACCESS` on a user row in the dashboard. |

**Preconditions**
- PRE-1. Supervisor is authenticated.
- PRE-2. Target account exists in the system.
- PRE-3. Target account status is ACTIVE.
- PRE-4. Target account is not the Supervisor's own account.

**Postconditions**
- POST-1. Target account status is set to INACTIVE in the database.
- POST-2. All active JWT tokens for that user are immediately invalidated via Redis denylist.
- POST-3. Target user receives a WebSocket push (`/topic/user-deactivated/{id}`) triggering a redirect to the login screen.

**Normal Flow**
1. TARS displays the user list; active users have a `TERMINATE_ACCESS` button.
2. Supervisor clicks `TERMINATE_ACCESS` on the target user.
3. TARS sends `PATCH /api/admin/users/{id}/deactivate` to the backend.
4. Backend sets account status to INACTIVE and blacklists all active tokens via Redis.
5. Backend pushes a WebSocket message to the deactivated user, forcing an immediate logout.
6. TARS reloads the user list, showing the account as INACTIVE with no action button.

**Alternative Flows**
- A1 Supervisor attempts to deactivate their own account: Backend returns 403; TARS displays "Cannot terminate your own access".

**Exceptions**
- E1 Redis Connection Failure: `tokenDenylistService.blacklistUser()` fails; session invalidation does not occur and the token remains valid until natural expiry. Account status is still set to INACTIVE in the database.
- E2 Database Unreachable: `DataAccessException` is caught by `GlobalExceptionHandler`, returns 503. TARS displays "Failed to terminate access".

---

## UC-05: Submit Temporal Observation Report
| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Gemini API, Database |
| **Description** | Agent submits an observation report describing a suspected temporal irregularity. TARS automatically triggers AI analysis (UC-08) upon submission. |
| **Trigger** | Agent fills in the report form and clicks Submit. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. Agent has not reached the monthly report limit for their plan.
- PRE-3. Agent has access to the selected timeline under their subscription plan.

**Postconditions**
- POST-1. Observation report saved in the database with status PENDING_ANALYSIS.
- POST-2. AI analysis triggered asynchronously (UC-08).

**Normal Flow**
1. TARS displays the report form with description, year, keywords, and timeline fields.
2. Agent fills in the fields and clicks Submit.
3. TARS validates that at least one field is filled.
4. TARS checks monthly report limit and timeline access for the agent's plan.
5. TARS checks for a duplicate report (same agent, same timeline, same year).
6. TARS saves the report with status PENDING_ANALYSIS and returns immediately.
7. TARS dispatches AI analysis asynchronously — ENTERPRISE requests routed to the priority executor, FREE/PRO to the standard executor (UC-08).
8. Agent receives the saved report response; analysis result arrives later via WebSocket push to `/topic/analysis/{agentId}`.

**Alternative Flows**
- A1 Required fields missing: Agent submits with no fields filled. TARS displays "At least one field must be filled" and blocks submission.
- A2 Duplicate report: Agent submits a report for a timeline and year they already have an active report for (PENDING_ANALYSIS or CONFIRMED status). Backend returns 409; TARS displays the conflict message.

**Exceptions**
- E1 Monthly limit reached: Agent has reached their plan's report limit. Backend returns 429; TARS displays "Monthly report limit reached. Upgrade your plan."
- E2 Timeline not accessible: Agent selects a timeline not included in their plan. Backend returns 403; TARS displays the error message.
- E3 OpenAI API unavailable: Report is saved successfully but analysis fails. Analysis is saved with status FAILED and report is marked REJECTED. Report is not retried automatically.
- E4 Database Unreachable: `DataAccessException` caught by `GlobalExceptionHandler`, returns 503.

---

## UC-06: Save Anomaly as Draft
| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Database |
| **Description** | Agent saves a partially completed anomaly report as a draft. No AI analysis is triggered. The draft can be retrieved and completed later via UC-07. |
| **Trigger** | Agent clicks Save Draft on the report form. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. Agent has entered at least one field in the report form.

**Postconditions**
- POST-1. Draft saved in the database with status DRAFT, linked to the agent's account.

**Normal Flow**
1. Agent has partially filled in the report form.
2. Agent clicks Save Draft.
3. TARS validates that at least one field (description, year, or keywords) is filled.
4. TARS saves the form data as a draft record with status DRAFT.
5. Draft appears immediately in the agent's draft list.

**Alternative Flows**
- A1 Validation Failure: Agent clicks Save Draft with no fields filled. Backend returns 400; TARS displays "At least one field must be filled" and blocks the save.

**Exceptions**
- E1 Database Unreachable: `DataAccessException` caught by `GlobalExceptionHandler`, returns 503. TARS displays a service unavailable error.

---

## UC-07: View, Edit, and Delete Draft
| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Database |
| **Description** | Agent views their list of saved drafts, selects one to resume editing, submit, or delete. |
| **Trigger** | Agent navigates to the Drafts tab on the dashboard. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. At least one draft exists for this agent.

**Postconditions**
- POST-1. Selected draft is loaded into the form with all previously saved fields pre-filled.
- POST-2. Agent can update, submit (UC-05), or delete the draft.

**Normal Flow**
1. TARS displays the agent's draft list, polled every 5 seconds.
2. Agent selects a draft; TARS pre-fills the form with the saved data.
3. Agent edits fields and clicks Save Draft to update, or Submit to promote the draft to a submission.
4. On submit: draft status changes to PENDING_ANALYSIS and UC-08 is triggered. Draft disappears from the draft list.
5. On update: draft is saved with the new values and remains in the draft list.

**Alternative Flows**
- A1 Delete draft: Agent clicks Delete on a draft. TARS deletes the record immediately (no confirmation dialog) and reloads the draft list.

**Exceptions**
- E1 Database Unreachable: `DataAccessException` caught by `GlobalExceptionHandler`, returns 503.

---

## UC-08: AI Analysis of New Anomaly
| | |
|---|---|
| **Primary actors** | OpenAI API (GPT-4o-mini) |
| **Secondary actors** | Database |
| **Description** | Triggered automatically when a new observation report is submitted (UC-05). OpenAI API analyzes the observation and returns a structured result. ENTERPRISE agents are routed to a dedicated higher-capacity thread pool for priority processing. |
| **Trigger** | TARS saves a new observation report with status PENDING_ANALYSIS (auto-triggered from UC-05, step 7). |

**Preconditions**
- PRE-1. Observation report saved in the database with status PENDING_ANALYSIS.
- PRE-2. OpenAI API is available and responding.
- PRE-3. OpenAI API key is configured and valid on the backend.

**Postconditions**
- POST-1. AI analysis saved in the database linked to the observation report.
- POST-2. If `confirmed = true`, a new Anomaly record is created with AI-determined type and paradox risk.
- POST-3. Analysis result pushed to the agent via WebSocket (`/topic/analysis/{agentId}`).

**Normal Flow**
1. TARS queries the database for existing reports from the same timeline within a ±100 year window, including all statuses (CONFIRMED, REJECTED, PENDING_ANALYSIS), excluding only the current report ID (not the entire agent's history).
2. TARS builds the OpenAI prompt: analyst role definition, security directive against prompt injection, related historical reports, new report data, expected JSON response schema.
3. ENTERPRISE requests are dispatched to `priorityExecutor` (4–8 threads); FREE and PRO requests to `standardExecutor` (2–4 threads).
4. TARS sends the prompt to the OpenAI API endpoint via `OpenAIHttpClient`.
5. OpenAI returns a JSON response: `confirmed`, `type` (PAR/DUP/DEV/RFT/ERO/LOP), `paradoxRisk` (LOW/MEDIUM/HIGH/CRITICAL), `contributingReportIds`, `explanation`, `injectionDetected`.
6. TARS parses the JSON response.
7. TARS saves the analysis and updates the report status (CONFIRMED or REJECTED). If `confirmed = true`, TARS links to an existing anomaly (if contributing report sets overlap ≥67%) or creates a new one.
8. TARS pushes the full updated report DTO to the agent via WebSocket.

**Alternative Flows**
- A1 Prompt injection detected: OpenAI sets `injectionDetected: true`. TARS quarantines the report (status FLAGGED), saves the explanation, and pushes the result to the agent. No anomaly is created.
- A2 Non-JSON or malformed response: JSON parsing fails on the first attempt. TARS retries with a stricter prompt. If the second attempt also fails, the analysis is saved with status UNRESOLVED and the report is marked REJECTED.
- A3 503 Service Unavailable: OpenAI returns 503 error. TARS implements exponential backoff retry (2s, 4s, 6s) up to 3 attempts before failing.

**Exceptions**
- E1 OpenAI API unreachable or timeout: Exception caught in `doAnalyze`; analysis saved with status FAILED, result pushed to agent. Report is not retried automatically.

---

## UC-09: View Timeline Graph
| | |
|---|---|
| **Primary actors** | Agent, Supervisor |
| **Secondary actors** | Database |
| **Description** | User views a 2D SVG graph of confirmed anomalies plotted by year (X axis) against timeline (Y axis). Each anomaly is a colored dot based on paradox risk. Agents see locked lanes for inaccessible timelines. Supervisors see all timelines as accessible. |
| **Trigger** | User navigates to the Timeline Graph page. |

**Preconditions**
- PRE-1. User is authenticated.
- PRE-2. At least one confirmed anomaly exists in the database.
- PRE-3. For Agents: at least one timeline is accessible under their subscription plan.

**Postconditions**
- POST-1. Graph is rendered with all anomaly dots matching current filters.
- POST-2. Each timeline is shown as a horizontal lane; inaccessible lanes are greyed out with a `⊘` prefix.

**Normal Flow**
1. TARS fetches all timelines via `GET /api/graph/timelines` — agents receive accessible flags, supervisors see all as accessible.
2. TARS fetches anomaly data via `GET /api/graph/anomalies` with no filters applied.
3. Angular renders the SVG graph: X axis = year (auto-scaled to data range), Y axis = timelines with confirmed anomalies.
4. Each anomaly is displayed as a colored dot by paradox risk: LOW (light green), MEDIUM (yellow), HIGH (orange), CRITICAL (red).
5. Inaccessible timeline lanes are shown with a grey background and `⊘` prefix on the label.
6. User hovers over a dot to see a tooltip: anomaly ID, year, type, paradox risk, timeline name.

**Alternative Flows**
- A1 No anomalies match current filters: TARS displays "NO_ANOMALIES_FOUND" and prompts the user to adjust filters.

**Exceptions**
- E1 API error: HTTP request fails for any reason. TARS displays "Failed to load anomaly data." and stops rendering.

---

## UC-10: Filter Anomalies on Graph
| | |
|---|---|
| **Primary actors** | Agent, Supervisor |
| **Secondary actors** | Database |
| **Description** | While viewing the timeline graph (UC-09), user applies filters to narrow which anomaly dots are displayed. The graph updates when the user clicks Apply Filters. |
| **Trigger** | User interacts with the filter panel on the graph page. |

**Preconditions**
- PRE-1. User is authenticated.
- PRE-2. Timeline graph (UC-09) is currently rendered.

**Postconditions**
- POST-1. Graph displays only anomaly dots matching all active filter criteria.

**Normal Flow**
1. TARS displays the filter panel with four controls: Timeline (dropdown), Paradox Risk (LOW/MEDIUM/HIGH/CRITICAL dropdown), Year From (number input), Year To (number input).
2. User sets one or more filter values.
3. User clicks `APPLY_FILTERS`.
4. TARS sends a new `GET /api/graph/anomalies` request with the selected values as query parameters.
5. Graph re-renders with only the matching anomaly dots.

**Alternative Flows**
- A1 Clear filters: User clicks `RESET`. TARS clears all filter values and reloads the graph with no filters applied.

**Exceptions**
- E1 API error: Filtered request fails. TARS displays "Failed to load anomaly data."

---

## UC-11: Alerts for Critical Anomalies
| | |
|---|---|
| **Primary actors** | Supervisor |
| **Secondary actors** | Database |
| **Description** | TARS automatically generates an in-app alert when an AI analysis returns paradox risk HIGH or CRITICAL. Alerts appear as toast notifications in the bottom-left corner of the Supervisor dashboard, visible to all active Supervisors simultaneously. |
| **Trigger** | UC-08 saves a confirmed anomaly with `paradoxRisk = HIGH` or `paradoxRisk = CRITICAL`. |

**Preconditions**
- PRE-1. AI analysis has been completed and saved for a confirmed anomaly.
- PRE-2. Anomaly paradox risk is HIGH or CRITICAL.
- PRE-3. No alert already exists for this anomaly (duplicate guard).

**Postconditions**
- POST-1. Alert record created in the database linked to the anomaly.
- POST-2. Alert pushed to all active Supervisor sessions via WebSocket and displayed as a toast.
- POST-3. Alert persists in the database until acknowledged.

**Normal Flow**
1. `GeminiService` calls `alertService.triggerIfCritical(anomaly)` after saving a confirmed anomaly.
2. `AlertService` checks paradox risk and creates an alert record in the database.
3. TARS pushes the `AlertDTO` to `/topic/alerts` via WebSocket.
4. All Supervisors with an active session receive the toast immediately via their WebSocket subscription.
5. On Supervisor dashboard load, `AlertToasts` calls `GET /api/admin/alerts` to fetch all unacknowledged alerts — this covers the case where the Supervisor was offline when the alert was created.
6. Supervisor reads the alert toast showing: anomaly ID, type, paradox risk, timeline, year.
7. Supervisor clicks `ACKNOWLEDGE`; TARS sends `PATCH /api/admin/alerts/{id}/acknowledge`.
8. Alert is marked acknowledged in the database and removed from all active Supervisor sessions via `/topic/alerts/acknowledged` WebSocket push.

**Alternative Flows**
- A1 Duplicate anomaly alert: `AlertService` detects an alert already exists for this anomaly and skips creation silently.

**Exceptions**
- E1 WebSocket push failure: Alert is saved in the database but the real-time toast does not appear. Alert is shown on the next Supervisor dashboard load via the `getUnacknowledged()` REST call.

---
## UC-12: Upgrade Subscription
| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Stripe API, Database |
| **Description** | Agent selects a higher-tier subscription plan to unlock access to more timelines and a higher monthly report quota. Payment is processed via Stripe. Access is granted immediately upon payment confirmation via Stripe webhook. |
| **Trigger** | Agent navigates to the Subscription page from the sidebar. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. Agent's current plan is not already ENTERPRISE.
- PRE-3. Stripe API key is configured on the backend (test mode).

**Postconditions**
- POST-1. Subscription record updated in the database with the new plan, billing cycle, and expiry date.
- POST-2. New timeline quota and monthly report limit are immediately active.
- POST-3. For ENTERPRISE upgrades: all timelines are automatically granted access.

**Normal Flow**
1. TARS displays the Subscription page: current plan, timelines used/allowed, reports used/allowed, renewal date.
2. Agent reviews the available upgrade plans (PRO, ENTERPRISE) and selects a billing cycle (MONTHLY or ANNUAL).
3. Agent clicks `UPGRADE_SYSTEM`.
4. TARS calls `POST /api/reports/subscription/upgrade`, which creates a Stripe Checkout session and returns the redirect URL.
5. Agent is redirected to the Stripe-hosted payment page.
6. Agent completes payment on Stripe.
7. Stripe sends a webhook to the TARS backend; `activateSubscription()` updates the subscription record, sets expiry date, and calls `timelineAccessService.onUpgrade()`.
8. TARS pushes the updated `SubscriptionDTO` to the agent via WebSocket (`/topic/subscription/{agentId}`).
9. Agent is redirected back to `/subscription?upgraded=true`; TARS displays an upgrade confirmation banner.
10. For ENTERPRISE upgrades: `onUpgrade()` automatically grants access to all timelines in the database. For PRO upgrades: existing active timelines are preserved; agent must manually add additional timelines via the timeline management interface (up to 5 total).

**Alternative Flows**
- A1 Payment declined or abandoned: Handled entirely by the Stripe-hosted page. Agent is returned to the Stripe form to retry or cancel. TARS receives no webhook and no changes are made.
- A2 Already on ENTERPRISE: Agent attempts to upgrade from ENTERPRISE to PRO. Backend returns 400; TARS displays "Already on ENTERPRISE plan".

**Exceptions**
- E1 Stripe API timeout: `createCheckoutSession()` throws; TARS returns 503 and displays "Payment service unavailable. Try again."
- E2 Webhook not received: Payment succeeds on Stripe but the backend is unreachable. Subscription is not updated until the webhook is replayed or manually resolved in the Stripe dashboard.

---

## UC-13: Cancel Subscription
| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Stripe API, Database |
| **Description** | Agent cancels their current paid subscription. The plan remains active until the end of the current billing cycle, after which a Stripe webhook triggers reversion to FREE and timeline access is reduced accordingly. |
| **Trigger** | Agent clicks `CANCEL_SUBSCRIPTION` on the Subscription page. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. Agent has an active paid subscription (PRO or ENTERPRISE).
- PRE-3. Cancellation has not already been scheduled.

**Postconditions**
- POST-1. Stripe subscription is marked `cancel_at_period_end = true`.
- POST-2. `cancellationScheduled = true` saved in the database.
- POST-3. Agent retains current plan access until the billing cycle ends.
- POST-4. When the period ends, Stripe fires `customer.subscription.deleted`; TARS reverts plan to FREE and deactivates all timelines beyond the first (oldest granted) via `onDowngradeToFree()`.

**Normal Flow**
1. TARS displays the Subscription page with a `CANCEL_SUBSCRIPTION` button (shown only for paid, non-cancelled plans).
2. Agent clicks `CANCEL_SUBSCRIPTION`.
3. TARS displays an inline confirmation showing: "Plan reverts to FREE at end of billing cycle. Active timelines beyond 1 will be deactivated."
4. Agent clicks `CONFIRM_CANCEL`.
5. TARS calls `POST /api/reports/subscription/cancel`; backend calls Stripe to set `cancelAtPeriodEnd = true` and sets `cancellationScheduled = true` in the database.
6. TARS displays a confirmation message with the exact expiry date: "Subscription cancelled. Access remains until {date}."
7. The `CANCEL_SUBSCRIPTION` button is hidden; subscription status shows `(CANCELS)` next to the renewal date.

**Alternative Flows**
- A1 Agent aborts confirmation: Agent clicks `ABORT` on the confirmation dialog. No changes are made.
- A2 FREE plan attempt: Agent attempts to cancel a FREE plan. Backend returns 400; TARS displays "No active paid subscription to cancel".

**Exceptions**
- E1 Stripe API unavailable: `cancelSubscription()` throws; TARS returns 503 and displays "Cancellation failed. Try again." No cancellation is processed.
