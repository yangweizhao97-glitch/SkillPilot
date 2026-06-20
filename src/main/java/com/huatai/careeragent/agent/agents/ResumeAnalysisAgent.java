package com.huatai.careeragent.agent.agents;

import com.huatai.careeragent.agent.core.Agent;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentResult;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.agent.schema.SchemaRepairService.RepairResult;
import com.huatai.careeragent.agent.tool.AgentNames;
import com.huatai.careeragent.agent.tool.GetResumeTool;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.report.ReportService;
import com.huatai.careeragent.report.ReportService.ResumeAnalysisReportResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResumeAnalysisAgent implements Agent<ResumeAnalysisAgent.Input, ResumeAnalysisReportResponse> {
    private final AgentToolGateway tools;
    private final AgentOutputSupport outputSupport;
    private final LlmClient llmClient;
    private final SchemaRepairService schemaRepairService;
    private final ReportService reportService;

    public ResumeAnalysisAgent(AgentToolGateway tools, AgentOutputSupport outputSupport, LlmClient llmClient,
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
        List<String> contexts = new ArrayList<>();
        contexts.add(outputSupport.json(resume));
        contexts.add(outputSupport.json(knowledge));
        if (input.jobId() != null) contexts.add(outputSupport.json(tools.job(input.jobId(), context, name())));
        LlmResponse response = llmClient.complete(LlmRequest.secured(
                "You are a resume reviewer. Return strict JSON only.",
                "Analyze the resume. Required keys: summary, highlights, weaknesses, projectIssues, suggestions, "
                        + "risks, nextActions, citations. Use only supplied citationId values.",
                contexts, context.traceId(), true
        ));
        RepairResult validated = schemaRepairService.validateOrRepair(
                "resume_analysis_result.schema.json", response.content(), context.traceId()
        );
        outputSupport.validateCitations(validated.value(), knowledge.items());
        ResumeAnalysisReportResponse saved = reportService.saveResumeAnalysis(
                context.userId(), input.resumeId(), validated.value()
        );
        return AgentResult.success(saved, "resumeAnalysisReportId=" + saved.reportId(),
                outputSupport.totalUsage(response.usage(), validated));
    }

    @Override public String summarizeInput(Input input) { return "resumeId=" + input.resumeId() + ",jobId=" + input.jobId(); }
    public record Input(Long resumeId, Long jobId) { }
}
