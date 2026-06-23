# SkillPilot Agent 编排改进方案

## 1. 文档目的

本文档整理当前 SkillPilot 后端在 Agent 编排方向的工程评审结论，并给出可落地的改进路线。

当前系统已经不是简单的“调用一次 LLM 的业务接口”，而是一个具备产品化雏形的多 Agent 应用后端：包含任务状态机、工具层、RAG、结构化输出校验、审计日志、流式交互、失败回退和面向用户的进度映射。

后续优化重点不应只是继续增加单点 Agent 能力，而是提升编排层对中间产物的理解、验证、决策和重跑能力，让系统从固定 AI 流水线逐步演进为可持续迭代的 Agent 平台。

---

## 2. 总体评价

SkillPilot 当前更接近一个“产品化 Agent 应用后端”，而不是实验脚本。它的工程边界比较清楚：

- Agent 不直接访问数据库，而是通过 Tool Gateway / Tool Executor 获取资源。
- LLM 输出通过 JSON Schema 校验和修复。
- 任务执行具备日志、traceId、状态流和 token 统计。
- 知识库区分用户私有资料与公共行业面经。
- 前端可以通过任务状态、执行日志和事件流展示执行进度。

按当前代码成熟度估算：

```text
业务闭环完整度：8/10
Agent 工程化程度：7/10
LLM 输出可靠性：7/10
上下文治理：6/10
多 Agent 编排能力：5/10
可观测性：8/10
可扩展性：7/10
```

当前最大短板不是业务功能，而是 Agent 编排仍偏固定流程：系统能稳定按顺序执行多个 Agent，但还缺少基于中间结果动态判断下一步、重跑某一步、进入降级路径或要求补充上下文的能力。

---

## 3. 当前编排现状

### 3.1 固定执行顺序

当前主流程由 `CareerWorkflowRunner` 驱动，固定顺序为：

```text
MATCHING_JOB
  → ANALYZING_RESUME
  → GENERATING_QUESTIONS
  → GENERATING_FINAL_REPORT
  → SUCCESS
```

代码位置：

- `src/main/java/com/huatai/careeragent/agent/workflow/CareerWorkflowRunner.java`
- `src/main/java/com/huatai/careeragent/task/AgentTask.java`
- `src/main/java/com/huatai/careeragent/task/WorkflowStatus.java`

`CareerWorkflowRunner` 当前主要做三件事：

1. 根据任务启用步骤计算 active steps。
2. 按顺序执行 `transition -> execute -> handoff`。
3. 最后进入 `SUCCESS`。

这个设计稳定、清晰，适合 MVP 和产品化初期，但编排层不知道每一步产出了什么，也不知道产物质量是否足够进入下一步。

### 3.2 LangGraph 尚未获得真正编排权

`LangGraphAgentWorkflowExecutor` 当前会请求外部 LangGraph 服务生成 plan，但校验逻辑要求返回计划必须完全等于 `CareerWorkflowRunner.EXECUTION_ORDER`。

这意味着 LangGraph 当前只能确认固定流程，不能做以下事情：

- 跳过不需要的步骤。
- 根据任务类型选择不同路径。
- 在某一步质量不足时补跑前置 Agent。
- 根据 verifier 结果重新规划。
- 进入 PARTIAL 或人工确认路径。

因此，LangGraph 当前更多是“可替换编排服务接口”的占位，而不是实际发挥动态编排价值。

### 3.3 Handoff 更偏审计而非智能协作

`HandoffCoordinator` 当前负责：

- 根据任务和 enabled steps 计算 active agent steps。
- 记录 handoff started / completed / rejected 日志。
- 调用 `HandoffPolicy` 校验方向、深度、目标 Agent 白名单和环路。

这层有审计价值，但还不是智能协作机制。它没有消费上一步 Agent 的中间产物，也不会基于产物质量决定下一步。

更准确地说，当前 `HandoffCoordinator` 是 handoff audit + policy checker，不是 workflow router。

### 3.4 Agent 自己构造上下文并落库

当前 `JobMatchAgent`、`ResumeAnalysisAgent`、`InterviewQuestionAgent` 都是自己决定：

- 调哪些工具。
- 用什么 query 搜索知识库。
- 如何拼接 LLM 上下文。
- 如何限制 citation。
- 如何保存结果。

例如：

- 简历分析 Agent 使用 `resume.title + " projects skills"` 查询用户知识库。
- 面试题 Agent 使用 `job.position + " interview projects"` 查询私有知识库，并用 `job.position + " 面试 高频问题 项目"` 查询公共知识库。

这种方式能跑通业务，但上下文策略会散落在各个 Agent 内部，后续随着 Agent 增多，会出现以下问题：

- token budget 无法统一治理。
- source ranking 规则不一致。
- citation policy 难以统一。
- prompt payload 容易膨胀。
- 敏感信息过滤和 prompt injection 防护难以复用。

---

## 4. 主要不足

### 4.1 缺少显式 Artifact 契约

现在每个 Agent 执行后会把结果保存到对应业务表，但编排层只知道“某一步执行完成”，不知道：

- 产物类型是什么。
- 产物 ID 是什么。
- 产物核心质量指标是什么。
- 产物是否完整。
- 产物是否可被后续步骤引用。
- 产物是否需要修复或重跑。

建议引入统一的 workflow step result：

```java
public record WorkflowStepResult(
        WorkflowStatus step,
        String agentName,
        String artifactType,
        Long artifactId,
        Map<String, Object> qualitySignals
) { }
```

这样编排层可以显式理解“上一步产生了什么”，而不是只能依赖数据库里是否存在记录。

### 4.2 缺少 Step Verifier

当前系统有 schema 校验，但 schema 校验只能判断输出结构合法，不能判断业务质量是否足够。

例如，面试题 JSON Schema 合法，并不代表：

- 题目数量足够。
- 题型覆盖合理。
- 难度分布合理。
- 引用覆盖率足够。
- 题目真的贴合简历项目和 JD。
- 公共面经知识被有效使用。

建议为关键步骤增加 verifier：

```java
public interface WorkflowStepVerifier {
    WorkflowStatus supports();
    VerificationResult verify(Long taskId, WorkflowStepResult result);
}
```

`VerificationResult` 至少应包含：

```java
public record VerificationResult(
        boolean passed,
        NextAction nextAction,
        String reason,
        Map<String, Object> metrics
) { }
```

推荐的 `NextAction`：

```java
public enum NextAction {
    CONTINUE,
    RETRY_STEP,
    SKIP_STEP,
    REPLAN,
    FAIL
}
```

### 4.3 缺少运行时决策循环

当前执行方式可以简化为：

```text
for step in activeExecutionOrder:
  transition(step)
  execute(step)
  handoff(next)
```

建议逐步升级为：

```text
plan = planner.create(task)

while plan.hasNext():
  step = plan.next()
  transition(step)
  result = execute(step)
  verification = verifier.verify(result)

  if verification.nextAction == CONTINUE:
      continue
  if verification.nextAction == RETRY_STEP:
      retry current step
  if verification.nextAction == REPLAN:
      plan = planner.replan(task, artifacts, verification)
  if verification.nextAction == SKIP_STEP:
      mark skipped and continue
  if verification.nextAction == FAIL:
      fail task
```

这个循环是从固定工作流演进到 Agent 编排平台的关键。

### 4.4 工具调用仍偏预设，不是模型驱动

当前 Agent 代码预先决定要调用哪些工具，再把工具结果喂给 LLM。这种方式稳定、可控、容易审计，但 Agent 自主性有限。

对于强业务闭环的后台任务，可以继续保持预设工具调用；但对于交互式 Agent，建议逐步引入受限 tool calling：

- Tutor Agent
- Interactive Interview Agent
- Learning Plan Agent
- 面试复盘问答 Agent

模型可以在权限和预算限制内按需选择：

- 查简历。
- 查 JD。
- 查用户知识库。
- 查公共面经库。
- 查历史面试评分。
- 查学习计划。

第一阶段不建议让所有后台工作流 Agent 都开放自主工具调用，应先在交互场景试点。

### 4.5 Memory 抽象仍需统一

当前系统已经有多种记忆能力：

- 模拟面试会话记忆。
- Tutor 会话历史。
- 最近消息压缩。
- 面试长期记忆。
- 学习计划结果。

但这些能力还没有形成统一抽象。未来建议分层：

```text
短期对话记忆：当前会话窗口和最近消息
任务级 artifacts：本次 career task 中产生的报告、题目、评分
长期用户画像：跨任务累积的优势、短板、目标岗位、学习偏好
可引用事实库：可以作为 citation 出现在模型回答中的事实来源
```

Agent 编排层只应直接消费任务级 artifacts 和 verifier 结果；长期记忆和可引用事实应通过统一上下文组装层进入 prompt。

---

## 5. 目标架构

### 5.1 Plan + Execute + Verify + Decide

目标不是把当前 Spring 工作流一次性替换成复杂图引擎，而是先补齐四个运行时能力：

```text
Plan：生成本次任务的执行计划
Execute：执行当前 Agent 步骤并产出 artifact
Verify：检查 artifact 是否满足进入下一步的条件
Decide：根据验证结果继续、重试、跳过、重规划或失败
```

推荐目标流程：

```text
创建任务
  → WorkflowPlanner 生成初始计划
  → WorkflowRunner 执行当前 step
  → Agent 产出 artifact
  → WorkflowStepVerifier 校验 artifact
  → WorkflowRouter 决策下一步
  → 完成或进入降级路径
```

### 5.2 核心组件建议

#### WorkflowPlan

表示本次任务的执行计划。

```java
public record WorkflowPlan(
        String planId,
        List<WorkflowPlanStep> steps
) { }
```

#### WorkflowPlanStep

表示一个可执行步骤。

```java
public record WorkflowPlanStep(
        WorkflowStatus status,
        String agentName,
        boolean required,
        int maxAttempts
) { }
```

#### WorkflowStepResult

表示步骤执行后的产物引用和质量信号。

```java
public record WorkflowStepResult(
        WorkflowStatus step,
        String agentName,
        String artifactType,
        Long artifactId,
        Map<String, Object> qualitySignals
) { }
```

#### WorkflowStepVerifier

负责判断步骤产物是否合格。

```java
public interface WorkflowStepVerifier {
    WorkflowStatus supports();
    VerificationResult verify(Long taskId, WorkflowStepResult result);
}
```

#### WorkflowRouter

负责根据验证结果和任务状态决定下一步。

```java
public interface WorkflowRouter {
    RouteDecision decide(WorkflowRuntime runtime, VerificationResult verification);
}
```

#### ContextAssembler

统一上下文组装策略。

```java
public interface ContextAssembler<I> {
    AssembledContext assemble(I input, AgentContext context, ContextPolicy policy);
}
```

该层负责：

- token budget。
- source ranking。
- citation policy。
- 敏感信息过滤。
- prompt injection 防护。
- 上下文去重。
- 私有知识和公共知识合并策略。

---

## 6. 分阶段改造方案

### 6.1 第一阶段：补齐 Artifact 和 Verifier

目标：不重写整体工作流，只让编排层知道每一步产出了什么、是否合格。

建议改动：

1. 修改 `CareerWorkflowStepHandler`，让 `execute` 返回 `WorkflowStepResult`。
2. 修改 `CareerWorkflowService.executeStep`，将各 Agent 的返回结果映射成 artifact。
3. 新增 `WorkflowStepVerifier` 接口和 registry。
4. 先实现以下 verifier：
   - `JobMatchVerifier`
   - `ResumeAnalysisVerifier`
   - `InterviewQuestionVerifier`
   - `FinalReportVerifier`
5. 修改 `CareerWorkflowRunner`，在每步执行后调用 verifier。
6. verifier 失败时先只支持一次 `RETRY_STEP`，超过次数后 fail 或生成 partial report。

首期 verifier 规则可以先保持简单。

`InterviewQuestionVerifier` 示例：

```text
题目数量 >= 5
至少包含 2 种 questionType
至少包含 2 种 difficulty
有 citation 的题目比例 >= 60%
每道题 expectedPoints 非空
```

验收标准：

- 每个步骤执行日志中能看到 artifactId。
- 每个步骤执行日志中能看到 verifier 结果。
- 面试题质量不足时能自动重试一次。
- 最终报告能明确区分 COMPLETE 与 PARTIAL。

### 6.2 第二阶段：引入 WorkflowRouter

目标：让 handoff 从日志记录升级为运行时决策。

建议改动：

1. 保留 `HandoffCoordinator` 的日志和 policy 能力，但将其职责收敛为 handoff audit。
2. 新增 `WorkflowRouter`，基于 verifier 结果决定下一步。
3. 新增 `WorkflowRuntime`，保存本次任务的 artifacts、attempts、visited agents 和 verifier metrics。
4. Runner 从线性 for-loop 改成 while-loop。

建议决策规则：

```text
verification passed
  → CONTINUE

required step failed and attempts < maxAttempts
  → RETRY_STEP

optional step failed
  → SKIP_STEP

关键 artifact 缺失
  → REPLAN 或 FAIL

Final report partial
  → SUCCESS with PARTIAL status 或 FAIL，取决于任务配置
```

验收标准：

- 同一 step 可以被明确重试，且日志中包含 attempt。
- optional step 失败不一定导致全任务失败。
- required step 失败能清晰进入 FAILED。
- handoff 日志能体现 route decision，而不只是 source -> target。

### 6.3 第三阶段：放开 LangGraph 的计划能力

目标：让 LangGraph 或外部编排服务具备有限计划能力，但仍受本地安全策略约束。

建议改动：

1. 修改 `LangGraphAgentWorkflowExecutor.validatePlan`。
2. 从“必须完全等于 EXECUTION_ORDER”改成“必须是合法子序列或合法 DAG 路径”。
3. 本地仍保留以下约束：
   - 不允许未知 WorkflowStatus。
   - 不允许越权 Agent。
   - 不允许跳过 required step。
   - 不允许从低进度回到高风险前置状态，除非 route decision 明确允许 retry。
   - 不允许超过 maxDepth / maxAttempts。
4. 将 verifier 结果回传给 LangGraph，用于 replan。

推荐先支持合法子序列：

```text
ANALYZING_RESUME → SUCCESS
MATCHING_JOB → GENERATING_FINAL_REPORT → SUCCESS
GENERATING_QUESTIONS → SUCCESS
```

再逐步支持更复杂的分支和重规划。

验收标准：

- LangGraph 能返回非完整主链计划。
- 本地 policy 能拒绝非法计划。
- LangGraph 不可绕过权限、状态机和 verifier。

### 6.4 第四阶段：统一 ContextAssembler

目标：把散落在各 Agent 里的上下文构造策略集中治理。

建议优先从 `InterviewQuestionAgent` 开始，因为它上下文来源最多：

```text
简历
  + JD
  + 用户私人知识库
  + 公共面经库
  + citation policy
```

第一版 `ContextAssembler` 可以只做：

- 统一 query 生成。
- 私有知识和公共知识 topK 配置。
- citation allow-list。
- token budget 粗略截断。
- source 去重。

后续再增强：

- 动态 source ranking。
- 按 Agent 类型配置 context policy。
- prompt injection 风险分级。
- 引用覆盖率预估。

验收标准：

- `InterviewQuestionAgent` 不再直接拼 query 和上下文列表。
- citation policy 由 assembler 统一生成。
- verifier 可以读取 assembler 输出的 source metrics。

### 6.5 第五阶段：受限 Tool Calling 试点

目标：在交互式 Agent 中引入模型驱动工具选择，而不是一次性改造所有 Agent。

优先试点：

1. Tutor Agent
2. Interactive Interview Agent
3. Learning Plan Agent

约束条件：

- 每个 Agent 有工具白名单。
- 每轮最多调用 N 次工具。
- 每次工具调用必须记录 audit log。
- 工具结果必须经过 sanitizer。
- 私有数据访问必须校验 userId 和 taskId。
- 失败时回退到预设工具路径。

后台批处理型 Agent 仍建议保持预设工具调用，直到 verifier 和 eval 足够成熟。

---

## 7. 建议指标

为了让 Agent 编排持续迭代，需要补充 Agent 级指标。

建议至少记录：

```text
schema 校验失败率
schema repair 成功率
LLM 重试率
工具调用失败率
引用命中率
引用覆盖率
verifier 通过率
verifier 失败原因分布
step retry 次数
workflow replan 次数
最终报告 COMPLETE/PARTIAL 比例
每类 Agent 平均耗时
每类 Agent 平均 token
用户是否采纳报告
用户是否继续追问
```

其中，最能体现编排质量的是：

- verifier 通过率。
- step retry 后成功率。
- workflow partial 比例。
- citation correctness。
- 用户继续追问率和采纳率。

---

## 8. 最小可落地版本

如果只做一个小版本，推荐范围如下：

### 8.1 必做

1. 新增 `WorkflowStepResult`。
2. 让 `CareerWorkflowService.executeStep` 返回 artifact 引用。
3. 新增 `WorkflowStepVerifier` 和 verifier registry。
4. 实现 `InterviewQuestionVerifier`。
5. 修改 `CareerWorkflowRunner`，每步后调用 verifier。
6. verifier 失败时允许当前 step 自动重试一次。

### 8.2 暂不做

- 不重写所有 Agent。
- 不立刻引入复杂 DAG。
- 不让所有 Agent 开放 tool calling。
- 不立即把 LangGraph 作为唯一编排引擎。
- 不重构全部上下文构造逻辑。

### 8.3 推荐第一版伪代码

```java
for (WorkflowStatus status : activeExecutionOrder) {
    stateService.transition(taskId, status);
    if (status == WorkflowStatus.SUCCESS) {
        break;
    }

    WorkflowStepResult result = stepHandler.execute(taskId, status);
    VerificationResult verification = verifierRegistry.verify(taskId, result);

    if (verification.nextAction() == NextAction.CONTINUE) {
        handoffCoordinator.handoff(...);
        continue;
    }

    if (verification.nextAction() == NextAction.RETRY_STEP && attempts.canRetry(status)) {
        result = stepHandler.execute(taskId, status);
        verification = verifierRegistry.verify(taskId, result);
    }

    if (!verification.passed()) {
        stateService.fail(taskId, verification.reason());
        return;
    }
}
```

这个版本的价值是：在不大拆架构的前提下，让 SkillPilot 具备第一层真正的 Agent 编排能力。

---

## 9. 结论

SkillPilot 当前的 Agent 工程基础是好的，尤其是工具层、日志、schema 校验、RAG 和任务状态机，比常见 demo 扎实很多。

但编排层目前仍偏线性：

```text
固定计划
  → 固定 Agent
  → 固定下一步
  → 最终报告
```

下一阶段建议演进为：

```text
计划
  → 执行
  → 产物
  → 验证
  → 决策
  → 必要时重试或重规划
```

优先级建议：

```text
1. WorkflowStepResult + Verifier
2. WorkflowRouter + Retry / Skip / Fail 决策
3. LangGraph 合法子序列计划
4. ContextAssembler 统一上下文治理
5. 交互式 Agent 试点受限 Tool Calling
6. Agent eval 和 metrics 平台化
```

这样可以让系统从“AI 求职助手应用”进一步升级成“可持续迭代的 Agent 平台”。
