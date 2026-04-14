# TARS — Temporal Anomaly Reporting System

A web application for recording, monitoring, and analyzing temporal anomalies detected across a multiverse space-time continuum. Designed for agents and supervisors operating in timeline monitoring systems.

Built with **Spring Boot** (backend) · **Angular + Tailwind CSS** (frontend) · **PostgreSQL** (database) · **Docker Compose** (deployment)

> Full documentation is in [`/docs`](./docs)

---

## Development Environment Setup

### Prerequisites

- Java JDK 17+
- Node.js and npm
- Angular CLI: `npm install -g @angular/cli`
- Docker (recommended) or a local PostgreSQL instance
- Redis (used for JWT denylist)

### Backend Setup (Spring Boot)

```bash
git clone <repo-url>
```

- Open the project in IntelliJ IDEA
- Update database credentials in `src/main/resources/application.properties`
- Sync Gradle to download dependencies
- Run the Spring Boot application — server starts on `http://localhost:8080`

### Frontend Setup (Angular)

```bash
cd frontend
npm install
ng serve
```

Navigate to `http://localhost:4200`

### Docker Setup

```bash
docker compose up
```

Starts Spring Boot, Angular, PostgreSQL, and Redis together.

---

## Contribution Guidelines

- **Pull Requests:** all contributions must go through pull requests
- **Commit Format:** follow [Conventional Commits](https://www.conventionalcommits.org/)
- **Signed Commits:** all commits must be signed with GPG or SSH keys
- **Pre-commit Hooks:** run `pre-commit run --all-files` before submitting
- **Squash Commits:** squash into a single commit before merging
- **Stay Updated:** keep your branch up to date with `main`
