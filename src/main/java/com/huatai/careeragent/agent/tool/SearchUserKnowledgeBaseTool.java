package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchItem;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchRequest;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeSearchService;
import com.huatai.careeragent.knowledge.retrieval.RetrievalMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SearchUserKnowledgeBaseTool implements Tool<SearchUserKnowledgeBaseTool.Input, SearchUserKnowledgeBaseTool.Output> {
    public static final String NAME = "searchUserKnowledgeBase";

    private final KnowledgeSearchService knowledgeSearchService;

    public SearchUserKnowledgeBaseTool(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public Set<String> allowedAgents() {
        return Set.of(AgentNames.JOB_MATCH_AGENT, AgentNames.RESUME_ANALYSIS_AGENT,
                AgentNames.INTERVIEW_QUESTION_AGENT, AgentNames.TUTOR_AGENT,
                AgentNames.INTERACTIVE_INTERVIEW_AGENT);
    }

    @Override
    public Output execute(Input input, ToolExecutionContext context) {
        List<Item> items = knowledgeSearchService.search(
                        context.userId(),
                        new KnowledgeSearchRequest(input.query(), input.sourceTypes(), input.topK(), input.retrievalMode())
                ).items().stream()
                .map(Item::from)
                .toList();
        return new Output(items);
    }

    public record Input(
            @NotBlank String query,
            List<FileType> sourceTypes,
            @Min(1) @Max(20) Integer topK,
            RetrievalMode retrievalMode
    ) {
    }

    public record Output(List<Item> items) {
    }

    public record Item(
            String citationId,
            FileType sourceType,
            String sourceTitle,
            String sourceLocator,
            String content,
            double score
    ) {
        private static Item from(KnowledgeSearchItem item) {
            return new Item(
                    item.citationId(),
                    item.sourceType(),
                    item.sourceTitle(),
                    item.sourceLocator(),
                    item.content(),
                    item.score()
            );
        }
    }
}
