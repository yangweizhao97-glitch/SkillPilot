# SkillPilot Runbook

## First Response

1. Check `GET /actuator/health` and `docker compose ps`.
2. Capture the affected `taskId` and `traceId`; do not request passwords, JWTs, API keys, or full resumes in support channels.
3. Read `/api/career-tasks/{taskId}` and `/api/career-tasks/{taskId}/logs` as the owning user.
4. Confirm whether the failure is application, database, Redis, file parsing, or model-provider related.

## Task Failure

- Locate the first failed Agent log by `traceId`, `agentName`, and `stepName`.
- Correct the underlying dependency or input issue.
- Use `POST /api/career-tasks/{taskId}/retry` only for a task in `FAILED`; the original task and logs remain unchanged for audit.
- Repeated failures with the same trace pattern require an incident record before manual database intervention.

## Model Failure

- `LLM_AUTHENTICATION`: verify `DASHSCOPE_API_KEY` is present in the process environment and rotate it if exposure is suspected.
- `LLM_RATE_LIMIT`: reduce concurrency or wait for provider quota recovery. Built-in retries are bounded.
- Timeout/provider failure: verify `LLM_BASE_URL`, outbound connectivity, provider status, and configured timeouts.
- Schema repair failure: inspect schema error summaries and hashes; raw model output is intentionally not logged.

## Database Or Redis Failure

```bash
docker compose ps
docker compose logs postgres --tail=100
docker compose logs redis --tail=100
```

- PostgreSQL must have the `vector` extension and all Flyway migrations applied.
- Never edit an applied Flyway migration. Add a forward migration.
- Restore from a verified database backup before replaying tasks after data loss.
- Redis is not the system of record; restart it after checking memory and persistence errors.

## File Parsing Failure

- Confirm the file is non-empty, below `MAX_UPLOAD_SIZE`, and has an allowed MIME type.
- Check that `UPLOAD_DIR` is writable and has disk space.
- A failed parse stores a short error status. Re-upload a corrected file rather than modifying stored content in place.
- Do not place uploaded documents in application logs or tickets.

## Minimum Monitoring

Track these signals per five-minute window and by deployment version:

| Signal | Source | Initial alert |
| --- | --- | --- |
| API 5xx rate | HTTP metrics / gateway | > 2% for 10 minutes |
| API p95 latency | HTTP metrics / gateway | > 2 seconds for 10 minutes |
| Task failure rate | `agent_tasks` terminal states | > 10% for 15 minutes |
| LLM failure rate | Agent errors grouped by `LLM_*` | > 10% for 10 minutes |
| Token consumption | Agent log token totals | unexpected 2x daily baseline |

Never label user email, resume text, JWT, or API keys as metric dimensions.

## Rollback

1. Stop new task creation at the gateway or UI.
2. Roll back the application image to the last verified version.
3. Do not roll back a database migration destructively. Use a forward repair migration if schema compatibility is broken.
4. Run health, authentication, upload, and report smoke checks before reopening traffic.

