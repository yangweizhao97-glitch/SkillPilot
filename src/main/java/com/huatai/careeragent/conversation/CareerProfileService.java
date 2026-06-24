package com.huatai.careeragent.conversation;

import com.huatai.careeragent.conversation.CareerAgentDtos.CareerIntent;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerProfileResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CareerProfileService {
    private final CareerProfileRepository repository;

    public CareerProfileService(CareerProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CareerProfileResponse observe(Long userId, String message, IntentResponse intent) {
        CareerProfile profile = repository.findByUserId(userId).orElseGet(() -> new CareerProfile(userId));
        profile.merge(targetRoles(message), careerStages(message), weaknessTags(message), preferenceTags(message));
        CareerProfile saved = repository.save(profile);
        return response(saved, intent);
    }

    @Transactional(readOnly = true)
    public CareerProfileResponse get(Long userId) {
        return repository.findByUserId(userId)
                .map(profile -> response(profile, null))
                .orElseGet(() -> new CareerProfileResponse(List.of(), List.of(), List.of(), List.of(), "", defaultPrompts()));
    }

    private static CareerProfileResponse response(CareerProfile profile, IntentResponse intent) {
        List<String> prompts = suggestedPrompts(profile, intent);
        return new CareerProfileResponse(
                profile.getTargetRoles(),
                profile.getCareerStages(),
                profile.getWeaknessTags(),
                profile.getPreferenceTags(),
                profile.getSummary(),
                prompts
        );
    }

    private static List<String> targetRoles(String message) {
        List<String> roles = new ArrayList<>();
        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "java", "spring", "后端", "backend")) roles.add("Java 后端");
        if (containsAny(lower, "前端", "frontend", "react", "vue")) roles.add("前端工程师");
        if (containsAny(lower, "算法", "机器学习", "ai", "算法岗")) roles.add("算法工程师");
        if (containsAny(lower, "数据分析", "数据岗", "bi")) roles.add("数据分析");
        if (containsAny(lower, "测试", "qa")) roles.add("测试工程师");
        if (containsAny(lower, "产品经理", "产品岗")) roles.add("产品经理");
        if (containsAny(lower, "外企", "英文")) roles.add("外企岗位");
        return roles;
    }

    private static List<String> careerStages(String message) {
        List<String> stages = new ArrayList<>();
        if (containsAny(message, "秋招")) stages.add("秋招");
        if (containsAny(message, "春招")) stages.add("春招");
        if (containsAny(message, "实习", "暑期")) stages.add("实习");
        if (containsAny(message, "社招", "跳槽")) stages.add("社招");
        if (containsAny(message, "校招", "应届")) stages.add("校招");
        return stages;
    }

    private static List<String> weaknessTags(String message) {
        List<String> tags = new ArrayList<>();
        if (containsAny(message, "项目表达", "项目经历", "讲项目")) tags.add("项目表达");
        if (containsAny(message, "算法", "leetcode", "力扣")) tags.add("算法");
        if (containsAny(message, "八股", "基础知识")) tags.add("八股");
        if (containsAny(message, "系统设计", "架构")) tags.add("系统设计");
        if (containsAny(message, "英文面试", "英文", "英语")) tags.add("英文面试");
        if (containsAny(message, "并发", "多线程")) tags.add("并发");
        if (containsAny(message, "数据库", "mysql", "sql")) tags.add("数据库");
        if (containsAny(message, "简历", "润色", "优化")) tags.add("简历表达");
        return tags;
    }

    private static List<String> preferenceTags(String message) {
        List<String> tags = new ArrayList<>();
        if (containsAny(message, "五道题", "5 道题", "5道题", "题目")) tags.add("偏好题目练习");
        if (containsAny(message, "计划", "学习路径", "安排")) tags.add("偏好学习计划");
        if (containsAny(message, "模拟面试", "追问")) tags.add("偏好模拟面试");
        return tags;
    }

    private static List<String> suggestedPrompts(CareerProfile profile, IntentResponse intent) {
        List<String> prompts = new ArrayList<>();
        if (!profile.getWeaknessTags().isEmpty()) {
            prompts.add("针对" + profile.getWeaknessTags().getFirst() + "出 5 道练习题");
        }
        if (!profile.getTargetRoles().isEmpty()) {
            prompts.add("按" + profile.getTargetRoles().getFirst() + "岗位重新评估匹配度");
        }
        if (intent != null && intent.intent() == CareerIntent.REPORT_QA) {
            prompts.add("基于这份报告生成学习计划");
        }
        if (intent != null && intent.intent() == CareerIntent.INTERVIEW_PREP) {
            prompts.add("开始一轮针对当前岗位的模拟面试");
        }
        prompts.addAll(defaultPrompts());
        return prompts.stream().distinct().limit(4).toList();
    }

    private static List<String> defaultPrompts() {
        return List.of("分析简历和岗位匹配度", "生成面试准备报告", "帮我优化这份简历", "基于报告生成学习计划");
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }
}
