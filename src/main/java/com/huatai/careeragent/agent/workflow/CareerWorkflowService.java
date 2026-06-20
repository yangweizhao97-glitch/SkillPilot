package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.agent.agents.InterviewQuestionAgent;
import com.huatai.careeragent.agent.agents.JobMatchAgent;
import com.huatai.careeragent.agent.agents.ResumeAnalysisAgent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentExecutor;
import com.huatai.careeragent.report.FinalReportService;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Service;

@Service
public class CareerWorkflowService {
    private final AgentTaskRepository taskRepository;
    private final AgentExecutor agentExecutor;
    private final JobMatchAgent jobMatchAgent;
    private final ResumeAnalysisAgent resumeAnalysisAgent;
    private final InterviewQuestionAgent interviewQuestionAgent;
    private final FinalReportService finalReportService;

    public CareerWorkflowService(AgentTaskRepository taskRepository, AgentExecutor agentExecutor,
                                 JobMatchAgent jobMatchAgent, ResumeAnalysisAgent resumeAnalysisAgent,
                                 InterviewQuestionAgent interviewQuestionAgent,
                                 FinalReportService finalReportService) {
        this.taskRepository = taskRepository;
        this.agentExecutor = agentExecutor;
        this.jobMatchAgent = jobMatchAgent;
        this.resumeAnalysisAgent = resumeAnalysisAgent;
        this.interviewQuestionAgent = interviewQuestionAgent;
        this.finalReportService = finalReportService;
    }

    public void executeStep(Long taskId, WorkflowStatus status) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        if (status == WorkflowStatus.SUCCESS) {
            if (task.getJobId() != null) finalReportService.generateForTask(taskId);
            return;
        }
        if (!task.getEnabledSteps().contains(status)) {
            return;
        }
        AgentContext context = new AgentContext(task.getUserId(), task.getId(), task.getTraceId());
        switch (status) {
            case MATCHING_JOB -> agentExecutor.execute(
                    jobMatchAgent, new JobMatchAgent.Input(task.getResumeId(), task.getJobId()), context
            );
            case ANALYZING_RESUME -> agentExecutor.execute(
                    resumeAnalysisAgent, new ResumeAnalysisAgent.Input(task.getResumeId(), task.getJobId()), context
            );
            case GENERATING_QUESTIONS -> agentExecutor.execute(
                    interviewQuestionAgent, new InterviewQuestionAgent.Input(task.getResumeId(), task.getJobId()), context
            );
            default -> { }
        }
    }
}
