package com.huatai.careeragent.task;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.common.trace.TraceIdContext;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.CareerTaskDtos.CreateCareerTaskRequest;
import com.huatai.careeragent.task.log.AgentExecutionLog;
import com.huatai.careeragent.task.log.AgentExecutionLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class CareerTaskService {
    private static final List<WorkflowStatus> DEFAULT_STEPS = List.of(
            WorkflowStatus.MATCHING_JOB,
            WorkflowStatus.ANALYZING_RESUME,
            WorkflowStatus.GENERATING_QUESTIONS
    );

    private final AgentTaskRepository agentTaskRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final CareerTaskAsyncExecutor asyncExecutor;
    private final AgentExecutionLogRepository executionLogRepository;

    public CareerTaskService(
            AgentTaskRepository agentTaskRepository,
            ResumeRepository resumeRepository,
            JobRepository jobRepository,
            CareerTaskAsyncExecutor asyncExecutor,
            AgentExecutionLogRepository executionLogRepository
    ) {
        this.agentTaskRepository = agentTaskRepository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.asyncExecutor = asyncExecutor;
        this.executionLogRepository = executionLogRepository;
    }

    @Transactional
    public CareerTaskResponse create(Long userId, CreateCareerTaskRequest request) {
        if (resumeRepository.findByIdAndUserId(request.resumeId(), userId).isEmpty()) {
            throw new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND);
        }
        if (jobRepository.findByIdAndUserId(request.jobId(), userId).isEmpty()) {
            throw new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND);
        }

        List<WorkflowStatus> enabledSteps = normalizeEnabledSteps(request.enabledSteps());
        String traceId = TraceIdContext.currentTraceId();
        AgentTask savedTask = agentTaskRepository.saveAndFlush(
                new AgentTask(userId, traceId, request.resumeId(), request.jobId(), enabledSteps)
        );
        executionLogRepository.save(AgentExecutionLog.transition(savedTask, WorkflowStatus.PENDING));
        CareerTaskResponse response = CareerTaskResponse.from(savedTask);
        scheduleAfterCommit(savedTask.getId(), traceId);
        return response;
    }

    @Transactional(readOnly = true)
    public CareerTaskResponse get(Long userId, Long taskId) {
        AgentTask task = agentTaskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException("CAREER_TASK_NOT_FOUND", "Career task not found", HttpStatus.NOT_FOUND));
        return CareerTaskResponse.from(task);
    }

    private List<WorkflowStatus> normalizeEnabledSteps(List<WorkflowStatus> requested) {
        if (requested == null || requested.isEmpty()) {
            return DEFAULT_STEPS;
        }
        for (WorkflowStatus status : requested) {
            if (!DEFAULT_STEPS.contains(status)) {
                throw new BusinessException(
                        "INVALID_ENABLED_STEP",
                        "Enabled steps may only contain matching, analysis, and question generation",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
        }
        return requested.stream().distinct().toList();
    }

    private void scheduleAfterCommit(Long taskId, String traceId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncExecutor.execute(taskId, traceId);
            }
        });
    }
}
