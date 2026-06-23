package com.huatai.careeragent.agent.context;

import com.huatai.careeragent.agent.agents.AgentOutputSupport;
import com.huatai.careeragent.agent.agents.AgentToolGateway;
import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.tool.GetJobDescriptionTool;
import com.huatai.careeragent.agent.tool.GetResumeTool;
import com.huatai.careeragent.agent.tool.SearchPublicInterviewKnowledgeTool;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InterviewQuestionContextAssembler implements ContextAssembler<InterviewQuestionContextInput> {
    private final AgentToolGateway tools;
    private final AgentOutputSupport outputSupport;

    public InterviewQuestionContextAssembler(AgentToolGateway tools, AgentOutputSupport outputSupport) {
        this.tools = tools;
        this.outputSupport = outputSupport;
    }

    @Override
    public AssembledContext assemble(InterviewQuestionContextInput input, AgentContext context,
                                     ContextPolicy policy) {
        GetResumeTool.Output resume = tools.resume(input.resumeId(), context, input.agentName());
        GetJobDescriptionTool.Output job = tools.job(input.jobId(), context, input.agentName());
        String privateQuery = job.position() + " interview projects";
        String publicQuery = job.position() + " 面试 高频问题 项目";
        SearchUserKnowledgeBaseTool.Output privateKnowledge = tools.search(
                privateQuery, policy.privateTopK(), context, input.agentName());
        SearchPublicInterviewKnowledgeTool.Output publicKnowledge = tools.searchPublic(
                publicQuery, null, job.position(), job.company(), null, null,
                policy.publicTopK(), context, input.agentName());
        Set<String> allowedCitations = outputSupport.allowedCitationIds(
                resume, job, privateKnowledge.items(), publicKnowledge);
        List<String> payloads = List.of(
                outputSupport.citedJson(outputSupport.resumeCitationId(resume), resume),
                outputSupport.citedJson(outputSupport.jobCitationId(job), job),
                outputSupport.json(privateKnowledge),
                outputSupport.json(publicKnowledge)
        );
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("privateQuery", privateQuery);
        metrics.put("publicQuery", publicQuery);
        metrics.put("privateSourceCount", privateKnowledge.items().size());
        metrics.put("publicSourceCount", publicKnowledge.items().size());
        metrics.put("allowedCitationCount", allowedCitations.size());
        metrics.put("maxContextItems", policy.maxContextItems());
        return new AssembledContext(payloads, allowedCitations, metrics);
    }
}
