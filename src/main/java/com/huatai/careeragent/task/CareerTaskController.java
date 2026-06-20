package com.huatai.careeragent.task;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.CareerTaskDtos.CreateCareerTaskRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/career-tasks")
public class CareerTaskController {
    private final CareerTaskService careerTaskService;

    public CareerTaskController(CareerTaskService careerTaskService) {
        this.careerTaskService = careerTaskService;
    }

    @PostMapping
    public ApiResponse<CareerTaskResponse> create(
            CurrentUser currentUser,
            @Valid @RequestBody CreateCareerTaskRequest request
    ) {
        return ApiResponse.ok(careerTaskService.create(currentUser.userId(), request));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<CareerTaskResponse> get(CurrentUser currentUser, @PathVariable Long taskId) {
        return ApiResponse.ok(careerTaskService.get(currentUser.userId(), taskId));
    }
}
