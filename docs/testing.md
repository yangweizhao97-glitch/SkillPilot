# Testing Strategy

## Test Layers

1. Unit tests cover chunking, workflow transitions, JSON Schema validation, retry classification, prompt boundaries, and audit redaction.
2. Integration tests cover authentication, persistence, user isolation, file parsing, pgvector retrieval, tools, agents, final report aggregation, and API errors.
3. The MVP smoke test in `CareerWorkflowIntegrationTest` covers upload, parse, chunk, embed, resource creation, task creation, polling result, logs, and final report retrieval with a mocked LLM.
4. Frontend Vitest tests cover authentication rendering, token propagation, API envelope handling, and expired-session cleanup.
5. Concurrent workflow tests prove that every final-report section belongs to the same `taskId`, even when one user starts multiple tasks rapidly.
6. Interview SSE integration tests cover authenticated async dispatch and the answer-received, evaluating, streaming, and completed event lifecycle.
7. Answer-evaluation tests cover score bounds, fixed weighted dimensions, message-level binding, user isolation, persistence, and SSE completion events.
8. Workflow-engine contract tests cover Spring delegation, task/trace propagation, invalid LangGraph plans, and configurable fallback.
9. Career-task SSE tests cover authenticated snapshots, persisted step events, reconnect cursors, terminal completion, user isolation, and frontend event-ID parsing.

External model calls must always be mocked in automated tests. PostgreSQL with pgvector is a real test dependency.

## Local Commands

```bash
docker compose up -d
JAVA_HOME=/path/to/jdk ./mvnw verify
cd frontend
npm ci
npm run check
```

Run focused suites while developing:

```bash
JAVA_HOME=/path/to/jdk ./mvnw -Dtest=CareerWorkflowIntegrationTest test
cd frontend && npm run test
```

## CI Contract

GitHub Actions uses Java 21, Node.js 22, `pgvector/pgvector:pg16`, and Redis 7. CI commands intentionally match the local commands above. A failure is actionable from the named backend, frontend, or repository-safety job.
