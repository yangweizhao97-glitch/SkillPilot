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
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.llm.PromptCatalog;
import com.huatai.careeragent.report.ReportService;
import com.huatai.careeragent.report.ReportService.ResumeAnalysisReportResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ResumeAnalysisAgent implements Agent<ResumeAnalysisAgent.Input, ResumeAnalysisReportResponse> {
    private final AgentToolGateway tools;
    private final AgentOutputSupport outputSupport;
    private final AgentLlmGateway llmClient;
    private final SchemaRepairService schemaRepairService;
    private final ReportService reportService;

    public ResumeAnalysisAgent(AgentToolGateway tools, AgentOutputSupport outputSupport, AgentLlmGateway llmClient,
                               SchemaRepairService schemaRepairService, ReportService reportService) {
        this.tools = tools;
        this.outputSupport = outputSupport;
        this.llmClient = llmClient;
        this.schemaRepairService = schemaRepairService;
        this.reportService = reportService;
    }

    @Override public String name() { return AgentNames.RESUME_ANALYSIS_AGENT; }
    @Override public String stepName() { return "ANALYZING_RESUME"; }

    @Override
    public AgentResult<ResumeAnalysisReportResponse> execute(Input input, AgentContext context) {
        GetResumeTool.Output resume = tools.resume(input.resumeId(), context, name());
        SearchUserKnowledgeBaseTool.Output knowledge = tools.search(resume.title() + " projects skills", context, name());
        GetJobDescriptionTool.Output job = input.jobId() == null ? null : tools.job(input.jobId(), context, name());
        Set<String> allowedCitations = outputSupport.allowedCitationIds(resume, job, knowledge.items());
        List<String> contexts = new ArrayList<>();
        contexts.add(outputSupport.citedJson(outputSupport.resumeCitationId(resume), resume));
        contexts.add(outputSupport.json(knowledge));
        if (job != null) contexts.add(outputSupport.citedJson(outputSupport.jobCitationId(job), job));
        LlmResponse response = llmClient.complete(LlmRequest.secured(
                PromptCatalog.RESUME_ANALYSIS.systemPrompt(),
                PromptCatalog.RESUME_ANALYSIS.instruction() + " "
                        + outputSupport.citationInstruction(allowedCitations),
                contexts, context.traceId(), true
        ), context, name());
        RepairResult validated = schemaRepairService.validateOrRepair(
                "resume_analysis_result.schema.json", response.content(), context.traceId(),
                request -> llmClient.complete(request, context, name())
        );
        outputSupport.normalizeCitations(validated.value(), allowedCitations, context.traceId());
        ResumeAnalysisReportResponse saved = reportService.saveResumeAnalysis(
                context.userId(), context.taskId(), input.resumeId(), validated.value()
        );
        return AgentResult.success(saved, "resumeAnalysisReportId=" + saved.reportId(),
                outputSupport.totalUsage(response.usage(), validated));
    }

    @Override public String summarizeInput(Input input) { return "resumeId=" + input.resumeId() + ",jobId=" + input.jobId(); }
    public record Input(Long resumeId, Long jobId) { }
}
