package com.huatai.careeragent.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerIntent;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerProfileResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentResponse;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.report.FinalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class CareerAgentAnswerService {
    private static final Logger log = LoggerFactory.getLogger(CareerAgentAnswerService.class);
    private static final int MAX_REPORT_CONTEXT_CHARS = 5000;

    private final CareerAgentConversationRepository conversationRepository;
    private final CareerAgentMessageRepository messageRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public CareerAgentAnswerService(
            CareerAgentConversationRepository conversationRepository,
            CareerAgentMessageRepository messageRepository,
            LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public String answer(Long userId, String message, IntentResponse intent, CareerProfileResponse profile,
                         Optional<FinalReport> report) {
        return answerInternal(userId, message, intent, profile, report, null);
    }

    @Transactional(readOnly = true)
    public String answerStreaming(Long userId, String message, IntentResponse intent, CareerProfileResponse profile,
                                  Optional<FinalReport> report, Consumer<String> onDelta) {
        return answerInternal(userId, message, intent, profile, report, onDelta);
    }

    private String answerInternal(Long userId, String message, IntentResponse intent, CareerProfileResponse profile,
                                  Optional<FinalReport> report, Consumer<String> onDelta) {
        if (intent.intent() != CareerIntent.REPORT_QA && intent.intent() != CareerIntent.GENERAL_CAREER_QA) {
            return null;
        }
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("intent", intent.intent().name());
            context.put("intentLabel", intent.label());
            context.put("careerProfile", profile);
            context.put("recentConversation", recentConversation(userId));
            report.ifPresent(value -> context.put("latestReport", reportContext(value)));
            context.put("currentUserMessage", message);

            String traceId = "career_agent_answer_" + userId + "_"
                    + UUID.randomUUID().toString().replace("-", "");
            LlmRequest request = LlmRequest.secured(
                    "You are SkillPilot Career Agent. Answer in concise Chinese. "
                            + "Use only the supplied context when discussing reports. "
                            + "Never reveal chain-of-thought; mention only brief, auditable process summaries if useful.",
                    """
                            根据用户当前问题生成可执行的职业建议或报告追问回答。
                            要求：
                            1. 直接回答用户，不输出内部推理过程。
                            2. 如果报告上下文不足，明确说明缺少依据，并建议下一步。
                            3. 如果是通用求职问题，结合用户画像给出 3-5 条具体建议。
                            4. 不编造未提供的报告结论、简历经历或岗位要求。
                            """,
                    List.of(writeJson(context)), traceId, false
            );
            var response = onDelta == null
                    ? llmClient.complete(request)
                    : llmClient.stream(request, onDelta);
            String content = response == null ? null : response.content();
            if (content == null || content.isBlank()) {
                return fallback(intent, report.isPresent());
            }
            return content.trim();
        } catch (RuntimeException exception) {
            log.warn("Career Agent answer generation fell back: userId={}, intent={}, reason={}",
                    userId, intent.intent(), exception.getMessage());
            return fallback(intent, report.isPresent());
        }
    }

    private List<Map<String, Object>> recentConversation(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId).stream()
                .findFirst()
                .map(conversation -> {
                    List<CareerAgentMessage> messages = new ArrayList<>(messageRepository
                            .findTop12ByUserIdAndConversationIdOrderByCreatedAtDescIdDesc(userId, conversation.getId()));
                    Collections.reverse(messages);
                    return messages.stream().map(message -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("role", message.getRole().name());
                        item.put("messageType", message.getMessageType().name());
                        item.put("content", message.getContent());
                        return item;
                    }).toList();
                })
                .orElse(List.of());
    }

    private Map<String, Object> reportContext(FinalReport report) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reportId", report.getId());
        context.put("taskId", report.getTaskId());
        context.put("version", report.getVersion());
        context.put("createdAt", report.getCreatedAt());
        context.put("reportJson", truncate(writeJson(report.getReportJson()), MAX_REPORT_CONTEXT_CHARS));
        context.entrySet().removeIf(entry -> entry.getValue() == null);
        return context;
    }

    private String fallback(IntentResponse intent, boolean hasReport) {
        if (intent.intent() == CareerIntent.REPORT_QA) {
            if (!hasReport) {
                return "我还没有可引用的历史报告。你可以先让我完成一次简历和岗位分析，报告生成后我就能围绕结论继续追问。";
            }
            return "我已找到历史报告，但当前 AI 回答服务暂时不可用。你可以先在报告页查看原始结论，稍后再继续追问。";
        }
        return "我可以继续帮你拆解这个求职问题。你可以补充目标岗位、面试时间、当前短板或已有简历，我会据此给出更具体的建议。";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize Career Agent answer context", exception);
        }
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }
}
