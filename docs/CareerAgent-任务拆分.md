# CareerAgent 任务拆分方案

本文基于 [CareerAgent-项目设计方案.md](./CareerAgent-项目设计方案.md)，使用 `agent-skills` 中的相关工作流进行任务拆分：

- `using-agent-skills`：选择适用 skill 与执行顺序。
- `spec-driven-development`：先固化规格、成功标准和边界。
- `planning-and-task-breakdown`：按依赖图和垂直切片拆成可验收任务。
- `api-and-interface-design`：先定义稳定 API、DTO、错误语义。
- `incremental-implementation`：每个任务保持小步可运行、可回滚。
- `test-driven-development`：新增逻辑优先用测试证明。
- `security-and-hardening`：认证、文件上传、用户数据、LLM 输出均按不可信边界处理。
- `observability-and-instrumentation`：任务、Agent、Tool、模型调用必须可追踪。
- `documentation-and-adrs`：关键架构选择用 ADR 留痕。
- `frontend-ui-engineering`：MVP 管理台按真实工作流构建，而不是展示页。
- `ci-cd-and-automation`、`shipping-and-launch`：建立质量门禁和发布前检查。

## 拆分原则

1. 优先垂直切片：每个阶段尽量交付一条能运行的用户路径，而不是只堆横向基础设施。
2. 合同先行：数据库表、DTO、API 响应、错误码和 JSON Schema 先确定，再实现。
3. 安全前置：用户资源隔离、文件限制、Prompt Injection 防护、LLM 输出校验不作为后补项。
4. 可观测性同步实现：traceId、结构化日志、耗时、token、重试次数随功能一起交付。
5. 小任务可验收：每个任务应在一次聚焦会话内完成，通常控制在 1-5 个主要文件。

## 建议命令

当前仓库还未初始化 Spring Boot 工程。工程创建后建议统一以下命令：

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw verify
./mvnw flyway:migrate
docker compose up -d postgres redis minio
```

第一版固定使用 Maven，不再同时维护 Gradle 路径。

## Phase 0：规格、边界与工程决策

### Task 0.1：补齐项目规格文档

**Description:** 将设计方案转成可执行规格，明确目标用户、MVP 范围、成功标准、命令、目录、测试策略、模型选择和边界。

**Recommended skills:** `spec-driven-development`、`documentation-and-adrs`

**Acceptance criteria:**
- [ ] 新增或补齐 `docs/spec.md`，包含 Objective、Tech Stack、Commands、Project Structure、Testing Strategy、Boundaries、Success Criteria。
- [ ] 明确 MVP 必做和不做事项，与设计方案第 12 节一致。
- [ ] 明确所有外部输入：HTTP 请求、文件、LLM 输出、第三方模型响应；SSE 标为 V2。
- [ ] 明确 V1 模型平台、chat 模型、embedding 模型、embedding 维度、上传大小限制。

**Verification:**
- [ ] 人工检查规格是否能指导后续实现。
- [ ] 所有执行前必须确定的问题集中列在 `Fixed V1 Decisions` 或 `Open Questions`，不散落在正文。

**Dependencies:** None

**Files likely touched:**
- `docs/spec.md`
- `docs/CareerAgent-项目设计方案.md`

**Estimated scope:** S

### Task 0.2：记录核心 ADR

**Description:** 为不可轻易回滚的技术选择建立 ADR，避免后续 Agent 或开发者反复改方向。

**Recommended skills:** `documentation-and-adrs`

**Acceptance criteria:**
- [ ] 新增 `docs/decisions/ADR-001-use-spring-boot-postgresql-pgvector-qwen.md`。
- [ ] 记录 Spring Boot、PostgreSQL + pgvector、Flyway、JWT、百炼/qwen-flash、text-embedding-v4、Tool Registry、状态机驱动 Agent 的选择理由。
- [ ] 列出至少 2 个被拒绝方案及原因。

**Verification:**
- [ ] ADR 状态为 `Accepted`。
- [ ] 设计方案中的技术栈与 ADR 没有冲突。

**Dependencies:** Task 0.1

**Files likely touched:**
- `docs/decisions/ADR-001-use-spring-boot-postgresql-pgvector-qwen.md`

**Estimated scope:** S

### Task 0.3：定义 API 与错误语义合同

**Description:** 在实现前确定 REST 资源命名、分页、统一响应、错误码、认证头和轮询进度行为；SSE 作为 V2 预留。

**Recommended skills:** `api-and-interface-design`、`security-and-hardening`

**Acceptance criteria:**
- [ ] 新增 `docs/api-design.md`，覆盖设计方案第 6 节所有 MVP API。
- [ ] 定义统一成功响应、分页响应和错误响应结构。
- [ ] 明确 400、401、403、404、409、422、500 的使用边界。
- [ ] 明确所有资源接口必须按 `userId` 做归属校验。

**Verification:**
- [ ] API 示例请求和响应字段使用 camelCase。
- [ ] 枚举值使用 UPPER_SNAKE。
- [ ] 没有把内部异常栈或实现细节暴露给客户端。

**Dependencies:** Task 0.1

**Files likely touched:**
- `docs/api-design.md`

**Estimated scope:** M

## Phase 1：工程骨架、认证与基础可观测性

### Task 1.1：初始化 Spring Boot 工程

**Description:** 创建可启动的后端工程骨架，接入基础依赖和环境配置。

**Recommended skills:** `incremental-implementation`、`ci-cd-and-automation`

**Acceptance criteria:**
- [ ] 根目录包含 `pom.xml`、Maven wrapper、`src/main`、`src/test`。
- [ ] Java package 使用 `com.huatai.careeragent`。
- [ ] 接入 Spring Web、Validation、Security、Spring Data JPA、Flyway、Actuator。
- [ ] 提供 `.env.example` 和 `application.yml` 示例配置。
- [ ] `GET /actuator/health` 可返回健康状态。

**Verification:**
- [ ] `./mvnw test` 通过。
- [ ] `./mvnw spring-boot:run` 可启动。

**Dependencies:** Phase 0

**Files likely touched:**
- `pom.xml`
- `src/main/java/com/huatai/careeragent/CareerAgentApplication.java`
- `src/main/resources/application.yml`
- `.env.example`

**Estimated scope:** M

### Task 1.2：配置本地依赖服务

**Description:** 用 Docker Compose 启动 PostgreSQL + pgvector 和 Redis，形成稳定开发环境。

**Recommended skills:** `incremental-implementation`、`documentation-and-adrs`

**Acceptance criteria:**
- [ ] 新增 `docker-compose.yml`，包含 PostgreSQL + pgvector、Redis。
- [ ] 明确第一版使用本地文件存储，MinIO 放到第二版。
- [ ] 数据库连接参数与 `.env.example` 对齐。
- [ ] README 或开发文档给出启动和清理命令。

**Verification:**
- [ ] `docker compose up -d` 成功。
- [ ] 应用能连接数据库和 Redis。

**Dependencies:** Task 1.1

**Files likely touched:**
- `docker-compose.yml`
- `.env.example`
- `README.md`

**Estimated scope:** S

### Task 1.3：实现统一响应、异常处理和 traceId

**Description:** 建立所有 API 的基础返回格式、异常映射和请求链路 ID。

**Recommended skills:** `api-and-interface-design`、`observability-and-instrumentation`

**Acceptance criteria:**
- [ ] 实现 `ApiResponse`、`PageResponse`、`BusinessException`、`GlobalExceptionHandler`。
- [ ] 实现 `TraceIdFilter`，接收或生成 `traceId`，写入响应头和日志上下文。
- [ ] 所有错误返回统一包含 `code`、`message`、可选 `details`、`traceId`。

**Verification:**
- [ ] 单元测试覆盖业务异常、参数校验异常、未知异常。
- [ ] 手动请求非法参数时响应不泄露堆栈。

**Dependencies:** Task 1.1

**Files likely touched:**
- `src/main/java/.../common/api/`
- `src/main/java/.../common/error/`
- `src/main/java/.../common/trace/`

**Estimated scope:** M

### Task 1.4：实现用户注册登录与 JWT 鉴权

**Description:** 交付最小可用认证闭环，支持用户注册、登录、获取当前用户。

**Recommended skills:** `security-and-hardening`、`test-driven-development`、`api-and-interface-design`

**Acceptance criteria:**
- [ ] 建立 `users` 表和实体。
- [ ] 实现 `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`。
- [ ] 密码使用安全哈希，不保存明文。
- [ ] JWT 包含用户 ID、角色、过期时间。
- [ ] 登录失败返回通用错误，不泄露邮箱是否存在。

**Verification:**
- [ ] 注册、登录、鉴权过滤器的单元/集成测试通过。
- [ ] 未登录访问受保护接口返回 401。
- [ ] 使用其他用户 token 不能访问当前用户外的数据。

**Dependencies:** Task 1.3

**Files likely touched:**
- `src/main/java/.../auth/`
- `src/main/java/.../user/`
- `src/main/java/.../common/security/`
- `src/main/resources/db/migration/V1__init.sql`

**Estimated scope:** M

## Phase 2：文件、文档与知识库入库

### Task 2.1：实现文件上传与元数据落库

**Description:** 允许登录用户上传简历、JD、笔记和项目文档，保存原始文件与上传记录。

**Recommended skills:** `security-and-hardening`、`test-driven-development`

**Acceptance criteria:**
- [ ] 实现 `POST /api/files/upload`、`GET /api/files`、`GET /api/files/{fileId}`。
- [ ] 限制文件大小为 20MB，限制 MIME 类型和 `fileType` 枚举。
- [ ] 文件存储路径不可由用户直接控制。
- [ ] `uploaded_files.user_id` 必须来自当前登录用户。

**Verification:**
- [ ] 上传合法 PDF/TXT/Markdown 成功。
- [ ] 超限文件、非法 MIME、未登录上传被拒绝。
- [ ] 用户 A 无法读取用户 B 的文件记录。

**Dependencies:** Task 1.4

**Files likely touched:**
- `src/main/java/.../file/controller/`
- `src/main/java/.../file/service/`
- `src/main/java/.../file/storage/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

### Task 2.2：实现文档解析与 documents 落库

**Description:** 使用 Apache Tika 解析文件文本，并创建标准化 `documents` 记录。

**Recommended skills:** `incremental-implementation`、`test-driven-development`

**Acceptance criteria:**
- [ ] 实现 `POST /api/files/{fileId}/parse`。
- [ ] 解析成功时更新 `uploaded_files.parse_status=SUCCESS`，失败时记录错误摘要。
- [ ] 保存 `documents`，包含 `docType`、`title`、`contentText`、`metadata`。
- [ ] 解析结果为空时返回可理解的业务错误。

**Verification:**
- [ ] 用样例 TXT/Markdown/PDF 做解析测试。
- [ ] 解析失败不会留下错误的 `SUCCESS` 状态。
- [ ] 重复解析同一文件行为明确：覆盖文档或返回冲突，按 API 文档执行。

**Dependencies:** Task 2.1

**Files likely touched:**
- `src/main/java/.../file/parser/`
- `src/main/java/.../file/service/`
- `src/main/java/.../knowledge/entity/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

### Task 2.3：实现文本切分器

**Description:** 将文档按标题、段落、项目条目切成可检索 chunk，并保留引用来源。

**Recommended skills:** `test-driven-development`、`incremental-implementation`

**Acceptance criteria:**
- [ ] 实现 chunk 大小 500-800 tokens、overlap 80-120 tokens 的可配置策略。
- [ ] 每个 chunk 包含 `chunkIndex`、`sourceType`、`sourceTitle`、`sourceLocator`、`tokenCount`。
- [ ] 简历和 JD 支持更细粒度的章节定位。

**Verification:**
- [ ] 单元测试覆盖短文本、长文本、空文本、标题分段、overlap。
- [ ] chunk 顺序稳定，重复切分结果可预测。

**Dependencies:** Task 2.2

**Files likely touched:**
- `src/main/java/.../knowledge/chunk/`
- `src/test/java/.../knowledge/chunk/`

**Estimated scope:** S

### Task 2.4：实现 embedding 入库与知识检索

**Description:** 封装 EmbeddingClient，保存 pgvector，提供向量/关键词/混合检索 API。

**Recommended skills:** `api-and-interface-design`、`observability-and-instrumentation`、`security-and-hardening`

**Acceptance criteria:**
- [ ] `document_chunks.embedding` 使用 pgvector 存储。
- [ ] 实现 `POST /api/knowledge/search`，支持 `sourceTypes`、`topK`、`retrievalMode`。
- [ ] 检索结果返回 citationId、sourceType、sourceTitle、sourceLocator、content、score。
- [ ] 模型供应商响应按不可信数据处理，失败可重试并记录。

**Verification:**
- [ ] 集成测试覆盖 chunk 入库和用户隔离检索。
- [ ] 检索只返回当前用户文档。
- [ ] 模型调用记录耗时、token 或估算 token、失败原因。

**Dependencies:** Task 2.3

**Files likely touched:**
- `src/main/java/.../knowledge/embedding/`
- `src/main/java/.../knowledge/retrieval/`
- `src/main/java/.../llm/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

## Phase 3：简历、岗位与任务状态机

### Task 3.1：实现 Resume 和 Job 基础资源

**Description:** 建立简历和岗位资源，使上传解析后的文档能被业务流程引用。

**Recommended skills:** `api-and-interface-design`、`test-driven-development`

**Acceptance criteria:**
- [ ] 实现 `POST /api/resumes`、`GET /api/resumes`、`GET /api/resumes/{resumeId}`。
- [ ] 实现 `POST /api/jobs`、`GET /api/jobs`、`GET /api/jobs/{jobId}`。
- [ ] 创建资源时校验 `documentId` 属于当前用户且类型匹配。
- [ ] JD 支持直接文本创建，也支持从文档创建。

**Verification:**
- [ ] API 集成测试覆盖创建、列表、详情、越权访问。
- [ ] 无效 documentId 或类型不匹配返回 422 或 404。

**Dependencies:** Task 2.2

**Files likely touched:**
- `src/main/java/.../resume/`
- `src/main/java/.../job/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

### Task 3.2：实现异步任务和状态机

**Description:** 建立 `agent_tasks` 与工作流状态推进能力，为 Career 工作流提供执行骨架。

**Recommended skills:** `incremental-implementation`、`observability-and-instrumentation`

**Acceptance criteria:**
- [ ] 实现 `POST /api/career-tasks`、`GET /api/career-tasks/{taskId}`。
- [ ] 支持 `PENDING -> PARSING_FILE -> EMBEDDING -> MATCHING_JOB -> ANALYZING_RESUME -> GENERATING_QUESTIONS -> SUCCESS`。
- [ ] 失败时进入 `FAILED`，记录 `errorMessage`。
- [ ] 支持异步执行和进度更新。

**Verification:**
- [ ] 状态流转单元测试覆盖合法流转和非法流转。
- [ ] 创建任务后可查询进度。
- [ ] 异步执行失败能被记录且不会吞异常。

**Dependencies:** Task 3.1

**Files likely touched:**
- `src/main/java/.../task/`
- `src/main/java/.../agent/workflow/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

### Task 3.3：实现任务日志查询和轮询进度

**Description:** 让前端通过轮询看到任务进度和后端执行轨迹。SSE 放到第二版增强，不作为第一版阻塞项。

**Recommended skills:** `observability-and-instrumentation`、`api-and-interface-design`

**Acceptance criteria:**
- [ ] 实现 `GET /api/career-tasks/{taskId}/logs`。
- [ ] `GET /api/career-tasks/{taskId}` 返回 status、progress、traceId、更新时间。
- [ ] `GET /api/career-tasks/{taskId}/events` 仅在文档中标为 V2 预留接口，不在 V1 实现。
- [ ] 日志查询按当前用户隔离。
- [ ] 事件中包含 status、progress、traceId、更新时间。

**Verification:**
- [ ] 轮询能看到任务状态变化。
- [ ] 越权订阅其他用户任务被拒绝。

**Dependencies:** Task 3.2

**Files likely touched:**
- `src/main/java/.../task/controller/`
- `src/main/java/.../task/service/`
- `src/main/java/.../agent/log/`

**Estimated scope:** S

## Phase 4：Tool Registry、权限与调用审计

### Task 4.1：定义 Tool 抽象和 Registry

**Description:** 建立 Agent 调用工具的受控入口，禁止 Agent 直接访问数据库或外部系统。

**Recommended skills:** `api-and-interface-design`、`incremental-implementation`

**Acceptance criteria:**
- [ ] 实现 `Tool<I,O>`、`ToolRequest`、`ToolResponse`、`ToolExecutionContext`。
- [ ] 实现线程安全 `ToolRegistry`。
- [ ] Tool 输入输出使用 DTO Validation 或 JSON Schema 校验。
- [ ] 未注册工具调用返回明确错误。

**Verification:**
- [ ] 单元测试覆盖注册、获取、重复注册、未注册。
- [ ] Tool context 必须包含 userId、taskId、traceId、agentName。

**Dependencies:** Task 3.2

**Files likely touched:**
- `src/main/java/.../agent/tool/`

**Estimated scope:** S

### Task 4.2：实现 Tool 权限校验和调用日志

**Description:** 每次工具调用前校验资源归属和 Agent 白名单，调用后记录完整审计日志。

**Recommended skills:** `security-and-hardening`、`observability-and-instrumentation`

**Acceptance criteria:**
- [ ] 实现 `ToolPermissionChecker`。
- [ ] 实现 `tool_call_logs` 表和写入服务。
- [ ] 记录 traceId、taskId、agentName、toolName、input、output、status、durationMs、errorMessage、token、retryCount。
- [ ] 日志中的敏感字段做脱敏或白名单记录。

**Verification:**
- [ ] 越权资源调用被拒绝并写入失败日志。
- [ ] 正常工具调用写入成功日志。
- [ ] 日志不包含密码、JWT、完整密钥。

**Dependencies:** Task 4.1

**Files likely touched:**
- `src/main/java/.../agent/tool/`
- `src/main/java/.../agent/log/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

### Task 4.3：实现 MVP 基础工具

**Description:** 实现岗位匹配和简历分析所需的最小工具集。

**Recommended skills:** `incremental-implementation`、`test-driven-development`

**Acceptance criteria:**
- [ ] 实现 `getResume`、`getJobDescription`、`searchUserKnowledgeBase`。
- [ ] 每个工具只返回 Agent 所需字段，避免泄漏内部实体。
- [ ] 工具失败返回结构化错误，可用于重试或终止任务。

**Verification:**
- [ ] 工具单元/集成测试覆盖成功、找不到资源、越权、检索为空。
- [ ] 调用日志能关联到 taskId 和 traceId。

**Dependencies:** Task 4.2

**Files likely touched:**
- `src/main/java/.../agent/tool/`
- `src/main/java/.../resume/service/`
- `src/main/java/.../job/service/`
- `src/main/java/.../knowledge/retrieval/`

**Estimated scope:** M

## Phase 5：LLM、Schema 校验与 Agent 基础能力

### Task 5.1：封装 LlmClient 和模型配置

**Description:** 提供统一 LLM 调用接口，第一版默认接阿里云百炼 / DashScope，隔离后续 DeepSeek、OpenAI、智谱等供应商差异。

**Recommended skills:** `api-and-interface-design`、`source-driven-development`、`observability-and-instrumentation`

**Acceptance criteria:**
- [ ] 实现 `LlmClient`、`LlmRequest`、`LlmResponse`、`ModelConfig`。
- [ ] 默认 chat 模型配置为 `qwen-flash`。
- [ ] 默认 embedding 模型配置为 `text-embedding-v4`，维度为 `1024`。
- [ ] 支持超时、重试、错误分类、token 统计。
- [ ] Prompt 输入中的用户文档统一标记为不可信上下文。
- [ ] 模型响应按不可信外部数据处理。

**Verification:**
- [ ] 单元测试覆盖请求构造、错误映射、token 字段解析。
- [ ] 模型调用日志含 provider、model、durationMs、token、traceId。

**Dependencies:** Task 1.3

**Files likely touched:**
- `src/main/java/.../llm/`
- `src/main/resources/application.yml`

**Estimated scope:** M

### Task 5.2：实现 JSON Schema 校验和修复

**Description:** 所有 Agent 输出必须符合 JSON Schema，失败时先尝试修复，再按策略重试。

**Recommended skills:** `test-driven-development`、`security-and-hardening`

**Acceptance criteria:**
- [ ] 实现 `JsonSchemaValidator` 和 `SchemaRepairService`。
- [ ] 新增 `resume_analysis_result.schema.json`、`job_match_result.schema.json`、`interview_questions.schema.json`。
- [ ] `answer_score.schema.json` 标为第二版模拟面试评分使用，不在 V1 创建。
- [ ] 校验失败记录原因和原始输出摘要，不记录敏感全文。
- [ ] 修复失败后返回结构化业务错误。

**Verification:**
- [ ] Schema 单元测试覆盖合法、缺字段、类型错误、额外字段。
- [ ] 修复服务有 mock LLM 测试或可替代测试。

**Dependencies:** Task 5.1

**Files likely touched:**
- `src/main/java/.../agent/schema/`
- `src/main/resources/schemas/`
- `src/test/java/.../agent/schema/`

**Estimated scope:** M

### Task 5.3：实现 AgentExecutor 和执行日志

**Description:** 为具体 Agent 提供统一执行入口、日志、失败重试、耗时和 token 统计。

**Recommended skills:** `observability-and-instrumentation`、`incremental-implementation`

**Acceptance criteria:**
- [ ] 实现 `Agent`、`AgentContext`、`AgentResult`、`AgentExecutor`。
- [ ] 实现 `agent_execution_logs` 表和服务。
- [ ] 支持可重试错误最多 2 次、指数退避。
- [ ] 每次执行记录 agentName、stepName、status、durationMs、token、errorMessage。

**Verification:**
- [ ] 单元测试覆盖成功执行、可重试失败、不可重试失败、日志落库。
- [ ] traceId 在 Agent、Tool、LLM 日志间一致。

**Dependencies:** Task 4.3, Task 5.2

**Files likely touched:**
- `src/main/java/.../agent/core/`
- `src/main/java/.../agent/log/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

## Phase 6：核心 Agent 垂直切片

### Task 6.1：实现岗位匹配 Agent

**Description:** 基于简历、JD 和 RAG 上下文生成结构化岗位匹配报告。

**Recommended skills:** `incremental-implementation`、`test-driven-development`、`security-and-hardening`

**Acceptance criteria:**
- [ ] 实现 `JobMatchAgent`。
- [ ] 输出符合 `job_match_result.schema.json`。
- [ ] 保存 `job_match_reports`，版本号递增。
- [ ] 报告包含 matchScore、summary、strengths、weaknesses、missingSkills、suggestedResumeChanges、citations。

**Verification:**
- [ ] 使用 mock LLM 的集成测试覆盖成功、Schema 失败修复、检索为空。
- [ ] `POST /api/jobs/{jobId}/match?resumeId={resumeId}` 返回任务或报告，行为与 API 文档一致。
- [ ] 引用的 citations 能对应到当前用户 chunk。

**Dependencies:** Task 5.3

**Files likely touched:**
- `src/main/java/.../agent/agents/JobMatchAgent.java`
- `src/main/java/.../job/`
- `src/main/java/.../report/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

### Task 6.2：实现简历分析 Agent

**Description:** 生成简历亮点、弱点、项目表达问题、优化建议、风险点和下一步行动。

**Recommended skills:** `incremental-implementation`、`test-driven-development`

**Acceptance criteria:**
- [ ] 实现 `ResumeAnalysisAgent`。
- [ ] 输出符合 `resume_analysis_result.schema.json`。
- [ ] 保存 `resume_analysis_reports`，版本号递增。
- [ ] `POST /api/resumes/{resumeId}/analyze` 可触发分析。

**Verification:**
- [ ] mock LLM 测试覆盖成功、Schema 错误、版本递增。
- [ ] 只使用当前用户简历和可选 JD 上下文。

**Dependencies:** Task 5.3

**Files likely touched:**
- `src/main/java/.../agent/agents/ResumeAnalysisAgent.java`
- `src/main/java/.../resume/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

### Task 6.3：实现面试题生成 Agent

**Description:** 根据简历、JD、项目文档生成带引用的个性化面试题。

**Recommended skills:** `incremental-implementation`、`test-driven-development`

**Acceptance criteria:**
- [ ] 实现 `InterviewQuestionAgent`。
- [ ] 输出符合 `interview_questions.schema.json`。
- [ ] 保存 `interview_questions`。
- [ ] 支持按 `resumeId`、`jobId`、`difficulty`、`questionType` 查询。

**Verification:**
- [ ] 每道题至少包含一个 citation 或明确无引用原因。
- [ ] 查询接口只返回当前用户问题。
- [ ] mock LLM 测试覆盖题型和难度筛选。

**Dependencies:** Task 5.3

**Files likely touched:**
- `src/main/java/.../agent/agents/InterviewQuestionAgent.java`
- `src/main/java/.../interview/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

### Task 6.4：串联 Career MVP 工作流

**Description:** 将岗位匹配、简历分析、面试题生成串成完整 `CAREER_PREPARE` 任务。

**Recommended skills:** `incremental-implementation`、`observability-and-instrumentation`

**Acceptance criteria:**
- [ ] `POST /api/career-tasks` 可按 enabledSteps 执行 MVP 流程。
- [ ] 状态依次推进到 `SUCCESS` 或 `FAILED`。
- [ ] 任一步失败可定位到 agentName、stepName、traceId。
- [ ] 支持重新运行失败步骤的后端服务能力或明确留为后续任务。

**Verification:**
- [ ] 端到端集成测试使用 mock LLM 跑完整工作流。
- [ ] 任务完成后能查到三类核心结果和完整日志。

**Dependencies:** Task 6.1, Task 6.2, Task 6.3

**Files likely touched:**
- `src/main/java/.../agent/workflow/CareerWorkflowService.java`
- `src/main/java/.../task/`
- `src/test/java/.../agent/workflow/`

**Estimated scope:** M

## Phase 7：第一版报告聚合

### Task 7.1：实现最终报告聚合

**Description:** 聚合岗位匹配、简历分析和面试题，形成第一版最终报告。模拟面试、回答保存和评分放到第二版。

**Recommended skills:** `api-and-interface-design`、`documentation-and-adrs`

**Acceptance criteria:**
- [ ] 实现 `GET /api/reports`、`GET /api/reports/{reportId}`。
- [ ] 创建或刷新 `final_reports.report_json`。
- [ ] MVP 输出 HTML/JSON 报告，PDF 导出留后续。
- [ ] 报告保留各子报告版本和引用来源。
- [ ] 报告不依赖模拟面试评分。

**Verification:**
- [ ] 集成测试覆盖报告生成、列表、详情、越权。
- [ ] 报告缺少某部分结果时有明确状态或占位，不抛未知异常。

**Dependencies:** Task 6.4

**Files likely touched:**
- `src/main/java/.../report/`
- `src/main/resources/db/migration/`

**Estimated scope:** M

## Phase 8：MVP 管理台前端

### Task 8.1：初始化前端工程和 API Client

**Description:** 创建最小管理台工程，封装登录、文件、任务、报告、日志 API。

**Recommended skills:** `frontend-ui-engineering`、`api-and-interface-design`

**Acceptance criteria:**
- [ ] 前端支持登录态保存和退出。
- [ ] API Client 统一处理 token、错误响应、分页响应。
- [ ] 页面路由包含登录、上传、任务、报告、日志。

**Verification:**
- [ ] `npm run build` 或等价命令通过。
- [ ] API mock 或本地后端联调能显示错误消息。

**Dependencies:** Task 0.3, Task 1.4

**Files likely touched:**
- `frontend/` 或 `src/main/frontend/`

**Estimated scope:** M

### Task 8.2：实现上传和任务创建页面

**Description:** 用户可以上传简历/JD，创建 Career 任务，并查看当前状态。

**Recommended skills:** `frontend-ui-engineering`、`test-driven-development`

**Acceptance criteria:**
- [ ] 文件上传控件支持文件类型、大小错误提示。
- [ ] 用户可选择 resume、job 和 enabledSteps 创建任务。
- [ ] 任务创建成功后跳转任务详情。
- [ ] 加载、空状态、错误状态完整。

**Verification:**
- [ ] 浏览器手动检查上传、创建任务、错误提示。
- [ ] 核心组件测试覆盖状态渲染。

**Dependencies:** Task 8.1, Task 3.2

**Files likely touched:**
- `frontend/src/pages/Upload*`
- `frontend/src/pages/Task*`
- `frontend/src/api/`

**Estimated scope:** M

### Task 8.3：实现报告和 trace 日志页面

**Description:** 用户可以查看最终报告、Agent 执行日志和 Tool 调用日志。

**Recommended skills:** `frontend-ui-engineering`、`observability-and-instrumentation`

**Acceptance criteria:**
- [ ] 报告页展示匹配分、简历建议、面试题和 citations。
- [ ] 日志页可按 taskId/traceId 查看 Agent 和 Tool 调用。
- [ ] 日志中的 JSON 输入输出有折叠和脱敏展示。
- [ ] 移动端和桌面端无文本重叠。

**Verification:**
- [ ] 使用真实或 mock 任务数据手动检查核心视图。
- [ ] 页面可从任务详情顺畅跳转。

**Dependencies:** Task 7.1, Task 8.2

**Files likely touched:**
- `frontend/src/pages/Report*`
- `frontend/src/pages/Logs*`
- `frontend/src/components/`

**Estimated scope:** M

## Phase 9：质量门禁、安全审计与发布准备

### Task 9.1：建立测试分层和基础测试集

**Description:** 明确单元、集成、端到端测试边界，补齐 MVP 关键路径测试。

**Recommended skills:** `test-driven-development`

**Acceptance criteria:**
- [ ] 单元测试覆盖纯逻辑：chunk、状态机、Schema 校验、权限判断。
- [ ] 集成测试覆盖 API、数据库、用户隔离。
- [ ] E2E 或冒烟测试覆盖登录、上传、创建任务、轮询进度、查看报告。
- [ ] 外部 LLM 使用 mock 或 fake provider，测试不依赖真实网络。

**Verification:**
- [ ] `./mvnw test` 通过。
- [ ] 前端测试命令通过。

**Dependencies:** Phase 1-8 可持续补充，MVP 完成前必须完成

**Files likely touched:**
- `src/test/`
- `frontend/src/**/*.test.*`
- `docs/testing.md`

**Estimated scope:** M

### Task 9.2：配置 CI 质量门禁

**Description:** 用 CI 自动执行后端测试、前端测试、构建和基础安全扫描。

**Recommended skills:** `ci-cd-and-automation`

**Acceptance criteria:**
- [ ] 新增 `.github/workflows/ci.yml`。
- [ ] 后端执行测试和构建。
- [ ] 前端执行 lint/test/build。
- [ ] 数据库集成测试使用 CI PostgreSQL 服务。
- [ ] secrets 不写入仓库。

**Verification:**
- [ ] 本地命令与 CI 命令一致。
- [ ] CI 首次运行成功或失败原因清晰可修复。

**Dependencies:** Task 1.1, Task 8.1

**Files likely touched:**
- `.github/workflows/ci.yml`
- `README.md`

**Estimated scope:** S

### Task 9.3：安全检查和 Prompt Injection 防护验证

**Description:** 针对认证、文件上传、用户隔离、LLM 上下文和日志脱敏做专项检查。

**Recommended skills:** `security-and-hardening`、`code-review-and-quality`

**Acceptance criteria:**
- [ ] 所有用户资源查询都包含当前用户约束。
- [ ] 文件上传有大小、类型、路径、防覆盖校验。
- [ ] Prompt 中的用户文档统一标注为不可信上下文。
- [ ] 模型不能自由选择任意系统工具，只能走工作流白名单。
- [ ] 日志不记录 JWT、密码、API Key、完整敏感文档。

**Verification:**
- [ ] 安全测试覆盖越权访问、恶意文件名、prompt injection 文本。
- [ ] 人工 code review 记录发现和修复。

**Dependencies:** Phase 1-7

**Files likely touched:**
- `src/test/java/.../security/`
- `docs/security-review.md`

**Estimated scope:** M

### Task 9.4：发布前检查和运行手册

**Description:** 准备 MVP 演示或上线所需的部署、回滚、监控、问题排查文档。

**Recommended skills:** `shipping-and-launch`、`documentation-and-adrs`

**Acceptance criteria:**
- [ ] README 包含 Quick Start、环境变量、启动依赖、测试命令。
- [ ] 新增 `docs/runbook.md`，覆盖任务失败、模型失败、数据库连接失败、文件解析失败。
- [ ] 定义发布检查清单：测试、构建、迁移、安全、监控。
- [ ] 定义最小监控指标：接口错误率、p95 延迟、任务失败率、LLM 失败率、token 成本。

**Verification:**
- [ ] 新同事或新 Agent 只看 README 和 runbook 能启动项目。
- [ ] 发布检查清单无阻塞项。

**Dependencies:** MVP 功能完成

**Files likely touched:**
- `README.md`
- `docs/runbook.md`
- `docs/release-checklist.md`

**Estimated scope:** S

## Phase 10：文字对话式模拟面试

### Task 10.1：实现交互式面试会话

**Description:** 基于已生成的个性化面试题，提供可持续追问、保存记录和主动结束的文字面试会话。

**Acceptance criteria:**
- [x] 用户可基于简历和岗位创建模拟面试会话。
- [x] 会话按题目顺序推进，并保存面试官与候选人的完整消息。
- [x] 模型可针对回答最多追问一次，模型异常时安全进入下一题。
- [x] 用户可主动结束面试，最后一题完成后也可自动结束。
- [x] 所有会话按 `userId` 隔离。
- [x] 前端提供桌面和移动端可用的文字对话界面。

**Verification:**
- [x] 会话状态、追问 Schema 和持久化集成测试通过。
- [x] 后端测试、前端 lint/test/build 通过。

**Dependencies:** Phase 6, Phase 8

## Phase 10.5：v0.1.1 主流程稳定化与测试对齐

**Description:** 在增加回答评分前，先固定职业分析与模拟面试的真实性、并发隔离和回归测试基线。

**Acceptance criteria:**
- [x] 删除职业任务中不执行真实工作的解析、向量化状态及旧日志状态。
- [x] Agent 步骤和工具调用测试对齐真实 started/completed/failed 事件。
- [x] 报告版本分配在并发任务下串行化，最终报告只聚合同一 `taskId` 的产物。
- [x] 增加同一用户快速发起两个任务的跨任务污染回归测试。
- [x] 模拟面试答案写入串行化，SSE 使用独立线程池并正确处理断连和终态。
- [x] 增加带认证的模拟面试 SSE 生命周期集成测试。
- [x] 完整后端 verify 与前端 check 通过。

**Release target:** `v0.1.1`

## Phase 11：回答评分与改进建议

**Description:** 在 v0.1.1 稳定基线完成后，为每次回答增加结构化评分、证据说明和可执行的改进建议。

**Acceptance criteria:**
- [x] 定义评分维度、固定权重、Schema 和异常降级策略。
- [x] 评分结果绑定 `sessionId`、`questionId` 和对应回答，禁止跨会话聚合。
- [x] SSE 推送评分中、评分完成或评分失败事件，不流式暴露未校验 JSON。
- [x] 前端逐题展示评分依据、优点、缺口和改进示例。
- [x] 增加评分 Schema、持久化、用户隔离和端到端测试。

**Release target:** `v0.1.2`

**Dependencies:** Phase 10.5 完成

## Phase 12：LangGraph 可替换编排层

**Description:** 在不迁移业务数据和工具权限的前提下，引入独立 LangGraph 编排服务；Spring Boot 继续负责节点执行、状态、日志、报告和失败落库。

**Acceptance criteria:**
- [x] 新增 `AgentWorkflowExecutor`，现有 Spring 编排成为默认实现和稳定 fallback。
- [x] LangGraph 适配器透传 `taskId`、`traceId` 和启用步骤。
- [x] 远程计划必须通过节点白名单、完整性和顺序校验后才能执行。
- [x] LangGraph 不可用或计划非法时可配置 fallback 或任务失败。
- [x] 提供 FastAPI/LangGraph 容器、健康检查和 HTTP 合同测试。
- [x] 完整后端、前端和 LangGraph 服务验证通过。

**Release target:** `v0.2.0`

**Dependencies:** Phase 11 完成

## Phase 13：职业任务 SSE 与断线恢复

**Description:** 用后端真实任务、步骤和工具日志驱动任务详情，替代固定间隔轮询；连接中断后通过持久化快照恢复一致状态。

**Acceptance criteria:**
- [x] 提供带用户隔离的 `GET /api/career-tasks/{taskId}/events`。
- [x] 首次连接与重连先推送数据库完整快照，再推送任务、步骤和工具增量事件。
- [x] 工具事件按 `toolCallId` 合并，事件携带稳定 SSE ID 和心跳。
- [x] 前端回传 `Last-Event-ID`，按 ID 去重，并使用有上限的指数退避重连。
- [x] 终态主动完成事件流，异常时仍可通过 REST 快照降级恢复。
- [x] 完整后端与前端验证通过。

**Release target:** `v0.2.1`

**Dependencies:** Phase 12 完成

## Phase 14：模拟面试会话总结与复盘报告

**Description:** 在逐题评分和真实自然语言流式输出稳定后，为已结束的模拟面试生成会话级复盘，给出可信的能力总结和可执行改进计划。

**Acceptance criteria:**
- [x] 每份复盘绑定唯一 `sessionId` 和 `userId`，只聚合同一会话的对话与逐题评分。
- [x] 复盘包含总分、四项维度、优势、主要缺口、行动计划和针对性练习题。
- [x] 模型结果通过 JSON Schema 校验，分数由后端基于逐题评分重新计算并覆盖模型值。
- [x] 同一会话重复请求幂等，模型异常时使用已校验评分生成降级复盘。
- [x] 面试结束后前端自动生成并展示，历史会话可读取已持久化复盘。
- [x] 增加数据库迁移、用户隔离、幂等、Schema 和前端构建验证。

**Release target:** `v0.2.2`

**Dependencies:** Phase 13 完成

## Checkpoints

### Checkpoint A：Phase 0-1 完成

- [ ] 规格、ADR、API 合同完成。
- [ ] 应用可启动，健康检查可用。
- [ ] 注册、登录、`/me` 可用。
- [ ] 统一异常和 traceId 可见。

### Checkpoint B：Phase 2-3 完成

- [ ] 用户可上传并解析简历/JD。
- [ ] 文档可切分、入库、检索。
- [ ] Resume、Job、Career Task 资源可用。
- [ ] 状态机可异步推进并记录失败。

### Checkpoint C：Phase 4-6 完成

- [ ] Agent 只能通过 Tool Registry 访问资源。
- [ ] Tool 和 Agent 日志可按 traceId 串联。
- [ ] 岗位匹配、简历分析、面试题生成可用。
- [ ] Career MVP 工作流可端到端完成。

### Checkpoint D：Phase 7-8 完成

- [ ] 最终报告可聚合展示。
- [ ] 前端可完成登录、上传、创建任务、查看报告、查看日志。

### Checkpoint E：Phase 9 完成

- [ ] 后端、前端、集成测试通过。
- [ ] CI 可运行。
- [ ] 安全检查通过。
- [ ] README、runbook、release checklist 完成。

## 并行化建议

可以并行：

- Task 0.2 和 Task 0.3：ADR 与 API 文档可并行，但最终要互相校验。
- Task 2.3 和 Task 5.1：文本切分和 LLM Client 可并行。
- Task 6.1、Task 6.2、Task 6.3：在 AgentExecutor、Tool、Schema 稳定后可并行开发。
- Task 8.1 可在 API 合同稳定后提前启动，先用 mock 数据。
- Task 9.1 可贯穿所有阶段补充测试。

必须顺序：

- 数据库迁移和核心实体变更必须先于依赖 API。
- Auth 和用户隔离必须先于文件、文档、任务资源。
- Tool Registry 和权限日志必须先于核心 Agent。
- JSON Schema 校验必须先于保存 Agent 结构化报告。
- 报告聚合必须等岗位匹配、简历分析和面试题三个核心结果至少有稳定合同。

## 主要风险与缓解

| Risk | Impact | Mitigation |
| --- | --- | --- |
| 一开始抽象多 Agent 编排过重 | 高 | MVP 使用后端状态机驱动固定节点，handoff 留后续 |
| LLM 输出不稳定 | 高 | JSON Schema 校验、修复、重试、mock provider 测试 |
| 用户数据越权 | 高 | 所有 repository/service 查询带 userId，安全测试覆盖 |
| 文件解析失败率高 | 中 | 解析状态落库、错误摘要、支持 TXT/Markdown fallback |
| pgvector 和模型维度不一致 | 中 | ModelConfig 记录 embedding 维度，启动或迁移时校验 |
| 日志泄露敏感信息 | 高 | 日志字段白名单、脱敏、禁止全量 request/response body |
| 前端早于 API 实现导致返工 | 中 | API 合同先行，前端使用 mock client 对齐合同 |
| 外部模型成本不可控 | 中 | 记录 token、任务级成本估算、限制重试和 topK |

## 后续迭代任务池

- 学习计划 Agent。
- PDF 导出。
- SSE 实时进度和断线重连。
- 模拟面试、评分和会话总结。
- 自动评测集与 Prompt 回归测试。
- MCP 工具接入。
- 多 Agent handoff。
- Elasticsearch / Milvus。
- 更完整的 Prompt Injection 检测与策略引擎。
