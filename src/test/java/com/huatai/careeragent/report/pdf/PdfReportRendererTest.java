package com.huatai.careeragent.report.pdf;

import com.huatai.careeragent.learning.LearningPlan;
import com.huatai.careeragent.report.FinalReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportRendererTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersReadableMultiSectionChineseReport() throws Exception {
        PdfExportProperties properties = new PdfExportProperties();
        PdfReportRenderer renderer = new PdfReportRenderer(properties);
        byte[] bytes = renderer.render(report(), Optional.of(plan()));

        assertThat(bytes).startsWith("%PDF".getBytes());
        try (var document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("职业分析报告", "岗位匹配", "简历分析", "面试题", "参考答案",
                    "岗位可能追问与参考答案", "个性化学习计划", "Kafka");
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
        Path fixture = tempDir.resolve("validation-report.pdf");
        Files.write(fixture, bytes);
    }

    @Test
    void rendersSprintPlanByDay() throws Exception {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("planMode", "SPRINT"); value.put("summary", "三天完成核心问题冲刺。");
        value.put("targetRole", "Java 后端工程师"); value.put("interviewDate", "2026-06-25");
        value.put("availableHoursPerDay", 2);
        value.put("dailyPlans", List.of(Map.of("day", 1, "focus", "事务传播",
                "actions", List.of("复盘项目事务边界"), "questions", List.of("REQUIRES_NEW 如何工作？"),
                "deliverables", List.of("五分钟口述稿"))));
        value.put("practiceQuestions", List.of("如何处理事务失效？"));
        value.put("likelyInterviewQuestions", likelyQuestions());
        value.put("successMetrics", List.of("能讲清事务边界"));

        byte[] bytes = new PdfReportRenderer(new PdfExportProperties()).render(
                report(), Optional.of(new LearningPlan(1L, 10L, 1L, value)));
        try (var document = PDDocument.load(bytes)) {
            assertThat(new PDFTextStripper().getText(document))
                    .contains("短期面试冲刺计划", "第 1 天", "事务传播");
        }
    }

    private FinalReport report() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("status", "COMPLETE");
        root.put("job", Map.of("position", "Java 后端工程师", "company", "示例科技"));
        root.put("jobMatch", Map.of("data", Map.of(
                "matchScore", 78, "summary", "基础能力匹配，需补齐消息系统生产经验。",
                "strengths", List.of("Spring Boot 项目经验"), "weaknesses", List.of("量化证据不足"),
                "missingSkills", List.of("Kafka 故障治理"), "suggestedResumeChanges", List.of("补充吞吐量指标"))));
        root.put("resumeAnalysis", Map.of("data", Map.of(
                "summary", "技术路径清晰。", "highlights", List.of("事务设计经验"),
                "suggestions", List.of("使用 STAR 结构"), "nextActions", List.of("重写核心项目"))));
        root.put("interviewQuestions", Map.of("items", List.of(Map.of(
                "question", "如何处理 Kafka 重复消费？",
                "expectedPoints", List.of("幂等键", "状态持久化"),
                "answerOutline", List.of("先说明重复原因", "再说明幂等键和状态机"),
                "referenceAnswer", "我会用业务幂等键和消费状态表保证重复消息不会产生重复业务效果。",
                "scoringRubric", List.of(Map.of("criterion", "幂等设计", "weight", 50),
                        Map.of("criterion", "并发与持久化", "weight", 50)),
                "commonMistakes", List.of("只依赖内存去重"),
                "followUpCandidates", List.of("唯一约束冲突时如何返回一致结果？")))));
        root.put("citations", List.of("resume_1", "job_2"));
        return new FinalReport(1L, 10L, 1L, 2L, 1, root);
    }

    private LearningPlan plan() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("summary", "八周内形成可验证的 Kafka 稳定性项目证据。");
        value.put("targetRole", "Java 后端工程师"); value.put("durationWeeks", 8); value.put("weeklyHours", 8);
        value.put("phases", List.of(Map.of("weekStart", 1, "weekEnd", 2, "title", "故障复现",
                "goals", List.of("掌握投递语义"), "actions", List.of("完成故障注入实验"),
                "deliverables", List.of("故障复盘文档"))));
        value.put("likelyInterviewQuestions", likelyQuestions());
        value.put("successMetrics", List.of("十分钟内讲清故障根因和验证结果"));
        return new LearningPlan(1L, 10L, 1L, value);
    }

    private List<Map<String, Object>> likelyQuestions() {
        return List.of(Map.of(
                "question", "如何定位 Kafka 重复消费？",
                "whyAsked", "岗位要求消息链路稳定性，报告指出 Kafka 故障治理证据不足。",
                "knowledgePoints", List.of("消费语义", "幂等设计"),
                "answerStrategy", List.of("先说明重复消费来源", "再结合项目讲幂等键和状态持久化"),
                "referenceAnswer", "我会先确认重复来自重平衡、超时重试还是业务异常，再用幂等键和状态表保证结果一致。",
                "practiceTasks", List.of("准备一段三分钟故障复盘口述稿"),
                "sourceMaterials", List.of("最终报告", "公共面经")
        ));
    }
}
