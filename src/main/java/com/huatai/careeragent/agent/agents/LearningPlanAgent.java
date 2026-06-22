package com.huatai.careeragent.agent.agents;

import com.huatai.careeragent.agent.core.Agent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentResult;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.agent.schema.SchemaRepairService.RepairResult;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.agent.tool.GetFinalReportTool;
import com.huatai.careeragent.agent.tool.SearchPublicInterviewKnowledgeTool;
import com.huatai.careeragent.learning.LearningPlanResponse;
import com.huatai.careeragent.learning.LearningPlanStore;
import com.huatai.careeragent.learning.LearningPlanEvidenceService;
import com.huatai.careeragent.learning.LearningPlanGenerationSpec;
import com.huatai.careeragent.learning.LearningPlanMode;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.llm.PromptCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class LearningPlanAgent implements Agent<LearningPlanAgent.Input, LearningPlanResponse> {
    private final AgentToolGateway tools;
    private final AgentOutputSupport outputSupport;
    private final AgentLlmGateway llm;
    private final SchemaRepairService schemaRepair;
    private final LearningPlanStore store;
    private final LearningPlanEvidenceService evidenceService;

    public LearningPlanAgent(AgentToolGateway tools, AgentOutputSupport outputSupport, AgentLlmGateway llm,
                             SchemaRepairService schemaRepair, LearningPlanStore store,
                             LearningPlanEvidenceService evidenceService) {
        this.tools = tools;
        this.outputSupport = outputSupport;
        this.llm = llm;
        this.schemaRepair = schemaRepair;
        this.store = store;
        this.evidenceService = evidenceService;
    }

    @Override public String name() { return AgentNames.LEARNING_PLAN_AGENT; }
    @Override public String stepName() { return "GENERATING_LEARNING_PLAN"; }

    @Override
    public AgentResult<LearningPlanResponse> execute(Input input, AgentContext context) {
        GetFinalReportTool.Output report = tools.finalReport(input.taskId(), context, name());
        var evidence = evidenceService.collect(context.userId(), input.resumeId(), input.jobId());
        SearchPublicInterviewKnowledgeTool.Output publicKnowledge = tools.searchPublic(
                learningQuery(input.spec()), input.spec().targetIndustry(), input.spec().targetPosition(),
                input.spec().targetCompany(), input.spec().experienceLevel(), null, context, name());
        String instruction = PromptCatalog.LEARNING_PLAN.instruction() + "\n" + modeInstruction(input.spec());
        LlmResponse response = llm.complete(LlmRequest.secured(
                PromptCatalog.LEARNING_PLAN.systemPrompt(), instruction,
                List.of(outputSupport.json(report.report()), outputSupport.json(input.spec().requestJson()),
                        outputSupport.json(evidence), outputSupport.json(publicKnowledge)), context.traceId(), true
        ), context, name());
        String schema = input.spec().resolvedMode() == LearningPlanMode.SPRINT
                ? "learning_plan_sprint.schema.json" : "learning_plan_long_term.schema.json";
        RepairResult validated = schemaRepair.validateOrRepair(
                schema, response.content(), context.traceId(),
                request -> llm.complete(request, context, name()));
        ObjectNode canonical = validated.value().deepCopy();
        canonical.put("planMode", input.spec().resolvedMode().name());
        if (input.spec().resolvedMode() == LearningPlanMode.SPRINT) {
            canonical.put("interviewDate", input.spec().interviewDate().toString());
            canonical.put("daysRemaining", input.spec().daysRemaining());
            canonical.put("availableHoursPerDay", input.spec().availableHoursPerDay());
        } else {
            canonical.put("durationWeeks", input.spec().durationWeeks());
            canonical.put("weeklyHours", input.spec().availableHoursPerDay() * 7);
        }
        if (!report.reportId().equals(input.reportId())) throw new IllegalStateException("Final report changed during generation");
        LearningPlanResponse saved = store.complete(context.userId(), input.taskId(), report.reportId(),
                input.generationId(), canonical);
        return AgentResult.success(saved, "learningPlanId=" + saved.planId(),
                outputSupport.totalUsage(response.usage(), validated));
    }

    @Override public String summarizeInput(Input input) { return "taskId=" + input.taskId(); }
    private String modeInstruction(LearningPlanGenerationSpec spec) {
        if (spec.resolvedMode() == LearningPlanMode.SPRINT) {
            return "生成 SPRINT 短期冲刺计划，按天安排 dailyPlans。优先覆盖岗位高频题、简历项目必问、"
                    + "真实评分缺口、模拟面试和面试前检查；不要生成 phases 或 milestones。";
        }
        return "生成 LONG_TERM 长期成长计划，按周安排 phases 和 milestones，包含项目产出、阶段模拟面试、"
                + "练习题，并根据真实评分与复盘缺口说明 adjustmentReason。";
    }
    private String learningQuery(LearningPlanGenerationSpec spec) {
        return String.join(" ", java.util.stream.Stream.of(spec.targetCompany(), spec.targetPosition(),
                        spec.targetIndustry(), spec.experienceLevel(), String.join(" ", spec.focusAreas()),
                        "面试 高频问题 学习")
                .filter(value -> value != null && !value.isBlank()).toList());
    }
    public record Input(Long taskId, Long reportId, String generationId,
                        LearningPlanGenerationSpec spec, Long resumeId, Long jobId) { }
}
