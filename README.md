# TARS — Temporal Anomaly Reporting System

TARS is a full-stack web application for reporting, analyzing, and correlating temporal anomalies across multiple timelines. The system uses AI-powered analysis to evaluate observation reports, classify anomaly types, and detect patterns through historical context correlation.

## Architecture Overview

TARS implements an event-driven, asynchronous architecture with real-time notifications:

- **Backend**: Spring Boot 3.2.5 with Java 21, PostgreSQL, Redis
- **Frontend**: Angular 21.2.0 with TypeScript, Tailwind CSS 4.x
- **AI Integration**: OpenAI API (GPT-4o-mini) for anomaly analysis
- **Real-time**: WebSocket (STOMP over SockJS) for live analysis updates
- **Async Processing**: Dual thread-pool executor for subscription-based queue prioritization

## Quick Start

### Prerequisites

- Java JDK 21+
- Node.js 18+ and npm 11+
- PostgreSQL 14+
- Redis 6+
- Docker & Docker Compose (optional)
- OpenAI API key

### Local Development Setup

**Backend:**
```bash
cd backend
# Configure application.properties with database, Redis, and OpenAI credentials
./gradlew bootRun
```

**Frontend:**
```bash
cd frontend
npm install
ng serve
```

**Docker Compose (full stack):**
```bash
docker compose up --build
```

Services exposed:
- Backend: http://localhost:8080
- Frontend: http://localhost:4200
- PostgreSQL: localhost:5432
- Redis: localhost:6379

## Technical Stack

### Backend
- **Java 21** with Spring Boot 3.2.5
- **Hibernate ORM** with Spring Data JPA
- **PostgreSQL** for persistent storage
- **Redis** for JWT denylist and session management
- **WebSocket** (STOMP over SockJS) for real-time push notifications
- **Spring Security** with JWT authentication
- **OpenAI API** (GPT-4o-mini) for AI-powered anomaly analysis

### Frontend
- **Angular 21.2.0** with TypeScript
- **Tailwind CSS 4.x** for styling
- **RxJS** for reactive state management
- **STOMP.js + SockJS** for WebSocket client
- **Custom SVG rendering** for timeline visualization

### Infrastructure
- **Docker Compose** for container orchestration
- **Gradle** for backend dependency management
- **npm** for frontend dependency management

## AI-Powered Anomaly Analysis

### Analysis Pipeline

When an agent submits an observation report, the system triggers an asynchronous AI analysis flow:

1. **Event-Driven Dispatch**: `ReportSubmittedEvent` is published after transaction commit via `@TransactionalEventListener(phase = AFTER_COMMIT)`
2. **Queue Selection**: Enterprise agents route to priority executor (4-8 threads), others to standard executor (2-4 threads)
3. **Historical Context Retrieval**: Queries reports within ±100 years on the same timeline, excluding only the current report
4. **Prompt Construction**: Sends observation + historical context + anomaly type definitions to OpenAI API
5. **Response Parsing**: Attempts JSON parse; on failure, retries with strict formatting instruction
6. **Anomaly Correlation**: Links to existing anomalies if contributing report sets overlap ≥67%
7. **Verification Logic**: Anomalies require reports from ≥2 distinct agents to become verified
8. **Real-time Push**: WebSocket notification sent to `/topic/analysis/{agentId}`

### Anomaly Classification

The system classifies temporal anomalies into six types:

| Type | Code | Description |
|------|------|-------------|
| Causal Paradox | PAR | Cause and effect are reversed |
| Temporal Duplication | DUP | Same object/person exists simultaneously in two instances |
| Timeline Deviation | DEV | Event occurred differently from reference records |
| Temporal Rift | RFT | Physical fracture in space-time continuum |
| Temporal Erosion | ERO | Existence/memory gradually disappears |
| Temporal Loop | LOP | Sequence of events repeats indefinitely |

### Prompt Injection Detection

The AI prompt includes security directives to detect explicit prompt injection attempts. Reports flagged with `injectionDetected: true` are quarantined with status `FLAGGED` rather than rejected.

## Key Architectural Features

### Event-Driven Async Processing

The system uses Spring's `@TransactionalEventListener` with `AFTER_COMMIT` phase to ensure AI analysis only triggers after the report is persisted. This prevents race conditions where the analysis thread might query for a report that hasn't been committed yet.

### Dual Thread-Pool Executor

`AsyncConfig` defines two thread pools for subscription-based queue prioritization:

- **Standard Executor** (FREE/PRO): Core 2, Max 4, Queue 100
- **Priority Executor** (ENTERPRISE): Core 4, Max 8, Queue 50

Enterprise subscribers receive faster analysis throughput through dedicated resources.

### Anomaly Verification System

Anomalies require corroboration from multiple independent agents:

- Single-agent reports create unverified anomalies
- When a second distinct agent submits a report with overlapping contributing reports (≥67%), the anomaly upgrades to verified
- This prevents false positives from hallucinating or biased agents

### 67% Overlap Threshold for Anomaly Merging

The system uses a 67% Jaccard-like overlap threshold to determine if two anomalies represent the same phenomenon:

```java
double overlap = (double) intersection.size() / Math.min(newContributing.size(), existingPool.size());
```

This accounts for timeline divergence while preventing over-merging.

### JWT Denylist in Redis

Token invalidation uses a Redis set with TTL matching token expiration:

- O(1) lookup complexity vs database queries
- Automatic cleanup via TTL
- Supports logout without stateful session management

### WebSocket Real-Time Notifications

Analysis results are pushed via STOMP to `/topic/analysis/{agentId}`:

- No polling overhead
- Near-instant delivery of analysis completion
- Supports concurrent analysis sessions

### Retry Logic with Exponential Backoff

OpenAI API calls implement retry logic for 503 errors:

- 3 max attempts
- Exponential backoff: 2s, 4s, 6s
- Prevents transient failures from blocking analysis

### Timeline Subscription Gating

Agents can only report from timelines included in their subscription:

- FREE: 1 timeline, 20 reports/month
- PRO: 5 timelines, 200 reports/month
- ENTERPRISE: unlimited timelines and reports

Access control enforced at service layer before report submission.

## Project Structure

```
TARS-Temporal-Anomaly-Reporting-System/
├── backend/
│   ├── src/main/java/com/tars/
│   │   ├── config/           # Async, WebSocket, CORS, Security configs
│   │   ├── controller/       # REST endpoints
│   │   ├── service/          # Business logic (GeminiService, ReportService)
│   │   ├── model/            # JPA entities and DTOs
│   │   ├── repository/       # Spring Data JPA repositories
│   │   └── exception/        # Custom exceptions
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── logback-spring.xml
│   └── build.gradle
├── frontend/
│   ├── src/app/
│   │   ├── core/             # Guards, services (auth, websocket, graph)
│   │   ├── features/         # Feature modules (auth, agent, supervisor, graph)
│   │   └── shared/           # Shared components
│   └── package.json
├── docs/                     # Specifications, UML diagrams
├── docker-compose.yml
└── README.md
```

## Configuration

### Backend (application.properties)

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/tars_database
spring.datasource.username=tars_user
spring.datasource.password=tars_password
spring.jpa.hibernate.ddl-auto=update

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# OpenAI API
openai.api.key=YOUR_OPENAI_API_KEY
openai.model=gpt-4o-mini

# Server
server.port=8080
```

### Frontend Environment

Create `frontend/src/environments/environment.ts`:
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  wsUrl: 'ws://localhost:8080/ws'
};
```

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | /api/auth/login | Authenticate and receive JWT |
| POST | /api/auth/logout | Invalidate JWT via Redis denylist |
| POST | /api/reports | Submit observation report |
| GET | /api/reports/{id} | Fetch report details |
| GET | /api/anomalies | List confirmed anomalies |
| WS | /ws | WebSocket endpoint for real-time updates |

See `docs/specifications.md` for complete API documentation.

## Security

- **JWT Authentication**: HTTP-only cookies with Redis-based denylist for O(1) invalidation
- **Password Hashing**: BCrypt with Spring Security
- **CORS**: Configured for development origins; restrict in production
- **Prompt Injection Detection**: AI analysis includes security directives to flag manipulation attempts
- **Subscription Enforcement**: Timeline access gated at service layer based on plan type

## Testing

**Backend:**
```bash
cd backend
./gradlew test
```

**Frontend:**
```bash
cd frontend
npm test
```

## Frontend Visualization

The Angular frontend includes a custom SVG-based timeline graph (`features/graph/graph.ts`) that:

- Renders anomalies as colored dots on timeline lanes
- Color-codes by paradox risk (LOW→green, MEDIUM→yellow, HIGH→orange, CRITICAL→red)
- Supports filtering by timeline, risk level, and year range
- Uses Angular signals for reactive state management
- Implements responsive layout with ResizeObserver

## Known Limitations

- OpenAI API rate limiting may require queue throttling at high volume
- Stripe payment integration planned for subscription management
- Timeline visualization currently limited to 2D lane-based view

## License

This project is licensed under the terms specified in [`LICENSE`](./LICENSE).
