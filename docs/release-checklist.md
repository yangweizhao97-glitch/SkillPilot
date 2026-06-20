# MVP Release Checklist

## Build And Test

- [ ] `./mvnw verify` passes on JDK 21 with PostgreSQL/pgvector and Redis.
- [ ] `npm ci && npm run check` passes on Node.js 22.
- [ ] MVP smoke test covers login through final report without a real model dependency.
- [ ] GitHub Actions backend, frontend, and repository-safety jobs pass.

## Configuration And Data

- [ ] Flyway validates against a production-like database backup.
- [ ] Production JWT and model keys come from secret management, not files or images.
- [ ] Upload path, capacity, backup, retention, and permissions are verified.
- [ ] Database backup and restore procedure has been rehearsed.

## Security

- [ ] No `.env`, credentials, tokens, uploaded files, or production data are tracked.
- [ ] Dependency audit has no unresolved high-severity production finding.
- [ ] Cross-user access, malicious filename, prompt injection, and log redaction tests pass.
- [ ] TLS, rate limiting, CSP, CORS, and access logs are configured at the deployment edge.

## Operations

- [ ] Health endpoint and database/Redis checks are connected to monitoring.
- [ ] API error rate, p95 latency, task failures, LLM failures, and token usage have dashboards and alerts.
- [ ] On-call owner has read `docs/runbook.md` and can retry a failed task.
- [ ] Rollback application version and forward database repair paths are confirmed.

## Release Decision

- [ ] Product owner accepts the MVP workflow and report output.
- [ ] Engineering owner confirms no open release blocker.
- [ ] Release version, commit SHA, migration version, and timestamp are recorded.

