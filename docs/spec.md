# Spec: CareerAgent

## Objective

CareerAgent 是一个 AI 学习 / 求职助手 Agent 后端平台。第一版目标不是做复杂聊天机器人，而是打通完整的 RAG + Agent 后端链路：

```text
登录
  -> 上传简历 / JD
  -> 文档解析
  -> chunk 切分
  -> embedding 入库
  -> RAG 检索
  -> Agent 工作流
  -> 结构化报告
  -> 日志追踪
```

第一版用户是准备求职的学生或初级开发者。用户上传自己的简历和目标岗位 JD 后，系统生成岗位匹配报告、简历分析报告和个性化面试题，并展示 Agent 执行日志、工具调用日志和引用来源。

## Version Scope

### V1 MVP

必须完成：

- 登录注册、JWT 鉴权、当前用户上下文。
- 上传简历和 JD。
- 解析 PDF / Word / Markdown / TXT。
- 文本清洗、chunk 切分、embedding 生成。
- PostgreSQL + pgvector 向量入库和 topK 检索。
- 关键词检索和简单混合检索。
- Career 任务状态机。
- Tool Registry 和工具权限校验。
- 岗位匹配 Agent。
- 简历分析 Agent。
- 面试题生成 Agent。
- JSON Schema 校验、失败修复或重试。
- Agent 执行日志、Tool 调用日志、traceId、耗时和 token 统计。
- 报告页和日志页。
- React + TypeScript 管理台。

暂不做：

- LangGraph。
- 模拟面试评分。
- 学习计划 Agent。
- PDF 导出。
- MinIO。
- 多模型路由。
- Elasticsearch / Milvus。
- 企业级权限系统。

### V2

- 引入 LangGraph，作为可替换的 Agent 工作流执行层。
- 实现模拟面试会话、回答保存、答案评分、追问和总结。
- 接入 MinIO。
- 增强 SSE 实时进度。
- 建立 Prompt/Agent 回归评测集。

### V3

- 学习计划 Agent。
- PDF 报告导出。
- 多模型路由和成本策略。
- MCP 工具接入。
- 多 Agent handoff 和长会话记忆。
- 更完整的 Prompt Injection 防护和运营监控。

## Tech Stack

### Backend

- Java 21
- Spring Boot 3.x
- Maven
- Spring Web MVC
- Spring Security + JWT
- Spring Validation
- Spring Data JPA
- Flyway
- Spring Async / ThreadPoolTaskExecutor
- Spring Boot Actuator

### Database and Storage

- PostgreSQL
- pgvector
- Redis
- Local file storage for V1
- MinIO for V2

### AI and RAG

- Unified `LlmClient`
- V1 default LLM provider: Alibaba Cloud Model Studio / DashScope
- V1 default chat model: `qwen-flash`
- V1 default embedding model: `text-embedding-v4`
- V1 embedding dimension: `1024`
- Other optional providers: DeepSeek / OpenAI / Zhipu
- Embedding provider hidden behind `EmbeddingClient`
- JSON Schema validation: NetworkNT JSON Schema Validator or equivalent
- RAG retrieval: pgvector similarity search + PostgreSQL keyword search

### Frontend

- React
- TypeScript
- Vite
- React Query or equivalent server-state library

## Commands

Project commands will be finalized when the Spring Boot and frontend projects are initialized. Use these as the target contract:

```bash
# Start dependencies
docker compose up -d postgres redis

# Backend
./mvnw spring-boot:run
./mvnw test
./mvnw verify
./mvnw flyway:migrate

# Frontend
cd frontend
npm install
npm run dev
npm run test
npm run build
```

## Project Structure

```text
career-agent/
  README.md
  pom.xml
  .env.example
  docker-compose.yml
  docs/
    CareerAgent-项目设计方案.md
    CareerAgent-任务拆分.md
    spec.md
    api-design.md
    database-design.md
    rag-design.md
    decisions/
  src/
    main/
      java/
        com/huatai/careeragent/
          CareerAgentApplication.java
          common/
          auth/
          user/
          file/
          knowledge/
          resume/
          job/
          task/
          agent/
          report/
          llm/
          config/
      resources/
        application.yml
        db/migration/
        schemas/
    test/
      java/
        com/huatai/careeragent/
  frontend/
    src/
      api/
      pages/
      components/
```

Package responsibilities:

- `common`: unified response, errors, security helpers, trace context.
- `auth`: register, login, JWT issuing.
- `user`: user entity and current user data.
- `file`: upload, storage, parsing.
- `knowledge`: chunking, embedding, retrieval.
- `resume`: resume resource and analysis report access.
- `job`: JD resource and match report access.
- `task`: async task creation, status, progress.
- `agent`: Agent core, workflow, tools, schema validation, logs.
- `report`: final report aggregation.
- `llm`: model provider abstraction.

## Code Style

Backend conventions:

- Controller only handles HTTP boundary concerns.
- Service owns business logic and transactions.
- Repository only handles persistence.
- DTOs are used for API input/output; entities are not returned directly.
- All external input is validated at the boundary.
- All user-owned resource queries must include current `userId`.
- LLM output is treated as untrusted data and must pass JSON Schema validation.

Example service style:

```java
@Service
public class ResumeService {
    private final ResumeRepository resumeRepository;

    public ResumeDetailResponse getResume(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new BusinessException("RESUME_NOT_FOUND", "Resume not found"));

        return ResumeDetailResponse.from(resume);
    }
}
```

API conventions:

- REST resources use plural nouns.
- Request and response fields use camelCase.
- Enum values use UPPER_SNAKE.
- List endpoints return a unified page response.
- Errors return structured code, message, details, and traceId.

## Testing Strategy

Use a small but meaningful test pyramid:

- Unit tests for chunking, state transitions, schema validation, permission checks, prompt builders.
- Integration tests for auth, file metadata, document parsing, knowledge retrieval, task creation, reports.
- Mock or fake LLM providers in tests; tests must not depend on real model network calls.
- Frontend component tests for upload, task status, report rendering and error states.
- One smoke flow for the full V1 path after the frontend exists.

Minimum V1 test coverage targets:

- Auth: register, login, unauthorized, invalid token.
- Resource isolation: user A cannot read user B files, resumes, jobs, tasks, reports, logs.
- RAG: chunk persistence, embedding persistence, topK retrieval, citations.
- Agent: schema success, schema failure, repair failure, task failure logging.
- Report: report aggregation with partial data and complete data.

## Observability

Every request and background task must have a `traceId`.

Record these events:

- File uploaded.
- File parsed success/failure.
- Embedding generated success/failure.
- Retrieval executed.
- Agent started/succeeded/failed.
- Tool called/succeeded/failed.
- LLM called/succeeded/failed.
- Career task status changed.

Telemetry fields:

- `traceId`
- `userId`
- `taskId`
- `agentName`
- `toolName`
- `status`
- `durationMs`
- `promptTokens`
- `completionTokens`
- `totalTokens`
- `retryCount`
- `errorCode`

Never log passwords, JWTs, API keys, full uploaded documents, or full LLM prompts containing private user data.

## Security Boundaries

Always:

- Validate all HTTP input.
- Check authentication on protected endpoints.
- Check ownership on every user resource.
- Hash passwords; never store plaintext.
- Keep API keys in environment variables.
- Treat uploaded documents as untrusted content.
- Treat LLM output and third-party model responses as untrusted content.
- Use Tool Registry allowlists for Agent actions.
- Apply file size and file type limits.

Ask first:

- Changing authentication strategy.
- Adding new external service integrations.
- Changing CORS policy.
- Storing new sensitive user data.
- Introducing LangGraph service deployment.
- Replacing PostgreSQL + pgvector with another vector store.
- Changing the embedding model or embedding dimension after data has been indexed.

Never:

- Commit secrets.
- Log credentials or tokens.
- Return stack traces to users.
- Let Agent call arbitrary system tools.
- Let model output bypass JSON Schema validation.
- Access user resources without `userId` constraints.

## Success Criteria

V1 is complete when:

- A user can register and log in.
- A user can upload a resume and JD.
- The system can parse uploaded documents.
- The system can create chunks and embeddings.
- The system can search relevant chunks with citations.
- A Career task can run through job matching, resume analysis and interview question generation.
- The final report is saved and viewable.
- Agent execution logs and Tool call logs are viewable by task or traceId.
- JSON Schema validation protects all Agent structured outputs.
- User data is isolated by `userId`.
- Backend and frontend build/test commands pass.

## Fixed V1 Decisions

- LLM provider: Alibaba Cloud Model Studio / DashScope.
- Chat model: `qwen-flash`.
- Embedding model: `text-embedding-v4`.
- Embedding dimension: `1024`.
- Progress updates: polling in V1; SSE in V2.
- Maximum upload size: `20MB`.
- Storage: local file storage in V1; MinIO in V2.
- Frontend: build a simple React + TypeScript web app for the MVP.

## Notes

Embedding means converting text into a numeric vector that represents semantic meaning. CareerAgent stores one vector per document chunk, then uses pgvector to find chunks that are semantically close to the user's task or Agent query.

The embedding dimension is the length of that vector. With `text-embedding-v4` and dimension `1024`, the database column should be created as:

```sql
embedding vector(1024)
```

Changing the dimension later requires rebuilding existing embeddings and migrating the pgvector column, so V1 fixes it at `1024`.
