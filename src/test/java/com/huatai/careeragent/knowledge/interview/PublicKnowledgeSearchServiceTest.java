package com.huatai.careeragent.knowledge.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.knowledge.chunk.ApproxTokenCounter;
import com.huatai.careeragent.knowledge.embedding.EmbeddingClient;
import com.huatai.careeragent.knowledge.embedding.EmbeddingResponse;
import com.huatai.careeragent.knowledge.embedding.EmbeddingVectorFormatter;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchDtos.SearchRequest;
import com.huatai.careeragent.knowledge.retrieval.RetrievalProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicKnowledgeSearchServiceTest {
    @Test
    void mergesVectorAndKeywordRowsAndReturnsTraceableCitation() {
        PublicKnowledgeSearchRepository repository = mock(PublicKnowledgeSearchRepository.class);
        EmbeddingClient embeddings = mock(EmbeddingClient.class);
        EmbeddingVectorFormatter formatter = mock(EmbeddingVectorFormatter.class);
        when(embeddings.embed(anyString())).thenReturn(new EmbeddingResponse(new float[]{1, 0}, 2, "test"));
        when(formatter.toPgVector(any())).thenReturn("[1,0]");
        PublicKnowledgeSearchRow row = new PublicKnowledgeSearchRow(7L, "question-hash", "如何设计限流？", "SYSTEM_DESIGN",
                "MEDIUM", "[\"令牌桶\"]", "[\"定义目标\"]", "参考答案", "[]", "[]", "[]",
                "互联网", "示例公司", "Java 后端", "3-5年", "二面", LocalDate.now(), "授权面经",
                "https://example.com/interview", "MANUAL", Instant.now(), 0.9, 0.88);
        when(repository.vector(anyString(), any(), anyInt())).thenReturn(List.of(row));
        when(repository.keyword(anyList(), any(), anyInt())).thenReturn(List.of(row));
        PublicKnowledgeSearchService service = new PublicKnowledgeSearchService(repository, embeddings, formatter,
                new ApproxTokenCounter(), new RetrievalProperties(), new ObjectMapper());

        var result = service.search(new SearchRequest("Java 限流", "互联网", "Java 后端",
                null, null, null, 5));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().citationId()).isEqualTo("public_question_7");
        assertThat(result.items().getFirst().sourceUrl()).isEqualTo("https://example.com/interview");
        assertThat(result.items().getFirst().knowledgePoints()).containsExactly("令牌桶");
    }
}
