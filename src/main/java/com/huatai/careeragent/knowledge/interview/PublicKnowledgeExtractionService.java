package com.huatai.careeragent.knowledge.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeDtos.*;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.PromptCatalog;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PublicKnowledgeExtractionService {
    private final LlmClient llmClient;
    private final SchemaRepairService schemaRepair;
    private final PublicKnowledgeAdminService adminService;
    private final ObjectMapper objectMapper;

    public PublicKnowledgeExtractionService(LlmClient llmClient, SchemaRepairService schemaRepair,
                                            PublicKnowledgeAdminService adminService, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.schemaRepair = schemaRepair;
        this.adminService = adminService;
        this.objectMapper = objectMapper;
    }

    public SourceResponse extractAndCreate(Long adminId, ExtractRequest request) {
        String traceId = "public_extract_" + UUID.randomUUID().toString().replace("-", "");
        var response = llmClient.complete(LlmRequest.secured(
                PromptCatalog.PUBLIC_KNOWLEDGE_EXTRACTION.systemPrompt(),
                PromptCatalog.PUBLIC_KNOWLEDGE_EXTRACTION.instruction(),
                List.of(request.content()), traceId, true));
        var validated = schemaRepair.validateOrRepair("public_interview_knowledge_extraction.schema.json",
                response.content(), traceId);
        List<ExperienceInput> experiences = objectMapper.convertValue(validated.value().path("experiences"),
                new TypeReference<>() { });
        return adminService.create(adminId, new CreateSourceRequest(request.sourceType(), request.platform(),
                request.sourceUrl(), request.title(), request.publishedAt(), request.copyrightStatus(),
                request.qualityScore(), experiences));
    }

    public record ExtractRequest(
            @NotNull KnowledgeSource.SourceType sourceType,
            @Size(max = 64) String platform,
            @Size(max = 1000) String sourceUrl,
            @NotBlank @Size(max = 255) String title,
            Instant publishedAt,
            @NotNull KnowledgeSource.CopyrightStatus copyrightStatus,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal qualityScore,
            @NotBlank @Size(max = 20000) String content
    ) { }
}
