package com.huatai.careeragent.conversation;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentConversationResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentMessageResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentMessageRole;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentMessageType;
import com.huatai.careeragent.conversation.CareerAgentDtos.AppendAgentMessageRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerProfileResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.PlanRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.PlanResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.RequiredResource;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.task.AgentTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CareerAgentConversationService {
    private static final String DEFAULT_TITLE = "Career Agent 对话";

    private final CareerAgentConversationRepository conversationRepository;
    private final CareerAgentMessageRepository messageRepository;
    private final CareerProfileService profileService;
    private final AgentTaskRepository taskRepository;
    private final FinalReportRepository reportRepository;

    public CareerAgentConversationService(
            CareerAgentConversationRepository conversationRepository,
            CareerAgentMessageRepository messageRepository,
            CareerProfileService profileService,
            AgentTaskRepository taskRepository,
            FinalReportRepository reportRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.profileService = profileService;
        this.taskRepository = taskRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public AgentConversationResponse getDefault(Long userId) {
        CareerAgentConversation conversation = conversationRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId)
                .stream()
                .findFirst()
                .orElse(null);
        if (conversation == null) {
            CareerProfileResponse profile = profileService.get(userId);
            return new AgentConversationResponse(null, DEFAULT_TITLE, profile, List.of(), null, null);
        }
        return response(userId, conversation);
    }

    @Transactional
    public List<AgentMessageResponse> recordPlan(Long userId, PlanRequest request, PlanResponse plan) {
        CareerAgentConversation conversation = getOrCreate(userId);
        List<CareerAgentMessage> messages = new ArrayList<>();
        messages.add(message(userId, conversation.getId(), AgentMessageRole.USER, AgentMessageType.TEXT,
                request.message(), null, null, Map.of()));
        messages.add(message(userId, conversation.getId(), AgentMessageRole.SYSTEM, AgentMessageType.PROCESS,
                "正在判断你的意图和所需资料。", null, null,
                Map.of("intent", plan.intent().intent().name(), "confidence", plan.intent().confidence())));
        if (!plan.missingResources().isEmpty()) {
            messages.add(resourceCard(userId, conversation.getId(), plan.missingResources()));
        } else if (plan.nextAction() == CareerAgentDtos.AgentNextAction.START_WORKFLOW || plan.task() != null) {
            messages.add(message(userId, conversation.getId(), AgentMessageRole.SYSTEM, AgentMessageType.TOOL_STATUS,
                    "上下文已齐全，正在调用职业分析工作流。", plan.task() == null ? null : plan.task().taskId(), null,
                    Map.of("workflowSteps", plan.workflowSteps().stream().map(Enum::name).toList())));
        } else if (plan.nextAction() == CareerAgentDtos.AgentNextAction.GET_REPORT) {
            messages.add(message(userId, conversation.getId(), AgentMessageRole.SYSTEM, AgentMessageType.TOOL_STATUS,
                    "正在调用报告检索工具，并结合历史报告整理回答。", null, plan.reportId(),
                    plan.reportId() == null ? Map.of() : Map.of("reportId", plan.reportId())));
        } else if (plan.nextAction() == CareerAgentDtos.AgentNextAction.ANSWER_DIRECTLY) {
            messages.add(message(userId, conversation.getId(), AgentMessageRole.SYSTEM, AgentMessageType.PROCESS,
                    "正在结合你的会话历史和求职画像生成回答。", null, null,
                    Map.of("intent", plan.intent().intent().name())));
        } else if (plan.nextAction() == CareerAgentDtos.AgentNextAction.GENERATE_LEARNING_PLAN) {
            messages.add(message(userId, conversation.getId(), AgentMessageRole.SYSTEM, AgentMessageType.TOOL_STATUS,
                    plan.learningPlanId() == null ? "正在检查报告是否满足学习计划生成条件。"
                            : "已调用学习计划生成能力，学习计划已生成。",
                    null, plan.reportId(), actionMetadata(plan)));
        } else if (plan.nextAction() == CareerAgentDtos.AgentNextAction.START_MOCK_INTERVIEW) {
            messages.add(message(userId, conversation.getId(), AgentMessageRole.SYSTEM, AgentMessageType.TOOL_STATUS,
                    plan.interviewSessionId() == null ? "正在检查是否已有可用于模拟面试的面试题。"
                            : "已调用模拟面试能力，面试会话已创建。",
                    null, null, actionMetadata(plan)));
        }
        if (plan.task() != null) {
            messages.add(message(userId, conversation.getId(), AgentMessageRole.SYSTEM, AgentMessageType.WORKFLOW_STATUS,
                    "任务 #" + plan.task().taskId() + " 已启动，后续进度会在这里更新。", plan.task().taskId(), null,
                    Map.of("status", plan.task().status().name(), "progress", plan.task().progress())));
        }
        messages.add(message(userId, conversation.getId(), AgentMessageRole.ASSISTANT, AgentMessageType.TEXT,
                plan.assistantMessage(), plan.task() == null ? null : plan.task().taskId(), plan.reportId(),
                actionMetadata(plan)));
        return messageRepository.saveAll(messages).stream().map(AgentMessageResponse::from).toList();
    }

    @Transactional
    public List<AgentMessageResponse> recordWorkflowProgress(Long userId, Long taskId, List<String> summaries) {
        CareerAgentConversation conversation = getOrCreate(userId);
        List<CareerAgentMessage> messages = summaries.stream()
                .map(summary -> message(userId, conversation.getId(), AgentMessageRole.SYSTEM,
                        AgentMessageType.WORKFLOW_STATUS, summary, taskId, null, Map.of()))
                .toList();
        return messageRepository.saveAll(messages).stream().map(AgentMessageResponse::from).toList();
    }

    @Transactional
    public AgentMessageResponse append(Long userId, AppendAgentMessageRequest request) {
        validateOwnedReferences(userId, request.taskId(), request.reportId());
        CareerAgentConversation conversation = getOrCreate(userId);
        CareerAgentMessage saved = messageRepository.save(message(
                userId,
                conversation.getId(),
                request.role() == null ? AgentMessageRole.SYSTEM : request.role(),
                request.messageType() == null ? AgentMessageType.TEXT : request.messageType(),
                request.content(),
                request.taskId(),
                request.reportId(),
                request.metadata() == null ? Map.of() : request.metadata()
        ));
        return AgentMessageResponse.from(saved);
    }

    private void validateOwnedReferences(Long userId, Long taskId, Long reportId) {
        if (taskId != null && taskRepository.findByIdAndUserId(taskId, userId).isEmpty()) {
            throw new BusinessException("CAREER_TASK_NOT_FOUND", "Career task not found", HttpStatus.NOT_FOUND);
        }
        if (reportId != null && reportRepository.findByIdAndUserId(reportId, userId).isEmpty()) {
            throw new BusinessException("REPORT_NOT_FOUND", "Report not found", HttpStatus.NOT_FOUND);
        }
    }

    private AgentConversationResponse response(Long userId, CareerAgentConversation conversation) {
        List<AgentMessageResponse> messages = messageRepository
                .findByUserIdAndConversationIdOrderByCreatedAtAscIdAsc(userId, conversation.getId())
                .stream()
                .map(AgentMessageResponse::from)
                .toList();
        return new AgentConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                profileService.get(userId),
                messages,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private CareerAgentConversation getOrCreate(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId)
                .stream()
                .findFirst()
                .orElseGet(() -> conversationRepository.save(new CareerAgentConversation(userId, DEFAULT_TITLE)));
    }

    private CareerAgentMessage resourceCard(Long userId, Long conversationId, List<RequiredResource> missingResources) {
        String content = missingResources.stream().map(CareerAgentConversationService::resourceLabel)
                .reduce((left, right) -> left + "、" + right)
                .map(value -> "需要补充：" + value)
                .orElse("需要补充资料");
        return message(userId, conversationId, AgentMessageRole.SYSTEM, AgentMessageType.RESOURCE_CARD,
                content, null, null, Map.of("missingResources", missingResources.stream().map(Enum::name).toList()));
    }

    private static String resourceLabel(RequiredResource resource) {
        return switch (resource) {
            case RESUME -> "简历";
            case JOB -> "岗位 / JD";
            case REPORT -> "报告";
        };
    }

    private static Map<String, Object> actionMetadata(PlanResponse plan) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("nextAction", plan.nextAction().name());
        if (plan.reportId() != null) metadata.put("reportId", plan.reportId());
        if (plan.learningPlanId() != null) metadata.put("learningPlanId", plan.learningPlanId());
        if (plan.interviewSessionId() != null) metadata.put("interviewSessionId", plan.interviewSessionId());
        return Map.copyOf(metadata);
    }

    private static CareerAgentMessage message(Long userId, Long conversationId, AgentMessageRole role,
                                              AgentMessageType messageType, String content, Long taskId,
                                              Long reportId, Map<String, Object> metadata) {
        return new CareerAgentMessage(userId, conversationId, role, messageType, content, taskId, reportId, metadata);
    }
}
