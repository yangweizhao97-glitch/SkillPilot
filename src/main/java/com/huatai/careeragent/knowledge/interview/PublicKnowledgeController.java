package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchDtos.SearchRequest;
import com.huatai.careeragent.knowledge.interview.PublicKnowledgeSearchDtos.SearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interview-knowledge")
public class PublicKnowledgeController {
    private final PublicKnowledgeSearchService searchService;
    public PublicKnowledgeController(PublicKnowledgeSearchService searchService) { this.searchService = searchService; }

    @GetMapping("/search")
    public ApiResponse<SearchResponse> search(@Valid @ModelAttribute SearchRequest request) {
        return ApiResponse.ok(searchService.search(request));
    }
}
