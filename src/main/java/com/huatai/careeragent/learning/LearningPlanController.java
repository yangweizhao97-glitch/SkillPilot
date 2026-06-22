package com.huatai.careeragent.learning;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

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
        return ApiResponse.ok(service.generate(user.userId(), request.taskId(), new LearningPlanService.GenerateOptions(
                request.planMode(), request.interviewDate(),
                request.availableHoursPerDay() == null ? 1 : request.availableHoursPerDay(),
                request.durationWeeks() == null ? 8 : request.durationWeeks(),
                request.targetIndustry(), request.targetCompany(), request.targetPosition(),
                request.experienceLevel(), request.focusAreas()
        )));
    }

    public record GenerateRequest(@NotNull Long taskId, LearningPlanMode planMode, LocalDate interviewDate,
                                  @Min(1) @Max(12) Integer availableHoursPerDay,
                                  @Min(2) @Max(24) Integer durationWeeks,
                                  @Size(max = 100) String targetIndustry,
                                  @Size(max = 100) String targetCompany,
                                  @Size(max = 100) String targetPosition,
                                  @Size(max = 100) String experienceLevel,
                                  @Size(max = 8) List<@Size(max = 100) String> focusAreas) { }
}
