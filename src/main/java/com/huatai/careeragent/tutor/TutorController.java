package com.huatai.careeragent.tutor;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/tutor/sessions")
public class TutorController {
    private final TutorService tutorService;
    private final TutorStreamService streamService;

    public TutorController(TutorService tutorService, TutorStreamService streamService) {
        this.tutorService = tutorService;
        this.streamService = streamService;
    }

    @PostMapping
    public ApiResponse<TutorService.TutorSessionResponse> create(
            CurrentUser currentUser, @RequestBody TutorService.CreateTutorSessionRequest request) {
        return ApiResponse.ok(tutorService.create(currentUser.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<TutorService.TutorSessionSummary>> list(CurrentUser currentUser) {
        return ApiResponse.ok(tutorService.list(currentUser.userId()));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<TutorService.TutorSessionResponse> get(CurrentUser currentUser,
                                                              @PathVariable Long sessionId) {
        return ApiResponse.ok(tutorService.get(currentUser.userId(), sessionId));
    }

    @PostMapping(value = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(CurrentUser currentUser, @PathVariable Long sessionId,
                              @Valid @RequestBody TutorMessageRequest request) {
        return streamService.message(currentUser.userId(), sessionId, request.content());
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(CurrentUser currentUser, @PathVariable Long sessionId) {
        tutorService.delete(currentUser.userId(), sessionId);
        return ApiResponse.ok(null);
    }

    public record TutorMessageRequest(@NotBlank @Size(max = 10000) String content) { }
}
