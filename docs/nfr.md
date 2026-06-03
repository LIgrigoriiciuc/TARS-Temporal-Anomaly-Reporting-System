# TARS — Non-Functional Requirements
| ID | Category | Requirement | Acceptance Criteria |
|---|---|---|---|
| NFR-01 | Performance | Response time for operations | < 500ms for 95% of REST API requests under normal load |
| NFR-02 | Performance | AI analysis response time | Result delivered via WebSocket push after async processing. UI returns immediately on submission; analysis result updates the report in place without blocking. |
| NFR-03 | Security | Authentication and authorization | Role-based access with 2 roles (Agent, Supervisor). JWT expires after 8 hours. Tokens added to server-side Redis denylist on logout. Role enforced per endpoint prefix in `JwtFilter`. |
| NFR-04 | Security | Password storage | Passwords hashed with BCrypt cost factor 12 (2^12 = 4096 rounds). |
| NFR-05 | Reliability | Application uptime | >= 99% uptime in production environment |
| NFR-06 | Scalability | Data volume | System performs without degradation up to 100,000 anomaly records |
| NFR-07 | Usability | UI responsiveness | Responsive web layout (Angular + Tailwind CSS). New user learns core functions in < 30 minutes |
| NFR-08 | Maintainability | Code architecture | Strict layered architecture: Angular SPA / Spring Boot REST API / Hibernate DAO. ORM mandatory for all DB access |
| NFR-09 | Portability | Deployment | Containerized via Docker Compose. Runs on any OS with Docker and JDK 21+ |
| NFR-10 | Interface | Stripe integration | Subscription payments via Stripe (test mode). Webhook confirms plan activation within 5 seconds of payment |
| NFR-11 | Interface | OpenAI API integration | Application uses a single shared API key for GPT-4o-mini. ENTERPRISE requests routed to a dedicated `priorityExecutor` thread pool (4–8 threads); FREE and PRO requests use `standardExecutor` (2–4 threads). On non-parseable JSON response, one automatic retry is performed with a stricter prompt before marking analysis as UNRESOLVED. On 503 errors, exponential backoff retry (2s, 4s, 6s) up to 3 attempts. |
| NFR-12 | Security | Input validation & injection protection | Server-side validation via Spring Boot `@Valid` enforced independently of client input. Hibernate ORM prevents SQL injection. HttpOnly cookies prevent XSS-based token theft. Free-text agent input is labeled `[AGENT INPUT]` in OpenAI prompts and accompanied by a security directive; the model flags injection attempts via `injectionDetected` and affected reports are quarantined with status FLAGGED. Client-side validation handled by Angular. |
| NFR-13 | Security | Secure session handling | JWT tokens stored as HttpOnly cookies; browser transmits token automatically with every request; on logout server clears the cookie via Set-Cookie header and adds the token to a Redis denylist ensuring immediate invalidation; HttpOnly flag prevents JavaScript access eliminating XSS-based token theft |
| NFR-14 | Testing | Test coverage | Minimum 70% code coverage on business logic layer measured via JaCoCo; JUnit 5 + Mockito; AI analysis parsing and subscription enforcement logic fully covered. OpenAI API interactions tested via mocking (Mockito); real API calls excluded from unit tests and tagged `integration` |
| NFR-15 | Maintainability | Audit logging | All authentication attempts logged (success and failure); all subscription changes and Stripe webhook events logged; 90-day retention; daily log rotation configured via Logback `TimeBasedRollingPolicy` |
| NFR-16 | Portability | Browser compatibility | Chrome, Firefox, Safari, Edge — last 2 versions each |
---

## Notes

**Redis JWT Denylist:** Redis is used as an in-memory store for the JWT denylist, enabling O(1) token invalidation on logout without database overhead. This ensures that logged-out tokens are immediately invalid regardless of their remaining expiry time.
