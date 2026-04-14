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
- POST-1. User is authenticated with an active JWT session.
- POST-2. User is redirected to the role-specific dashboard.

**Normal Flow**
1. TARS displays the login screen with email and password fields.
2. User enters their email address.
3. User enters their password.
4. User clicks Login.
5. TARS validates credentials against the database.
6. TARS issues a JWT token set as an HttpOnly cookie.
7. TARS redirects the user to the role-specific dashboard.

**Alternative Flows**
- A1 Invalid Credentials: User enters wrong email/password. TARS displays "Invalid credentials" and returns to step 1.
- A2 Password Recovery: At step 3, user selects "Forgot password." TARS triggers the reset email workflow.

**Exceptions**
- E1 Account Locked: After 5 failed attempts, the system blocks the account for 15 minutes.
- E2 Database Timeout: TARS cannot reach the database; displays a technical error.

---

## UC-02: Logout

| | |
|---|---|
| **Primary actors** | Agent, Supervisor |
| **Secondary actors** | Database |
| **Description** | Authenticated user securely terminates their active session. TARS invalidates the JWT token server-side and redirects to the login screen. |
| **Trigger** | User clicks the Logout button from any page in the application. |

**Preconditions**
- PRE-1. User is authenticated with an active JWT session.

**Postconditions**
- POST-1. JWT token is invalidated on the server (added to denylist).
- POST-2. Token is cleared from the browser.
- POST-3. User is redirected to the login screen.

**Normal Flow**
1. User clicks the Logout button.
2. TARS sends a logout request to the backend.
3. Backend adds the JWT token to the denylist, invalidating it server-side.
4. TARS instructs the server to clear the JWT cookie.
5. TARS redirects the user to the login screen.

**Alternative Flows**
- A1 Session Timeout: System detects inactivity and triggers the logout flow automatically.

**Exceptions**
- E1 Redis Connection Failure: Backend cannot reach the denylist store. TARS clears the local cookie but the token remains valid server-side until natural expiry.
- E2 Network Error: Request fails to reach the server. TARS forces a local cookie clear and redirect to login.

---

## UC-03: Create User Account

| | |
|---|---|
| **Primary actors** | Supervisor |
| **Secondary actors** | Database |
| **Description** | Supervisor creates a new user account and assigns a role (Agent or Supervisor). Upon creation, the new user receives their login credentials by email. |
| **Trigger** | Supervisor opens User Management and selects Add new user. |

**Preconditions**
- PRE-1. Supervisor is authenticated.
- PRE-2. The email address to be assigned does not already exist in the system.

**Postconditions**
- POST-1. New account is saved to the database with the assigned role and Active status.
- POST-2. Login credentials are sent to the new user's email address.

**Normal Flow**
1. TARS displays the user list with roles and statuses.
2. Supervisor selects Add new user.
3. TARS displays the creation form: username, email, role, temporary password.
4. Supervisor fills in all fields and clicks Save.
5. TARS validates all fields.
6. TARS creates the account with status Active.
7. TARS sends an email to the new user containing their login credentials.

**Alternative Flows**
- A1 Validation Error: Supervisor leaves a required field empty or uses a duplicate email. TARS highlights the field and blocks the Save action until corrected.
- A2 Required fields missing: TARS highlights empty fields and blocks saving.

**Exceptions**
- E1 SMTP Server Down: Account is created in the DB, but the invitation email fails to send. System logs a "Critical Mail Error."

---

## UC-04: Deactivate User Account

| | |
|---|---|
| **Primary actors** | Supervisor |
| **Secondary actors** | Database |
| **Description** | Supervisor deactivates an existing user account. The user can no longer log in, but all their anomaly reports and drafts are preserved in the system. |
| **Trigger** | Supervisor selects a user in User Management and clicks Deactivate. |

**Preconditions**
- PRE-1. Supervisor is authenticated.
- PRE-2. Target account exists in the system.
- PRE-3. Target account is currently Active.
- PRE-4. Target account is not the Supervisor's own account.

**Postconditions**
- POST-1. Target account status is set to Inactive in the database.
- POST-2. Any active JWT session for that user is invalidated immediately.

**Normal Flow**
1. TARS displays the user detail view with a Deactivate button.
2. Supervisor clicks Deactivate.
3. TARS displays a confirmation dialog.
4. Supervisor confirms the action.
5. TARS sets the account status to Inactive.
6. TARS invalidates any active JWT tokens for that user.
7. TARS displays the updated user list with the account marked Inactive.

**Alternative Flows**
- A1 Operation Cancelled: Supervisor clicks "Cancel" on the confirmation dialog. Flow ends with no changes.

**Exceptions**
- E1 Supervisor attempts to deactivate their own account: TARS blocks the action and displays a warning message.

---

## UC-05: Submit Temporal Observation Report

| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Gemini API |
| **Description** | Agent submits an observation report describing a suspected temporal irregularity. TARS automatically triggers AI analysis (UC-08) upon submission. If severity is critical, UC-11 (Alerts) is triggered. |
| **Trigger** | Agent selects Report new anomaly from the navigation menu. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. Agent's subscription plan allows the chosen timeline.
- PRE-3. Agent has not reached the monthly report limit for their plan.

**Postconditions**
- POST-1. Observation report saved in the database with status Pending Analysis.
- POST-2. AI analysis result attached to the report.
- POST-3. If AI-determined severity >= 4 or paradox_risk = critical, UC-11 (Alerts) is triggered.

**Normal Flow**
1. TARS displays the anomaly report form.
2. Agent enters a free-text description of what was observed.
3. Agent enters the temporal coordinate (year).
4. Agent selects the affected timeline from the list of plan-accessible timelines.
5. Agent enters keywords (persons, objects, locations).
6. Agent clicks Submit.
7. TARS validates all required fields are filled.
8. TARS saves the observation report with status Pending Analysis.
9. TARS sends description, keywords, year, timeline, and related DB records to Gemini API — triggers UC-08.
10. TARS receives the AI analysis and attaches it to the report.
11. TARS displays the AI analysis result: whether a confirmed anomaly was detected, its type, severity, and paradox risk.

**Alternative Flows**
- A1 Duplicate detected by AI: At step 10, AI identifies a very similar existing observation. TARS warns the agent and offers the option to link to the existing anomaly or continue as a separate report. Agent chooses. Returns to step 11.
- A2 Timeline not accessible under current plan: Agent attempts to select a restricted timeline. TARS shows a lock icon and an upgrade prompt. Agent is redirected to UC-12 if they choose to upgrade.
- A3 Required fields missing: TARS highlights empty fields and blocks submission.
- A4 Monthly report limit reached: TARS blocks submission and displays an upgrade prompt redirecting to UC-12.

**Exceptions**
- E1 Gemini API unavailable: Anomaly is saved without AI analysis. A background job retries after 15 minutes.

---

## UC-06: Save Anomaly as Draft

| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Database |
| **Description** | Agent saves a partially completed anomaly report as a draft. No AI analysis is triggered. The draft can be retrieved and completed later via UC-07. |
| **Trigger** | Agent clicks Save as draft on the anomaly report form. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. Agent has entered at least one field in the report form.

**Postconditions**
- POST-1. Draft saved in the database with status Draft, linked to the agent's account.

**Normal Flow**
1. Agent has partially filled in the anomaly report form.
2. Agent clicks Save as draft instead of Submit.
3. TARS validates that at least one field has been filled.
4. TARS saves the form data as a draft record with status Draft.
5. TARS displays a confirmation message.
6. Draft is visible in the agent's draft list (UC-07).

**Alternative Flows**
- A1 Validation Failure: Agent clicks save with zero fields filled. TARS highlights the requirement and blocks the save.

**Exceptions**
- E1 Database Connection Loss: TARS cannot reach the database to store the draft. A "Service Unavailable" error is displayed.

---

## UC-07: View and Resume Draft

| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Database |
| **Description** | Agent views their list of saved drafts and selects one to resume. The selected draft opens the standard anomaly report form pre-filled with the previously saved data. |
| **Trigger** | Agent navigates to the Drafts section from the navigation menu. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. At least one draft exists for this agent.
- PRE-3. Agent's subscription plan still allows access to the timeline saved in the draft.

**Postconditions**
- POST-1. Selected draft is opened in the report form with all previously saved fields pre-filled.
- POST-2. Agent can modify, submit (UC-05), or delete the draft.

**Normal Flow**
1. TARS displays the agent's draft list: date saved, timeline, partial description.
2. Agent selects a draft.
3. TARS opens the anomaly report form pre-filled with all saved data from the draft.
4. Agent reviews and completes the remaining fields.
5. Agent clicks Submit — flow continues as UC-05 from step 6.
6. Upon successful submission, the draft record is deleted from the database.

**Alternative Flows**
- A1 Delete draft: At step 2, agent clicks Delete on a draft. TARS displays a confirmation dialog. Agent confirms. TARS deletes the draft record and displays the updated list.
- A2 Draft references a timeline no longer accessible under the agent's plan: TARS shows a warning and prompts the agent to update the timeline selection or upgrade via UC-12.

**Exceptions**
- E1 Data Corruption: The draft record is malformed in the database. TARS fails to render the form and logs a technical error.

---

## UC-08: AI Analysis of New Anomaly

| | |
|---|---|
| **Primary actors** | Gemini API |
| **Secondary actors** | Database |
| **Description** | Triggered automatically when a new observation report is submitted via UC-05. Gemini API analyzes the raw observation data and determines: whether a confirmed anomaly exists, its classification, severity, correlations with existing records, and paradox risk. ENTERPRISE subscribers receive priority queue processing. |
| **Trigger** | TARS saves a new observation report with status Pending Analysis (auto-triggered from UC-05, step 9). |

**Preconditions**
- PRE-1. New observation report has been saved in the database with status Pending Analysis.
- PRE-2. Gemini API is available and responding.
- PRE-3. Application-level Gemini API key is configured and valid on the backend.

**Postconditions**
- POST-1. AI analysis saved in the database linked to the observation report. If a confirmed anomaly is detected, a new Anomaly record is created with the AI-determined type and severity.
- POST-2. Analysis result visible to the agent in the UI.

**Normal Flow**
1. TARS queries the database for all anomalies from the same timeline and nearby years, filtered by matching keywords.
2. TARS builds the prompt: AI role definition (temporal analyst), list of related existing anomalies, new anomaly data, response format instructions (JSON).
3. ENTERPRISE agent requests are placed in the priority queue; FREE and PRO requests in the standard queue.
4. TARS sends the prompt to the Gemini API endpoint.
5. Gemini API returns a JSON response: confirmed (true/false), type (PAR/DUP/DEV/RFT/ERO/LOP), severity (1–5), correlations (list of anomaly IDs), paradox_risk (low/medium/high/critical), explanation (text).
6. TARS parses the JSON response.
7. TARS saves the analysis to the AnomalyAnalysis table. If confirmed = true, TARS creates a new Anomaly record with the AI-determined attributes.
8. TARS displays the analysis result in the agent UI.

**Alternative Flows**
- A1 Non-JSON response from Gemini: At step 6, JSON parsing fails. TARS retries with a stricter prompt. If the second call succeeds, continues from step 7. If it also fails, the raw text is saved in the explanation field and type/severity fields are marked as Unresolved.

**Exceptions**
- E1 API timeout (> 10s): Analysis status saved as Pending. A background job retries after 15 minutes.
- E2 API quota exceeded or invalid key: Analysis marked as Failed. System administrator is notified automatically.

---

## UC-09: View Timeline Graph

| | |
|---|---|
| **Primary actors** | Agent, Supervisor |
| **Secondary actors** | Database |
| **Description** | User views a 2D graph of all anomalies accessible to them, plotted by year (X axis) against timeline ID (Y axis). Each anomaly is represented as a colored dot based on severity. Agents see only plan-accessible timelines. Supervisors see all timelines. |
| **Trigger** | User selects Timeline Graph from the main navigation menu. |

**Preconditions**
- PRE-1. User is authenticated.
- PRE-2. At least one anomaly exists in the database and is accessible to the user.
- PRE-3. For Agents: at least one timeline is accessible under their subscription plan.

**Postconditions**
- POST-1. Graph is rendered with all accessible anomaly data.
- POST-2. Each timeline is displayed as a horizontal lane on the Y axis.

**Normal Flow**
1. TARS fetches all anomalies accessible to the user's role and subscription plan via REST API.
2. Angular renders the 2D graph: X axis = year, Y axis = timeline ID.
3. Each anomaly is displayed as a colored dot: green (severity 1–2), yellow (3), orange (4), red (5).
4. Timelines outside the agent's subscription plan are shown as locked lanes with a lock icon.
5. User hovers over a dot to see the anomaly summary in a tooltip (type, severity, year, timeline).
6. User clicks a dot to open the full anomaly detail view including the attached AI analysis.

**Alternative Flows**
- A1 Zoom and pan: User scrolls to zoom into a specific year range. Graph re-renders for the selected range. All currently active filters (UC-10) remain applied.

**Exceptions**
- E1 API Timeout: The request for anomaly data takes > 10s. TARS displays a "Connection Timed Out" message.
- E2 Rendering Crash: The dataset is too large for the browser to handle. TARS displays a "Graph failed to load" fallback.

---

## UC-10: Filter Anomalies on Graph

| | |
|---|---|
| **Primary actors** | Agent, Supervisor |
| **Secondary actors** | Database |
| **Description** | While viewing the timeline graph (UC-09), user applies one or more filters to narrow down which anomaly dots are displayed. The graph updates in real time as filters are applied or removed. |
| **Trigger** | User interacts with the filter panel while the timeline graph is displayed. |

**Preconditions**
- PRE-1. User is authenticated.
- PRE-2. Timeline graph (UC-09) is currently rendered.
- PRE-3. At least one anomaly is visible on the graph before filtering.

**Postconditions**
- POST-1. Graph displays only the anomaly dots matching all active filter criteria.
- POST-2. Active filters are preserved if the user zooms or pans the graph.

**Normal Flow**
1. TARS displays the filter panel alongside the graph: keyword search, severity (1–5), anomaly type (PAR/DUP/DEV/RFT/ERO/LOP), status (Open/Under investigation/Resolved/Draft), year range.
2. User selects or enters one or more filter criteria.
3. TARS applies filters in real time and updates the graph.
4. Dots not matching the criteria are hidden. Matching dots remain visible.
5. User can add, modify, or remove individual filters at any time.
6. TARS updates the graph after each filter change.

**Alternative Flows**
- A1 Clear all filters: User clicks Clear filters. TARS removes all active filter criteria. Graph reverts to showing all accessible anomalies.

**Exceptions**
- E1 Database Query Error: A complex filter string causes a backend error. TARS blocks the update and displays a technical warning.

---

## UC-11: Alerts for Critical Anomalies

| | |
|---|---|
| **Primary actors** | Supervisor |
| **Secondary actors** | Database |
| **Description** | TARS automatically generates an in-app alert when an AI analysis returns severity >= 4 or paradox_risk = critical. Alerts appear in a dedicated notification panel visible to all Supervisors. |
| **Trigger** | AI analysis (UC-08) returns severity >= 4 or paradox_risk = critical. |

**Preconditions**
- PRE-1. AI analysis has been completed and saved for a new anomaly.
- PRE-2. Analysis result contains severity >= 4 or paradox_risk = critical.
- PRE-3. At least one Supervisor account exists in the system.

**Postconditions**
- POST-1. Alert record created in the database linked to the anomaly.
- POST-2. Alert visible in the Supervisor notification panel.

**Normal Flow**
1. TARS detects severity >= 4 or paradox_risk = critical in the completed AI analysis.
2. TARS creates an alert record in the database linked to the anomaly.
3. If at least one Supervisor session is active: alert appears immediately in the notification panel.
4. If no Supervisor session is active: alert is stored and shown on next Supervisor login.
5. Supervisor reads the alert in the notification panel.
6. Supervisor clicks View anomaly to open the full anomaly detail view.
7. Supervisor marks the alert as Acknowledged.

**Alternative Flows**
- A1 Delayed Notification: No Supervisor is online. Alert is stored and displayed immediately upon the next Supervisor login.

**Exceptions**
- E1 WebSocket Failure: The real-time notification bridge is broken. The alert is saved in the DB but does not pop up until the page is manually refreshed.

---

## UC-12: Upgrade Subscription

| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Stripe API, Database |
| **Description** | Agent selects a higher-tier subscription plan to unlock access to more timelines and a higher monthly report quota. Payment is processed via Stripe in test mode. Access is granted immediately upon payment confirmation via Stripe webhook. |
| **Trigger** | Agent opens the Subscription section from the dashboard, or is redirected from UC-05 or UC-09 after hitting a plan limit. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. Agent's current plan is not already ENTERPRISE.
- PRE-3. Stripe API key is configured on the backend (test mode).

**Postconditions**
- POST-1. Subscription record updated in the database with the new plan, billing cycle, and expiry date.
- POST-2. Accessible timeline count and monthly report quota updated immediately.

**Normal Flow**
1. TARS displays the Subscription page: current plan, timelines used/allowed, reports used/allowed, renewal date.
2. Agent reviews the available upgrade plans: PRO, ENTERPRISE.
3. Agent selects a plan and billing cycle (monthly or annual).
4. Agent clicks Upgrade.
5. TARS calls the Stripe Checkout API and redirects the agent to the Stripe-hosted payment page.
6. Agent completes payment on Stripe.
7. Stripe sends a payment_succeeded webhook to the TARS backend.
8. TARS verifies the Stripe signature and updates the Subscription record in the database.
9. TARS redirects the agent to the dashboard with a confirmation banner.
10. New timeline slots and report quota are immediately active.

**Alternative Flows**
- A1 Payment Declined: Stripe reports a card failure. User is returned to the plan selection page to try a different card.

**Exceptions**
- E1 Webhook Failure: Payment is successful on Stripe, but TARS backend is down or the signature is invalid. Subscription is not updated (requires manual Admin sync).
- E2 Stripe API Timeout: TARS cannot initiate the session. Displays "Service Unavailable."

---

## UC-13: Cancel Subscription

| | |
|---|---|
| **Primary actors** | Agent |
| **Secondary actors** | Stripe API, Database |
| **Description** | Agent cancels their current paid subscription. The plan remains active until the end of the current billing cycle, after which it reverts to FREE. Timelines and quotas are adjusted at plan expiry. |
| **Trigger** | Agent opens the Subscription section and clicks Cancel subscription. |

**Preconditions**
- PRE-1. Agent is authenticated.
- PRE-2. Agent has an active paid subscription (PRO or ENTERPRISE).
- PRE-3. Stripe API key is configured on the backend.

**Postconditions**
- POST-1. Stripe subscription is marked for cancellation at period end.
- POST-2. Agent retains current plan access until the billing cycle ends.
- POST-3. Plan reverts to FREE at end of billing cycle. Timeline access and report quota are reduced accordingly.

**Normal Flow**
1. TARS displays the Subscription page with current plan details and a Cancel subscription button.
2. Agent clicks Cancel subscription.
3. TARS displays a confirmation dialog showing the exact date the plan will revert to FREE.
4. Agent confirms the cancellation.
5. TARS calls the Stripe cancel API to schedule cancellation at period end.
6. TARS updates the subscription record with cancellation_scheduled = true.
7. TARS displays a confirmation message. Agent retains current plan until expiry.

**Alternative Flows**
- A1 Agent cancels the confirmation dialog: No changes are made. Subscription remains active.

**Exceptions**
- E1 Stripe API unavailable: TARS displays an error and suggests retrying. No cancellation is processed.
