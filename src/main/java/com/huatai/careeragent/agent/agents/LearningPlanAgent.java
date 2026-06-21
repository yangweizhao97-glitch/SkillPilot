package com.huatai.careeragent.agent.agents;

import com.huatai.careeragent.agent.core.Agent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentResult;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.agent.schema.SchemaRepairService.RepairResult;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.agent.tool.GetFinalReportTool;
import com.huatai.careeragent.learning.LearningPlanResponse;
import com.huatai.careeragent.learning.LearningPlanStore;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.llm.PromptCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LearningPlanAgent implements Agent<LearningPlanAgent.Input, LearningPlanResponse> {
    private final AgentToolGateway tools;
    private final AgentOutputSupport outputSupport;
    private final AgentLlmGateway llm;
    private final SchemaRepairService schemaRepair;
    private final LearningPlanStore store;

    public LearningPlanAgent(AgentToolGateway tools, AgentOutputSupport outputSupport, AgentLlmGateway llm,
                             SchemaRepairService schemaRepair, LearningPlanStore store) {
        this.tools = tools;
        this.outputSupport = outputSupport;
        this.llm = llm;
        this.schemaRepair = schemaRepair;
        this.store = store;
    }

    @Override public String name() { return AgentNames.LEARNING_PLAN_AGENT; }
    @Override public String stepName() { return "GENERATING_LEARNING_PLAN"; }

    @Override
    public AgentResult<LearningPlanResponse> execute(Input input, AgentContext context) {
        GetFinalReportTool.Output report = tools.finalReport(input.taskId(), context, name());
        LlmResponse response = llm.complete(LlmRequest.secured(
                PromptCatalog.LEARNING_PLAN.systemPrompt(), PromptCatalog.LEARNING_PLAN.instruction(),
                List.of(outputSupport.json(report.report())), context.traceId(), true
        ), context, name());
        RepairResult validated = schemaRepair.validateOrRepair(
                "learning_plan.schema.json", response.content(), context.traceId(),
                request -> llm.complete(request, context, name()));
        if (!report.reportId().equals(input.reportId())) throw new IllegalStateException("Final report changed during generation");
        LearningPlanResponse saved = store.complete(context.userId(), input.taskId(), report.reportId(),
                input.generationId(), validated.value());
        return AgentResult.success(saved, "learningPlanId=" + saved.planId(),
                outputSupport.totalUsage(response.usage(), validated));
    }

    @Override public String summarizeInput(Input input) { return "taskId=" + input.taskId(); }
    public record Input(Long taskId, Long reportId, String generationId) { }
}
