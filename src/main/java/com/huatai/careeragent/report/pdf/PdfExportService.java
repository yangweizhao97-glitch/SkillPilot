package com.huatai.careeragent.report.pdf;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.learning.LearningPlanRepository;
import com.huatai.careeragent.report.FinalReport;
import com.huatai.careeragent.report.FinalReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class PdfExportService {
    private final FinalReportRepository reportRepository;
    private final LearningPlanRepository learningPlanRepository;
    private final PdfReportRenderer renderer;
    private final PdfExportProperties properties;

    public PdfExportService(FinalReportRepository reportRepository, LearningPlanRepository learningPlanRepository,
                            PdfReportRenderer renderer, PdfExportProperties properties) {
        this.reportRepository = reportRepository;
        this.learningPlanRepository = learningPlanRepository;
        this.renderer = renderer;
        this.properties = properties;
    }

    @Transactional
    public PdfExportResponse export(Long userId, Long reportId) {
        FinalReport report = requireReport(userId, reportId);
        byte[] bytes = renderer.render(report,
                learningPlanRepository.findByUserIdAndTaskId(userId, report.getTaskId()));
        String fileName = "career-report-" + report.getId() + "-v" + report.getVersion() + ".pdf";
        Path root = exportRoot();
        Path relative = Path.of(String.valueOf(userId), fileName);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IllegalStateException("Invalid PDF export path");
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), fileName, ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new BusinessException("PDF_EXPORT_FAILED", "Could not store PDF report", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        report.markExported(relative.toString());
        reportRepository.save(report);
        return new PdfExportResponse(report.getId(), fileName, bytes.length, "EXPORTED");
    }

    @Transactional(readOnly = true)
    public PdfDownload download(Long userId, Long reportId) {
        FinalReport report = requireReport(userId, reportId);
        if (!"EXPORTED".equals(report.getExportStatus()) || report.getExportPath() == null) {
            throw new BusinessException("PDF_NOT_EXPORTED", "PDF report has not been exported", HttpStatus.NOT_FOUND);
        }
        Path root = exportRoot();
        Path path = root.resolve(report.getExportPath()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new BusinessException("PDF_NOT_FOUND", "PDF report file not found", HttpStatus.NOT_FOUND);
        }
        try {
            return new PdfDownload(path.getFileName().toString(), Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new BusinessException("PDF_READ_FAILED", "Could not read PDF report", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private FinalReport requireReport(Long userId, Long reportId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new BusinessException("REPORT_NOT_FOUND", "Report not found", HttpStatus.NOT_FOUND));
    }

    private Path exportRoot() { return Path.of(properties.getExportDir()).toAbsolutePath().normalize(); }

    public record PdfExportResponse(Long reportId, String fileName, long sizeBytes, String status) { }
    public record PdfDownload(String fileName, byte[] content) { }
}
