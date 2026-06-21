# Security Review

Review date: 2026-06-21

## Verified Controls

- JWT protects all APIs except registration, login, and health endpoints.
- Repository and service queries scope user-owned files, documents, resumes, jobs, tasks, reports, questions, and tools by `userId`.
- Upload storage strips directory components, sanitizes names, stores randomized filenames, normalizes paths, checks containment, limits size, and allows only supported MIME types.
- Agents access business data through the Tool Registry. Tool permission checks bind `userId`, `taskId`, `traceId`, agent name, and the tool allowlist.
- Every secured LLM context is Unicode-normalized, inspected by a centralized Prompt Injection policy, locally sanitized when risky, and then wrapped as untrusted data.
- Policy rules cover delimiter escape, instruction override, role spoofing, Prompt/credential exfiltration, tool manipulation, and common Bearer/JWT/API-key formats.
- Prompt security audit events contain rule IDs, risk, content length, trace ID, and a short content hash; raw user content is never logged.
- JSON Schema validation and citation allowlisting reject malformed or invented structured output.
- Tool audit logs redact credential fields, Bearer/JWT/`sk-` patterns, and replace full resume/JD bodies with length and SHA-256 fingerprints.
- `.env` files, upload data, build output, and IDE state are ignored. CI rejects tracked local environment files.

## Findings And Fixes

| Severity | Finding | Resolution |
| --- | --- | --- |
| High | Tool audit output could contain complete resume or JD text. | Document fields now store only length and a short SHA-256 fingerprint. |
| Medium | User text could include a literal `</UNTRUSTED_CONTEXT>` delimiter. | `<`, `>`, and `&` are escaped before wrapping untrusted context. |
| Medium | Secret values under non-sensitive field names could remain visible. | Bearer, JWT, and common `sk-` patterns are redacted recursively. |
| High | Instruction overrides could be obfuscated with role tags, Chinese wording, or zero-width Unicode controls. | Normalize Unicode, remove invisible controls, classify risk, and redact matched instruction segments before the model call. |

## Tests

- Malicious traversal filename and unsupported MIME tests: `FileControllerTest`.
- Cross-user file, document, task, tool, question, and report access tests across integration suites.
- Prompt policy, obfuscation, benign-content, secret and boundary tests: `PromptInjectionPolicyTest`, `DashScopeLlmClientTest`, and `PromptRegressionSuiteTest`.
- Recursive audit redaction tests: `AuditDataSanitizerTest` and `ToolExecutorIntegrationTest`.

## Residual Risks

- MIME type is client supplied; production deployment should add content signature verification and malware scanning.
- Local disk storage is suitable for MVP only; production should use encrypted object storage and retention policies.
- Rate limiting, account lockout, TLS termination, CSP, and centralized secret management belong at the production gateway/platform layer.
- Generated recommendations still require user review and must not be treated as hiring guarantees.
- Rule-based detection cannot recognize every semantic attack; production telemetry should be reviewed and rules/versioned as attack patterns evolve.
- MCP is disabled by default. Operators must configure a trusted HTTP(S) endpoint and explicit tool/Agent allowlists; discovered server tools never grant authority by themselves.
- MCP calls retain local task ownership checks and redacted Tool Registry audit logs. Bearer tokens, remote content, and structured results are not persisted in clear text.
