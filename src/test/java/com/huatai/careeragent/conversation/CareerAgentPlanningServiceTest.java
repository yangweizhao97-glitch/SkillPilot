package com.huatai.careeragent.conversation;

import com.huatai.careeragent.conversation.CareerAgentDtos.AgentNextAction;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerIntent;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerProfileResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.PlanRequest;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.learning.LearningPlanService;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.CareerTaskService;
import com.huatai.careeragent.task.CareerTaskType;
import com.huatai.careeragent.task.WorkflowStatus;
import com.huatai.careeragent.interview.InteractiveInterviewService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CareerAgentPlanningServiceTest {
    private final ResumeRepository resumes = mock(ResumeRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final FinalReportRepository reports = mock(FinalReportRepository.class);
    private final CareerTaskService taskService = mock(CareerTaskService.class);
    private final CareerProfileService profileService = mock(CareerProfileService.class);
    private final CareerAgentAnswerService answerService = mock(CareerAgentAnswerService.class);
    private final LearningPlanService learningPlanService = mock(LearningPlanService.class);
    private final InteractiveInterviewService interviewService = mock(InteractiveInterviewService.class);
    private final CareerAgentPlanningService service = new CareerAgentPlanningService(
            new CareerIntentService(), resumes, jobs, reports, taskService, profileService, answerService,
            learningPlanService, interviewService
    );

    @Test
    void plansWorkflowWithLatestResumeAndJobWithoutExecuting() {
        Resume resume = resume(10L, "后端简历");
        Job job = job(20L, "Acme", "Java 后端");
        when(resumes.findByUserId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(resume)));
        when(jobs.findByUserId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(job)));
        when(reports.findByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of());
        when(profileService.observe(any(), any(), any())).thenReturn(profile());

        var response = service.plan(7L, new PlanRequest("分析一下我和 Java 后端岗位是否匹配", null, null, null, false));

        assertThat(response.intent().intent()).isEqualTo(CareerIntent.JOB_MATCH);
        assertThat(response.nextAction()).isEqualTo(AgentNextAction.START_WORKFLOW);
        assertThat(response.canStartWorkflow()).isTrue();
        assertThat(response.workflowSteps()).containsExactly(WorkflowStatus.MATCHING_JOB);
        assertThat(response.resumeId()).isEqualTo(10L);
        assertThat(response.jobId()).isEqualTo(20L);
        assertThat(response.task()).isNull();
        verifyNoInteractions(taskService);
        verifyNoInteractions(answerService);
    }

    @Test
    void executesResumeOnlyReviewWhenRequested() {
        Resume resume = resume(10L, "后端简历");
        CareerTaskResponse task = new CareerTaskResponse(
                99L, "trace-1", CareerTaskType.CAREER_PREPARE, WorkflowStatus.PENDING, 0,
                10L, null, List.of(WorkflowStatus.ANALYZING_RESUME), List.of(), null,
                null, null, null, null
        );
        when(resumes.findByUserId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(resume)));
        when(jobs.findByUserId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(reports.findByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of());
        when(taskService.createForSteps(7L, 10L, null, List.of(WorkflowStatus.ANALYZING_RESUME))).thenReturn(task);
        when(profileService.observe(any(), any(), any())).thenReturn(profile());

        var response = service.plan(7L, new PlanRequest("帮我优化这份简历", null, null, null, true));

        assertThat(response.intent().intent()).isEqualTo(CareerIntent.RESUME_REVIEW);
        assertThat(response.missingResources()).isEmpty();
        assertThat(response.nextAction()).isEqualTo(AgentNextAction.GET_REPORT);
        assertThat(response.task()).isEqualTo(task);
        verify(taskService).createForSteps(7L, 10L, null, List.of(WorkflowStatus.ANALYZING_RESUME));
        verifyNoInteractions(answerService);
    }

    @Test
    void answersGeneralCareerQuestionDirectly() {
        when(resumes.findByUserId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(jobs.findByUserId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(reports.findByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of());
        when(profileService.observe(any(), any(), any())).thenReturn(profile());
        when(answerService.answer(any(), any(), any(), any(), any())).thenReturn("建议先明确目标岗位，再围绕项目表达和面试题做准备。");

        var response = service.plan(7L, new PlanRequest("我最近有点迷茫，应该先做什么", null, null, null, false));

        assertThat(response.intent().intent()).isEqualTo(CareerIntent.GENERAL_CAREER_QA);
        assertThat(response.nextAction()).isEqualTo(AgentNextAction.ANSWER_DIRECTLY);
        assertThat(response.assistantMessage()).contains("明确目标岗位");
        verifyNoInteractions(taskService);
    }

    private Resume resume(Long id, String title) {
        Resume resume = mock(Resume.class);
        when(resume.getId()).thenReturn(id);
        when(resume.getTitle()).thenReturn(title);
        return resume;
    }

    private Job job(Long id, String company, String position) {
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(id);
        when(job.getCompany()).thenReturn(company);
        when(job.getPosition()).thenReturn(position);
        return job;
    }

    private CareerProfileResponse profile() {
        return new CareerProfileResponse(List.of("Java 后端"), List.of(), List.of(), List.of(), "目标：Java 后端",
                List.of("分析简历和岗位匹配度"));
    }
}
