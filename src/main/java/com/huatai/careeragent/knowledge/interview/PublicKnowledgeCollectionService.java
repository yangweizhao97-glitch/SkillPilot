package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.agent.tool.ToolException;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeDtos.SourceResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PublicKnowledgeCollectionService {
    private final PublicKnowledgeDiscoveryService discoveryService;
    private final PublicKnowledgeExtractionService extractionService;

    public PublicKnowledgeCollectionService(PublicKnowledgeDiscoveryService discoveryService,
                                            PublicKnowledgeExtractionService extractionService) {
        this.discoveryService = discoveryService;
        this.extractionService = extractionService;
    }

    public CollectionResponse collect(Long adminId, CollectionRequest request) {
        var candidates = discoveryService.discover(
                new PublicKnowledgeDiscoveryService.DiscoveryRequest(request.query(), request.limit())).candidates();
        List<CollectedSource> results = new ArrayList<>();
        for (var candidate : candidates) {
            try {
                var page = discoveryService.fetch(candidate);
                SourceResponse source = extractionService.extractAndCreate(adminId,
                        new PublicKnowledgeExtractionService.ExtractRequest(
                                KnowledgeSource.SourceType.WEB, request.platform(), page.url(), page.title(),
                                page.publishedAt(), KnowledgeSource.CopyrightStatus.PUBLIC_SUMMARY,
                                request.sourceQualityScore() == null ? new BigDecimal("0.5000")
                                        : request.sourceQualityScore(), page.text()));
                results.add(new CollectedSource(candidate.url(), source.sourceId(), "PENDING_QUALITY_REVIEW", null));
            } catch (ToolException exception) {
                results.add(new CollectedSource(candidate.url(), null, "FAILED", exception.getCode()));
            } catch (BusinessException exception) {
                results.add(new CollectedSource(candidate.url(), null, "FAILED", exception.getCode()));
            } catch (RuntimeException exception) {
                results.add(new CollectedSource(candidate.url(), null, "FAILED", "COLLECTION_FAILED"));
            }
        }
        int imported = (int) results.stream().filter(item -> item.sourceId() != null).count();
        return new CollectionResponse(candidates.size(), imported, candidates.size() - imported, List.copyOf(results));
    }

    public record CollectionRequest(@NotBlank @Size(max = 1000) String query,
                                    @Size(max = 64) String platform,
                                    @Min(1) @Max(20) Integer limit,
                                    @jakarta.validation.constraints.DecimalMin("0.0")
                                    @jakarta.validation.constraints.DecimalMax("1.0")
                                    BigDecimal sourceQualityScore) { }
    public record CollectionResponse(int discovered, int imported, int failed, List<CollectedSource> sources) { }
    public record CollectedSource(String sourceUrl, Long sourceId, String status, String errorCode) { }
}
