package com.huatai.careeragent.learning;

import com.huatai.careeragent.agent.agents.LearningPlanAgent;
import com.huatai.careeragent.agent.core.AgentExecutor;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.report.FinalReport;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Map;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class LearningPlanServiceTest {
    private final LearningPlanRepository plans = mock(LearningPlanRepository.class);
    private final AgentTaskRepository tasks = mock(AgentTaskRepository.class);
    private final FinalReportRepository reports = mock(FinalReportRepository.class);
    private final LearningPlanAgent agent = mock(LearningPlanAgent.class);
    private final AgentExecutor executor = mock(AgentExecutor.class);
    private final LearningPlanStore store = mock(LearningPlanStore.class);
    private final LearningPlanService service = new LearningPlanService(plans, tasks, reports, agent, executor, store);

    @Test
    void doesNotRevealAnotherUsersTask() {
        when(tasks.findByIdAndUserId(9L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(3L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Career task not found");
        verifyNoInteractions(reports, agent, executor);
    }

    @Test
    void refusesToGenerateBeforeCareerTaskCompletes() {
        AgentTask task = mock(AgentTask.class);
        when(task.getStatus()).thenReturn(WorkflowStatus.ANALYZING_RESUME);
        when(tasks.findByIdAndUserId(9L, 3L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.generate(3L, 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo("CAREER_TASK_NOT_COMPLETED"));
        verifyNoInteractions(reports, agent, executor, store);
    }

    @Test
    void refusesPartialFinalReport() {
        AgentTask task = mock(AgentTask.class);
        FinalReport report = mock(FinalReport.class);
        when(task.getStatus()).thenReturn(WorkflowStatus.SUCCESS);
        when(tasks.findByIdAndUserId(9L, 3L)).thenReturn(Optional.of(task));
        when(reports.findFirstByUserIdAndTaskIdOrderByVersionDescIdDesc(3L, 9L))
                .thenReturn(Optional.of(report));
        when(report.getReportJson()).thenReturn(Map.of("status", "PARTIAL"));

        assertThatThrownBy(() -> service.generate(3L, 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo("FINAL_REPORT_NOT_FOUND"));
        verifyNoInteractions(agent, executor, store);
    }

    @Test
    void exposesConcurrentGenerationAsConflict() {
        AgentTask task = mock(AgentTask.class);
        FinalReport report = mock(FinalReport.class);
        when(task.getStatus()).thenReturn(WorkflowStatus.SUCCESS);
        when(task.getTraceId()).thenReturn("trace-test");
        when(tasks.findByIdAndUserId(9L, 3L)).thenReturn(Optional.of(task));
        when(reports.findFirstByUserIdAndTaskIdOrderByVersionDescIdDesc(3L, 9L))
                .thenReturn(Optional.of(report));
        when(report.getId()).thenReturn(12L);
        when(report.getReportJson()).thenReturn(Map.of("status", "COMPLETE"));
        when(plans.findByUserIdAndTaskId(3L, 9L)).thenReturn(Optional.empty());
        when(store.claim(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(12L), any())).thenThrow(new IllegalStateException("already running"));

        assertThatThrownBy(() -> service.generate(3L, 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo("LEARNING_PLAN_GENERATION_IN_PROGRESS"));
        verifyNoInteractions(agent, executor);
        verify(store).claim(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(12L), any());
    }

    @Test
    void autoSelectsSprintForAnInterviewWithinSevenDays() {
        var spec = service.resolve(new LearningPlanService.GenerateOptions(
                LearningPlanMode.AUTO, LocalDate.now().plusDays(3), 2, 8,
                "互联网", "目标公司", "Java 后端", "中级", List.of("事务", "消息队列")
        ));

        org.assertj.core.api.Assertions.assertThat(spec.resolvedMode()).isEqualTo(LearningPlanMode.SPRINT);
        org.assertj.core.api.Assertions.assertThat(spec.daysRemaining()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(spec.availableHoursPerDay()).isEqualTo(2);
    }

    @Test
    void explicitSprintRequiresAnInterviewWithinSevenDays() {
        assertThatThrownBy(() -> service.resolve(new LearningPlanService.GenerateOptions(
                LearningPlanMode.SPRINT, LocalDate.now().plusDays(10), 2, 8,
                null, null, null, null, List.of()
        ))).isInstanceOfSatisfying(BusinessException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                        .isEqualTo("SPRINT_INTERVIEW_DATE_REQUIRED"));
    }
}
