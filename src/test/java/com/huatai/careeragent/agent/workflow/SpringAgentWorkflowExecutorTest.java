package com.huatai.careeragent.agent.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringAgentWorkflowExecutorTest {
    @Test
    void delegatesStableExecutionOrderToRunner() {
        CareerWorkflowRunner runner = mock(CareerWorkflowRunner.class);
        SpringAgentWorkflowExecutor executor = new SpringAgentWorkflowExecutor(runner);

        executor.execute(9L);

        verify(runner).run(9L, CareerWorkflowRunner.EXECUTION_ORDER);
        assertThat(executor.engine()).isEqualTo("spring");
    }
}
