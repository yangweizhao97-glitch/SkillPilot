package com.huatai.careeragent.learning;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning-plans")
public class LearningPlanController {
    private final LearningPlanService service;

    public LearningPlanController(LearningPlanService service) { this.service = service; }

    @GetMapping
    public ApiResponse<LearningPlanResponse> get(CurrentUser user, @RequestParam Long taskId) {
        return ApiResponse.ok(service.get(user.userId(), taskId));
    }

    @PostMapping
    public ApiResponse<LearningPlanResponse> generate(CurrentUser user, @Valid @RequestBody GenerateRequest request) {
        return ApiResponse.ok(service.generate(user.userId(), request.taskId()));
    }

    public record GenerateRequest(@NotNull Long taskId) { }
}
