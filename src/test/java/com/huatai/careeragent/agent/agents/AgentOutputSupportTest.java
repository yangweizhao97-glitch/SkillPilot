package com.huatai.careeragent.agent.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.tool.GetJobDescriptionTool;
import com.huatai.careeragent.agent.tool.GetResumeTool;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import com.huatai.careeragent.file.FileType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOutputSupportTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentOutputSupport support = new AgentOutputSupport(objectMapper);

    @Test
    void buildsAllowedCitationsFromResumeJobAndKnowledge() {
        GetResumeTool.Output resume = new GetResumeTool.Output(89L, "Backend", "Resume text");
        GetJobDescriptionTool.Output job = new GetJobDescriptionTool.Output(109L, "Acme", "Engineer", "JD text");
        SearchUserKnowledgeBaseTool.Item chunk = new SearchUserKnowledgeBaseTool.Item(
                "chunk_7", FileType.PROJECT_DOC, "Project", "page 1", "Evidence", 0.9
        );

        assertThat(support.allowedCitationIds(resume, job, List.of(chunk)))
                .containsExactly("resume_89", "job_109", "chunk_7");
    }

    @Test
    void removesUnknownCitationsAndKeepsVerifiedOnes() throws Exception {
        JsonNode result = objectMapper.readTree("""
                {"citations":["resume_89","made_up_source"]}
                """);

        support.normalizeCitations(result, Set.of("resume_89"), "trace_test");

        assertThat(result.path("citations").size()).isEqualTo(1);
        assertThat(result.path("citations").get(0).asText()).isEqualTo("resume_89");
    }

    @Test
    void suppliesReasonWhenInterviewQuestionLosesItsOnlyCitation() throws Exception {
        JsonNode result = objectMapper.readTree("""
                {"questions":[{"citations":["unknown"],"noCitationReason":null}]}
                """);

        support.normalizeCitations(result, Set.of("resume_89"), "trace_test");

        JsonNode question = result.path("questions").get(0);
        assertThat(question.path("citations")).isEmpty();
        assertThat(question.path("noCitationReason").asText())
                .isEqualTo("No verified source citation was available");
    }
}
