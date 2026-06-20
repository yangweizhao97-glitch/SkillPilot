package com.huatai.careeragent.agent.agents;

import com.huatai.careeragent.agent.core.AgentContext;
import com.huatai.careeragent.agent.core.AgentException;
import com.huatai.careeragent.agent.tool.GetJobDescriptionTool;
import com.huatai.careeragent.agent.tool.GetResumeTool;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import com.huatai.careeragent.agent.tool.ToolExecutionContext;
import com.huatai.careeragent.agent.tool.ToolExecutor;
import com.huatai.careeragent.agent.tool.ToolRequest;
import com.huatai.careeragent.agent.tool.ToolResponse;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.knowledge.retrieval.RetrievalMode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentToolGateway {
    private final ToolExecutor toolExecutor;

    public AgentToolGateway(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    public GetResumeTool.Output resume(Long resumeId, AgentContext context, String agentName) {
        return invoke(GetResumeTool.NAME, new GetResumeTool.Input(resumeId), context, agentName, GetResumeTool.Output.class);
    }

    public GetJobDescriptionTool.Output job(Long jobId, AgentContext context, String agentName) {
        return invoke(GetJobDescriptionTool.NAME, new GetJobDescriptionTool.Input(jobId), context, agentName,
                GetJobDescriptionTool.Output.class);
    }

    public SearchUserKnowledgeBaseTool.Output search(String query, AgentContext context, String agentName) {
        return invoke(
                SearchUserKnowledgeBaseTool.NAME,
                new SearchUserKnowledgeBaseTool.Input(query, List.of(FileType.NOTE, FileType.PROJECT_DOC), 8, RetrievalMode.HYBRID),
                context, agentName, SearchUserKnowledgeBaseTool.Output.class
        );
    }

    private <I, O> O invoke(String toolName, I input, AgentContext context, String agentName, Class<O> outputType) {
        ToolResponse<?> response = toolExecutor.execute(new ToolRequest<>(
                toolName, input,
                new ToolExecutionContext(context.userId(), context.taskId(), context.traceId(), agentName)
        ));
        if (!response.success()) {
            throw new AgentException(response.error().code(), response.error().message(), response.error().retryable());
        }
        return outputType.cast(response.output());
    }
}
