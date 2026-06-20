package com.huatai.careeragent.task;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.common.api.PageResponse;
import com.huatai.careeragent.common.trace.TraceIdContext;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.CareerTaskDtos.CreateCareerTaskRequest;
import com.huatai.careeragent.task.log.AgentExecutionLog;
import com.huatai.careeragent.task.log.AgentExecutionLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        return createForSteps(userId, request.resumeId(), request.jobId(), normalizeEnabledSteps(request.enabledSteps()));
    }

    @Transactional
    public CareerTaskResponse createForSteps(Long userId, Long resumeId, Long jobId, List<WorkflowStatus> enabledSteps) {
        if (resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty()) {
            throw new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND);
        }
        if (jobId != null && jobRepository.findByIdAndUserId(jobId, userId).isEmpty()) {
            throw new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND);
        }
        if ((enabledSteps.contains(WorkflowStatus.MATCHING_JOB) || enabledSteps.contains(WorkflowStatus.GENERATING_QUESTIONS))
                && jobId == null) {
            throw new BusinessException("JOB_REQUIRED", "Job is required for the selected steps", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String traceId = TraceIdContext.currentTraceId();
        AgentTask savedTask = agentTaskRepository.saveAndFlush(
                new AgentTask(userId, traceId, resumeId, jobId, normalizeEnabledSteps(enabledSteps))
        );
        executionLogRepository.save(AgentExecutionLog.transition(savedTask, WorkflowStatus.PENDING));
        CareerTaskResponse response = CareerTaskResponse.from(savedTask);
        scheduleAfterCommit(savedTask.getId(), traceId);
        return response;
    }

    @Transactional
    public CareerTaskResponse retry(Long userId, Long taskId) {
        AgentTask failed = agentTaskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException("CAREER_TASK_NOT_FOUND", "Career task not found", HttpStatus.NOT_FOUND));
        if (failed.getStatus() != WorkflowStatus.FAILED) {
            throw new BusinessException("CAREER_TASK_NOT_FAILED", "Only failed tasks can be retried", HttpStatus.CONFLICT);
        }
        return createForSteps(userId, failed.getResumeId(), failed.getJobId(), failed.getEnabledSteps());
    }

    @Transactional(readOnly = true)
    public CareerTaskResponse get(Long userId, Long taskId) {
        AgentTask task = agentTaskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException("CAREER_TASK_NOT_FOUND", "Career task not found", HttpStatus.NOT_FOUND));
        return CareerTaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<CareerTaskResponse> list(Long userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<AgentTask> result = agentTaskRepository.findByUserId(
                userId, PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(result.map(CareerTaskResponse::from).getContent(), safePage, safePageSize,
                result.getTotalElements(), result.getTotalPages());
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
