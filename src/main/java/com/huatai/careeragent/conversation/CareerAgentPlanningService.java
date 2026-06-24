package com.huatai.careeragent.conversation;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentNextAction;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentResourceRef;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerIntent;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerProfileResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.PlanRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.PlanResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.RequiredResource;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.learning.LearningPlanService;
import com.huatai.careeragent.report.FinalReport;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.CareerTaskService;
import com.huatai.careeragent.task.WorkflowStatus;
import com.huatai.careeragent.interview.InteractiveInterviewService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class CareerAgentPlanningService {
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final CareerIntentService intentService;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final FinalReportRepository reportRepository;
    private final CareerTaskService careerTaskService;
    private final CareerProfileService profileService;
    private final CareerAgentAnswerService answerService;
    private final LearningPlanService learningPlanService;
    private final InteractiveInterviewService interviewService;

    public CareerAgentPlanningService(
            CareerIntentService intentService,
            ResumeRepository resumeRepository,
            JobRepository jobRepository,
            FinalReportRepository reportRepository,
            CareerTaskService careerTaskService,
            CareerProfileService profileService,
            CareerAgentAnswerService answerService,
            LearningPlanService learningPlanService,
            InteractiveInterviewService interviewService
    ) {
        this.intentService = intentService;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.reportRepository = reportRepository;
        this.careerTaskService = careerTaskService;
        this.profileService = profileService;
        this.answerService = answerService;
        this.learningPlanService = learningPlanService;
        this.interviewService = interviewService;
    }

    public PlanResponse plan(Long userId, PlanRequest request) {
        return planInternal(userId, request, null);
    }

    public PlanResponse planStreaming(Long userId, PlanRequest request, Consumer<String> onDelta) {
        return planInternal(userId, request, onDelta);
    }

    private PlanResponse planInternal(Long userId, PlanRequest request, Consumer<String> onDelta) {
        AgentContext context = resolveContext(userId, request);
        IntentResponse intent = intentService.classify(new IntentRequest(
                request.message(),
                context.resume().isPresent(),
                context.job().isPresent(),
                context.report().isPresent()
        ));
        CareerProfileResponse profile = profileService.observe(userId, request.message(), intent);
        List<WorkflowStatus> workflowSteps = workflowSteps(intent.intent());
        boolean canStartWorkflow = intent.nextAction() == AgentNextAction.START_WORKFLOW
                && intent.missingResources().isEmpty()
                && !workflowSteps.isEmpty();

        CareerTaskResponse task = null;
        Long learningPlanId = null;
        Long interviewSessionId = null;
        AgentNextAction nextAction = intent.nextAction();
        if (request.executeWorkflow() && canStartWorkflow) {
            Resume resume = context.resume().orElseThrow();
            Long jobId = context.job().map(Job::getId).orElse(null);
            task = careerTaskService.createForSteps(userId, resume.getId(), jobId, workflowSteps);
            nextAction = AgentNextAction.GET_REPORT;
        } else if (request.executeWorkflow() && nextAction == AgentNextAction.GENERATE_LEARNING_PLAN
                && intent.missingResources().isEmpty()) {
            learningPlanId = tryGenerateLearningPlan(userId, context.report()).orElse(null);
        } else if (request.executeWorkflow() && nextAction == AgentNextAction.START_MOCK_INTERVIEW
                && intent.missingResources().isEmpty()) {
            interviewSessionId = tryStartMockInterview(userId, context.resume(), context.job()).orElse(null);
        }

        return new PlanResponse(
                intent,
                selectedResources(context),
                intent.missingResources(),
                nextAction,
                canStartWorkflow,
                workflowSteps,
                context.resume().map(Resume::getId).orElse(null),
                context.job().map(Job::getId).orElse(null),
                context.report().map(FinalReport::getId).orElse(null),
                task,
                learningPlanId,
                interviewSessionId,
                profile,
                profile.suggestedPrompts(),
                List.of(),
                assistantMessage(userId, request, intent, context, profile, canStartWorkflow, task,
                        learningPlanId, interviewSessionId, onDelta)
        );
    }

    private AgentContext resolveContext(Long userId, PlanRequest request) {
        Optional<Resume> resume = request.resumeId() == null
                ? latestResume(userId)
                : Optional.of(resumeRepository.findByIdAndUserId(request.resumeId(), userId)
                .orElseThrow(() -> notFound("RESUME_NOT_FOUND", "Resume not found")));
        Optional<Job> job = request.jobId() == null
                ? latestJob(userId)
                : Optional.of(jobRepository.findByIdAndUserId(request.jobId(), userId)
                .orElseThrow(() -> notFound("JOB_NOT_FOUND", "Job not found")));
        Optional<FinalReport> report = request.reportId() == null
                ? latestReport(userId)
                : Optional.of(reportRepository.findByIdAndUserId(request.reportId(), userId)
                .orElseThrow(() -> notFound("REPORT_NOT_FOUND", "Report not found")));
        return new AgentContext(resume, job, report);
    }

    private Optional<Resume> latestResume(Long userId) {
        return resumeRepository.findByUserId(userId, PageRequest.of(0, 1, LATEST_FIRST)).stream().findFirst();
    }

    private Optional<Job> latestJob(Long userId) {
        return jobRepository.findByUserId(userId, PageRequest.of(0, 1, LATEST_FIRST)).stream().findFirst();
    }

    private Optional<FinalReport> latestReport(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().findFirst();
    }

    private static BusinessException notFound(String code, String message) {
        return new BusinessException(code, message, HttpStatus.NOT_FOUND);
    }

    private static List<WorkflowStatus> workflowSteps(CareerIntent intent) {
        return switch (intent) {
            case CAREER_ANALYSIS -> List.of(
                    WorkflowStatus.MATCHING_JOB,
                    WorkflowStatus.ANALYZING_RESUME,
                    WorkflowStatus.GENERATING_QUESTIONS
            );
            case JOB_MATCH -> List.of(WorkflowStatus.MATCHING_JOB);
            case RESUME_REVIEW -> List.of(WorkflowStatus.ANALYZING_RESUME);
            case INTERVIEW_PREP -> List.of(WorkflowStatus.GENERATING_QUESTIONS);
            case MOCK_INTERVIEW, LEARNING_PLAN, REPORT_QA, GENERAL_CAREER_QA -> List.of();
        };
    }

    private static List<AgentResourceRef> selectedResources(AgentContext context) {
        List<AgentResourceRef> resources = new ArrayList<>();
        context.resume().ifPresent(resume ->
                resources.add(new AgentResourceRef(RequiredResource.RESUME, resume.getId(), resume.getTitle())));
        context.job().ifPresent(job ->
                resources.add(new AgentResourceRef(RequiredResource.JOB, job.getId(), jobTitle(job))));
        context.report().ifPresent(report ->
                resources.add(new AgentResourceRef(RequiredResource.REPORT, report.getId(), "报告 v" + report.getVersion())));
        return List.copyOf(resources);
    }

    private static String jobTitle(Job job) {
        if (job.getCompany() == null || job.getCompany().isBlank()) {
            return job.getPosition();
        }
        return job.getCompany() + " / " + job.getPosition();
    }

    private String assistantMessage(Long userId, PlanRequest request, IntentResponse intent, AgentContext context,
                                    CareerProfileResponse profile, boolean canStartWorkflow, CareerTaskResponse task,
                                    Long learningPlanId, Long interviewSessionId, Consumer<String> onDelta) {
        if (!intent.missingResources().isEmpty()) {
            return "我已识别为「" + intent.label() + "」，还需要补充：" + intent.missingResources();
        }
        if (task != null) {
            return "我已按「" + intent.label() + "」启动分析流程。";
        }
        if (canStartWorkflow) {
            return "我已识别为「" + intent.label() + "」，上下文已齐全，可以启动分析流程。";
        }
        if (intent.intent() == CareerIntent.LEARNING_PLAN) {
            if (learningPlanId != null) {
                return "我已基于完整报告生成学习计划，计划编号 #" + learningPlanId + "。你可以在报告详情里查看后续安排。";
            }
            return learningPlanPrerequisiteMessage(context.report());
        }
        if (intent.intent() == CareerIntent.MOCK_INTERVIEW) {
            if (interviewSessionId != null) {
                return "我已创建模拟面试会话 #" + interviewSessionId + "。接下来可以进入面试练习并逐题回答。";
            }
            return "我已识别为「模拟面试」，但需要先完成职业分析并生成面试题。你可以先让我基于简历和岗位完成一次分析。";
        }
        String answer = onDelta == null
                ? answerService.answer(userId, request.message(), intent, profile, context.report())
                : answerService.answerStreaming(userId, request.message(), intent, profile, context.report(), onDelta);
        if (answer != null && !answer.isBlank()) {
            return answer;
        }
        return "我已识别为「" + intent.label() + "」，可以在对话中继续处理。";
    }

    private Optional<Long> tryGenerateLearningPlan(Long userId, Optional<FinalReport> report) {
        if (report.isEmpty() || report.get().getTaskId() == null || !isCompleteReport(report.get())) {
            return Optional.empty();
        }
        try {
            return Optional.of(learningPlanService.generate(userId, report.get().getTaskId()).planId());
        } catch (BusinessException exception) {
            return Optional.empty();
        }
    }

    private Optional<Long> tryStartMockInterview(Long userId, Optional<Resume> resume, Optional<Job> job) {
        if (resume.isEmpty() || job.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(interviewService.create(userId, resume.get().getId(), job.get().getId()).sessionId());
        } catch (BusinessException exception) {
            return Optional.empty();
        }
    }

    private String learningPlanPrerequisiteMessage(Optional<FinalReport> report) {
        if (report.isEmpty()) {
            return "我已识别为「学习计划」，但需要先有一份完整职业分析报告。你可以先让我完成简历和岗位分析。";
        }
        Object status = report.get().getReportJson().get("status");
        return "我已找到报告 #" + report.get().getId() + "，但学习计划需要完整职业分析报告。当前报告状态是 "
                + status + "，建议等待完整分析完成后再生成学习计划。";
    }

    private boolean isCompleteReport(FinalReport report) {
        return "COMPLETE".equals(String.valueOf(report.getReportJson().get("status")));
    }

    private record AgentContext(
            Optional<Resume> resume,
            Optional<Job> job,
            Optional<FinalReport> report
    ) {
    }
}
