package com.huatai.careeragent.knowledge.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.interview.QuestionDifficulty;
import com.huatai.careeragent.interview.QuestionType;
import com.huatai.careeragent.knowledge.chunk.ApproxTokenCounter;
import com.huatai.careeragent.knowledge.embedding.EmbeddingClient;
import com.huatai.careeragent.knowledge.embedding.EmbeddingVectorFormatter;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchDtos.*;
import com.huatai.careeragent.knowledge.retrieval.RetrievalProperties;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PublicKnowledgeSearchService {
    private final PublicKnowledgeSearchRepository repository;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingVectorFormatter vectorFormatter;
    private final ApproxTokenCounter tokenCounter;
    private final RetrievalProperties properties;
    private final ObjectMapper objectMapper;

    public PublicKnowledgeSearchService(PublicKnowledgeSearchRepository repository, EmbeddingClient embeddingClient,
                                        EmbeddingVectorFormatter vectorFormatter, ApproxTokenCounter tokenCounter,
                                        RetrievalProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.embeddingClient = embeddingClient;
        this.vectorFormatter = vectorFormatter;
        this.tokenCounter = tokenCounter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SearchResponse search(SearchRequest request) {
        int topK = request.topK() == null ? properties.getDefaultTopK()
                : Math.min(Math.max(request.topK(), 1), properties.getMaxTopK());
        String vector = vectorFormatter.toPgVector(embeddingClient.embed(request.query()).vector());
        Map<String, PublicKnowledgeSearchRow> merged = new LinkedHashMap<>();
        repository.vector(vector, request, topK * 2).forEach(row -> merged.put(row.questionHash(), row));
        repository.keyword(keywords(request.query()), request, topK * 2).forEach(row -> merged.merge(
                row.questionHash(), row, (left, right) -> left.score() >= right.score() ? left : right));
        List<SearchItem> items = merged.values().stream()
                .sorted(Comparator.comparingDouble(PublicKnowledgeSearchRow::score).reversed()
                        .thenComparing(PublicKnowledgeSearchRow::questionId))
                .limit(topK).map(this::toItem).toList();
        return new SearchResponse(items);
    }

    private List<String> keywords(String query) {
        return tokenCounter.tokenize(query).stream().map(String::toLowerCase)
                .filter(value -> value.length() >= 2).distinct().limit(8).toList();
    }

    private SearchItem toItem(PublicKnowledgeSearchRow row) {
        return new SearchItem("public_question_" + row.questionId(), row.questionId(), row.question(),
                QuestionType.valueOf(row.questionType()), QuestionDifficulty.valueOf(row.difficulty()),
                strings(row.knowledgePoints()), strings(row.answerOutline()), row.referenceAnswer(),
                rubric(row.scoringRubric()), strings(row.commonMistakes()), strings(row.followUpCandidates()),
                row.industry(), row.company(), row.position(), row.experienceLevel(), row.interviewRound(),
                row.eventDate(), row.sourceTitle(), row.sourceUrl(), row.platform(), row.collectedAt(),
                row.qualityScore(), row.score());
    }

    private List<String> strings(String json) { return read(json, new TypeReference<>() {}); }
    private List<Map<String, Object>> rubric(String json) { return read(json, new TypeReference<>() {}); }
    private <T> T read(String json, TypeReference<T> type) {
        try { return objectMapper.readValue(json, type); }
        catch (Exception exception) { throw new IllegalStateException("Invalid public knowledge JSON", exception); }
    }
}
