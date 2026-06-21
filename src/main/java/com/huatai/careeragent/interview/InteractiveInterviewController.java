package com.huatai.careeragent.interview;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.interview.InteractiveInterviewService.InterviewSessionResponse;
import com.huatai.careeragent.interview.InteractiveInterviewService.InterviewSessionSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/interview/sessions")
public class InteractiveInterviewController {
    private final InteractiveInterviewService service;
    private final InterviewStreamService streamService;
    private final InterviewReviewService reviewService;

    public InteractiveInterviewController(InteractiveInterviewService service, InterviewStreamService streamService,
                                          InterviewReviewService reviewService) {
        this.service = service;
        this.streamService = streamService;
        this.reviewService = reviewService;
    }

    @PostMapping
    public ApiResponse<InterviewSessionResponse> create(CurrentUser currentUser,
                                                        @Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(service.create(currentUser.userId(), request.resumeId(), request.jobId()));
    }

    @GetMapping
    public ApiResponse<List<InterviewSessionSummary>> list(CurrentUser currentUser) {
        return ApiResponse.ok(service.list(currentUser.userId()));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<InterviewSessionResponse> get(CurrentUser currentUser, @PathVariable Long sessionId) {
        return ApiResponse.ok(service.get(currentUser.userId(), sessionId));
    }

    @PostMapping("/{sessionId}/answers")
    public ApiResponse<InterviewSessionResponse> answer(CurrentUser currentUser, @PathVariable Long sessionId,
                                                        @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.ok(service.answer(currentUser.userId(), sessionId, request.answer()));
    }

    @PostMapping(value = "/{sessionId}/answers/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answerStream(CurrentUser currentUser, @PathVariable Long sessionId,
                                   @Valid @RequestBody SubmitAnswerRequest request) {
        return streamService.answer(currentUser.userId(), sessionId, request.answer());
    }

    @PostMapping("/{sessionId}/finish")
    public ApiResponse<InterviewSessionResponse> finish(CurrentUser currentUser, @PathVariable Long sessionId) {
        return ApiResponse.ok(service.finish(currentUser.userId(), sessionId));
    }

    @GetMapping("/{sessionId}/review")
    public ApiResponse<InterviewReviewService.ReviewState> review(CurrentUser currentUser,
                                                                  @PathVariable Long sessionId) {
        return ApiResponse.ok(reviewService.get(currentUser.userId(), sessionId));
    }

    @PostMapping("/{sessionId}/review")
    public ApiResponse<InterviewReviewService.ReviewResponse> generateReview(CurrentUser currentUser,
                                                                             @PathVariable Long sessionId) {
        return ApiResponse.ok(reviewService.generate(currentUser.userId(), sessionId));
    }

    public record CreateSessionRequest(@NotNull Long resumeId, @NotNull Long jobId) { }
    public record SubmitAnswerRequest(@NotBlank @Size(max = 10000) String answer) { }
}
