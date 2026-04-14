# TARS — Non-Functional Requirements

| ID | Category | Requirement | Acceptance Criteria |
|---|---|---|---|
| NFR-01 | Performance | Response time for operations | < 500ms for 95% of REST API requests under normal load |
| NFR-02 | Performance | AI analysis response time | Result displayed in < 10s. On timeout, status shown as Pending without blocking the UI |
| NFR-03 | Security | Authentication and authorization | Role-based access with 2 roles (Agent, Supervisor). JWT expires after 8 hours. Tokens added to server-side denylist on logout |
| NFR-04 | Security | Password storage | Passwords hashed with bcrypt (cost factor >= 12) |
| NFR-05 | Reliability | Application uptime | >= 99% uptime in production environment |
| NFR-06 | Scalability | Data volume | System performs without degradation up to 100,000 anomaly records |
| NFR-07 | Usability | UI responsiveness | Responsive web layout (Angular + Tailwind CSS). New user learns core functions in < 30 minutes |
| NFR-08 | Maintainability | Code architecture | Strict layered architecture: Angular SPA / Spring Boot REST API / Hibernate DAO. ORM mandatory for all DB access |
| NFR-09 | Portability | Deployment | Containerized via Docker Compose. Runs on any OS with Docker and JDK 17+ |
| NFR-10 | Interface | Stripe integration | Subscription payments via Stripe (test mode). Webhook confirms plan activation within 5 seconds of payment |
| NFR-11 | Interface | Gemini API integration | Application uses a single shared API key. ENTERPRISE requests processed with priority. Retry logic implemented for timeouts |
| NFR-12 | Security | Input validation & injection protection | Server-side sanitization of all free-text fields; Hibernate ORM prevents SQL injection; XSS protection enforced; anomaly description fields sanitized before Gemini prompt construction to prevent prompt injection. Angular reactive forms handle client-side validation; server-side validation enforced independently via Spring Boot regardless of client input |
| NFR-13 | Security | Secure session handling | JWT tokens stored as HttpOnly cookies; browser transmits token automatically with every request; on logout server clears the cookie via Set-Cookie header and adds the token to a Redis denylist ensuring immediate invalidation; HttpOnly flag prevents JavaScript access eliminating XSS-based token theft |
| NFR-14 | Testing | Test coverage | Minimum 70% code coverage on business logic layer measured via JaCoCo; JUnit 5 + Mockito; AI analysis parsing and subscription enforcement logic fully covered. Gemini API interactions tested via mocking (Mockito); real API calls excluded from unit tests |
| NFR-15 | Maintainability | Audit logging | All authentication attempts logged (success and failure); all subscription changes and Stripe webhook events logged; 90-day retention; log rotation enabled via Logback |
| NFR-16 | Portability | Browser compatibility | Chrome, Firefox, Safari, Edge — last 2 versions each |

---

## Notes

**Redis JWT Denylist:** Redis is used as an in-memory store for the JWT denylist, enabling O(1) token invalidation on logout without database overhead. This ensures that logged-out tokens are immediately invalid regardless of their remaining expiry time.
