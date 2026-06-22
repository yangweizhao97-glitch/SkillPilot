package com.huatai.careeragent.tutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TutorStreamService {
    private static final Logger log = LoggerFactory.getLogger(TutorStreamService.class);
    private final TutorService tutorService;
    private final Executor executor;

    public TutorStreamService(TutorService tutorService, @Qualifier("tutorStreamExecutor") Executor executor) {
        this.tutorService = tutorService;
        this.executor = executor;
    }

    public SseEmitter message(Long userId, Long sessionId, String content) {
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        executor.execute(() -> {
            try {
                send(emitter, closed, "TUTOR_MESSAGE_RECEIVED", new Status("问题已接收"));
                send(emitter, closed, "TUTOR_RETRIEVING", new Status("正在检索相关资料"));
                send(emitter, closed, "TUTOR_GENERATING", new Status("正在组织回答"));
                TutorService.TutorSessionResponse session = tutorService.messageStreaming(
                        userId, sessionId, content,
                        delta -> sendUnchecked(emitter, closed, "TUTOR_DELTA", new Delta(delta))
                );
                send(emitter, closed, "TUTOR_COMPLETED", new Session(session));
                if (!closed.get()) emitter.complete();
            } catch (Exception exception) {
                log.warn("Tutor streaming failed: sessionId={}, reason={}", sessionId, exception.getMessage());
                try {
                    send(emitter, closed, "TUTOR_FAILED", new Status("答疑生成失败，请重试"));
                    if (!closed.get()) emitter.complete();
                } catch (Exception sendFailure) {
                    if (!closed.get()) emitter.completeWithError(sendFailure);
                }
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, AtomicBoolean closed, String name, Object data) throws Exception {
        if (!closed.get()) emitter.send(SseEmitter.event().name(name).data(data));
    }

    private void sendUnchecked(SseEmitter emitter, AtomicBoolean closed, String name, Object data) {
        try { send(emitter, closed, name, data); }
        catch (Exception exception) { throw new IllegalStateException("Could not send tutor event", exception); }
    }

    public record Status(String message) { }
    public record Delta(String delta) { }
    public record Session(TutorService.TutorSessionResponse session) { }
}
