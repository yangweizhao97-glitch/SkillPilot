package com.huatai.careeragent.knowledge.retrieval;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchRequest;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeSearchController {
    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeSearchController(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @PostMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(
            CurrentUser currentUser,
            @Valid @RequestBody KnowledgeSearchRequest request
    ) {
        return ApiResponse.ok(knowledgeSearchService.search(currentUser.userId(), request));
    }
}
