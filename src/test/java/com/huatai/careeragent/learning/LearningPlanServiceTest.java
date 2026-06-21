package com.huatai.careeragent.learning;

import com.huatai.careeragent.agent.agents.LearningPlanAgent;
import com.huatai.careeragent.agent.core.AgentExecutor;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.task.AgentTaskRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LearningPlanServiceTest {
    private final LearningPlanRepository plans = mock(LearningPlanRepository.class);
    private final AgentTaskRepository tasks = mock(AgentTaskRepository.class);
    private final FinalReportRepository reports = mock(FinalReportRepository.class);
    private final LearningPlanAgent agent = mock(LearningPlanAgent.class);
    private final AgentExecutor executor = mock(AgentExecutor.class);
    private final LearningPlanService service = new LearningPlanService(plans, tasks, reports, agent, executor);

    @Test
    void doesNotRevealAnotherUsersTask() {
        when(tasks.findByIdAndUserId(9L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(3L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Career task not found");
        verifyNoInteractions(reports, agent, executor);
    }
}
