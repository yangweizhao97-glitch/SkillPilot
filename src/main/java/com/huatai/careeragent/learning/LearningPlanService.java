package com.huatai.careeragent.learning;

import com.huatai.careeragent.agent.agents.LearningPlanAgent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentExecutor;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.report.FinalReport;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import com.huatai.careeragent.task.WorkflowStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LearningPlanService {
    private final LearningPlanRepository repository;
    private final AgentTaskRepository taskRepository;
    private final FinalReportRepository reportRepository;
    private final LearningPlanAgent agent;
    private final AgentExecutor executor;
    private final LearningPlanStore store;

    public LearningPlanService(LearningPlanRepository repository, AgentTaskRepository taskRepository,
                               FinalReportRepository reportRepository, LearningPlanAgent agent,
                               AgentExecutor executor, LearningPlanStore store) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.reportRepository = reportRepository;
        this.agent = agent;
        this.executor = executor;
        this.store = store;
    }

    @Transactional(readOnly = true)
    public LearningPlanResponse get(Long userId, Long taskId) {
        requireTask(userId, taskId);
        return repository.findByUserIdAndTaskId(userId, taskId)
                .filter(plan -> "READY".equals(plan.getGenerationStatus())).map(LearningPlanResponse::from)
                .orElseThrow(() -> new BusinessException("LEARNING_PLAN_NOT_FOUND", "Learning plan not found",
                        HttpStatus.NOT_FOUND));
    }

    public LearningPlanResponse generate(Long userId, Long taskId) {
        return generate(userId, taskId, new GenerateOptions(
                LearningPlanMode.AUTO, null, 1, 8, null, null, null, null, List.of()
        ));
    }

    public LearningPlanResponse generate(Long userId, Long taskId, GenerateOptions options) {
        AgentTask task = requireTask(userId, taskId);
        if (task.getStatus() != WorkflowStatus.SUCCESS) {
            throw new BusinessException("CAREER_TASK_NOT_COMPLETED", "Career task is not completed",
                    HttpStatus.CONFLICT);
        }
        FinalReport report = reportRepository.findFirstByUserIdAndTaskIdOrderByVersionDescIdDesc(userId, taskId)
                .orElseThrow(() -> new BusinessException("FINAL_REPORT_NOT_FOUND", "Final report not found",
                        HttpStatus.UNPROCESSABLE_ENTITY));
        if (!"COMPLETE".equals(String.valueOf(report.getReportJson().get("status")))) {
            throw new BusinessException("FINAL_REPORT_NOT_FOUND", "Final report not found", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        LearningPlanGenerationSpec spec = resolve(options);
        LearningPlan existing = repository.findByUserIdAndTaskId(userId, taskId).orElse(null);
        if (existing != null && "READY".equals(existing.getGenerationStatus())
                && report.getId().equals(existing.getReportId()) && existing.matches(spec)) {
            return LearningPlanResponse.from(existing);
        }
        String generationId;
        try {
            generationId = store.claim(userId, taskId, report.getId(), spec);
        } catch (DataIntegrityViolationException | IllegalStateException exception) {
            throw new BusinessException("LEARNING_PLAN_GENERATION_IN_PROGRESS",
                    "Learning plan generation is already running", HttpStatus.CONFLICT);
        }
        try {
            return executor.execute(agent, new LearningPlanAgent.Input(
                            taskId, report.getId(), generationId, spec, task.getResumeId(), task.getJobId()),
                    new AgentContext(userId, taskId, task.getTraceId())).output();
        } catch (RuntimeException exception) {
            store.fail(userId, taskId, generationId);
            throw exception;
        }
    }

    LearningPlanGenerationSpec resolve(GenerateOptions options) {
        LearningPlanMode requested = options.planMode() == null ? LearningPlanMode.AUTO : options.planMode();
        LocalDate today = LocalDate.now();
        Integer days = options.interviewDate() == null ? null
                : Math.toIntExact(ChronoUnit.DAYS.between(today, options.interviewDate()));
        if (days != null && days < 0) {
            throw new BusinessException("INTERVIEW_DATE_INVALID", "面试日期不能早于今天", HttpStatus.BAD_REQUEST);
        }
        LearningPlanMode resolved = requested == LearningPlanMode.AUTO
                ? days != null && days <= 7 ? LearningPlanMode.SPRINT : LearningPlanMode.LONG_TERM
                : requested;
        if (resolved == LearningPlanMode.SPRINT && (days == null || days > 7)) {
            throw new BusinessException("SPRINT_INTERVIEW_DATE_REQUIRED",
                    "短期冲刺计划需要未来 7 天内的面试日期", HttpStatus.BAD_REQUEST);
        }
        int hours = Math.min(Math.max(options.availableHoursPerDay(), 1), 12);
        int weeks = Math.min(Math.max(options.durationWeeks(), 2), 24);
        return new LearningPlanGenerationSpec(requested, resolved, options.interviewDate(), days, hours, weeks,
                text(options.targetIndustry()), text(options.targetCompany()), text(options.targetPosition()),
                text(options.experienceLevel()), options.focusAreas() == null ? List.of()
                        : options.focusAreas().stream().map(this::text).filter(java.util.Objects::nonNull)
                        .distinct().limit(8).toList());
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record GenerateOptions(LearningPlanMode planMode, LocalDate interviewDate,
                                  int availableHoursPerDay, int durationWeeks,
                                  String targetIndustry, String targetCompany, String targetPosition,
                                  String experienceLevel, List<String> focusAreas) { }

    private AgentTask requireTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException("CAREER_TASK_NOT_FOUND", "Career task not found",
                        HttpStatus.NOT_FOUND));
    }
}
