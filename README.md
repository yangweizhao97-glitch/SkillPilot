# SkillPilot / CareerAgent

SkillPilot is a Spring Boot and React career preparation workspace. It uploads and parses resumes and job descriptions, builds a pgvector knowledge base, runs structured AI agents, and aggregates job match, resume analysis, interview questions, citations, and execution logs into a versioned report.

## Stack

- Java 21, Spring Boot 3.5, Spring Security, JPA, Flyway
- PostgreSQL 16 with pgvector, Redis 7
- React 19, TypeScript, Vite
- DashScope-compatible chat API; local deterministic embeddings by default

## Quick Start

Requirements: Docker Desktop, JDK 21 or newer, Node.js 22 or newer.

```bash
cp .env.example .env
docker compose up -d
JAVA_HOME=/path/to/jdk ./mvnw spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. The frontend proxies `/api` to `http://localhost:8080`.

To run real AI analysis, set `DASHSCOPE_API_KEY` in `.env` and export the file before starting Spring Boot. Never commit `.env`.

```bash
set -a
source .env
set +a
JAVA_HOME=/path/to/jdk ./mvnw spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
docker compose ps
```

## Quality Checks

```bash
JAVA_HOME=/path/to/jdk ./mvnw verify
cd frontend && npm ci && npm run check
```

Backend integration tests use PostgreSQL and Redis but mock external LLM calls. No real model key is required for tests.

## Environment

The complete template is [.env.example](.env.example). Important variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/career_agent` |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database credentials | Development values in Compose |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection | `localhost:6379` |
| `JWT_SECRET` | JWT signing secret, minimum 32 characters | Development placeholder only |
| `DASHSCOPE_API_KEY` | Real model API key | Empty |
| `CHAT_MODEL` | Chat model | `qwen-flash` |
| `UPLOAD_DIR` | Local upload storage | `./data/uploads` |
| `MAX_UPLOAD_SIZE` | Upload limit | `20MB` |

## Main APIs

- Authentication: `/api/auth/register`, `/api/auth/login`, `/api/auth/me`
- Files and knowledge: `/api/files`, `/api/documents/{id}/chunks`, `/api/documents/{id}/embeddings`
- Resources: `/api/resumes`, `/api/jobs`
- Workflow: `/api/career-tasks`, `/api/career-tasks/{id}/logs`
- Reports: `/api/reports`, `/api/reports/{id}`
- Interactive interview: `/api/interview/sessions`, `/api/interview/sessions/{id}/answers/stream`

## Operations

- Testing strategy: [docs/testing.md](docs/testing.md)
- Security review: [docs/security-review.md](docs/security-review.md)
- Troubleshooting runbook: [docs/runbook.md](docs/runbook.md)
- Release checklist: [docs/release-checklist.md](docs/release-checklist.md)
