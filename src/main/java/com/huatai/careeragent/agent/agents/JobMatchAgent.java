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
import com.huatai.careeragent.report.ReportService;
import com.huatai.careeragent.report.ReportService.JobMatchReportResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class JobMatchAgent implements Agent<JobMatchAgent.Input, JobMatchReportResponse> {
    private final AgentToolGateway tools;
    private final AgentOutputSupport outputSupport;
    private final AgentLlmGateway llmClient;
    private final SchemaRepairService schemaRepairService;
    private final ReportService reportService;

    public JobMatchAgent(AgentToolGateway tools, AgentOutputSupport outputSupport, AgentLlmGateway llmClient,
                         SchemaRepairService schemaRepairService, ReportService reportService) {
        this.tools = tools;
        this.outputSupport = outputSupport;
        this.llmClient = llmClient;
        this.schemaRepairService = schemaRepairService;
        this.reportService = reportService;
    }

    @Override public String name() { return AgentNames.JOB_MATCH_AGENT; }
    @Override public String stepName() { return "MATCHING_JOB"; }

    @Override
    public AgentResult<JobMatchReportResponse> execute(Input input, AgentContext context) {
        GetResumeTool.Output resume = tools.resume(input.resumeId(), context, name());
        GetJobDescriptionTool.Output job = tools.job(input.jobId(), context, name());
        SearchUserKnowledgeBaseTool.Output knowledge = tools.search(job.position() + " " + job.description(), context, name());
        Set<String> allowedCitations = outputSupport.allowedCitationIds(resume, job, knowledge.items());
        LlmResponse response = llmClient.complete(LlmRequest.secured(
                "You are a career matching analyst. Return strict JSON only.",
                "Compare the resume with the job. Required keys: matchScore, summary, strengths, weaknesses, "
                        + "missingSkills, suggestedResumeChanges, citations. "
                        + outputSupport.citationInstruction(allowedCitations),
                List.of(
                        outputSupport.citedJson(outputSupport.resumeCitationId(resume), resume),
                        outputSupport.citedJson(outputSupport.jobCitationId(job), job),
                        outputSupport.json(knowledge)
                ),
                context.traceId(), true
        ), context, name());
        RepairResult validated = schemaRepairService.validateOrRepair(
                "job_match_result.schema.json", response.content(), context.traceId()
        );
        outputSupport.normalizeCitations(validated.value(), allowedCitations, context.traceId());
        JobMatchReportResponse saved = reportService.saveJobMatch(
                context.userId(), context.taskId(), input.resumeId(), input.jobId(), validated.value());
        return AgentResult.success(saved, "jobMatchReportId=" + saved.reportId(),
                outputSupport.totalUsage(response.usage(), validated));
    }

    @Override public String summarizeInput(Input input) { return "resumeId=" + input.resumeId() + ",jobId=" + input.jobId(); }
    public record Input(Long resumeId, Long jobId) { }
}
