# Changelog

## 0.1.0 - 2026-06-20

- Split document processing from the career analysis workflow and introduced document `READY` state.
- Limited career analysis to job matching, resume analysis, interview question generation, and final report aggregation.
- Expanded RAG retrieval to resumes, job descriptions, notes, and project documents.
- Bound all generated artifacts and final report aggregation to one `taskId`.
- Added truthful workflow and tool lifecycle events with merged `toolCallId` timeline cards.
- Added observable LLM calls and incremental SSE events for interactive interviews.
