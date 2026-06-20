package com.huatai.careeragent.interview;

import com.huatai.careeragent.interview.InteractiveInterviewService.InterviewSessionResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class InterviewStreamService {
    private final InteractiveInterviewService interviews;
    private final Executor executor;

    public InterviewStreamService(InteractiveInterviewService interviews,
                                  @Qualifier("interviewStreamExecutor") Executor executor) {
        this.interviews = interviews;
        this.executor = executor;
    }

    public SseEmitter answer(Long userId, Long sessionId, String answer) {
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        executor.execute(() -> {
            try {
                send(emitter, closed, "INTERVIEW_ANSWER_RECEIVED", new Status("回答已接收"));
                send(emitter, closed, "INTERVIEW_EVALUATING", new Status("正在分析回答"));
                InterviewSessionResponse before = interviews.get(userId, sessionId);
                InterviewSessionResponse after = interviews.answer(userId, sessionId, answer);
                var additions = after.messages().subList(Math.min(before.messages().size() + 1, after.messages().size()),
                        after.messages().size());
                for (var message : additions) {
                    String content = message.content();
                    for (int offset = 0; offset < content.length(); offset += 12) {
                        send(emitter, closed, "INTERVIEW_FOLLOWUP_STREAMING",
                                new Delta(content.substring(offset, Math.min(content.length(), offset + 12))));
                    }
                }
                send(emitter, closed, "INTERVIEW_FEEDBACK_COMPLETED", new Session(after));
                if (after.status() == InterviewSessionStatus.FINISHED) {
                    send(emitter, closed, "INTERVIEW_COMPLETED", new Session(after));
                } else if (after.currentQuestion() > before.currentQuestion()) {
                    send(emitter, closed, "INTERVIEW_NEXT_QUESTION", new Session(after));
                }
                if (!closed.get()) emitter.complete();
            } catch (Exception exception) {
                try {
                    send(emitter, closed, "INTERVIEW_FAILED", new Status("面试回答处理失败"));
                    if (!closed.get()) emitter.complete();
                } catch (Exception sendFailure) {
                    if (!closed.get()) emitter.completeWithError(sendFailure);
                }
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, AtomicBoolean closed, String name, Object data) throws Exception {
        if (closed.get()) return;
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    public record Status(String message) { }
    public record Delta(String delta) { }
    public record Session(InterviewSessionResponse session) { }
}
