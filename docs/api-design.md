# CareerAgent API Design

本文定义 CareerAgent 当前 REST API 合同。它覆盖登录、上传简历/JD、解析、切分与检索、Career 任务、岗位匹配、简历分析、面试题生成、报告、日志和文字模拟面试。职业分析结果继续使用结构化 JSON；文字面试回复通过 SSE 推送。回答评分和 PDF 导出留给后续版本。

## General Rules

### Base URL

```text
/api
```

### Authentication

除注册、登录、健康检查外，所有接口都需要：

```http
Authorization: Bearer <jwt>
```

JWT 中至少包含：

- `userId`
- `role`
- `exp`

### Naming

- REST endpoint 使用复数资源名。
- 请求和响应字段使用 camelCase。
- 枚举值使用 UPPER_SNAKE。
- 时间字段使用 ISO-8601 字符串。
- 列表接口默认分页。

### Success Response

```json
{
  "success": true,
  "data": {},
  "traceId": "trace_abc"
}
```

### Page Response

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "totalItems": 0,
    "totalPages": 0
  },
  "traceId": "trace_abc"
}
```

### Error Response

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request",
    "details": {}
  },
  "traceId": "trace_abc"
}
```

### HTTP Status Rules

| Status | Meaning |
| --- | --- |
| 400 | Bad syntax or malformed request |
| 401 | Missing, invalid, or expired token |
| 403 | Authenticated but not allowed |
| 404 | Resource not found or not owned by current user |
| 409 | Conflict, duplicate, or invalid state transition |
| 422 | Valid JSON but semantically invalid |
| 500 | Server error, never expose stack trace |

## Auth

### Register

```http
POST /api/auth/register
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password123",
  "nickname": "Alice"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "Alice",
    "role": "USER"
  },
  "traceId": "trace_abc"
}
```

### Login

```http
POST /api/auth/login
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "accessToken": "jwt",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "userId": 1,
      "email": "user@example.com",
      "nickname": "Alice",
      "role": "USER"
    }
  },
  "traceId": "trace_abc"
}
```

### Current User

```http
GET /api/auth/me
```

## Files and Documents

### Upload File

```http
POST /api/files/upload
Content-Type: multipart/form-data
```

Form fields:

- `file`: required, max `20MB`
- `fileType`: `RESUME | JD | NOTE | PROJECT_DOC`

Response:

```json
{
  "success": true,
  "data": {
    "fileId": 1,
    "fileName": "resume.pdf",
    "fileType": "RESUME",
    "mimeType": "application/pdf",
    "sizeBytes": 123456,
    "parseStatus": "PENDING",
    "createdAt": "2026-06-19T12:00:00Z"
  },
  "traceId": "trace_abc"
}
```

### List Files

```http
GET /api/files?page=1&pageSize=20&fileType=RESUME
```

### Get File

```http
GET /api/files/{fileId}
```

### Parse File

```http
POST /api/files/{fileId}/parse
```

Response:

```json
{
  "success": true,
  "data": {
    "fileId": 1,
    "documentId": 10,
    "parseStatus": "SUCCESS"
  },
  "traceId": "trace_abc"
}
```

### List Documents

```http
GET /api/documents?page=1&pageSize=20&docType=RESUME
```

### Get Document

```http
GET /api/documents/{documentId}
```

## Resume and Job

### Create Resume

```http
POST /api/resumes
```

Request:

```json
{
  "documentId": 10,
  "title": "Java Backend Resume"
}
```

### List Resumes

```http
GET /api/resumes?page=1&pageSize=20
```

### Get Resume

```http
GET /api/resumes/{resumeId}
```

### Create Job

```http
POST /api/jobs
```

Request from document:

```json
{
  "documentId": 11,
  "company": "Example Inc",
  "position": "Java Backend Intern"
}
```

Request from text:

```json
{
  "company": "Example Inc",
  "position": "Java Backend Intern",
  "jdText": "岗位职责..."
}
```

### List Jobs

```http
GET /api/jobs?page=1&pageSize=20
```

### Get Job

```http
GET /api/jobs/{jobId}
```

## Knowledge Search

### Search Knowledge

```http
POST /api/knowledge/search
```

Request:

```json
{
  "query": "Spring Boot JWT 权限设计",
  "sourceTypes": ["RESUME", "JD", "PROJECT_DOC", "NOTE"],
  "topK": 8,
  "retrievalMode": "HYBRID"
}
```

Rules:

- `topK`: default `8`, max `20`
- `retrievalMode`: `VECTOR | KEYWORD | HYBRID`
- Only current user's chunks are searchable.

Response:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "citationId": "chunk_101",
        "documentId": 10,
        "sourceType": "RESUME",
        "sourceTitle": "resume.pdf",
        "sourceLocator": "项目经历 / CareerAgent",
        "content": "...",
        "score": 0.86
      }
    ]
  },
  "traceId": "trace_abc"
}
```

## Career Tasks

### Create Career Task

```http
POST /api/career-tasks
```

Request:

```json
{
  "resumeId": 1,
  "jobId": 2,
  "enabledSteps": [
    "MATCHING_JOB",
    "ANALYZING_RESUME",
    "GENERATING_QUESTIONS"
  ]
}
```

Response:

```json
{
  "success": true,
  "data": {
    "taskId": 100,
    "traceId": "trace_abc",
    "status": "PENDING",
    "progress": 0
  },
  "traceId": "trace_abc"
}
```

### Get Career Task

```http
GET /api/career-tasks/{taskId}
```

Response:

```json
{
  "success": true,
  "data": {
    "taskId": 100,
    "traceId": "trace_abc",
    "taskType": "CAREER_PREPARE",
    "status": "GENERATING_QUESTIONS",
    "progress": 80,
    "resumeId": 1,
    "jobId": 2,
    "errorMessage": null,
    "createdAt": "2026-06-19T12:00:00Z",
    "startedAt": "2026-06-19T12:00:01Z",
    "finishedAt": null
  },
  "traceId": "trace_abc"
}
```

当前职业分析进度使用该接口轮询。任务事件流仍是后续增强项：

```http
GET /api/career-tasks/{taskId}/events
```

### Get Task Logs

```http
GET /api/career-tasks/{taskId}/logs
```

## Agent Actions

V1 can expose direct trigger endpoints for convenience. They should internally create or reuse an Agent task.

### Analyze Resume

```http
POST /api/resumes/{resumeId}/analyze
```

### List Resume Analysis Reports

```http
GET /api/resumes/{resumeId}/analysis-reports
```

### Match Job

```http
POST /api/jobs/{jobId}/match?resumeId={resumeId}
```

### List Job Match Reports

```http
GET /api/jobs/{jobId}/match-reports?resumeId={resumeId}
```

### Generate Interview Questions

```http
POST /api/interview/questions/generate
```

Request:

```json
{
  "resumeId": 1,
  "jobId": 2,
  "count": 10,
  "questionTypes": ["PROJECT", "TECH", "BEHAVIOR", "JD_MATCH"],
  "difficulties": ["EASY", "MEDIUM", "HARD"]
}
```

### List Interview Questions

```http
GET /api/interview/questions?resumeId=1&jobId=2&questionType=PROJECT&difficulty=MEDIUM
```

### Interactive Interview Session

```http
POST /api/interview/sessions
GET /api/interview/sessions
GET /api/interview/sessions/{sessionId}
POST /api/interview/sessions/{sessionId}/answers
POST /api/interview/sessions/{sessionId}/answers/stream
POST /api/interview/sessions/{sessionId}/finish
```

`answers/stream` produces `text/event-stream` and emits interview lifecycle events incrementally. Answer scoring is not part of v0.1.1.

## Reports

### List Final Reports

```http
GET /api/reports?page=1&pageSize=20
```

### Get Final Report

```http
GET /api/reports/{reportId}
```

### Refresh Final Report

```http
POST /api/reports/refresh
```

```json
{"taskId": 100}
```

Refresh is strictly scoped by `taskId`; resume/job “latest version” aggregation is not supported.

Response:

```json
{
  "success": true,
  "data": {
    "reportId": 1,
    "taskId": 100,
    "resumeId": 1,
    "jobId": 2,
    "version": 1,
    "reportJson": {
      "jobMatch": {},
      "resumeAnalysis": {},
      "interviewQuestions": []
    },
    "createdAt": "2026-06-19T12:00:00Z"
  },
  "traceId": "trace_abc"
}
```

V3 may add:

```http
POST /api/reports/{reportId}/export
```

## Logs and Traces

### Agent Execution Logs

```http
GET /api/agent/logs?taskId=100&page=1&pageSize=20
```

### Tool Call Logs

```http
GET /api/agent/tool-call-logs?taskId=100&page=1&pageSize=20
```

### Trace Detail

```http
GET /api/agent/traces/{traceId}
```

All log endpoints must:

- Filter by current user.
- Avoid returning secrets.
- Return summarized input/output by default.
- Allow full JSON only after sensitive-field masking.

## V1 Enums

```text
Role: USER, ADMIN
FileType: RESUME, JD, NOTE, PROJECT_DOC
ParseStatus: PENDING, SUCCESS, FAILED
DocType: RESUME, JD, NOTE, PROJECT_DOC
RetrievalMode: VECTOR, KEYWORD, HYBRID
TaskType: CAREER_PREPARE, RESUME_ANALYSIS, JOB_MATCH
WorkflowStatus: PENDING, MATCHING_JOB, ANALYZING_RESUME, GENERATING_QUESTIONS, FINAL_REPORT, SUCCESS, FAILED
QuestionType: PROJECT, TECH, BEHAVIOR, JD_MATCH
Difficulty: EASY, MEDIUM, HARD
LogStatus: SUCCESS, FAILED
```

## Security Requirements

- Every resource query must include current `userId`.
- Login failure must not reveal whether an email exists.
- Uploaded file names must not decide storage paths.
- LLM output must pass JSON Schema before persistence.
- Tool calls must verify both Agent permission and resource ownership.
- Logs must never return JWT, password, API key, or full private document content.
