# 🌌 TARS — Temporal Anomaly Reporting System

> **"In an infinite multiverse, someone has to watch the timeline."**

TARS is a sci-fi themed web application for agents operating across parallel timelines to report, monitor, and analyze temporal anomalies. The system sits outside the paradoxes it catalogs — a meta-linear observer immune to the very inconsistencies it documents. When an agent witnesses a causal paradox, timeline deviation, or spatio-temporal rift, they submit a report. The Gemini API instantly analyzes it, correlates it with historical observations, and renders a verdict: **anomaly confirmed or rejected**.

Built for a multiverse where causality is negotiable, where the same object can exist twice, and where history rewrites itself. TARS keeps the records straight.

---

## 🚀 Quick Start — Get TARS Running in 3 Steps

### 1️⃣ **Clone & Configure**

```bash
git clone https://github.com/your-org/TARS-Temporal-Anomaly-Reporting-System.git
cd TARS-Temporal-Anomaly-Reporting-System
```

#### Backend Setup
- Open the project in IntelliJ IDEA (or your favorite Java IDE)
- Update database credentials in `backend/src/main/resources/application.properties`:
  ```properties
  spring.datasource.url=jdbc:postgresql://localhost:5432/tars_database
  spring.datasource.username=your_db_user
  spring.datasource.password=your_db_password
  
  # Redis for JWT denylist
  spring.data.redis.host=localhost
  spring.data.redis.port=6379
  
  # Gemini API (obtain key from Google AI Studio)
  gemini.api.key=YOUR_GEMINI_API_KEY
  gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent
  ```
- Sync Gradle dependencies
- Run `TarsApplication.java` → Backend launches on `http://localhost:8080`

#### Frontend Setup
```bash
cd frontend
npm install
ng serve
```
- Frontend launches on `http://localhost:4200`

### 2️⃣ **Optional: Use Docker Compose (Recommended)**

```bash
docker compose up
```

This spins up the entire stack:
- **Spring Boot** backend (http://localhost:8080)
- **Angular** frontend (http://localhost:4200)
- **PostgreSQL** database
- **Redis** cache

### 3️⃣ **Log In & Report an Anomaly**

- Create an agent account or log in with supervisor credentials
- Navigate to the **Agent Dashboard**
- Click **"Report Anomaly"** and submit a temporal observation
- Watch the Gemini API analyze in real-time via WebSocket
- See if your anomaly is **confirmed** or **rejected**

---

## 🛠️ Technical Stack

### **Backend**
- **Language**: Java 21
- **Framework**: Spring Boot 3.2.5
- **ORM**: Hibernate (Spring Data JPA)
- **Security**: JWT tokens with Redis denylist for O(1) logout
- **Real-time**: WebSocket (STOMP) for live anomaly analysis updates
- **Email**: Spring Mail (Gmail SMTP)
- **Validation**: Spring Validation

### **Frontend**
- **Framework**: Angular 21.2.0 (TypeScript)
- **Styling**: Tailwind CSS 4.x
- **WebSocket**: STOMP.js + SockJS (real-time analysis notifications)
- **State Management**: RxJS observables
- **Build Tool**: Angular CLI + Vite

### **Data & Infrastructure**
- **Database**: PostgreSQL (time-series optimized)
- **Cache**: Redis (JWT denylist, session store)
- **API Integration**: Google Gemini 2.0 Flash (AI anomaly analysis)
- **Deployment**: Docker Compose (multi-container orchestration)
- **Dependency Management**: Gradle (backend), npm (frontend)

---

## 🤖 Gemini API Integration — How TARS Thinks

When an agent submits an observation report, the backend automatically triggers a **Gemini 2.0 Flash** analysis via the Google Generative AI API:

1. **Historical Context Retrieval**
   - Queries the database for related reports from other agents
   - Searches within a **50-year time window** around the report's year
   - Filters by timeline and keyword relevance
   - Excludes reports from the submitting agent (to prevent self-confirmation bias)

2. **Prompt Engineering**
   - Constructs a detailed prompt that feeds Gemini:
     - The new observation (description, year, keywords, timeline)
     - Historical context from correlated reports
     - 6 temporal anomaly definitions (PAR, DUP, DEV, RFT, ERO, LOP)
     - Paradox risk levels (LOW, MEDIUM, HIGH, CRITICAL)

3. **AI Analysis**
   - Gemini responds with a JSON verdict:
     ```json
     {
       "confirmed": true,
       "type": "PAR",
       "paradoxRisk": "CRITICAL",
       "explanation": "The effect precedes the cause by 3 years...",
       "contributingReportIds": [42, 101, 205]
     }
     ```

4. **Response Processing**
   - Parses Gemini's JSON response
   - On parse failure: **auto-retry** with a stricter prompt format
   - Extracts contributing report IDs (reports that caused Gemini to agree)
   - Creates or links to an **Anomaly record**

5. **Anomaly Linking & Verification Strategy** ⭐ **Quirk Alert!**
   - If a new report's contributing reports **overlap 75%** with an existing anomaly → link to it
   - If an anomaly is **unverified** (only one agent reported it):
     - When a **different agent** submits a corroborating report → upgrade to **VERIFIED**
     - This prevents single-agent false positives; anomalies need independent corroboration
   - If no anomaly exists → create a new one, starting as **unverified**

6. **Real-time Notification**
   - Sends WebSocket message to the agent's `/topic/analysis/{agentId}` channel
   - Agent's browser receives the analysis result instantly
   - Report status updates: `CONFIRMED` or `REJECTED`

---

## 🌀 The Quirks — What Makes TARS Weird (In A Good Way)

### **1. The Corroboration System: Trust, But Verify**
Anomalies don't become "real" until **multiple independent agents report them**. A single agent could be hallucinating or misreading timelines. Only when a *different* agent submits a report that overlaps 75%+ with the original anomaly's contributing reports does TARS upgrade it to **VERIFIED**. This mirrors real scientific consensus: extraordinary claims require extraordinary evidence.

### **2. Self-Exclusion Logic**
When analyzing a new report, Gemini sees historical context from other agents but **not** from the same submitting agent. Why? To prevent feedback loops where an agent's own past misreadings reinforce future false positives.

### **3. 75% Overlap Threshold**
Two anomalies are considered the "same" if their contributing report sets overlap by at least 75%. This is intentional fuzziness—timelines can diverge, and we don't need 100% identical report lists to say "these are the same phenomenon."

### **4. Meta-Linear Immunity**
The database operates in strict **server time**. TARS never experiences the paradoxes it studies. It observes all timeline inconsistencies as external data, never as contradictions to its own state. This is both a philosophical stance and a practical design principle: the system cannot become incoherent.

### **5. Two-Tier Anomaly Analysis**
First pass: Gemini analyzes normally. 
Second pass (on parse failure): Gemini gets a **strict instruction**: `"Your response must start with { and end with }. No other characters outside the JSON object."` 
This retry mechanism handles LLM quirks (markdown formatting, explanations outside JSON, etc.).

### **6. JWT Denylist in Redis**
On logout, the user's token is added to a Redis set (with TTL = token expiration). Subsequent requests check this set. Compared to database lookups, this is **O(1) and blazingly fast**. Token validation becomes a cache hit, not a query.

### **7. WebSocket→Agent Pipeline**
Analysis completion immediately pushes a message to the agent's WebSocket channel. No polling. The agent sees their verdict in near real-time. This is crucial for a system where decisions might matter urgently.

### **8. Async Processing with @Async**
Report analysis runs in a background thread pool. The API returns immediately (`202 Accepted`), and the agent receives the result via WebSocket when ready. This keeps the UI responsive even for 100 concurrent analyses.

### **9. Timeline Subscription Gating** (Spec-Level Quirk)
Agents can't report from timelines they're not subscribed to. Free plan = 1 timeline, Pro = 5, Enterprise = unlimited. This creates a natural silo—agents specialize in their assigned multiverses.

### **10. Anomaly Type Classification Without Historical Knowledge**
The 6 anomaly types (PAR, DUP, DEV, RFT, ERO, LOP) are only assigned by Gemini *if the report is confirmed*. Rejected reports have no type. This prevents the database from being polluted with speculative classifications.

---

## 📋 Prerequisites to Set Up Locally

- **Java JDK 21+** (checked with `java -version`)
- **Node.js 18+** & **npm 11+** (checked with `node -v` && `npm -v`)
- **Angular CLI** (installed globally: `npm install -g @angular/cli`)
- **PostgreSQL 14+** (running on `localhost:5432`, or configured in `application.properties`)
- **Redis 6+** (running on `localhost:6379`, used for JWT denylist and session store)
- **Docker & Docker Compose** (optional but recommended)
- **Gemini API Key** (free tier from [Google AI Studio](https://aistudio.google.com/apikey))

---

## 📖 Project Structure

```
TARS-Temporal-Anomaly-Reporting-System/
├── backend/                          # Spring Boot application
│   ├── src/main/java/com/tars/      # Source code
│   │   ├── TarsApplication.java     # Entry point
│   │   ├── config/                  # Spring configs (CORS, JWT, WebSocket)
│   │   ├── controller/              # REST endpoints
│   │   ├── service/                 # Business logic
│   │   │   ├── GeminiService.java  # Gemini API integration
│   │   │   ├── ReportService.java  # Report CRUD + async dispatch
│   │   │   └── ...
│   │   ├── model/                   # Entities (ObservationReport, Anomaly, etc.)
│   │   ├── repository/              # JPA repositories
│   │   └── exception/               # Custom exceptions
│   ├── src/main/resources/
│   │   ├── application.properties    # DB, Redis, Gemini config
│   │   └── logback-spring.xml       # Logging config
│   ├── build.gradle                 # Gradle build file
│   └── Dockerfile                   # Docker image for backend
│
├── frontend/                         # Angular application
│   ├── src/
│   │   ├── app/
│   │   │   ├── app.ts               # Root component
│   │   │   ├── app.routes.ts        # Route definitions
│   │   │   ├── core/                # Guards, services
│   │   │   │   ├── guards/
│   │   │   │   │   └── auth-guard.ts
│   │   │   │   └── services/
│   │   │   │       ├── auth.ts
│   │   │   │       ├── draft.ts
│   │   │   │       ├── user.ts
│   │   │   │       └── websocket.ts
│   │   │   └── features/            # Feature modules
│   │   │       ├── auth/            # Login/register
│   │   │       ├── agent/           # Agent dashboard
│   │   │       └── supervisor/      # Supervisor dashboard
│   │   ├── index.html
│   │   ├── main.ts
│   │   └── styles.css
│   ├── package.json
│   └── angular.json
│
├── docs/                             # Documentation
│   ├── specifications.md             # System specs & use cases
│   ├── usecase.md                   # Use case descriptions
│   ├── nfr.md                       # Non-functional requirements
│   ├── DomainEntitiesUML.svg        # Data model diagram
│   └── UseCaseDiagram.svg           # Use case diagram
│
├── docker-compose.yml               # Multi-container orchestration
├── build.gradle                     # Root Gradle build
├── settings.gradle
├── gradlew / gradlew.bat           # Gradle wrapper
└── README.md                        # You are here

```

---

## 🏗️ Development Workflow

### **Backend Development**

1. **Start PostgreSQL & Redis**
   ```bash
   # Via Docker
   docker run --rm -d --name postgres -p 5432:5432 \
     -e POSTGRES_DB=tars_database \
     -e POSTGRES_USER=tars_user \
     -e POSTGRES_PASSWORD=tars_password \
     postgres:15
   
   docker run --rm -d --name redis -p 6379:6379 redis:7
   ```

2. **Run Spring Boot**
   ```bash
   cd backend
   ./gradlew bootRun
   ```

3. **API Testing**
   - Import Postman collection from `/docs` (if available)
   - Or use `curl`:
   ```bash
   curl -X POST http://localhost:8080/api/reports \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "description": "Saw myself walk past me",
       "year": 2026,
       "keywords": "duplication, paradox",
       "timelineId": 1
     }'
   ```

### **Frontend Development**

1. **Start Angular Dev Server**
   ```bash
   cd frontend
   npm start
   ```

2. **Hot Module Replacement**
   - Edit a component or template → browser auto-reloads
   - Check browser console for TypeScript errors

3. **Build for Production**
   ```bash
   ng build --configuration production
   ```

### **Database Migrations**

TARS uses Hibernate auto-updates (`ddl-auto=update`). On startup:
- New entities are created automatically
- Existing columns are preserved
- For manual migrations, write SQL scripts in `backend/src/main/resources/db/migration/`

### **Docker Compose Workflow** (Recommended for Full Stack)

```bash
docker compose up --build
```

This:
- Builds the Spring Boot backend Docker image
- Pulls Node.js image and builds Angular
- Starts PostgreSQL, Redis, backend, and frontend in dependency order
- Exposes services on ports 8080, 4200, 5432, 6379

---

## 🧪 Testing

### **Backend**
```bash
cd backend
./gradlew test
```
Tests are in `src/test/java/com/tars/`.

### **Frontend**
```bash
cd frontend
npm test
```
Tests use Vitest + jsdom for component/service testing.

---

## 🔐 Security & Best Practices

- **JWT Tokens**: Stored in HTTP-only cookies; validated on every request
- **CORS**: Configured for `localhost:4200` in dev; set stricter origins in production
- **Password Hashing**: Spring Security's `BCryptPasswordEncoder`
- **API Key Management**: Gemini API key stored in `application.properties` (use environment variables in production)
- **HTTPS**: Enable in production via reverse proxy (Nginx, HAProxy)
- **Database Credentials**: Use Docker secrets or environment variables, never commit to Git

---

## 📊 API Overview

### **Core Endpoints**

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/reports` | Submit a new observation report |
| `GET` | `/api/reports/{id}` | Fetch report details + analysis result |
| `GET` | `/api/anomalies` | List all confirmed anomalies on your timelines |
| `POST` | `/api/auth/login` | Authenticate agent/supervisor |
| `POST` | `/api/auth/logout` | Invalidate JWT token |
| `WS` | `/ws/anomaly-updates` | WebSocket endpoint for real-time analysis |

See `docs/specifications.md` for full API spec.

---

## 🎨 UI/UX

- **Component Library**: Tailwind CSS (utility-first)
- **Responsive**: Mobile, tablet, desktop
- **Themes**: Light mode (default), dark mode support planned
- **Accessibility**: WCAG 2.1 AA target (in progress)
- **Real-time Feedback**: WebSocket-powered toast notifications for analysis results

---

## 🔧 Configuration

### **Backend (`application.properties`)**

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/tars_database
spring.datasource.username=tars_user
spring.datasource.password=tars_password
spring.jpa.hibernate.ddl-auto=update

# Redis (JWT denylist, sessions)
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Server
server.port=8080

# Email (Gmail)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-specific-password

# Gemini API
gemini.api.key=YOUR_GEMINI_API_KEY
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent
```

### **Frontend Environment**

Create `frontend/src/environments/environment.ts`:
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  wsUrl: 'ws://localhost:8080/ws'
};
```

---

## 📝 Contribution Guidelines

- **Pull Requests**: All contributions via PR; no direct commits to `main`
- **Commits**: Follow [Conventional Commits](https://www.conventionalcommits.org/)
  - `feat: add anomaly type filter`
  - `fix: resolve JWT expiration bug`
  - `docs: update README`
- **Signed Commits**: GPG or SSH key signature required
- **Squash & Merge**: Squash commits before merging
- **Keep Updated**: Rebase on latest `main` before submitting
- **Pre-commit Hooks**: Run `pre-commit run --all-files` before push

---

## 🐞 Known Issues & Future Work

- **Issue**: Gemini API rate limiting on high-volume submissions → implement queue + backpressure
- **Future**: Stripe payment integration for subscription tiers
- **Future**: Timeline visualization (temporal graph UI)
- **Future**: Anomaly correlation matrix (visual heatmap of related anomalies)
- **Future**: Bulk report uploads (CSV import)

---

## 📞 Support & Troubleshooting

### **Backend won't start**
```
Error: Connection refused (PostgreSQL)
→ Ensure PostgreSQL is running: docker run --name postgres -p 5432:5432 -e POSTGRES_PASSWORD=tars_password postgres:15
```

### **Frontend shows blank page**
```
Error: Cannot GET /
→ Ensure Angular dev server is running: cd frontend && ng serve
→ Check browser console for module load errors
```

### **WebSocket connection fails**
```
Error: WebSocket connection to 'ws://...' failed
→ Verify backend is running on port 8080
→ Check CORS config in backend/src/main/java/com/tars/config/CorsConfig.java
→ Ensure firewall allows WebSocket upgrade
```

### **Gemini API returns 404**
```
→ Verify API key is valid and has access to Gemini 2.0 Flash
→ Check quota at https://makersuite.google.com/app/apikeys
→ Ensure URL matches current API endpoint
```

---

## 📄 License

This project is licensed under the terms specified in [`LICENSE`](./LICENSE). 

---

## 🌟 Acknowledgments

Built with ❤️ for timeline integrity everywhere.

**TARS: Keeping Reality Consistent Since 2026.**

---

*Last updated: May 20, 2026*
