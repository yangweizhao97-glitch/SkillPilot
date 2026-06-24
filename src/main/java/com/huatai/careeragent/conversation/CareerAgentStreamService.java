package com.huatai.careeragent.conversation;

import com.huatai.careeragent.conversation.CareerAgentDtos.PlanRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.PlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CareerAgentStreamService {
    private static final Logger log = LoggerFactory.getLogger(CareerAgentStreamService.class);

    private final CareerAgentPlanningService planningService;
    private final CareerAgentConversationService conversationService;
    private final Executor executor;

    public CareerAgentStreamService(
            CareerAgentPlanningService planningService,
            CareerAgentConversationService conversationService,
            @Qualifier("careerAgentStreamExecutor") Executor executor
    ) {
        this.planningService = planningService;
        this.conversationService = conversationService;
        this.executor = executor;
    }

    public SseEmitter plan(Long userId, PlanRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));

        executor.execute(() -> {
            AtomicInteger deltaCount = new AtomicInteger();
            try {
                send(emitter, closed, "AGENT_MESSAGE_RECEIVED", new Status("消息已发送"));
                send(emitter, closed, "AGENT_PLANNING", new Status("正在理解你的目标并整理上下文"));
                PlanResponse plan = planningService.planStreaming(userId, request, delta -> {
                    deltaCount.incrementAndGet();
                    sendUnchecked(emitter, closed, "AGENT_DELTA", new Delta(delta));
                });
                var messages = conversationService.recordPlan(userId, request, plan);
                PlanResponse completed = new PlanResponse(
                        plan.intent(),
                        plan.selectedResources(),
                        plan.missingResources(),
                        plan.nextAction(),
                        plan.canStartWorkflow(),
                        plan.workflowSteps(),
                        plan.resumeId(),
                        plan.jobId(),
                        plan.reportId(),
                        plan.task(),
                        plan.learningPlanId(),
                        plan.interviewSessionId(),
                        plan.profile(),
                        plan.suggestedPrompts(),
                        messages,
                        plan.assistantMessage()
                );
                if (deltaCount.get() == 0) {
                    send(emitter, closed, "AGENT_RESPONSE_READY", new Status("已完成处理"));
                }
                send(emitter, closed, "AGENT_COMPLETED", completed);
                if (!closed.get()) emitter.complete();
            } catch (Exception exception) {
                log.warn("Career Agent streaming failed: userId={}, reason={}", userId, exception.getMessage());
                try {
                    send(emitter, closed, "AGENT_FAILED", new Status("消息处理失败，请重试"));
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
        try {
            send(emitter, closed, name, data);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not send Career Agent event", exception);
        }
    }

    public record Status(String message) { }
    public record Delta(String delta) { }
}
