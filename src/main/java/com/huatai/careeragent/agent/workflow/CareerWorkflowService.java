package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.agent.agents.InterviewQuestionAgent;
import com.huatai.careeragent.agent.agents.JobMatchAgent;
import com.huatai.careeragent.agent.agents.ResumeAnalysisAgent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentExecutor;
import com.huatai.careeragent.agent.core.AgentResult;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.interview.InterviewQuestionService.InterviewQuestionResponse;
import com.huatai.careeragent.report.FinalReportService;
import com.huatai.careeragent.report.FinalReportService.FinalReportResponse;
import com.huatai.careeragent.report.ReportService.JobMatchReportResponse;
import com.huatai.careeragent.report.ReportService.ResumeAnalysisReportResponse;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public WorkflowStepResult executeStep(Long taskId, WorkflowStatus status) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        if (status == WorkflowStatus.GENERATING_FINAL_REPORT) {
            if (task.getJobId() != null) {
                return finalReportResult(finalReportService.generateForTask(taskId));
            }
            return new WorkflowStepResult(status, AgentNames.FINAL_REPORT_AGENT, "FINAL_REPORT", null,
                    Map.of("skipped", true, "reason", "Job is not available"));
        }
        if (status == WorkflowStatus.SUCCESS) {
            return new WorkflowStepResult(status, "CAREER_WORKFLOW", "TASK", taskId, Map.of("terminal", true));
        }
        if (!task.getEnabledSteps().contains(status)) {
            return new WorkflowStepResult(status, agentName(status), "SKIPPED_STEP", null,
                    Map.of("skipped", true, "reason", "Step is disabled"));
        }
        AgentContext context = new AgentContext(task.getUserId(), task.getId(), task.getTraceId());
        return switch (status) {
            case MATCHING_JOB -> jobMatchResult(agentExecutor.execute(
                    jobMatchAgent, new JobMatchAgent.Input(task.getResumeId(), task.getJobId()), context
            ));
            case ANALYZING_RESUME -> resumeAnalysisResult(agentExecutor.execute(
                    resumeAnalysisAgent, new ResumeAnalysisAgent.Input(task.getResumeId(), task.getJobId()), context
            ));
            case GENERATING_QUESTIONS -> interviewQuestionResult(agentExecutor.execute(
                    interviewQuestionAgent, new InterviewQuestionAgent.Input(task.getResumeId(), task.getJobId()), context
            ));
            default -> new WorkflowStepResult(status, agentName(status), "UNKNOWN", null, Map.of());
        };
    }

    private WorkflowStepResult jobMatchResult(AgentResult<JobMatchReportResponse> result) {
        JobMatchReportResponse output = result.output();
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("version", output.version());
        signals.put("matchScore", output.result().get("matchScore"));
        signals.put("summaryPresent", hasText(output.result().get("summary")));
        signals.put("citationCount", collectionSize(output.result().get("citations")));
        return new WorkflowStepResult(WorkflowStatus.MATCHING_JOB, AgentNames.JOB_MATCH_AGENT,
                "JOB_MATCH_REPORT", output.reportId(), signals);
    }

    private WorkflowStepResult resumeAnalysisResult(AgentResult<ResumeAnalysisReportResponse> result) {
        ResumeAnalysisReportResponse output = result.output();
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("version", output.version());
        signals.put("summaryPresent", hasText(output.result().get("summary")));
        signals.put("highlightCount", collectionSize(output.result().get("highlights")));
        signals.put("suggestionCount", collectionSize(output.result().get("suggestions")));
        signals.put("nextActionCount", collectionSize(output.result().get("nextActions")));
        signals.put("citationCount", collectionSize(output.result().get("citations")));
        return new WorkflowStepResult(WorkflowStatus.ANALYZING_RESUME, AgentNames.RESUME_ANALYSIS_AGENT,
                "RESUME_ANALYSIS_REPORT", output.reportId(), signals);
    }

    private WorkflowStepResult interviewQuestionResult(AgentResult<List<InterviewQuestionResponse>> result) {
        List<InterviewQuestionResponse> questions = result.output();
        int questionCount = questions.size();
        long withExpectedPoints = questions.stream()
                .filter(question -> !question.expectedPoints().isEmpty())
                .count();
        long withEvidence = questions.stream()
                .filter(question -> !question.citations().isEmpty() || hasText(question.noCitationReason()))
                .count();
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("questionCount", questionCount);
        signals.put("questionTypeCount", questions.stream().map(InterviewQuestionResponse::questionType)
                .filter(Objects::nonNull).distinct().count());
        signals.put("difficultyCount", questions.stream().map(InterviewQuestionResponse::difficulty)
                .filter(Objects::nonNull).distinct().count());
        signals.put("expectedPointsCoverage", ratio(withExpectedPoints, questionCount));
        signals.put("evidenceCoverage", ratio(withEvidence, questionCount));
        signals.put("questionIds", questions.stream().map(InterviewQuestionResponse::questionId).toList());
        signals.putAll(result.metadata());
        Long artifactId = questions.isEmpty() ? null : questions.getFirst().questionId();
        return new WorkflowStepResult(WorkflowStatus.GENERATING_QUESTIONS, AgentNames.INTERVIEW_QUESTION_AGENT,
                "INTERVIEW_QUESTIONS", artifactId, signals);
    }

    private WorkflowStepResult finalReportResult(FinalReportResponse output) {
        Map<String, Object> report = output.report();
        Map<String, Object> signals = new LinkedHashMap<>();
        String reportStatus = String.valueOf(report.get("status"));
        signals.put("reportStatus", reportStatus);
        signals.put("complete", "COMPLETE".equals(reportStatus));
        signals.put("jobMatchAvailable", sectionAvailable(report.get("jobMatch")));
        signals.put("resumeAnalysisAvailable", sectionAvailable(report.get("resumeAnalysis")));
        signals.put("interviewQuestionsAvailable", sectionAvailable(report.get("interviewQuestions")));
        signals.put("citationCount", collectionSize(report.get("citations")));
        return new WorkflowStepResult(WorkflowStatus.GENERATING_FINAL_REPORT, AgentNames.FINAL_REPORT_AGENT,
                "FINAL_REPORT", output.reportId(), signals);
    }

    private boolean sectionAvailable(Object section) {
        return section instanceof Map<?, ?> map && "AVAILABLE".equals(String.valueOf(map.get("status")));
    }

    private String agentName(WorkflowStatus status) {
        return switch (status) {
            case MATCHING_JOB -> AgentNames.JOB_MATCH_AGENT;
            case ANALYZING_RESUME -> AgentNames.RESUME_ANALYSIS_AGENT;
            case GENERATING_QUESTIONS -> AgentNames.INTERVIEW_QUESTION_AGENT;
            case GENERATING_FINAL_REPORT -> AgentNames.FINAL_REPORT_AGENT;
            default -> "CAREER_WORKFLOW";
        };
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private int collectionSize(Object value) {
        return value instanceof Collection<?> collection ? collection.size() : 0;
    }

    private double ratio(long numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }
}
