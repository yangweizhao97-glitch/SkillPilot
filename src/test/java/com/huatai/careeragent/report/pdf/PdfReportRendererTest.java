package com.huatai.careeragent.report.pdf;

import com.huatai.careeragent.learning.LearningPlan;
import com.huatai.careeragent.report.FinalReport;
import org.apache.pdfbox.Loader;
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
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("职业分析报告", "岗位匹配", "简历分析", "面试题", "个性化学习计划", "Kafka");
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
        Path fixture = tempDir.resolve("validation-report.pdf");
        Files.write(fixture, bytes);
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
                "question", "如何处理 Kafka 重复消费？", "expectedPoints", List.of("幂等键", "状态持久化")))));
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
        value.put("successMetrics", List.of("十分钟内讲清故障根因和验证结果"));
        return new LearningPlan(1L, 10L, 1L, value);
    }
}
