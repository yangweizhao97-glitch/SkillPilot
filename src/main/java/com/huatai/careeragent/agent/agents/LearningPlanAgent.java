package com.huatai.careeragent.agent.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huatai.careeragent.agent.core.Agent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentResult;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.agent.schema.SchemaRepairService.RepairResult;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.agent.tool.GetFinalReportTool;
import com.huatai.careeragent.agent.tool.PlannedToolCall;
import com.huatai.careeragent.agent.tool.RestrictedToolCallResult;
import com.huatai.careeragent.agent.tool.RestrictedToolCallingService;
import com.huatai.careeragent.agent.tool.SearchPublicInterviewKnowledgeTool;
import com.huatai.careeragent.agent.tool.ToolCallingPolicy;
import com.huatai.careeragent.learning.LearningPlanResponse;
import com.huatai.careeragent.learning.LearningPlanStore;
import com.huatai.careeragent.learning.LearningPlanEvidenceService;
import com.huatai.careeragent.learning.LearningPlanGenerationSpec;
import com.huatai.careeragent.learning.LearningPlanMode;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.llm.PromptCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LearningPlanAgent implements Agent<LearningPlanAgent.Input, LearningPlanResponse> {
    private static final Logger log = LoggerFactory.getLogger(LearningPlanAgent.class);
    private static final int MAX_MODEL_PLANNED_TOOL_CALLS = 1;

    private final AgentToolGateway tools;
    private final AgentOutputSupport outputSupport;
    private final AgentLlmGateway llm;
    private final SchemaRepairService schemaRepair;
    private final LearningPlanStore store;
    private final LearningPlanEvidenceService evidenceService;
    private final RestrictedToolCallingService toolCalling;
    private final ObjectMapper objectMapper;

    public LearningPlanAgent(AgentToolGateway tools, AgentOutputSupport outputSupport, AgentLlmGateway llm,
                             SchemaRepairService schemaRepair, LearningPlanStore store,
                             LearningPlanEvidenceService evidenceService,
                             RestrictedToolCallingService toolCalling,
                             ObjectMapper objectMapper) {
        this.tools = tools;
        this.outputSupport = outputSupport;
        this.llm = llm;
        this.schemaRepair = schemaRepair;
        this.store = store;
        this.evidenceService = evidenceService;
        this.toolCalling = toolCalling;
        this.objectMapper = objectMapper;
    }

    @Override public String name() { return AgentNames.LEARNING_PLAN_AGENT; }
    @Override public String stepName() { return "GENERATING_LEARNING_PLAN"; }

    @Override
    public AgentResult<LearningPlanResponse> execute(Input input, AgentContext context) {
        GetFinalReportTool.Output report = tools.finalReport(input.taskId(), context, name());
        var evidence = evidenceService.collect(context.userId(), input.resumeId(), input.jobId());
        Object publicKnowledge = publicKnowledgeContext(input, context);
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

    private Object publicKnowledgeContext(Input input, AgentContext context) {
        try {
            List<PlannedToolCall> plannedCalls = planPublicKnowledgeTools(input, context);
            List<RestrictedToolCallResult> results = toolCalling.execute(plannedCalls,
                    new ToolCallingPolicy(name(), Set.of(SearchPublicInterviewKnowledgeTool.NAME),
                            MAX_MODEL_PLANNED_TOOL_CALLS),
                    context);
            return Map.of(
                    "mode", "RESTRICTED_TOOL_CALLING",
                    "allowedTools", List.of(SearchPublicInterviewKnowledgeTool.NAME),
                    "maxCalls", MAX_MODEL_PLANNED_TOOL_CALLS,
                    "results", results
            );
        } catch (RuntimeException exception) {
            log.warn("Learning plan restricted tool calling fell back to preset path: taskId={}, reason={}",
                    input.taskId(), exception.getMessage());
            SearchPublicInterviewKnowledgeTool.Output fallback = tools.searchPublic(
                    learningQuery(input.spec()), input.spec().targetIndustry(), input.spec().targetPosition(),
                    input.spec().targetCompany(), input.spec().experienceLevel(), null, context, name());
            return Map.of("mode", "PRESET_TOOL_FALLBACK", "result", fallback);
        }
    }

    private List<PlannedToolCall> planPublicKnowledgeTools(Input input, AgentContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("availableTools", List.of(Map.of(
                "toolName", SearchPublicInterviewKnowledgeTool.NAME,
                "maxCalls", MAX_MODEL_PLANNED_TOOL_CALLS,
                "requiredArguments", List.of("query", "topK"),
                "optionalArguments", List.of("industry", "position", "company", "experienceLevel",
                        "interviewRound")
        )));
        payload.put("defaultArguments", publicSearchDefaults(input.spec()));
        payload.put("learningPlanRequest", input.spec().requestJson());
        payload.put("instruction", "Return only JSON. Select at most one public interview knowledge search "
                + "that improves the learning plan. Use an empty toolCalls array when search is not useful.");
        LlmResponse response = llm.complete(LlmRequest.secured(
                "You are a strict tool planner for a learning-plan agent.",
                """
                        Return strict JSON with this shape:
                        {"toolCalls":[{"toolName":"searchPublicInterviewKnowledge","arguments":{"query":"...","topK":6}}]}
                        Do not call tools outside the availableTools list.
                        Do not exceed maxCalls.
                        """,
                List.of(outputSupport.json(payload)), context.traceId(), true
        ), context, name());
        return parsePlannedToolCalls(response.content(), input.spec());
    }

    private List<PlannedToolCall> parsePlannedToolCalls(String content, LearningPlanGenerationSpec spec) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode toolCalls = root.path("toolCalls");
            if (!toolCalls.isArray()) {
                return List.of();
            }
            List<PlannedToolCall> calls = new ArrayList<>();
            for (JsonNode node : toolCalls) {
                String toolName = node.path("toolName").asText("");
                Map<String, Object> arguments = objectMapper.convertValue(
                        node.path("arguments"), new TypeReference<Map<String, Object>>() { });
                if (SearchPublicInterviewKnowledgeTool.NAME.equals(toolName)) {
                    arguments = publicSearchArguments(spec, arguments);
                }
                calls.add(new PlannedToolCall(toolName, arguments));
            }
            return List.copyOf(calls);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse planned tool calls", exception);
        }
    }

    private Map<String, Object> publicSearchDefaults(LearningPlanGenerationSpec spec) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("query", learningQuery(spec));
        defaults.put("industry", spec.targetIndustry());
        defaults.put("position", spec.targetPosition());
        defaults.put("company", spec.targetCompany());
        defaults.put("experienceLevel", spec.experienceLevel());
        defaults.put("interviewRound", null);
        defaults.put("topK", 6);
        return defaults;
    }

    private Map<String, Object> publicSearchArguments(LearningPlanGenerationSpec spec, Map<String, Object> planned) {
        Map<String, Object> arguments = new LinkedHashMap<>(publicSearchDefaults(spec));
        for (String key : List.of("query", "industry", "position", "company", "experienceLevel",
                "interviewRound", "topK")) {
            Object value = planned.get(key);
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                arguments.put(key, value);
            }
        }
        arguments.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(arguments);
    }

    public record Input(Long taskId, Long reportId, String generationId,
                        LearningPlanGenerationSpec spec, Long resumeId, Long jobId) { }
}
