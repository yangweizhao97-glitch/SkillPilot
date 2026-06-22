package com.huatai.careeragent.report.pdf;

import com.huatai.careeragent.learning.LearningPlan;
import com.huatai.careeragent.report.FinalReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PdfReportRenderer {
    private static final float MARGIN = 48;
    private final PdfExportProperties properties;

    public PdfReportRenderer(PdfExportProperties properties) { this.properties = properties; }

    public byte[] render(FinalReport report, Optional<LearningPlan> learningPlan) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream();
             LoadedFont loadedFont = loadFont(document)) {
            PDFont font = loadedFont.font();
            Writer writer = new Writer(document, font);
            Map<String, Object> root = report.getReportJson();
            writer.title("SkillPilot 职业分析报告");
            writer.meta("任务 #" + report.getTaskId() + "  ·  报告 V" + report.getVersion());
            writer.meta(text(map(root.get("job")).get("position")) + "  ·  " + text(map(root.get("job")).get("company")));

            Map<String, Object> match = map(map(root.get("jobMatch")).get("data"));
            writer.section("岗位匹配");
            writer.paragraph("匹配分：" + text(match.get("matchScore")));
            writer.paragraph(text(match.get("summary")));
            writer.list("优势", list(match.get("strengths")));
            writer.list("主要差距", concat(list(match.get("weaknesses")), list(match.get("missingSkills"))));

            Map<String, Object> analysis = map(map(root.get("resumeAnalysis")).get("data"));
            writer.section("简历分析");
            writer.paragraph(text(analysis.get("summary")));
            writer.list("亮点", list(analysis.get("highlights")));
            writer.list("改进建议", concat(list(analysis.get("suggestions")), list(match.get("suggestedResumeChanges"))));
            writer.list("下一步", list(analysis.get("nextActions")));

            writer.section("面试题");
            int number = 1;
            for (Object raw : listObjects(map(root.get("interviewQuestions")).get("items"))) {
                Map<String, Object> question = map(raw);
                writer.paragraph(number++ + ". " + text(question.get("question")));
                writer.list("考察要点", list(question.get("expectedPoints")));
            }

            writer.section("引用来源");
            writer.paragraph(String.join("、", list(root.get("citations"))));
            learningPlan.ifPresent(plan -> {
                writer.majorSectionBreak();
                renderLearningPlan(writer, plan.getResultJson());
            });
            writer.finish();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not render PDF report", exception);
        }
    }

    private void renderLearningPlan(Writer writer, Map<String, Object> plan) {
        boolean sprint = "SPRINT".equals(text(plan.get("planMode"))) || plan.containsKey("dailyPlans");
        writer.section(sprint ? "短期面试冲刺计划" : "个性化学习计划");
        writer.paragraph(text(plan.get("summary")));
        if (sprint) {
            writer.paragraph("目标岗位：" + text(plan.get("targetRole")) + "  ·  面试日期："
                    + text(plan.get("interviewDate")) + "  ·  每天：" + text(plan.get("availableHoursPerDay")) + " 小时");
            for (Object raw : listObjects(plan.get("dailyPlans"))) {
                Map<String, Object> day = map(raw);
                writer.subheading("第 " + text(day.get("day")) + " 天：" + text(day.get("focus")));
                writer.list("行动", list(day.get("actions")));
                writer.list("必练题", list(day.get("questions")));
                writer.list("交付物", list(day.get("deliverables")));
            }
        } else {
            writer.paragraph("目标岗位：" + text(plan.get("targetRole")) + "  ·  周期："
                    + text(plan.get("durationWeeks")) + " 周  ·  每周：" + text(plan.get("weeklyHours")) + " 小时");
        for (Object raw : listObjects(plan.get("phases"))) {
            Map<String, Object> phase = map(raw);
            writer.subheading("第 " + text(phase.get("weekStart")) + "–" + text(phase.get("weekEnd"))
                    + " 周：" + text(phase.get("title")));
            writer.list("目标", list(phase.get("goals")));
            writer.list("行动", list(phase.get("actions")));
            writer.list("交付物", list(phase.get("deliverables")));
        }
        }
        writer.list("专项练习题", list(plan.get("practiceQuestions")));
        writer.list("成功指标", list(plan.get("successMetrics")));
    }

    private LoadedFont loadFont(PDDocument document) throws IOException {
        for (Path candidate : fontCandidates()) {
            if (Files.isRegularFile(candidate)) {
                if (candidate.toString().toLowerCase().endsWith(".ttc")) {
                    TrueTypeCollection collection = null;
                    try {
                        collection = new TrueTypeCollection(candidate.toFile());
                        AtomicReference<TrueTypeFont> selected = new AtomicReference<>();
                        collection.processAllFonts(font -> { if (selected.get() == null) selected.set(font); });
                        if (selected.get() != null) {
                            return new LoadedFont(PDType0Font.load(document, selected.get(), true), collection);
                        }
                    } catch (IOException ignored) {
                        // Try the next configured/system font.
                    }
                    if (collection != null) collection.close();
                } else {
                    try {
                        return new LoadedFont(PDType0Font.load(document, candidate.toFile()), () -> { });
                    } catch (IOException ignored) {
                        // Continue to a CJK outline font when a legacy bitmap font cannot be embedded.
                    }
                }
            }
        }
        throw new IllegalStateException("No CJK PDF font found; configure PDF_FONT_PATH");
    }

    private List<Path> fontCandidates() {
        List<Path> values = new ArrayList<>();
        if (properties.getFontPath() != null && !properties.getFontPath().isBlank()) {
            values.add(Path.of(properties.getFontPath()));
        }
        values.add(Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"));
        values.add(Path.of("/System/Library/Fonts/Supplemental/Songti.ttc"));
        values.add(Path.of("/System/Library/Fonts/STHeiti Medium.ttc"));
        values.add(Path.of("/System/Library/Fonts/Supplemental/NISC18030.ttf"));
        values.add(Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.otf"));
        values.add(Path.of("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"));
        return values;
    }

    private record LoadedFont(PDFont font, AutoCloseable resource) implements AutoCloseable {
        @Override public void close() {
            try { resource.close(); }
            catch (Exception exception) { throw new IllegalStateException("Could not close PDF font", exception); }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }
    private static List<Object> listObjects(Object value) {
        return value instanceof List<?> values ? new ArrayList<>(values) : List.of();
    }
    private static List<String> list(Object value) {
        return value instanceof Collection<?> values ? values.stream().map(PdfReportRenderer::text).filter(v -> !v.isBlank()).toList() : List.of();
    }
    private static List<String> concat(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first); result.addAll(second); return result;
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }

    private static final class Writer {
        private final PDDocument document;
        private final PDFont font;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        Writer(PDDocument document, PDFont font) throws IOException {
            this.document = document; this.font = font; newPage();
        }

        void title(String value) { write(value, 22, 30, color(22, 74, 48)); }
        void meta(String value) { write(value, 9, 14, color(92, 105, 97)); }
        void section(String value) { ensure(52); y -= 14; write(value, 16, 24, color(22, 74, 48)); rule(); y -= 8; }
        void subheading(String value) { ensure(28); write(value, 12, 19, color(31, 42, 35)); }
        void paragraph(String value) { if (!value.isBlank()) write(value, 10.5f, 17, color(50, 60, 54)); }
        void list(String label, List<String> items) {
            if (items.isEmpty()) return;
            subheading(label);
            items.forEach(item -> write("• " + item, 10, 16, color(65, 76, 69)));
        }
        void majorSectionBreak() { if (y < 360) ensure(PDRectangle.A4.getHeight()); }

        void finish() throws IOException {
            stream.close();
            int count = document.getNumberOfPages();
            for (int index = 0; index < count; index++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(index),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.beginText(); footer.setFont(font, 8); footer.setNonStrokingColor(color(110, 120, 114));
                    footer.newLineAtOffset(PDRectangle.A4.getWidth() / 2 - 12, 24);
                    footer.showText((index + 1) + " / " + count); footer.endText();
                }
            }
        }

        private void write(String value, float size, float leading, PDColor color) {
            try {
                for (String line : wrap(value, size, PDRectangle.A4.getWidth() - MARGIN * 2)) {
                    ensure(leading + 4);
                    stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color);
                    stream.newLineAtOffset(MARGIN, y); stream.showText(line); stream.endText(); y -= leading;
                }
            } catch (IOException exception) { throw new IllegalStateException("Could not write PDF content", exception); }
        }

        private List<String> wrap(String value, float size, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String paragraph : value.replace('\r', ' ').split("\\n", -1)) {
                StringBuilder line = new StringBuilder();
                for (int offset = 0; offset < paragraph.length();) {
                    int point = paragraph.codePointAt(offset); String next = new String(Character.toChars(point));
                    String candidate = line + next;
                    if (!line.isEmpty() && font.getStringWidth(candidate) / 1000 * size > width) {
                        lines.add(line.toString()); line.setLength(0);
                    }
                    line.append(next); offset += Character.charCount(point);
                }
                if (!line.isEmpty()) lines.add(line.toString());
            }
            return lines;
        }

        private void ensure(float needed) {
            if (y - needed >= 48) return;
            try { newPage(); }
            catch (IOException exception) { throw new IllegalStateException("Could not add PDF page", exception); }
        }
        private void newPage() throws IOException {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4); document.addPage(page);
            stream = new PDPageContentStream(document, page); y = PDRectangle.A4.getHeight() - MARGIN;
        }
        private void rule() {
            try { stream.setStrokingColor(color(197, 211, 202)); stream.moveTo(MARGIN, y + 6); stream.lineTo(PDRectangle.A4.getWidth() - MARGIN, y + 6); stream.stroke(); }
            catch (IOException exception) { throw new IllegalStateException("Could not draw PDF rule", exception); }
        }
        private static PDColor color(int r, int g, int b) { return new PDColor(new float[]{r / 255f, g / 255f, b / 255f}, PDDeviceRGB.INSTANCE); }
    }
}
