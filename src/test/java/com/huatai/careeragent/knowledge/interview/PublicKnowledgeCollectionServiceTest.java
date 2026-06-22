package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.knowledge.interview.PublicKnowledgeDtos.SourceResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicKnowledgeCollectionServiceTest {
    @Test
    void discoversFetchesAndImportsAheadOfUserQueries() {
        PublicKnowledgeDiscoveryService discovery = mock(PublicKnowledgeDiscoveryService.class);
        PublicKnowledgeExtractionService extraction = mock(PublicKnowledgeExtractionService.class);
        var candidate = new PublicKnowledgeDiscoveryService.DiscoveryCandidate(
                "Java 面经", "https://example.com/interview", "摘要", Instant.now());
        when(discovery.discover(any())).thenReturn(new PublicKnowledgeDiscoveryService.DiscoveryResponse(List.of(candidate)));
        when(discovery.fetch(candidate)).thenReturn(new PublicKnowledgeDiscoveryService.FetchedPage(
                candidate.title(), candidate.url(), "完整但临时的公开页面文本", candidate.publishedAt()));
        when(extraction.extractAndCreate(eq(9L), any())).thenReturn(new SourceResponse(
                3L, "Java 面经", "EXAMPLE", candidate.url(), KnowledgeSource.ReviewStatus.PENDING,
                new BigDecimal("0.6"), 1, 5, 0, 0, 0, Instant.now(), null));
        PublicKnowledgeCollectionService service = new PublicKnowledgeCollectionService(discovery, extraction);

        var result = service.collect(9L, new PublicKnowledgeCollectionService.CollectionRequest(
                "Java 后端 面经", "EXAMPLE", 5, new BigDecimal("0.6")));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.sources().getFirst().status()).isEqualTo("PENDING_QUALITY_REVIEW");
        verify(extraction).extractAndCreate(eq(9L), argThat(request ->
                request.content().equals("完整但临时的公开页面文本")));
    }
}
