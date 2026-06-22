package com.huatai.careeragent.task.log;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogItem;
import com.huatai.careeragent.task.log.TaskLogDtos.TaskLogResponse;
import com.huatai.careeragent.task.log.TaskLogDtos.UserTaskProgressResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.huatai.careeragent.agent.tool.ToolCallLogRepository;
import com.huatai.careeragent.task.log.TaskLogDtos.ToolCallItem;

@Service
public class TaskLogService {
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionLogRepository executionLogRepository;
    private final ToolCallLogRepository toolCallLogRepository;
    private final UserVisibleTaskEventMapper userVisibleTaskEventMapper;

    public TaskLogService(
            AgentTaskRepository agentTaskRepository,
            AgentExecutionLogRepository executionLogRepository,
            ToolCallLogRepository toolCallLogRepository,
            UserVisibleTaskEventMapper userVisibleTaskEventMapper
    ) {
        this.agentTaskRepository = agentTaskRepository;
        this.executionLogRepository = executionLogRepository;
        this.toolCallLogRepository = toolCallLogRepository;
        this.userVisibleTaskEventMapper = userVisibleTaskEventMapper;
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
        List<ToolCallItem> toolCalls = toolCallLogRepository
                .findByTaskIdAndUserIdOrderByCreatedAtAscIdAsc(taskId, userId).stream()
                .map(ToolCallItem::from).toList();
        return new TaskLogResponse(taskId, task.getTraceId(), items, toolCalls,
                userVisibleTaskEventMapper.steps(task, items, toolCalls),
                userVisibleTaskEventMapper.technicalDetails(items, toolCalls));
    }

    @Transactional(readOnly = true)
    public UserTaskProgressResponse progress(Long userId, Long taskId) {
        TaskLogResponse logs = list(userId, taskId);
        return new UserTaskProgressResponse(logs.taskId(), logs.steps(), logs.technicalDetails());
    }
}
