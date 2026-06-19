package com.huatai.careeragent.resume;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.api.PageResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.resume.ResumeDtos.CreateResumeRequest;
import com.huatai.careeragent.resume.ResumeDtos.ResumeResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public ApiResponse<ResumeResponse> create(CurrentUser currentUser, @Valid @RequestBody CreateResumeRequest request) {
        return ApiResponse.ok(resumeService.create(currentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ResumeResponse>> list(
            CurrentUser currentUser,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(resumeService.list(currentUser.userId(), page, pageSize));
    }

    @GetMapping("/{resumeId}")
    public ApiResponse<ResumeResponse> get(CurrentUser currentUser, @PathVariable Long resumeId) {
        return ApiResponse.ok(resumeService.get(currentUser.userId(), resumeId));
    }
}
