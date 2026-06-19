package com.huatai.careeragent.job;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.api.PageResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.job.JobDtos.CreateJobRequest;
import com.huatai.careeragent.job.JobDtos.JobResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ApiResponse<JobResponse> create(CurrentUser currentUser, @Valid @RequestBody CreateJobRequest request) {
        return ApiResponse.ok(jobService.create(currentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<JobResponse>> list(
            CurrentUser currentUser,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(jobService.list(currentUser.userId(), page, pageSize));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<JobResponse> get(CurrentUser currentUser, @PathVariable Long jobId) {
        return ApiResponse.ok(jobService.get(currentUser.userId(), jobId));
    }
}
