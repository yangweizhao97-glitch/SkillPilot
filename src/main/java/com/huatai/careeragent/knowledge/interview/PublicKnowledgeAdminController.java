package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/interview-knowledge/sources")
public class PublicKnowledgeAdminController {
    private final PublicKnowledgeAdminService service;
    private final PublicKnowledgeDiscoveryService discoveryService;
    private final PublicKnowledgeExtractionService extractionService;
    public PublicKnowledgeAdminController(PublicKnowledgeAdminService service,
                                          PublicKnowledgeDiscoveryService discoveryService,
                                          PublicKnowledgeExtractionService extractionService) {
        this.service = service;
        this.discoveryService = discoveryService;
        this.extractionService = extractionService;
    }

    @PostMapping
    public ApiResponse<SourceResponse> create(CurrentUser user, @Valid @RequestBody CreateSourceRequest request) {
        return ApiResponse.ok(service.create(user.userId(), request));
    }

    @PostMapping("/{sourceId}/process")
    public ApiResponse<SourceResponse> process(@PathVariable Long sourceId) {
        return ApiResponse.ok(service.process(sourceId));
    }

    @PostMapping("/{sourceId}/review")
    public ApiResponse<SourceResponse> review(CurrentUser user, @PathVariable Long sourceId,
                                              @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(service.review(user.userId(), sourceId, request));
    }

    @GetMapping("/{sourceId}")
    public ApiResponse<SourceResponse> get(@PathVariable Long sourceId) { return ApiResponse.ok(service.get(sourceId)); }

    @PostMapping("/discover")
    public ApiResponse<PublicKnowledgeDiscoveryService.DiscoveryResponse> discover(
            @Valid @RequestBody PublicKnowledgeDiscoveryService.DiscoveryRequest request) {
        return ApiResponse.ok(discoveryService.discover(request));
    }

    @PostMapping("/extract")
    public ApiResponse<SourceResponse> extract(CurrentUser user,
            @Valid @RequestBody PublicKnowledgeExtractionService.ExtractRequest request) {
        return ApiResponse.ok(extractionService.extractAndCreate(user.userId(), request));
    }
}
