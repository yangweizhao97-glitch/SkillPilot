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
                send(emitter, closed, "INTERVIEW_SCORING", new Status("正在生成评分与改进建议"));
                InterviewSessionResponse before = interviews.get(userId, sessionId);
                InterviewSessionResponse after = interviews.answerStreaming(userId, sessionId, answer,
                        delta -> sendUnchecked(emitter, closed, "INTERVIEW_FOLLOWUP_STREAMING", new Delta(delta)));
                if (after.evaluations().size() > before.evaluations().size()) {
                    send(emitter, closed, "INTERVIEW_SCORE_COMPLETED",
                            new Evaluation(after.evaluations().getLast()));
                } else {
                    send(emitter, closed, "INTERVIEW_SCORE_FAILED", new Status("本次评分暂不可用，面试已继续"));
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
                    send(emitter, closed, "INTERVIEW_FAILED", new Status("面试回答处理失败，请重试"));
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

    private void sendUnchecked(SseEmitter emitter, AtomicBoolean closed, String name, Object data) {
        try {
            send(emitter, closed, name, data);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not send interview stream event", exception);
        }
    }

    public record Status(String message) { }
    public record Delta(String delta) { }
    public record Session(InterviewSessionResponse session) { }
    public record Evaluation(InteractiveInterviewService.InterviewAnswerEvaluationResponse evaluation) { }
}
