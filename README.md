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

Run the offline Prompt regression gate independently:

```bash
JAVA_HOME=/path/to/jdk ./mvnw -Dtest=PromptRegressionSuiteTest test
```

The readable result is written to `target/prompt-regression-report.json`.

## LangGraph Workflow Engine

Spring remains the default stable workflow engine. To run the optional LangGraph orchestrator:

```bash
docker compose --profile langgraph up -d langgraph
export WORKFLOW_ENGINE=langgraph
export LANGGRAPH_BASE_URL=http://localhost:8090
JAVA_HOME=/path/to/jdk ./mvnw spring-boot:run
```

If the orchestrator is unavailable or returns a plan that violates the workflow state contract, Spring fallback is used by default. Set `LANGGRAPH_FALLBACK_ENABLED=false` to fail the task instead.

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
| `WORKFLOW_ENGINE` | Workflow engine: `spring` or `langgraph` | `spring` |
| `LANGGRAPH_BASE_URL` | LangGraph orchestrator URL | `http://localhost:8090` |
| `LANGGRAPH_FALLBACK_ENABLED` | Fall back to Spring on orchestration failure | `true` |
| `MCP_ENABLED` | Enable the optional MCP Streamable HTTP adapter | `false` |
| `MCP_ENDPOINT` | Trusted MCP server endpoint | Empty |
| `MCP_ALLOWED_TOOLS` | Explicit comma-separated remote tool allowlist | Empty |
| `AGENT_HANDOFF_ENABLED` | Emit and enforce controlled Agent handoffs | `true` |
| `AGENT_HANDOFF_MAX_DEPTH` | Maximum handoffs in one career task | `4` |

MCP remains disabled unless an endpoint and explicit tool allowlist are configured. Remote tools are invoked through the same Tool Registry as local tools, so task ownership, agent permissions, timing, failure status, and redacted audit logs remain enforced. The adapter supports JSON and SSE Streamable HTTP responses and does not fall back to an unapproved remote tool.

## Main APIs

- Authentication: `/api/auth/register`, `/api/auth/login`, `/api/auth/me`
- Files and knowledge: `/api/files`, `/api/documents/{id}/chunks`, `/api/documents/{id}/embeddings`
- Resources: `/api/resumes`, `/api/jobs`
- Workflow: `/api/career-tasks`, `/api/career-tasks/{id}/logs`, `/api/career-tasks/{id}/events`
- Reports: `/api/reports`, `/api/reports/{id}`
- Interactive interview: `/api/interview/sessions`, `/api/interview/sessions/{id}/answers/stream`
- Interview memory: `GET/DELETE /api/interview/memory?resumeId={id}&jobId={id}`

## Operations

- Testing strategy: [docs/testing.md](docs/testing.md)
- Security review: [docs/security-review.md](docs/security-review.md)
- Troubleshooting runbook: [docs/runbook.md](docs/runbook.md)
- Release checklist: [docs/release-checklist.md](docs/release-checklist.md)
