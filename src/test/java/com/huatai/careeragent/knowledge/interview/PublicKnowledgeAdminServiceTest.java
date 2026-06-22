package com.huatai.careeragent.knowledge.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.knowledge.embedding.EmbeddingClient;
import com.huatai.careeragent.knowledge.embedding.EmbeddingVectorFormatter;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeDtos.ReviewRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PublicKnowledgeAdminServiceTest {
    @Test
    void refusesApprovalBeforeEveryQuestionIsEmbedded() {
        KnowledgeSourceRepository sources = mock(KnowledgeSourceRepository.class);
        InterviewExperienceRepository experiences = mock(InterviewExperienceRepository.class);
        PublicInterviewQuestionRepository questions = mock(PublicInterviewQuestionRepository.class);
        PublicKnowledgeEmbeddingRepository embeddingRepository = mock(PublicKnowledgeEmbeddingRepository.class);
        PublicQuestionEvidenceRepository evidenceRepository = mock(PublicQuestionEvidenceRepository.class);
        KnowledgeSource source = new KnowledgeSource(KnowledgeSource.SourceType.MANUAL, "MANUAL", null,
                "Java 面经", null, "hash", KnowledgeSource.CopyrightStatus.AUTHORIZED,
                new BigDecimal("0.8"), 1L);
        when(sources.findById(9L)).thenReturn(Optional.of(source));
        when(experiences.findBySourceIdOrderByIdAsc(9L)).thenReturn(List.of());
        when(embeddingRepository.allEmbedded(9L)).thenReturn(false);
        PublicKnowledgeAdminService service = new PublicKnowledgeAdminService(sources, experiences, questions,
                embeddingRepository, evidenceRepository, new PublicKnowledgeSanitizer(), mock(EmbeddingClient.class),
                mock(EmbeddingVectorFormatter.class), new ObjectMapper());

        assertThatThrownBy(() -> service.review(2L, 9L,
                new ReviewRequest(KnowledgeSource.ReviewStatus.APPROVED, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("尚未完成向量化");
        verify(sources, never()).save(any());
    }

    @Test
    void refusesApprovalWhenQualityGateHasNotPassed() {
        KnowledgeSourceRepository sources = mock(KnowledgeSourceRepository.class);
        InterviewExperienceRepository experiences = mock(InterviewExperienceRepository.class);
        PublicInterviewQuestionRepository questions = mock(PublicInterviewQuestionRepository.class);
        PublicKnowledgeEmbeddingRepository embeddings = mock(PublicKnowledgeEmbeddingRepository.class);
        PublicQuestionEvidenceRepository evidence = mock(PublicQuestionEvidenceRepository.class);
        KnowledgeSource source = new KnowledgeSource(KnowledgeSource.SourceType.WEB, "NOWCODER", null,
                "Java 面经", null, "hash2", KnowledgeSource.CopyrightStatus.PUBLIC_SUMMARY,
                new BigDecimal("0.8"), 1L);
        when(sources.findById(10L)).thenReturn(Optional.of(source));
        when(experiences.findBySourceIdOrderByIdAsc(10L)).thenReturn(List.of());
        when(embeddings.allEmbedded(10L)).thenReturn(true);
        when(evidence.allAccepted(10L, PublicKnowledgeAdminService.MINIMUM_PUBLISH_QUALITY)).thenReturn(false);
        PublicKnowledgeAdminService service = new PublicKnowledgeAdminService(sources, experiences, questions,
                embeddings, evidence, new PublicKnowledgeSanitizer(), mock(EmbeddingClient.class),
                mock(EmbeddingVectorFormatter.class), new ObjectMapper());

        assertThatThrownBy(() -> service.review(2L, 10L,
                new ReviewRequest(KnowledgeSource.ReviewStatus.APPROVED, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("未通过质量审核");
    }
}
