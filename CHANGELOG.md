# Changelog

## 0.2.10 - 2026-06-21

- Removed fictitious disabled workflow transitions so career-task events reflect only agents that actually ran.
- Hardened task isolation, learning-plan completeness checks, generation claims, and schema-repair tool visibility.
- Added recoverable learning-plan generation leases with row-level locking and stale-claim takeover.
- Moved interview model calls outside database transactions and added guarded, recoverable answer processing.
- Limited PDF aggregation to completed learning plans and made interview stream failures persist in the client.
- Enforced runtime JWT secrets, content-based upload type detection, rollback file cleanup, and safer PDF exports.
- Switched career-task SSE workers to virtual threads and fixed delayed browser download URL cleanup.

## 0.2.9 - 2026-06-21

- Added authenticated PDF generation and download for user-owned final reports.
- Rendered task metadata, job matching, resume analysis, interview questions, citations, and the task-bound learning plan as a paginated A4 report.
- Added atomic local export storage, configurable CJK font and export paths, persisted export state, and path traversal protection.
- Added PDF text extraction tests plus rendered-page visual verification for Chinese glyphs, wrapping, section spacing, and page footers.

## 0.2.8 - 2026-06-21

- Added a task-scoped Learning Plan Agent that reads one final report through the audited Tool Registry.
- Added a strict learning-plan JSON Schema, secured Prompt contract, repair validation, and offline Prompt regression fixture.
- Persisted one idempotent learning plan per user and career task with cross-user and cross-task isolation.
- Added report-page generation controls and structured roadmap display for priorities, weekly phases, deliverables, and success metrics.

## 0.2.7 - 2026-06-21

- Added bounded, persisted interview memory scoped by user, resume, job, and source session.
- Derived memory only from Schema-validated answer evaluations, retaining scores, topics, strengths, and improvement areas instead of raw answers.
- Added the latest three session summaries to answer evaluation and follow-up context through the existing secured LLM boundary.
- Added authenticated memory read and clear APIs plus an in-session memory summary and user-controlled reset action.
- Enforced item count and length limits and kept memory isolated from unrelated users and resume/job pairs.
- Added Flyway V9 and cross-session reuse, raw-answer exclusion, isolation, deletion, migration, and frontend regression coverage.

## 0.2.6 - 2026-06-21

- Added controlled Agent handoffs between active career-analysis stages and the final report aggregator.
- Added an explicit source-to-target allowlist, forward-only routing, maximum-depth enforcement, and loop detection.
- Kept handoff payloads task-scoped and persisted-result based instead of copying resume, JD, or model content into events.
- Added truthful handoff started, completed, and rejected events to the existing recoverable task stream.
- Skipped handoffs for disabled stages and workflows without a final report, preserving partial-task behavior.
- Added policy, routing, lifecycle, partial-workflow, frontend, and full workflow regression coverage.

## 0.2.5 - 2026-06-21

- Added an optional MCP Streamable HTTP client with initialization, discovery, session, JSON, and SSE response support.
- Routed remote calls through the existing Tool Registry to preserve task ownership, agent permissions, timing, and tool-call visualization.
- Required local server configuration plus explicit remote-tool and agent allowlists; MCP remains disabled by default.
- Added bounded request/response sizes, timeouts, discovery pagination, protocol validation, and isolated remote failures.
- Redacted MCP content and structured results from audit storage while preserving length and content hashes.
- Added MCP protocol, allowlist, permission, SSE, and audit regression coverage.

## 0.2.4 - 2026-06-21

- Added a centralized Prompt Injection policy engine for every secured LLM context.
- Added Unicode normalization and zero-width control removal before security evaluation.
- Added risk-scored detection and sanitization for boundary escape, instruction override, role spoofing, Prompt exfiltration, tool manipulation, and common credential formats.
- Added structured security audit events containing only rule identifiers, risk, length, trace ID, and a content hash.
- Added bilingual attack, obfuscation, secret-redaction, boundary, and benign-content regression coverage.

## 0.2.3 - 2026-06-21

- Centralized six production Prompt contracts with explicit versioned identifiers.
- Added an offline evaluation dataset for job matching, resume analysis, interview questions, answer scoring, follow-up generation, and session reviews.
- Added regression gates for Prompt fingerprints, JSON Schema compliance, factual anchors, score ranges, citation allowlists, dimension completeness, and prompt-injection isolation.
- Added a machine-readable Prompt regression report and GitHub Actions artifact upload.
- Documented the dedicated local quality-gate command and intentional Prompt update workflow.

## 0.2.2 - 2026-06-21

- Added one persisted, user-isolated review per completed interview session.
- Aggregated only evaluations and transcript messages bound to the same `sessionId`.
- Added Schema-validated overall summaries, canonical server-side scores, strengths, gaps, action plans, and practice questions.
- Added idempotent review generation with a deterministic fallback when the model is unavailable.
- Added automatic and on-demand review generation plus a historical-session review panel in the frontend.
- Replaced post-hoc interview text slicing with provider-level LLM streaming and immediate candidate-message rendering.

## 0.2.1 - 2026-06-21

- Added authenticated career-task SSE with database-backed initial synchronization.
- Added incremental task, step, and merged tool-call events with stable event IDs and heartbeats.
- Added reconnect support through `Last-Event-ID`, full snapshot reconciliation, and frontend deduplication.
- Replaced task-detail polling with a recoverable authenticated fetch stream and bounded exponential reconnect.
- Added SSE lifecycle, reconnect metadata, user-isolation, and client parser regression tests.

## 0.2.0 - 2026-06-21

- Introduced the missing `AgentWorkflowExecutor` boundary with the existing Spring workflow as the default engine.
- Added a configurable LangGraph HTTP adapter with task/trace propagation and strict workflow-plan validation.
- Added automatic Spring fallback for unavailable or invalid remote orchestration plans.
- Added a containerized FastAPI/LangGraph orchestrator and contract tests.
- Kept business tools, persistence, reports, state transitions, and audit logs inside Spring Boot.

## 0.1.2 - 2026-06-20

- Added Schema-validated per-answer interview scoring with fixed weighted dimensions.
- Bound every evaluation to its user, session, question, and candidate answer message.
- Combined scoring and follow-up decisions into one model call with safe failure degradation.
- Added SSE scoring lifecycle events and expandable frontend score cards with actionable feedback.
- Added Flyway V7 persistence plus Schema, isolation, service, and SSE regression coverage.

## 0.1.1 - 2026-06-20

- Removed legacy fake workflow and log states and aligned tests with truthful step/tool lifecycle events.
- Serialized report version allocation and interview answer updates to prevent concurrent-task data mixing.
- Added an end-to-end concurrent task isolation regression test for task-scoped report aggregation.
- Hardened interview SSE execution, disconnect handling, terminal events, and authenticated async dispatch.
- Added an authenticated SSE lifecycle integration test and refreshed release documentation.

## 0.1.0 - 2026-06-20

- Split document processing from the career analysis workflow and introduced document `READY` state.
- Limited career analysis to job matching, resume analysis, interview question generation, and final report aggregation.
- Expanded RAG retrieval to resumes, job descriptions, notes, and project documents.
- Bound all generated artifacts and final report aggregation to one `taskId`.
- Added truthful workflow and tool lifecycle events with merged `toolCallId` timeline cards.
- Added observable LLM calls and incremental SSE events for interactive interviews.
