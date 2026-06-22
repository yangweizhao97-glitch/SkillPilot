# SkillPilot 智能面试与行业面经知识库优化方案

## 1. 文档目的

本文档定义 SkillPilot 下一轮功能优化的固定范围与实施方案。目标不是继续堆叠孤立功能，而是将现有的职业分析、面试题、模拟面试、学习计划和 RAG 能力连接成一条持续反馈的求职训练链路：

```text
简历与 JD 分析
  → 个性化题目、参考答案与评分标准
  → 自适应模拟面试
  → AI 答疑与概念辅导
  → 短期冲刺或长期学习计划
  → 面试复盘继续修正题目和计划
```

本轮优化包括四项产品能力和一项数据基础设施：

1. 面试题同时生成参考答案、回答框架和评分标准。
2. 模拟面试根据用户实际回答进行澄清、纠错和深入追问。
3. 新增可引用用户资料与公共知识库的 AI 答疑 Agent。
4. 学习计划区分短期面试冲刺和长期能力成长。
5. 建设覆盖互联网及其他行业的公共面经知识库，并可选接入实时搜索工具。

### 1.1 核心改造原则

本方案必须在不破坏现有功能基础的前提下实施：

- 保留现有职业分析任务、`taskId` 数据隔离、结构化报告和 PDF 导出合同。
- 保留现有模拟面试会话、评分、流式输出、复盘和长期记忆能力。
- 保留现有私人知识库、混合检索、引用和 Prompt Injection 防护。
- 保留现有 Tool Registry、工具时间线、Agent 权限和 MCP 白名单边界。
- 新字段优先采用兼容默认值，数据库只通过可前向迁移的 Flyway 脚本升级。
- API 优先做向后兼容扩展；必须变更合同时，先同时支持旧版和新版，再迁移前端。
- 每个批次独立完成实现、迁移、自动化测试和回归验证，不允许多个未验证批次叠加。
- 新实现稳定并完成调用方迁移后，必须删除旧实现、废弃 Schema、无效配置和未使用代码。

改造流程固定为：

```text
建立现有行为回归基线
  → 增量增加新数据和新接口
  → 新旧路径并行验证
  → 切换生产调用路径
  → 全量回归
  → 删除旧代码和兼容层
  → 静态检查与残余代码审计
```

---

## 2. 当前能力与主要差距

### 2.1 已有能力

- 简历、JD、笔记和项目文档上传、解析、切块、向量化。
- `RESUME`、`JD`、`NOTE`、`PROJECT_DOC` 混合检索。
- 岗位匹配、简历分析、面试题生成和任务级最终报告。
- 面试回答评分、参考表达、一次追问、流式追问和会话复盘。
- 用户、简历、岗位维度的长期面试记忆。
- 任务进度、Agent、工具和 LLM 调用可视化。
- 阿里云百炼 / DashScope 文本生成和 Embedding。
- 通用 MCP Streamable HTTP 客户端及 Tool Registry 安全边界。

### 2.2 当前差距

- 面试题只有考察要点，没有完整的参考答案、评分规则和常见错误。
- 模拟面试仅支持简单的一次追问，无法稳定区分未回答、答非所问、部分回答和关键错误。
- 无效回答仍可能形成 0 分记忆，污染长期能力画像。
- 缺少独立的多轮 AI 答疑入口。
- 学习计划只按周生成，无法适配距离面试仅剩 1～7 天的用户。
- 项目只有用户私人知识库，没有跨用户共享、经过治理的公共行业面经库。
- MCP 只有通用客户端框架，当前运行配置未启用任何具体远程工具。

---

## 3. 总体架构

### 3.1 双层知识库

系统将知识数据分为两个安全域。

#### 用户私人知识库

只允许资料所有者访问：

- 简历与 JD
- 项目文档和课程笔记
- 用户真实面试记录
- 模拟面试消息、评分与复盘
- 用户学习计划及完成记录

#### 公共行业面经库

向所有合法用户提供经过清洗和治理的行业信息：

- 行业、岗位、方向和职级
- 公司、业务线和面试轮次
- 高频问题、知识点和常见追问
- 回答框架、评分要点和常见错误
- 来源链接、采集时间、质量分和使用状态

公共库不得通过伪造 `userId` 混入私人库。检索层应显式合并 `PRIVATE` 与 `PUBLIC` 两个 scope，并分别执行权限检查。

### 3.2 组合检索

Agent 的检索上下文按以下顺序组装：

```text
用户简历和 JD
  + 用户私人知识片段
  + 公共行业面经片段
  + 可选的实时互联网搜索结果
```

检索采用：

- 元数据过滤：行业、岗位、公司、职级、轮次、时间范围。
- 关键词检索：技术名词、公司和岗位高频词。
- 向量检索：问题语义和项目场景相似度。
- 混合排序：相关性、时效性、来源质量和个性化权重。
- 去重：标准化问题哈希和向量相似度共同判断。

---

## 4. 公共行业面经知识库

### 4.1 数据来源

首期支持以下来源：

1. 管理员人工录入或批量导入的授权资料。
2. 用户主动提交并明确允许匿名共享的面试经验。
3. 用户粘贴的公开面经链接或文本。
4. 受信任搜索 API 或 Web Search MCP 返回的公开网页结果。
5. 牛客等面经平台的公开页面摘要，前提是访问和使用方式符合平台规则。

Codex 在开发阶段可以帮助检索和整理内容，但部署后的 SkillPilot 不能调用当前 Codex 会话。生产环境必须接入正式搜索 API、MCP 服务或其他受控搜索提供方。

### 4.2 内容采集流水线

```text
发现候选来源
  → URL 和来源校验
  → 页面或文件解析
  → 隐私与敏感信息清理
  → 正文去噪
  → 相似内容去重
  → 百炼模型结构化抽取
  → Schema 校验与质量评分
  → 切块和 Embedding
  → 审核后发布到公共知识库
```

百炼模型主要负责：

- 抽取行业、公司、岗位、职级和轮次。
- 将叙述型面经拆成独立问题。
- 识别知识点和问题类型。
- 生成回答框架、参考答案和评分要点。
- 生成常见追问和常见错误。
- 对相似问题进行归一化和聚类。

搜索工具负责发现和获取候选资料，模型不应把自身参数知识描述为实时搜索结果。

### 4.3 建议数据模型

#### `knowledge_sources`

| 字段 | 说明 |
|---|---|
| `id` | 来源 ID |
| `source_type` | `MANUAL`、`USER_SHARED`、`WEB`、`IMPORT` |
| `platform` | 来源平台，如 `NOWCODER` |
| `source_url` | 原始链接 |
| `title` | 原始标题 |
| `published_at` | 原始发布时间 |
| `collected_at` | 采集时间 |
| `content_hash` | 去重哈希 |
| `copyright_status` | 授权或使用状态 |
| `review_status` | `PENDING`、`APPROVED`、`REJECTED` |
| `quality_score` | 来源质量分 |

#### `interview_experiences`

| 字段 | 说明 |
|---|---|
| `id` | 面经 ID |
| `source_id` | 来源 ID |
| `industry` | 行业 |
| `company` | 公司 |
| `position` | 岗位 |
| `level` | 职级或经验范围 |
| `interview_round` | 面试轮次 |
| `summary` | 结构化摘要 |
| `tags` | 技术和业务标签 |
| `event_date` | 面试发生时间 |
| `status` | 发布状态 |

#### `public_interview_questions`

| 字段 | 说明 |
|---|---|
| `id` | 公共题目 ID |
| `experience_id` | 来源面经 ID |
| `normalized_question` | 归一化问题 |
| `question_type` | 技术、项目、行为、系统设计等 |
| `difficulty` | 难度 |
| `knowledge_points` | 知识点 |
| `answer_outline` | 回答结构 |
| `reference_answer` | 参考答案 |
| `scoring_rubric` | 评分规则 JSON |
| `common_mistakes` | 常见错误 |
| `follow_up_candidates` | 追问题库 |
| `embedding` | 向量 |

公共库只保留必要摘要、结构化问题和引用信息，不默认长期保存或展示第三方完整原文。

---

## 5. 面试题与参考答案

### 5.1 Schema 扩展

在现有题目结构上增加：

```json
{
  "question": "如何设计上游 API Key 池的故障切换？",
  "questionType": "SYSTEM_DESIGN",
  "difficulty": "MEDIUM",
  "expectedPoints": ["可用性探测", "限流", "熔断", "恢复策略"],
  "answerOutline": ["明确失败类型", "描述选择算法", "描述切换与恢复"],
  "referenceAnswer": "参考回答正文",
  "scoringRubric": [
    {"criterion": "覆盖限流和网络错误", "weight": 30},
    {"criterion": "说明健康检查和熔断", "weight": 40},
    {"criterion": "说明恢复与一致性", "weight": 30}
  ],
  "commonMistakes": ["只描述轮询，没有说明故障状态"],
  "followUpCandidates": ["半开状态如何恢复主 Key？"],
  "citations": ["public_question_123", "resume_8"]
}
```

### 5.2 展示权限

参考答案不得在正式模拟面试答题前直接返回给前端。建议提供三种模式：

- `PRACTICE`：用户可以手动展开参考答案。
- `MOCK_INTERVIEW`：提交回答并完成评分后才显示。
- `REVIEW`：整场面试结束后统一展示。

系统设计题和开放题使用“回答框架 + 示例答案 + 可接受方案”，不声明唯一正确答案。

---

## 6. 自适应模拟面试

### 6.1 回答分类

每次回答先产生结构化判定：

- `NO_ANSWER`：没有提供可评估内容。
- `OFF_TOPIC`：与当前问题无关。
- `PARTIAL`：覆盖部分关键点。
- `INCORRECT`：存在关键事实或逻辑错误。
- `COMPLETE`：达到进入下一题的标准。

### 6.2 下一步动作

- `CLARIFY`：要求解释模糊表述。
- `DEEPEN`：围绕遗漏要点深入追问。
- `CHALLENGE`：增加异常、并发、性能或边界条件。
- `CORRECT`：指出关键错误并要求重新回答。
- `NEXT`：进入下一道主问题。

示例：用户回答“你好”时，不应直接进入下一题，而应返回：

> 你的回答还没有涉及双通道统一处理逻辑。请从鉴权、消息路由和会话状态三个方面具体说明。

### 6.3 会话状态

新增或扩展：

- `parent_question_id`
- `root_question_id`
- `turn_index`
- `follow_up_depth`
- `answer_disposition`
- `next_action`
- `missing_points`

每道题最多允许 2～3 次追问，达到上限后提供简短反馈并进入下一题。`NO_ANSWER` 和明显 `OFF_TOPIC` 不写入长期能力平均分，避免无效回答污染用户画像。

模拟面试继续使用 SSE 推送：

- 回答已接收
- 正在判断回答类型
- 正在评分
- 正在生成针对性追问
- 追问自然语言增量
- 评分或纠错完成
- 下一题或面试结束

---

## 7. AI 答疑 Agent

### 7.1 能力范围

新增 `TUTOR_AGENT`，支持：

- 技术概念解释。
- 面试题讲解和参考回答。
- 对用户刚才的回答解释扣分原因。
- 结合简历项目回答“这个项目应该怎么讲”。
- 结合 JD 回答“这个岗位重点考什么”。
- 对学习计划中的知识点进行多轮辅导。
- 对需要时效性的内容调用受控互联网搜索。

模型 API Key 只能保存在后端，通过现有 `LlmClient` 调用，禁止将 Key 下发到浏览器。

### 7.2 工具权限

建议允许 `TUTOR_AGENT` 调用：

- `GetResumeTool`
- `GetJobDescriptionTool`
- `SearchUserKnowledgeBaseTool`
- `SearchPublicInterviewKnowledgeTool`
- `GetInterviewQuestionTool`
- `GetInterviewEvaluationTool`
- `GetLearningPlanTool`
- 可选的 `SearchWebTool` 或搜索 MCP

回答必须区分来源：

- 用户私人资料引用。
- 公共行业知识库引用。
- 实时互联网来源。
- 无外部依据的模型通用解释。

### 7.3 对话能力

- 会话与消息持久化。
- SSE 自然语言流式输出。
- 多轮上下文压缩。
- 引用卡片。
- 基于某条面试题、评分或计划的上下文入口。
- 用户可清空会话。
- 所有外部上下文继续通过现有 Prompt Injection 防护。

---

## 8. 短期与长期学习计划

### 8.1 输入参数

生成计划前收集：

- `interviewDate`
- `availableHoursPerDay`
- `targetIndustry`
- `targetCompany`
- `targetPosition`
- `experienceLevel`
- `focusAreas`
- `planMode`: `AUTO`、`SPRINT`、`LONG_TERM`

### 8.2 短期冲刺计划

适用于距离面试 1～7 天，按天生成：

- 目标岗位近期高频问题。
- 简历项目必问题。
- 每题回答框架与参考答案。
- 系统设计、场景题和关键八股。
- 每日模拟面试。
- 错题和薄弱点重练。
- 面试前检查清单。

若只剩三天：

1. 第一天：高频基础题和简历项目表达。
2. 第二天：系统设计、场景题和薄弱项。
3. 第三天：完整模拟面试、错题复盘和表达训练。

### 8.3 长期成长计划

适用于 2～24 周：

- 基础知识体系。
- 技术专项。
- 项目实践和可验证产出。
- 周度里程碑。
- 阶段模拟面试。
- 根据真实评分自动调整后续优先级。

### 8.4 Schema 方向

```text
planMode
interviewDate
daysRemaining
dailyPlans
weeklyPlans
practiceQuestions
mockInterviewSchedule
sourceMaterials
adjustmentReason
```

短期计划允许按天表达，长期计划继续按周表达。公共面经只能作为增强来源，最终排序必须同时考虑用户简历、JD 和已暴露的真实能力缺口。

---

## 9. 新增工具与检索服务

建议新增本地工具：

- `SearchPublicInterviewKnowledgeTool`
- `SearchIndustryQuestionBankTool`
- `SearchCurrentInterviewExperienceTool`
- `GetQuestionReferenceAnswerTool`
- `GetInterviewEvaluationTool`
- `GetLearningPlanTool`
- `SaveInterviewExperienceTool`（仅允许受控后台任务使用）

工具调用继续进入现有可视化时间线，展示：

- 工具名称
- 行业、岗位、公司等参数摘要
- 运行状态和耗时
- 命中数量
- 来源摘要
- 失败原因

工具日志不得保存第三方完整正文、用户私人原文或远程 MCP Bearer Token。

---

## 10. MCP 与实时搜索方案

### 10.1 当前 MCP 状态

项目已实现通用 MCP Streamable HTTP 客户端，支持：

- `initialize`
- `notifications/initialized`
- `tools/list`
- `tools/call`
- JSON 与 SSE 响应
- MCP Session ID
- Bearer Token
- 工具与 Agent 白名单
- 请求和响应大小限制
- 连接和读取超时
- Tool Registry 权限、时间线和脱敏审计

当前运行配置为：

- `MCP_ENABLED=false`
- 未配置 `MCP_ENDPOINT`
- `MCP_ALLOWED_TOOLS` 为空
- 当前生产 Agent 没有实际调用远程 MCP 工具

因此，当前是“具备 MCP 接入能力”，不是“已经接入搜索 MCP”。

### 10.2 建议搜索 MCP 合同

可信搜索 MCP 可以提供：

- `search_web`
- `search_domain`
- `fetch_page`
- `extract_article`

必须限制：

- 受信任域名和协议。
- 单次搜索结果数。
- 页面大小、抓取时长和重试次数。
- 登录、付费或明确禁止自动访问的页面。
- 来源链接、时间戳和内容哈希。
- 进入公共库前的审核、去重和隐私处理。

如果直接使用百炼账号下已开放的搜索或知识库能力，应在后端增加独立 Provider Adapter，不让 Agent 直接拼装供应商 HTTP 请求。无论采用百炼接口还是 MCP，最终都必须通过 Tool Registry。

---

## 11. API 方向

### 公共题库与检索

```http
GET  /api/interview-knowledge/search
POST /api/admin/interview-knowledge/sources
POST /api/admin/interview-knowledge/sources/{sourceId}/process
GET  /api/admin/interview-knowledge/sources/{sourceId}
```

### 面试题答案

```http
GET /api/interview/questions/{questionId}/answer
```

接口必须根据练习模式、面试状态和用户归属决定是否返回参考答案。

### AI 答疑

```http
POST /api/tutor/sessions
GET  /api/tutor/sessions
GET  /api/tutor/sessions/{sessionId}
POST /api/tutor/sessions/{sessionId}/messages/stream
DELETE /api/tutor/sessions/{sessionId}
```

### 学习计划

```http
POST /api/learning-plans
```

请求体增加计划模式、面试日期、可投入时间和目标信息。

---

## 12. 安全、质量与合规边界

- 公共知识和私人知识严格分域。
- 用户提交共享资料必须明确授权，并在公开前去除姓名、电话、邮箱、群号等个人信息。
- 第三方网页必须保留来源链接和采集时间。
- 不把未经验证的模型答案标记为真实公司题目。
- 不默认复制和重新展示第三方完整原文。
- 对过期面经降低排序权重。
- 对单一来源的内容增加低置信度标记。
- 参考答案必须经过 Schema 校验，不得虚构用户经历。
- 搜索和 MCP 调用必须命中本地工具及 Agent 白名单。
- 搜索结果进入模型前继续执行 Prompt Injection 检测和内容边界包装。

---

## 13. 固定实施顺序

每个批次都必须满足以下统一完成条件，才允许进入下一批次：

1. 现有 API 和主流程回归测试全部通过。
2. 新增功能具备正常、失败、并发、越权和数据隔离测试。
3. 前后端类型、事件名称和 Schema 保持一致。
4. 数据库迁移可在包含历史数据的环境中执行。
5. 新路径启用后删除不再使用的旧 Service、DTO、Prompt、Schema、事件和配置。
6. 执行未使用代码、重复实现、废弃依赖和无效配置检查。

### 批次一：题目答案与自适应面试

- 扩展面试题 Schema 和数据库字段。
- 生成回答框架、参考答案、评分规则和常见错误。
- 增加回答分类和下一步动作。
- 支持 2～3 轮针对性追问。
- 无效回答不污染长期记忆。

### 批次二：AI 答疑 Agent

- 新增 Tutor 会话和消息模型。
- 接入私人知识库、面试题、评分和计划工具。
- 支持流式回答与引用。

### 批次三：短期与长期学习计划

- 增加面试日期和可投入时间。
- 实现 `SPRINT` 与 `LONG_TERM` 两套 Schema。
- 把题库、评分缺口和复盘结果纳入计划。

### 批次四：公共行业面经知识库与搜索

- 新增公共来源、面经和题库数据模型。
- 建设采集、脱敏、去重、结构化和审核流水线。
- 接入正式搜索 API 或搜索 MCP。
- 将公共知识检索工具授权给面试题、学习计划和 Tutor Agent。

四个批次完成后停止增加新阶段，进入端到端验收与发布。

### 13.1 兼容迁移策略

- 面试题新增答案字段时，历史题目允许字段为空，不阻塞原有题目展示和模拟面试。
- 新版自适应决策失败时，回退到现有评分和下一题逻辑，但必须产生真实失败事件。
- Tutor Agent 使用独立会话表和接口，不修改现有职业任务状态机。
- 学习计划保留现有周计划读取能力，新版通过 `planMode` 区分日计划和周计划。
- 公共知识库使用独立 scope 和数据表，现有私人文档无需搬迁或重新授权。
- 搜索 MCP 默认关闭；未配置搜索服务时，现有本地 RAG 和职业分析仍可完整运行。

### 13.2 完成后的代码清理

每个批次结束时执行以下清理：

- 删除被新版逻辑替代且已无调用方的 Service、Repository 方法和 DTO。
- 删除旧 Prompt、旧 JSON Schema 和不再消费的 SSE 事件。
- 删除前端未使用组件、状态、类型、API 方法和兼容分支。
- 删除无效环境变量、配置项、Feature Flag 和示例配置。
- 删除重复的数据转换、检索和 LLM 调用封装，统一走现有网关。
- 删除临时迁移脚本、调试输出、测试夹具和生成文件；已发布的 Flyway 迁移不得修改或删除。
- 使用 `rg`、编译器、ESLint 和测试覆盖检查未引用代码。
- 检查依赖树，移除不再使用的 Maven 与 npm 依赖。
- 执行 `git diff --check`，确保工作区没有意外文件和格式残留。

---

## 14. 验收标准

- [ ] 每道生成题目具备回答框架、参考答案、评分规则和来源。
- [ ] 模拟面试可以识别未回答、答非所问、部分回答和关键错误。
- [ ] 追问明确针对缺失要点，不会在无效回答后直接跳到无关问题。
- [ ] 无效回答不会污染长期能力平均分。
- [ ] Tutor Agent 可以回答通用概念并引用用户资料、公共知识或实时来源。
- [ ] 模型 API Key 不出现在前端、日志或工具结果中。
- [ ] 学习计划可以根据面试日期自动选择短期冲刺或长期成长。
- [ ] 公共面经库支持行业、岗位、公司、职级和轮次过滤。
- [ ] 所有公共知识片段都可追溯到来源与更新时间。
- [ ] 搜索结果经过脱敏、去重、Schema 校验和 Prompt Injection 防护。
- [ ] MCP 默认关闭，未配置白名单时不允许任何远程调用。
- [ ] 新增 Agent、工具、流式事件和权限隔离具备自动化回归测试。
- [ ] 改造前已有的后端、前端和 Prompt 回归测试全部通过。
- [ ] 历史任务、历史面试会话、历史题目和历史学习计划仍可读取。
- [ ] 未配置公共知识库或搜索 MCP 时，现有主流程仍可完整运行。
- [ ] 不再使用的旧代码、旧配置、旧事件和兼容分支已经清理。
- [ ] Maven、TypeScript、ESLint、生产构建和 `git diff --check` 全部通过。

---

## 15. 最终产品链路

```text
互联网或授权面经来源
  → 公共行业面经知识库
  → 百炼结构化、答案生成与向量化
  → 用户简历/JD/私人资料联合检索
  → 个性化面试题与参考答案
  → 自适应模拟面试与连续追问
  → AI 答疑与概念辅导
  → 短期冲刺或长期学习计划
  → 面试复盘反向更新能力画像和计划
```

该方案延续项目当前的任务隔离、Tool Registry、Schema 校验、引用、流式事件和审计设计，不通过绕开现有安全边界来换取新功能。

---

## 16. 批次四实施记录（2026-06-22）

已完成公共面经库的第一版生产链路：

- V18 新增独立的公共来源、面经和公共题目表，不复用私人文档的 `user_id` 数据域。
- 支持结构化人工导入，以及百炼对粘贴文本进行结构化抽取；模型 JSON 必须经过独立 Schema 校验。
- 导入内容经过联系方式脱敏、Prompt Injection 清理、内容哈希与问题哈希去重。
- 来源必须依次完成导入、向量化处理和管理员审核；只有 `APPROVED/PUBLISHED` 数据可以被检索。
- 公共检索支持行业、岗位、公司、职级和轮次过滤，并组合向量相关度、关键词、来源质量和时效性排序。
- `searchPublicInterviewKnowledge` 已进入 Tool Registry，并授权给面试题与学习计划 Agent；Tutor 同时检索私人资料和公共题库。
- 搜索 MCP 适配器默认关闭，要求工具白名单与域名白名单；发现结果只作为候选来源，不能绕过审核直接发布。

新增接口：

```text
GET  /api/interview-knowledge/search
POST /api/admin/interview-knowledge/sources
POST /api/admin/interview-knowledge/sources/extract
POST /api/admin/interview-knowledge/sources/discover
POST /api/admin/interview-knowledge/sources/{sourceId}/process
POST /api/admin/interview-knowledge/sources/{sourceId}/review
GET  /api/admin/interview-knowledge/sources/{sourceId}
```

外部搜索启用条件：

```text
PUBLIC_KNOWLEDGE_SEARCH_ENABLED=true
PUBLIC_KNOWLEDGE_SEARCH_TOOL=search_web
PUBLIC_KNOWLEDGE_ALLOWED_DOMAINS=nowcoder.com,example-authorized-domain.com
MCP_ENABLED=true
MCP_ALLOWED_TOOLS=search_web
```

代码完成不等于已经拥有第三方内容授权。生产数据仍需由管理员导入授权资料，或配置符合平台规则的正式搜索服务后逐条审核发布。
