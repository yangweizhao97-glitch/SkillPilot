# Security Review

Review date: 2026-06-20

## Verified Controls

- JWT protects all APIs except registration, login, and health endpoints.
- Repository and service queries scope user-owned files, documents, resumes, jobs, tasks, reports, questions, and tools by `userId`.
- Upload storage strips directory components, sanitizes names, stores randomized filenames, normalizes paths, checks containment, limits size, and allows only supported MIME types.
- Agents access business data through the Tool Registry. Tool permission checks bind `userId`, `taskId`, `traceId`, agent name, and the tool allowlist.
- LLM document contexts are marked untrusted. XML-like delimiters in user content are escaped so content cannot close its own boundary.
- JSON Schema validation and citation allowlisting reject malformed or invented structured output.
- Tool audit logs redact credential fields, Bearer/JWT/`sk-` patterns, and replace full resume/JD bodies with length and SHA-256 fingerprints.
- `.env` files, upload data, build output, and IDE state are ignored. CI rejects tracked local environment files.

## Findings And Fixes

| Severity | Finding | Resolution |
| --- | --- | --- |
| High | Tool audit output could contain complete resume or JD text. | Document fields now store only length and a short SHA-256 fingerprint. |
| Medium | User text could include a literal `</UNTRUSTED_CONTEXT>` delimiter. | `<`, `>`, and `&` are escaped before wrapping untrusted context. |
| Medium | Secret values under non-sensitive field names could remain visible. | Bearer, JWT, and common `sk-` patterns are redacted recursively. |

## Tests

- Malicious traversal filename and unsupported MIME tests: `FileControllerTest`.
- Cross-user file, document, task, tool, question, and report access tests across integration suites.
- Prompt boundary tests: `DashScopeLlmClientTest`.
- Recursive audit redaction tests: `AuditDataSanitizerTest` and `ToolExecutorIntegrationTest`.

## Residual Risks

- MIME type is client supplied; production deployment should add content signature verification and malware scanning.
- Local disk storage is suitable for MVP only; production should use encrypted object storage and retention policies.
- Rate limiting, account lockout, TLS termination, CSP, and centralized secret management belong at the production gateway/platform layer.
- Generated recommendations still require user review and must not be treated as hiring guarantees.

