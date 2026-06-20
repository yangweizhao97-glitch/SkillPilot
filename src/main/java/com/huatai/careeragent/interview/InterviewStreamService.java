package com.huatai.careeragent.interview;

import com.huatai.careeragent.interview.InteractiveInterviewService.InterviewSessionResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@Service
public class InterviewStreamService {
    private final InteractiveInterviewService interviews;

    public InterviewStreamService(InteractiveInterviewService interviews) {
        this.interviews = interviews;
    }

    public SseEmitter answer(Long userId, Long sessionId, String answer) {
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            try {
                send(emitter, "INTERVIEW_ANSWER_RECEIVED", new Status("回答已接收"));
                send(emitter, "INTERVIEW_EVALUATING", new Status("正在分析回答"));
                InterviewSessionResponse before = interviews.get(userId, sessionId);
                InterviewSessionResponse after = interviews.answer(userId, sessionId, answer);
                var additions = after.messages().subList(Math.min(before.messages().size() + 1, after.messages().size()),
                        after.messages().size());
                for (var message : additions) {
                    String content = message.content();
                    for (int offset = 0; offset < content.length(); offset += 12) {
                        send(emitter, "INTERVIEW_FOLLOWUP_STREAMING",
                                new Delta(content.substring(offset, Math.min(content.length(), offset + 12))));
                    }
                }
                send(emitter, "INTERVIEW_FEEDBACK_COMPLETED", new Session(after));
                String next = after.status() == InterviewSessionStatus.FINISHED
                        ? "INTERVIEW_COMPLETED" : "INTERVIEW_NEXT_QUESTION";
                send(emitter, next, new Session(after));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) throws Exception {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    public record Status(String message) { }
    public record Delta(String delta) { }
    public record Session(InterviewSessionResponse session) { }
}
