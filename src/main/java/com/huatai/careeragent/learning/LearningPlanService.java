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
        LearningPlan existing = repository.findByUserIdAndTaskId(userId, taskId).orElse(null);
        if (existing != null && "READY".equals(existing.getGenerationStatus())
                && report.getId().equals(existing.getReportId())) return LearningPlanResponse.from(existing);
        String generationId;
        try {
            generationId = store.claim(userId, taskId, report.getId());
        } catch (DataIntegrityViolationException | IllegalStateException exception) {
            throw new BusinessException("LEARNING_PLAN_GENERATION_IN_PROGRESS",
                    "Learning plan generation is already running", HttpStatus.CONFLICT);
        }
        try {
            return executor.execute(agent, new LearningPlanAgent.Input(taskId, report.getId(), generationId),
                    new AgentContext(userId, taskId, task.getTraceId())).output();
        } catch (RuntimeException exception) {
            store.fail(userId, taskId, generationId);
            throw exception;
        }
    }

    private AgentTask requireTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException("CAREER_TASK_NOT_FOUND", "Career task not found",
                        HttpStatus.NOT_FOUND));
    }
}
