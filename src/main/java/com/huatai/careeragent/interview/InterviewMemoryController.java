package com.huatai.careeragent.interview;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview/memory")
public class InterviewMemoryController {
    private final InterviewMemoryService service;

    public InterviewMemoryController(InterviewMemoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<InterviewMemoryService.MemorySnapshot> get(
            CurrentUser currentUser, @RequestParam Long resumeId, @RequestParam Long jobId) {
        return ApiResponse.ok(service.get(currentUser.userId(), resumeId, jobId));
    }

    @DeleteMapping
    public ApiResponse<Void> clear(CurrentUser currentUser,
                                   @RequestParam Long resumeId, @RequestParam Long jobId) {
        service.clear(currentUser.userId(), resumeId, jobId);
        return ApiResponse.ok(null);
    }
}
