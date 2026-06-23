package com.huatai.careeragent.agent.agents;

import com.huatai.careeragent.agent.core.Agent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentResult;
import com.huatai.careeragent.agent.context.AssembledContext;
import com.huatai.careeragent.agent.context.ContextPolicy;
import com.huatai.careeragent.agent.context.InterviewQuestionContextAssembler;
import com.huatai.careeragent.agent.context.InterviewQuestionContextInput;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.agent.schema.SchemaRepairService.RepairResult;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.interview.InterviewQuestionService;
import com.huatai.careeragent.interview.InterviewQuestionService.InterviewQuestionResponse;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.llm.PromptCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterviewQuestionAgent implements Agent<InterviewQuestionAgent.Input, List<InterviewQuestionResponse>> {
    private final AgentOutputSupport outputSupport;
    private final AgentLlmGateway llmClient;
    private final SchemaRepairService schemaRepairService;
    private final InterviewQuestionService questionService;
    private final InterviewQuestionContextAssembler contextAssembler;

    public InterviewQuestionAgent(AgentOutputSupport outputSupport, AgentLlmGateway llmClient,
                                  SchemaRepairService schemaRepairService,
                                  InterviewQuestionService questionService,
                                  InterviewQuestionContextAssembler contextAssembler) {
        this.outputSupport = outputSupport;
        this.llmClient = llmClient;
        this.schemaRepairService = schemaRepairService;
        this.questionService = questionService;
        this.contextAssembler = contextAssembler;
    }

    @Override public String name() { return AgentNames.INTERVIEW_QUESTION_AGENT; }
    @Override public String stepName() { return "GENERATING_QUESTIONS"; }

    @Override
    public AgentResult<List<InterviewQuestionResponse>> execute(Input input, AgentContext context) {
        AssembledContext assembled = contextAssembler.assemble(
                new InterviewQuestionContextInput(input.resumeId(), input.jobId(), name()),
                context, ContextPolicy.interviewQuestionsDefault());
        LlmResponse response = llmClient.complete(LlmRequest.secured(
                PromptCatalog.INTERVIEW_QUESTIONS.systemPrompt(),
                PromptCatalog.INTERVIEW_QUESTIONS.instruction() + " "
                        + outputSupport.citationInstruction(assembled.allowedCitationIds()),
                assembled.promptPayloads(),
                context.traceId(), true
        ), context, name());
        RepairResult validated = schemaRepairService.validateOrRepair(
                "interview_questions.schema.json", response.content(), context.traceId(),
                request -> llmClient.complete(request, context, name())
        );
        outputSupport.normalizeCitations(validated.value(), assembled.allowedCitationIds(), context.traceId());
        List<InterviewQuestionResponse> saved = questionService.save(
                context.userId(), input.resumeId(), input.jobId(), context.taskId(), validated.value()
        );
        return AgentResult.success(saved, "interviewQuestionCount=" + saved.size(),
                outputSupport.totalUsage(response.usage(), validated));
    }

    @Override public String summarizeInput(Input input) { return "resumeId=" + input.resumeId() + ",jobId=" + input.jobId(); }
    public record Input(Long resumeId, Long jobId) { }
}
