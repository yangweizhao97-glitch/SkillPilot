package com.huatai.careeragent.agent.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "career-agent.workflow.engine", havingValue = "spring", matchIfMissing = true)
public class SpringAgentWorkflowExecutor implements AgentWorkflowExecutor {
    private final CareerWorkflowRunner runner;

    public SpringAgentWorkflowExecutor(CareerWorkflowRunner runner) {
        this.runner = runner;
    }

    @Override
    public void execute(Long taskId) {
        runner.run(taskId, CareerWorkflowRunner.EXECUTION_ORDER);
    }

    @Override
    public String engine() {
        return "spring";
    }
}
