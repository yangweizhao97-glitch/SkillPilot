package com.huatai.careeragent.task;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.common.api.PageResponse;
import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.CareerTaskDtos.CreateCareerTaskRequest;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogResponse;
import com.huatai.careeragent.task.log.TaskLogDtos.UserTaskProgressResponse;
import com.huatai.careeragent.task.log.TaskLogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/career-tasks")
public class CareerTaskController {
    private final CareerTaskService careerTaskService;
    private final TaskLogService taskLogService;
    private final CareerTaskEventStreamService eventStreamService;

    public CareerTaskController(CareerTaskService careerTaskService, TaskLogService taskLogService,
                                CareerTaskEventStreamService eventStreamService) {
        this.careerTaskService = careerTaskService;
        this.taskLogService = taskLogService;
        this.eventStreamService = eventStreamService;
    }

    @PostMapping
    public ApiResponse<CareerTaskResponse> create(
            CurrentUser currentUser,
            @Valid @RequestBody CreateCareerTaskRequest request
    ) {
        return ApiResponse.ok(careerTaskService.create(currentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<CareerTaskResponse>> list(
            CurrentUser currentUser,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(careerTaskService.list(currentUser.userId(), page, pageSize));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<CareerTaskResponse> get(CurrentUser currentUser, @PathVariable Long taskId) {
        return ApiResponse.ok(careerTaskService.get(currentUser.userId(), taskId));
    }

    @GetMapping("/{taskId}/logs")
    public ApiResponse<TaskLogResponse> logs(CurrentUser currentUser, @PathVariable Long taskId) {
        return ApiResponse.ok(taskLogService.list(currentUser.userId(), taskId));
    }

    @GetMapping("/{taskId}/progress")
    public ApiResponse<UserTaskProgressResponse> progress(CurrentUser currentUser, @PathVariable Long taskId) {
        return ApiResponse.ok(taskLogService.progress(currentUser.userId(), taskId));
    }

    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(CurrentUser currentUser, @PathVariable Long taskId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return eventStreamService.open(currentUser.userId(), taskId, lastEventId);
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<CareerTaskResponse> retry(CurrentUser currentUser, @PathVariable Long taskId) {
        return ApiResponse.ok(careerTaskService.retry(currentUser.userId(), taskId));
    }
}
