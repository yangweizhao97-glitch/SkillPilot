package com.huatai.careeragent.task.log;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogItem;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskLogService {
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionLogRepository executionLogRepository;

    public TaskLogService(
            AgentTaskRepository agentTaskRepository,
            AgentExecutionLogRepository executionLogRepository
    ) {
        this.agentTaskRepository = agentTaskRepository;
        this.executionLogRepository = executionLogRepository;
    }

    @Transactional(readOnly = true)
    public TaskLogResponse list(Long userId, Long taskId) {
        AgentTask task = agentTaskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(
                        "CAREER_TASK_NOT_FOUND",
                        "Career task not found",
                        HttpStatus.NOT_FOUND
                ));
        List<TaskLogItem> items = executionLogRepository
                .findByTaskIdAndUserIdOrderByCreatedAtAscIdAsc(taskId, userId)
                .stream()
                .map(TaskLogItem::from)
                .toList();
        return new TaskLogResponse(taskId, task.getTraceId(), items);
    }
}
