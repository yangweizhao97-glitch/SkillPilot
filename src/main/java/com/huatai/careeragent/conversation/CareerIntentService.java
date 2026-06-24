package com.huatai.careeragent.conversation;

import com.huatai.careeragent.conversation.CareerAgentDtos.AgentNextAction;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerIntent;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.RequiredResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CareerIntentService {

    public IntentResponse classify(IntentRequest request) {
        String message = request.message().trim();
        IntentDefinition definition = infer(message);
        List<RequiredResource> missingResources = missing(definition.requiredResources(), request);

        return new IntentResponse(
                definition.intent(),
                definition.label(),
                definition.summary(),
                definition.confidence(),
                definition.needsWorkflow(),
                definition.requiredResources(),
                missingResources,
                nextAction(definition, missingResources),
                reason(definition, missingResources)
        );
    }

    private IntentDefinition infer(String message) {
        String lower = message.toLowerCase(Locale.ROOT);

        if (wantsGeneralAdvice(message, lower)) {
            return new IntentDefinition(
                    CareerIntent.GENERAL_CAREER_QA,
                    "通用求职问答",
                    "先直接回答用户的通用求职问题，不强制要求补充资料。",
                    0.78,
                    false,
                    List.of()
            );
        }
        if (matches(message, "学习计划", "学习路径", "提升计划", "备考计划")
                || message.matches(".*补.*能力.*")) {
            return new IntentDefinition(
                    CareerIntent.LEARNING_PLAN,
                    "学习计划",
                    "基于报告或目标拆解下一步学习安排。",
                    0.86,
                    false,
                    List.of(RequiredResource.REPORT)
            );
        }
        if (matches(message, "模拟面试", "面试官", "开始面试")
                || lower.contains("mock interview")
                || (message.contains("面试") && matches(message, "练", "模拟", "追问", "回答"))) {
            return new IntentDefinition(
                    CareerIntent.MOCK_INTERVIEW,
                    "模拟面试",
                    "进入多轮面试练习与回答反馈。",
                    0.84,
                    false,
                    List.of(RequiredResource.RESUME, RequiredResource.JOB)
            );
        }
        if (matches(message, "报告", "结果", "分析结论", "之前")
                && matches(message, "问", "解释", "怎么看", "总结", "继续")) {
            return new IntentDefinition(
                    CareerIntent.REPORT_QA,
                    "报告问答",
                    "围绕已有报告继续追问。",
                    0.82,
                    false,
                    List.of(RequiredResource.REPORT)
            );
        }
        if (matches(message, "面试题", "面经", "八股", "准备面试")
                || lower.contains("interview")) {
            return new IntentDefinition(
                    CareerIntent.INTERVIEW_PREP,
                    "面试准备",
                    "生成针对岗位和简历的面试准备材料。",
                    0.84,
                    true,
                    List.of(RequiredResource.RESUME, RequiredResource.JOB)
            );
        }
        if (matches(message, "匹配", "适合", "胜任", "岗位", "职位")
                || lower.contains("jd")
                || lower.contains("job")) {
            return new IntentDefinition(
                    CareerIntent.JOB_MATCH,
                    "岗位匹配",
                    "判断简历和目标岗位的匹配度。",
                    0.83,
                    true,
                    List.of(RequiredResource.RESUME, RequiredResource.JOB)
            );
        }
        if (matches(message, "简历", "优化", "润色", "修改")
                || lower.contains("resume")) {
            return new IntentDefinition(
                    CareerIntent.RESUME_REVIEW,
                    "简历优化",
                    "分析简历亮点、缺口和修改建议。",
                    0.80,
                    true,
                    List.of(RequiredResource.RESUME)
            );
        }
        if (matches(message, "分析", "准备", "求职", "职业", "能力")) {
            return new IntentDefinition(
                    CareerIntent.CAREER_ANALYSIS,
                    "完整职业分析",
                    "串联岗位匹配、简历分析、面试题和最终报告。",
                    0.76,
                    true,
                    List.of(RequiredResource.RESUME, RequiredResource.JOB)
            );
        }

        return new IntentDefinition(
                CareerIntent.GENERAL_CAREER_QA,
                "通用求职问答",
                "先理解问题，再决定是否需要调用工作流。",
                0.55,
                false,
                List.of()
        );
    }

    private static boolean matches(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wantsGeneralAdvice(String message, String lower) {
        boolean asksForAdvice = matches(message, "通用建议", "一般怎么", "怎么准备", "如何准备", "先给我建议",
                "先给我通用", "通用", "泛泛", "不结合简历", "不用结合简历", "不需要简历", "先不看简历")
                || lower.contains("general advice");
        boolean asksForConcreteArtifact = matches(message, "生成报告", "完整报告", "匹配度", "岗位匹配", "分析这份",
                "这份简历", "这份JD", "这份 jd", "模拟面试", "开始面试", "生成面试题");
        return asksForAdvice && !asksForConcreteArtifact;
    }

    private static List<RequiredResource> missing(List<RequiredResource> requiredResources, IntentRequest request) {
        List<RequiredResource> missing = new ArrayList<>();
        for (RequiredResource resource : requiredResources) {
            if (resource == RequiredResource.RESUME && !request.hasResume()) {
                missing.add(resource);
            } else if (resource == RequiredResource.JOB && !request.hasJob()) {
                missing.add(resource);
            } else if (resource == RequiredResource.REPORT && !request.hasReport()) {
                missing.add(resource);
            }
        }
        return List.copyOf(missing);
    }

    private static AgentNextAction nextAction(IntentDefinition definition, List<RequiredResource> missingResources) {
        if (!missingResources.isEmpty()) {
            return AgentNextAction.ASK_USER;
        }
        return switch (definition.intent()) {
            case CAREER_ANALYSIS, RESUME_REVIEW, JOB_MATCH, INTERVIEW_PREP -> AgentNextAction.START_WORKFLOW;
            case MOCK_INTERVIEW -> AgentNextAction.START_MOCK_INTERVIEW;
            case LEARNING_PLAN -> AgentNextAction.GENERATE_LEARNING_PLAN;
            case REPORT_QA -> AgentNextAction.GET_REPORT;
            case GENERAL_CAREER_QA -> AgentNextAction.ANSWER_DIRECTLY;
        };
    }

    private static String reason(IntentDefinition definition, List<RequiredResource> missingResources) {
        if (missingResources.isEmpty()) {
            return "意图已识别，所需上下文已满足。";
        }
        return "意图已识别，但还需要补齐资源：" + missingResources;
    }

    private record IntentDefinition(
            CareerIntent intent,
            String label,
            String summary,
            double confidence,
            boolean needsWorkflow,
            List<RequiredResource> requiredResources
    ) {
    }
}
