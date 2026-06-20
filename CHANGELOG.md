# Changelog

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
