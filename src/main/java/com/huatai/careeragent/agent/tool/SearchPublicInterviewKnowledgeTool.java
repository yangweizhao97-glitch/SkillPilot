package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchDtos.SearchItem;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchDtos.SearchRequest;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SearchPublicInterviewKnowledgeTool implements Tool<SearchPublicInterviewKnowledgeTool.Input,
        SearchPublicInterviewKnowledgeTool.Output> {
    public static final String NAME = "searchPublicInterviewKnowledge";
    private final PublicKnowledgeSearchService service;

    public SearchPublicInterviewKnowledgeTool(PublicKnowledgeSearchService service) { this.service = service; }

    @Override public String name() { return NAME; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public Set<String> allowedAgents() {
        return Set.of(AgentNames.INTERVIEW_QUESTION_AGENT, AgentNames.LEARNING_PLAN_AGENT,
                AgentNames.TUTOR_AGENT, AgentNames.INTERACTIVE_INTERVIEW_AGENT);
    }
    @Override public Output execute(Input input, ToolExecutionContext context) {
        return new Output(service.search(new SearchRequest(input.query(), input.industry(), input.position(),
                input.company(), input.experienceLevel(), input.interviewRound(), input.topK())).items());
    }

    public record Input(@NotBlank String query, String industry, String position, String company,
                        String experienceLevel, String interviewRound, @Min(1) @Max(20) Integer topK) { }
    public record Output(List<SearchItem> items) { }
}
