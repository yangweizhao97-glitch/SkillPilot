package com.huatai.careeragent.task;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.CareerTaskDtos.CreateCareerTaskRequest;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogResponse;
import com.huatai.careeragent.task.log.TaskLogService;
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
    private final TaskLogService taskLogService;

    public CareerTaskController(CareerTaskService careerTaskService, TaskLogService taskLogService) {
        this.careerTaskService = careerTaskService;
        this.taskLogService = taskLogService;
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

    @GetMapping("/{taskId}/logs")
    public ApiResponse<TaskLogResponse> logs(CurrentUser currentUser, @PathVariable Long taskId) {
        return ApiResponse.ok(taskLogService.list(currentUser.userId(), taskId));
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<CareerTaskResponse> retry(CurrentUser currentUser, @PathVariable Long taskId) {
        return ApiResponse.ok(careerTaskService.retry(currentUser.userId(), taskId));
    }
}
