package com.huatai.careeragent.learning;

import com.huatai.careeragent.agent.agents.LearningPlanAgent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentExecutor;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningPlanService {
    private final LearningPlanRepository repository;
    private final AgentTaskRepository taskRepository;
    private final FinalReportRepository reportRepository;
    private final LearningPlanAgent agent;
    private final AgentExecutor executor;

    public LearningPlanService(LearningPlanRepository repository, AgentTaskRepository taskRepository,
                               FinalReportRepository reportRepository, LearningPlanAgent agent,
                               AgentExecutor executor) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.reportRepository = reportRepository;
        this.agent = agent;
        this.executor = executor;
    }

    @Transactional(readOnly = true)
    public LearningPlanResponse get(Long userId, Long taskId) {
        requireTask(userId, taskId);
        return repository.findByUserIdAndTaskId(userId, taskId).map(LearningPlanResponse::from)
                .orElseThrow(() -> new BusinessException("LEARNING_PLAN_NOT_FOUND", "Learning plan not found",
                        HttpStatus.NOT_FOUND));
    }

    public LearningPlanResponse generate(Long userId, Long taskId) {
        AgentTask task = requireTask(userId, taskId);
        LearningPlanResponse existing = repository.findByUserIdAndTaskId(userId, taskId)
                .map(LearningPlanResponse::from).orElse(null);
        if (existing != null) return existing;
        if (reportRepository.findFirstByUserIdAndTaskIdOrderByVersionDescIdDesc(userId, taskId).isEmpty()) {
            throw new BusinessException("FINAL_REPORT_NOT_FOUND", "Final report not found", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return executor.execute(agent, new LearningPlanAgent.Input(taskId),
                new AgentContext(userId, taskId, task.getTraceId())).output();
    }

    private AgentTask requireTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException("CAREER_TASK_NOT_FOUND", "Career task not found",
                        HttpStatus.NOT_FOUND));
    }
}
