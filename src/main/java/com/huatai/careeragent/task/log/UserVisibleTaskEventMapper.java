package com.huatai.careeragent.task.log;

import com.huatai.careeragent.agent.tool.ToolCallStatus;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.WorkflowStatus;
import com.huatai.careeragent.task.log.TaskLogDtos.StepDetail;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogItem;
import com.huatai.careeragent.task.log.TaskLogDtos.TechnicalDetail;
import com.huatai.careeragent.task.log.TaskLogDtos.ToolCallItem;
import com.huatai.careeragent.task.log.TaskLogDtos.UserEventType;
import com.huatai.careeragent.task.log.TaskLogDtos.UserStep;
import com.huatai.careeragent.task.log.TaskLogDtos.UserStepStatus;
import com.huatai.careeragent.task.log.TaskLogDtos.UserVisibleStep;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Component
public class UserVisibleTaskEventMapper {
    private static final List<UserStep> ORDER = List.of(
            UserStep.READING_INPUTS,
            UserStep.ANALYZING_REQUIREMENTS,
            UserStep.RETRIEVING_CONTEXT,
            UserStep.JOB_MATCHING,
            UserStep.RESUME_OPTIMIZATION,
            UserStep.INTERVIEW_QUESTIONS,
            UserStep.FINAL_REPORT
    );

    public List<UserVisibleStep> steps(AgentTask task, List<TaskLogItem> logs, List<ToolCallItem> tools) {
        UserStep active = activeStep(task, logs, tools);
        UserStep failed = task.getStatus() == WorkflowStatus.FAILED ? active : null;
        int visibleThrough = task.getStatus() == WorkflowStatus.SUCCESS ? ORDER.size() - 1 : ORDER.indexOf(active);
        if (visibleThrough < 0) visibleThrough = 0;

        Instant taskStart = Optional.ofNullable(task.getStartedAt()).orElse(task.getCreatedAt());
        List<UserVisibleStep> result = new ArrayList<>();
        for (int index = 0; index <= visibleThrough; index++) {
            UserStep step = ORDER.get(index);
            if (!included(task, step)) continue;
            UserStepStatus status = step == failed ? UserStepStatus.FAILED
                    : task.getStatus() == WorkflowStatus.SUCCESS || index < visibleThrough
                    ? UserStepStatus.SUCCESS : UserStepStatus.RUNNING;
            Instant startedAt = startTime(step, taskStart, logs, tools);
            Instant completedAt = status == UserStepStatus.SUCCESS
                    ? completionTime(step, task, logs, tools, startedAt) : null;
            Long duration = completedAt == null || startedAt == null ? null
                    : Math.max(0, Duration.between(startedAt, completedAt).toMillis());
            UserEventType type = status == UserStepStatus.FAILED ? UserEventType.TASK_FAILED
                    : status == UserStepStatus.RUNNING ? UserEventType.STEP_STARTED
                    : task.getStatus() == WorkflowStatus.SUCCESS && step == UserStep.FINAL_REPORT
                    ? UserEventType.TASK_COMPLETED : UserEventType.STEP_COMPLETED;
            result.add(new UserVisibleStep(type, step, title(step), summary(step, status, tools), status,
                    startedAt, completedAt, duration, progress(step, status, tools), details(step, tools)));
        }
        return result;
    }

    public List<TechnicalDetail> technicalDetails(List<TaskLogItem> logs, List<ToolCallItem> tools) {
        List<TechnicalDetail> result = new ArrayList<>();
        logs.stream().filter(log -> log.status() != ExecutionLogStatus.HANDOFF_STARTED)
                .forEach(log -> result.add(new TechnicalDetail(
                        "执行事件", workflowLabel(log.workflowStatus()), log.status().name(), log.durationMs(),
                        log.updatedAt(), log.status() == ExecutionLogStatus.STEP_FAILED ? "该阶段执行失败" : "执行状态已记录")));
        tools.forEach(tool -> result.add(new TechnicalDetail(
                "能力调用", toolLabel(tool.toolName()), tool.status().name(), tool.durationMs(), tool.updatedAt(),
                tool.status() == ToolCallStatus.TOOL_FAILED ? "调用未成功，敏感参数已隐藏" : "调用参数与结果已脱敏隐藏")));
        return result.stream().sorted(Comparator.comparing(TechnicalDetail::occurredAt,
                Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    private UserStep activeStep(AgentTask task, List<TaskLogItem> logs, List<ToolCallItem> tools) {
        WorkflowStatus workflow = failedWorkflow(task, logs).orElse(task.getStatus());
        return switch (workflow) {
            case PENDING -> UserStep.READING_INPUTS;
            case MATCHING_JOB -> matchingSubstep(tools);
            case ANALYZING_RESUME -> UserStep.RESUME_OPTIMIZATION;
            case GENERATING_QUESTIONS -> UserStep.INTERVIEW_QUESTIONS;
            case GENERATING_FINAL_REPORT, GENERATING_LEARNING_PLAN, SUCCESS -> UserStep.FINAL_REPORT;
            case FAILED -> UserStep.READING_INPUTS;
        };
    }

    private Optional<WorkflowStatus> failedWorkflow(AgentTask task, List<TaskLogItem> logs) {
        if (task.getStatus() != WorkflowStatus.FAILED) return Optional.empty();
        return logs.stream().filter(log -> log.status() == ExecutionLogStatus.STEP_FAILED)
                .max(Comparator.comparing(TaskLogItem::updatedAt)).map(TaskLogItem::workflowStatus);
    }

    private UserStep matchingSubstep(List<ToolCallItem> tools) {
        boolean resumeRead = completed(tools, "getResume");
        boolean jobRead = completed(tools, "getJobDescription");
        if (!resumeRead || !jobRead) return UserStep.READING_INPUTS;
        boolean retrievalStarted = tools.stream().anyMatch(tool -> isSearch(tool.toolName()));
        if (!retrievalStarted) return UserStep.ANALYZING_REQUIREMENTS;
        boolean retrievalRunning = tools.stream().anyMatch(tool -> isSearch(tool.toolName())
                && tool.status() == ToolCallStatus.TOOL_STARTED);
        return retrievalRunning ? UserStep.RETRIEVING_CONTEXT : UserStep.JOB_MATCHING;
    }

    private boolean completed(List<ToolCallItem> tools, String name) {
        return tools.stream().anyMatch(tool -> name.equals(tool.toolName())
                && tool.status() == ToolCallStatus.TOOL_COMPLETED);
    }

    private boolean isSearch(String name) {
        return "searchUserKnowledgeBase".equals(name) || "searchPublicInterviewKnowledge".equals(name);
    }

    private boolean included(AgentTask task, UserStep step) {
        EnumSet<WorkflowStatus> enabled = task.getEnabledSteps().isEmpty()
                ? EnumSet.noneOf(WorkflowStatus.class) : EnumSet.copyOf(task.getEnabledSteps());
        return switch (step) {
            case READING_INPUTS -> true;
            case ANALYZING_REQUIREMENTS, RETRIEVING_CONTEXT, JOB_MATCHING -> enabled.contains(WorkflowStatus.MATCHING_JOB);
            case RESUME_OPTIMIZATION -> enabled.contains(WorkflowStatus.ANALYZING_RESUME);
            case INTERVIEW_QUESTIONS -> enabled.contains(WorkflowStatus.GENERATING_QUESTIONS);
            case FINAL_REPORT -> task.getJobId() != null;
        };
    }

    private Instant startTime(UserStep step, Instant fallback, List<TaskLogItem> logs, List<ToolCallItem> tools) {
        return switch (step) {
            case READING_INPUTS -> firstTool(tools, "getResume", "getJobDescription").orElse(fallback);
            case RETRIEVING_CONTEXT -> firstSearch(tools).orElse(workflowStart(logs, WorkflowStatus.MATCHING_JOB).orElse(fallback));
            case RESUME_OPTIMIZATION -> workflowStart(logs, WorkflowStatus.ANALYZING_RESUME).orElse(fallback);
            case INTERVIEW_QUESTIONS -> workflowStart(logs, WorkflowStatus.GENERATING_QUESTIONS).orElse(fallback);
            case FINAL_REPORT -> workflowStart(logs, WorkflowStatus.GENERATING_FINAL_REPORT).orElse(fallback);
            case ANALYZING_REQUIREMENTS, JOB_MATCHING -> workflowStart(logs, WorkflowStatus.MATCHING_JOB).orElse(fallback);
        };
    }

    private Instant completionTime(UserStep step, AgentTask task, List<TaskLogItem> logs,
                                   List<ToolCallItem> tools, Instant startedAt) {
        Optional<Instant> exact = switch (step) {
            case READING_INPUTS -> lastToolCompletion(tools, "getResume", "getJobDescription");
            case RETRIEVING_CONTEXT -> lastSearchCompletion(tools);
            case JOB_MATCHING -> workflowCompletion(logs, WorkflowStatus.MATCHING_JOB);
            case RESUME_OPTIMIZATION -> workflowCompletion(logs, WorkflowStatus.ANALYZING_RESUME);
            case INTERVIEW_QUESTIONS -> workflowCompletion(logs, WorkflowStatus.GENERATING_QUESTIONS);
            case FINAL_REPORT -> Optional.ofNullable(task.getFinishedAt());
            case ANALYZING_REQUIREMENTS -> firstSearch(tools);
        };
        return exact.orElseGet(() -> Optional.ofNullable(task.getFinishedAt()).orElse(startedAt));
    }

    private Optional<Instant> workflowStart(List<TaskLogItem> logs, WorkflowStatus status) {
        return logs.stream().filter(log -> log.workflowStatus() == status && log.status() == ExecutionLogStatus.STEP_STARTED)
                .map(TaskLogItem::updatedAt).min(Comparator.naturalOrder());
    }

    private Optional<Instant> workflowCompletion(List<TaskLogItem> logs, WorkflowStatus status) {
        return logs.stream().filter(log -> log.workflowStatus() == status && log.status() == ExecutionLogStatus.STEP_COMPLETED)
                .map(TaskLogItem::updatedAt).max(Comparator.naturalOrder());
    }

    private Optional<Instant> firstTool(List<ToolCallItem> tools, String... names) {
        return tools.stream().filter(tool -> List.of(names).contains(tool.toolName())).map(ToolCallItem::updatedAt)
                .min(Comparator.naturalOrder());
    }

    private Optional<Instant> firstSearch(List<ToolCallItem> tools) {
        return tools.stream().filter(tool -> isSearch(tool.toolName())).map(ToolCallItem::updatedAt)
                .min(Comparator.naturalOrder());
    }

    private Optional<Instant> lastToolCompletion(List<ToolCallItem> tools, String... names) {
        return tools.stream().filter(tool -> List.of(names).contains(tool.toolName())
                        && tool.status() == ToolCallStatus.TOOL_COMPLETED)
                .map(this::completedAt).max(Comparator.naturalOrder());
    }

    private Optional<Instant> lastSearchCompletion(List<ToolCallItem> tools) {
        return tools.stream().filter(tool -> isSearch(tool.toolName()) && tool.status() == ToolCallStatus.TOOL_COMPLETED)
                .map(this::completedAt).max(Comparator.naturalOrder());
    }

    private Instant completedAt(ToolCallItem tool) {
        return tool.durationMs() == null ? tool.updatedAt() : tool.updatedAt().plusMillis(tool.durationMs());
    }

    private String title(UserStep step) {
        return switch (step) {
            case READING_INPUTS -> "读取简历和岗位描述";
            case ANALYZING_REQUIREMENTS -> "分析岗位需求";
            case RETRIEVING_CONTEXT -> "检索相关项目和知识库内容";
            case JOB_MATCHING -> "进行岗位匹配";
            case RESUME_OPTIMIZATION -> "生成简历优化建议";
            case INTERVIEW_QUESTIONS -> "生成面试题";
            case FINAL_REPORT -> "整理最终报告";
        };
    }

    private String summary(UserStep step, UserStepStatus status, List<ToolCallItem> tools) {
        if (status == UserStepStatus.FAILED) return "此步骤未能完成，请重试任务";
        boolean done = status == UserStepStatus.SUCCESS;
        return switch (step) {
            case READING_INPUTS -> done ? "已读取简历内容和岗位描述" : "正在安全读取本次分析所需材料";
            case ANALYZING_REQUIREMENTS -> done ? "已提取岗位的核心技能、职责与经验要求" : "正在识别岗位中的核心技能与职责要求";
            case RETRIEVING_CONTEXT -> retrievalSummary(tools, done);
            case JOB_MATCHING -> done ? "已完成经历与岗位核心要求的匹配分析" : "正在基于检索结果评估经历与岗位要求";
            case RESUME_OPTIMIZATION -> done ? "已整理简历亮点、差距与优化方向" : "正在结合岗位要求生成可执行的简历建议";
            case INTERVIEW_QUESTIONS -> done ? "已生成与岗位和个人经历相关的面试题" : "正在围绕岗位重点生成针对性面试题";
            case FINAL_REPORT -> done ? "报告已生成" : "正在汇总岗位匹配、简历建议和面试题";
        };
    }

    private String retrievalSummary(List<ToolCallItem> tools, boolean done) {
        int count = tools.stream().filter(tool -> isSearch(tool.toolName()) && tool.status() == ToolCallStatus.TOOL_COMPLETED)
                .mapToInt(this::resultCount).sum();
        if (done && count > 0) return "已从知识库检索到 " + count + " 条相关内容";
        return done ? "已完成相关项目经历和知识库内容检索" : "正在查找与岗位要求相关的经历和面试资料";
    }

    private int collectionSize(Object value) {
        return value instanceof Collection<?> collection ? collection.size() : 0;
    }

    private List<String> progress(UserStep step, UserStepStatus status, List<ToolCallItem> tools) {
        List<String> items = new ArrayList<>();
        if (step == UserStep.READING_INPUTS) {
            if (completed(tools, "getResume")) items.add("已读取简历内容");
            if (completed(tools, "getJobDescription")) items.add("已提取岗位描述");
        } else if (step == UserStep.RETRIEVING_CONTEXT) {
            tools.stream().filter(tool -> isSearch(tool.toolName())).map(this::toolSummary).distinct().limit(3).forEach(items::add);
        }
        if (items.isEmpty()) items.add(summary(step, status, tools));
        return items.stream().limit(3).toList();
    }

    private List<StepDetail> details(UserStep step, List<ToolCallItem> tools) {
        return tools.stream().filter(tool -> belongsTo(step, tool.toolName()))
                .map(tool -> new StepDetail(toolSummary(tool), userToolStatus(tool.status()), tool.durationMs(),
                        tool.updatedAt(), source(tool.toolName()))).limit(3).toList();
    }

    private boolean belongsTo(UserStep step, String toolName) {
        return step == UserStep.READING_INPUTS && ("getResume".equals(toolName) || "getJobDescription".equals(toolName))
                || step == UserStep.RETRIEVING_CONTEXT && isSearch(toolName);
    }

    private String toolSummary(ToolCallItem tool) {
        if (tool.status() == ToolCallStatus.TOOL_FAILED) return toolLabel(tool.toolName()) + "未成功";
        return switch (tool.toolName()) {
            case "getResume" -> "已读取简历内容";
            case "getJobDescription" -> "已提取岗位要求";
            case "searchUserKnowledgeBase" -> countSummary(tool, "个人知识库");
            case "searchPublicInterviewKnowledge" -> countSummary(tool, "公共面经资料");
            default -> toolLabel(tool.toolName()) + "已完成";
        };
    }

    private String countSummary(ToolCallItem tool, String source) {
        int count = resultCount(tool);
        return count > 0 ? "已从" + source + "找到 " + count + " 条相关内容" : "已检索" + source;
    }

    private int resultCount(ToolCallItem tool) {
        return tool.resultSummary() == null ? 0 : collectionSize(tool.resultSummary().get("items"));
    }

    private String source(String toolName) {
        return switch (toolName) {
            case "getResume" -> "本次上传的简历";
            case "getJobDescription" -> "本次岗位描述";
            case "searchUserKnowledgeBase" -> "个人知识库";
            case "searchPublicInterviewKnowledge" -> "公共面经知识库";
            default -> "Agent 内部能力";
        };
    }

    private String toolLabel(String toolName) {
        return switch (toolName) {
            case "getResume" -> "读取简历";
            case "getJobDescription" -> "读取岗位描述";
            case "searchUserKnowledgeBase" -> "检索个人知识库";
            case "searchPublicInterviewKnowledge" -> "检索公共面经知识库";
            case "getFinalReport" -> "读取报告";
            case "callMcp" -> "调用扩展能力";
            default -> "内部能力调用";
        };
    }

    private String workflowLabel(WorkflowStatus status) {
        return switch (status) {
            case PENDING -> "任务已创建";
            case MATCHING_JOB -> "岗位匹配";
            case ANALYZING_RESUME -> "简历分析";
            case GENERATING_QUESTIONS -> "面试题生成";
            case GENERATING_FINAL_REPORT -> "报告整理";
            case GENERATING_LEARNING_PLAN -> "学习计划生成";
            case SUCCESS -> "任务完成";
            case FAILED -> "任务失败";
        };
    }

    private String userToolStatus(ToolCallStatus status) {
        return switch (status) {
            case TOOL_STARTED -> "进行中";
            case TOOL_COMPLETED -> "已完成";
            case TOOL_FAILED -> "失败";
        };
    }
}
