package com.huatai.careeragent.task;

import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogItem;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogResponse;
import com.huatai.careeragent.task.log.TaskLogDtos.ToolCallItem;
import com.huatai.careeragent.task.log.TaskLogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CareerTaskEventStreamService {
    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long POLL_MILLIS = 300L;

    private final CareerTaskService taskService;
    private final TaskLogService logService;
    private final Executor executor;

    public CareerTaskEventStreamService(CareerTaskService taskService, TaskLogService logService,
                                        @Qualifier("careerTaskStreamExecutor") Executor executor) {
        this.taskService = taskService;
        this.logService = logService;
        this.executor = executor;
    }

    public SseEmitter open(Long userId, Long taskId, String lastEventId) {
        CareerTaskResponse initialTask = taskService.get(userId, taskId);
        TaskLogResponse initialLogs = logService.list(userId, taskId);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        executor.execute(() -> stream(userId, taskId, lastEventId, initialTask, initialLogs, emitter, closed));
        return emitter;
    }

    private void stream(Long userId, Long taskId, String lastEventId,
                        CareerTaskResponse task, TaskLogResponse logs,
                        SseEmitter emitter, AtomicBoolean closed) {
        try {
            send(emitter, closed, snapshotId(task), "TASK_SNAPSHOT",
                    new Snapshot(task, logs.items(), logs.toolCalls(), lastEventId, Instant.now()));
            Set<Long> seenLogs = new HashSet<>();
            logs.items().forEach(item -> seenLogs.add(item.logId()));
            Map<String, ToolCallItem> seenTools = new HashMap<>();
            logs.toolCalls().forEach(item -> seenTools.put(item.toolCallId(), item));
            CareerTaskResponse previousTask = task;
            int idlePolls = 0;

            while (!closed.get() && !terminal(previousTask)) {
                Thread.sleep(POLL_MILLIS);
                boolean changed = false;
                CareerTaskResponse currentTask = taskService.get(userId, taskId);
                TaskLogResponse currentLogs = logService.list(userId, taskId);
                if (!currentTask.equals(previousTask)) {
                    send(emitter, closed, taskEventId(currentTask), "TASK_UPDATED", currentTask);
                    previousTask = currentTask;
                    changed = true;
                }
                for (TaskLogItem item : currentLogs.items()) {
                    if (seenLogs.add(item.logId())) {
                        send(emitter, closed, "step-" + item.logId(), "STEP_EVENT", item);
                        changed = true;
                    }
                }
                for (ToolCallItem item : currentLogs.toolCalls()) {
                    ToolCallItem previous = seenTools.put(item.toolCallId(), item);
                    if (!item.equals(previous)) {
                        send(emitter, closed, toolEventId(item), "TOOL_EVENT", item);
                        changed = true;
                    }
                }
                idlePolls = changed ? 0 : idlePolls + 1;
                if (idlePolls >= 30) {
                    emitter.send(SseEmitter.event().name("HEARTBEAT")
                            .data(new Heartbeat(taskId, Instant.now())));
                    idlePolls = 0;
                }
            }
            if (!closed.get()) {
                send(emitter, closed, taskEventId(previousTask), "TASK_STREAM_COMPLETED", previousTask);
                emitter.complete();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!closed.get()) emitter.completeWithError(exception);
        } catch (Exception exception) {
            if (!closed.get()) emitter.completeWithError(exception);
        }
    }

    private void send(SseEmitter emitter, AtomicBoolean closed, String id, String name, Object data) throws Exception {
        if (!closed.get()) emitter.send(SseEmitter.event().id(id).name(name).data(data));
    }

    private boolean terminal(CareerTaskResponse task) {
        return task.status() == WorkflowStatus.SUCCESS || task.status() == WorkflowStatus.FAILED;
    }

    private String snapshotId(CareerTaskResponse task) {
        return "snapshot-" + task.taskId() + "-" + task.updatedAt().toEpochMilli();
    }

    private String taskEventId(CareerTaskResponse task) {
        return "task-" + task.taskId() + "-" + task.updatedAt().toEpochMilli();
    }

    private String toolEventId(ToolCallItem item) {
        return "tool-" + item.toolCallId() + "-" + item.status();
    }

    public record Snapshot(CareerTaskResponse task, java.util.List<TaskLogItem> logs,
                           java.util.List<ToolCallItem> toolCalls, String resumedAfterEventId,
                           Instant synchronizedAt) { }
    public record Heartbeat(Long taskId, Instant sentAt) { }
}
