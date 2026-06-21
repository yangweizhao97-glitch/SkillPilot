package com.huatai.careeragent.agent.agents;

import com.huatai.careeragent.agent.core.Agent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentResult;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.agent.schema.SchemaRepairService.RepairResult;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.agent.tool.GetJobDescriptionTool;
import com.huatai.careeragent.agent.tool.GetResumeTool;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import com.huatai.careeragent.interview.InterviewQuestionService;
import com.huatai.careeragent.interview.InterviewQuestionService.InterviewQuestionResponse;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.llm.PromptCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class InterviewQuestionAgent implements Agent<InterviewQuestionAgent.Input, List<InterviewQuestionResponse>> {
    private final AgentToolGateway tools;
    private final AgentOutputSupport outputSupport;
    private final AgentLlmGateway llmClient;
    private final SchemaRepairService schemaRepairService;
    private final InterviewQuestionService questionService;

    public InterviewQuestionAgent(AgentToolGateway tools, AgentOutputSupport outputSupport, AgentLlmGateway llmClient,
                                  SchemaRepairService schemaRepairService, InterviewQuestionService questionService) {
        this.tools = tools;
        this.outputSupport = outputSupport;
        this.llmClient = llmClient;
        this.schemaRepairService = schemaRepairService;
        this.questionService = questionService;
    }

    @Override public String name() { return AgentNames.INTERVIEW_QUESTION_AGENT; }
    @Override public String stepName() { return "GENERATING_QUESTIONS"; }

    @Override
    public AgentResult<List<InterviewQuestionResponse>> execute(Input input, AgentContext context) {
        GetResumeTool.Output resume = tools.resume(input.resumeId(), context, name());
        GetJobDescriptionTool.Output job = tools.job(input.jobId(), context, name());
        SearchUserKnowledgeBaseTool.Output knowledge = tools.search(job.position() + " interview projects", context, name());
        Set<String> allowedCitations = outputSupport.allowedCitationIds(resume, job, knowledge.items());
        LlmResponse response = llmClient.complete(LlmRequest.secured(
                PromptCatalog.INTERVIEW_QUESTIONS.systemPrompt(),
                PromptCatalog.INTERVIEW_QUESTIONS.instruction() + " "
                        + outputSupport.citationInstruction(allowedCitations),
                List.of(
                        outputSupport.citedJson(outputSupport.resumeCitationId(resume), resume),
                        outputSupport.citedJson(outputSupport.jobCitationId(job), job),
                        outputSupport.json(knowledge)
                ),
                context.traceId(), true
        ), context, name());
        RepairResult validated = schemaRepairService.validateOrRepair(
                "interview_questions.schema.json", response.content(), context.traceId(),
                request -> llmClient.complete(request, context, name())
        );
        outputSupport.normalizeCitations(validated.value(), allowedCitations, context.traceId());
        List<InterviewQuestionResponse> saved = questionService.save(
                context.userId(), input.resumeId(), input.jobId(), context.taskId(), validated.value()
        );
        return AgentResult.success(saved, "interviewQuestionCount=" + saved.size(),
                outputSupport.totalUsage(response.usage(), validated));
    }

    @Override public String summarizeInput(Input input) { return "resumeId=" + input.resumeId() + ",jobId=" + input.jobId(); }
    public record Input(Long resumeId, Long jobId) { }
}
