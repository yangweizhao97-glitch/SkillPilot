package com.huatai.careeragent.knowledge.retrieval;

import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.knowledge.chunk.ApproxTokenCounter;
import com.huatai.careeragent.knowledge.embedding.EmbeddingClient;
import com.huatai.careeragent.knowledge.embedding.EmbeddingResponse;
import com.huatai.careeragent.knowledge.embedding.EmbeddingVectorFormatter;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchItem;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchRequest;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeSearchService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchService.class);

    private final KnowledgeSearchRepository knowledgeSearchRepository;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingVectorFormatter vectorFormatter;
    private final ApproxTokenCounter tokenCounter;
    private final RetrievalProperties retrievalProperties;

    public KnowledgeSearchService(
            KnowledgeSearchRepository knowledgeSearchRepository,
            EmbeddingClient embeddingClient,
            EmbeddingVectorFormatter vectorFormatter,
            ApproxTokenCounter tokenCounter,
            RetrievalProperties retrievalProperties
    ) {
        this.knowledgeSearchRepository = knowledgeSearchRepository;
        this.embeddingClient = embeddingClient;
        this.vectorFormatter = vectorFormatter;
        this.tokenCounter = tokenCounter;
        this.retrievalProperties = retrievalProperties;
    }

    public KnowledgeSearchResponse search(Long userId, KnowledgeSearchRequest request) {
        int topK = safeTopK(request.topK());
        RetrievalMode mode = request.retrievalMode() == null ? RetrievalMode.HYBRID : request.retrievalMode();
        List<FileType> sourceTypes = request.sourceTypes() == null ? List.of() : request.sourceTypes();

        long start = System.nanoTime();
        List<ChunkSearchRow> rows = switch (mode) {
            case VECTOR -> vectorSearch(request.query(), userId, sourceTypes, topK);
            case KEYWORD -> keywordSearch(request.query(), userId, sourceTypes, topK);
            case HYBRID -> hybridSearch(request.query(), userId, sourceTypes, topK);
        };
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info(
                "Knowledge search completed: mode={}, topK={}, resultCount={}, estimatedQueryTokens={}, elapsedMs={}",
                mode,
                topK,
                rows.size(),
                tokenCounter.count(request.query()),
                elapsedMs
        );

        return new KnowledgeSearchResponse(rows.stream().map(this::toItem).toList());
    }

    private List<ChunkSearchRow> vectorSearch(String query, Long userId, List<FileType> sourceTypes, int topK) {
        EmbeddingResponse embedding = embeddingClient.embed(query);
        String vectorLiteral = vectorFormatter.toPgVector(embedding.vector());
        return knowledgeSearchRepository.searchVector(userId, vectorLiteral, sourceTypes, topK);
    }

    private List<ChunkSearchRow> keywordSearch(String query, Long userId, List<FileType> sourceTypes, int topK) {
        return knowledgeSearchRepository.searchKeyword(userId, keywords(query), sourceTypes, topK);
    }

    private List<ChunkSearchRow> hybridSearch(String query, Long userId, List<FileType> sourceTypes, int topK) {
        Map<Long, ChunkSearchRow> merged = new LinkedHashMap<>();
        for (ChunkSearchRow row : vectorSearch(query, userId, sourceTypes, topK * 2)) {
            merged.put(row.chunkId(), row);
        }
        for (ChunkSearchRow row : keywordSearch(query, userId, sourceTypes, topK * 2)) {
            ChunkSearchRow existing = merged.get(row.chunkId());
            if (existing == null) {
                merged.put(row.chunkId(), row);
            } else {
                merged.put(row.chunkId(), new ChunkSearchRow(
                        existing.chunkId(),
                        existing.documentId(),
                        existing.sourceType(),
                        existing.sourceTitle(),
                        existing.sourceLocator(),
                        existing.content(),
                        Math.max(existing.score(), row.score())
                ));
            }
        }
        return merged.values()
                .stream()
                .sorted(Comparator.comparingDouble(ChunkSearchRow::score).reversed().thenComparing(ChunkSearchRow::chunkId))
                .limit(topK)
                .toList();
    }

    private List<String> keywords(String query) {
        List<String> keywords = new ArrayList<>();
        for (String token : tokenCounter.tokenize(query)) {
            if (StringUtils.hasText(token) && token.length() >= 2) {
                keywords.add(token.toLowerCase());
            }
        }
        return keywords.stream().distinct().limit(8).toList();
    }

    private int safeTopK(Integer topK) {
        if (topK == null) {
            return retrievalProperties.getDefaultTopK();
        }
        return Math.min(Math.max(topK, 1), retrievalProperties.getMaxTopK());
    }

    private KnowledgeSearchItem toItem(ChunkSearchRow row) {
        return new KnowledgeSearchItem(
                "chunk_" + row.chunkId(),
                row.documentId(),
                row.sourceType(),
                row.sourceTitle(),
                row.sourceLocator(),
                row.content(),
                row.score()
        );
    }
}
