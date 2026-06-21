package com.huatai.careeragent.report;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.report.FinalReportService.FinalReportResponse;
import com.huatai.careeragent.report.FinalReportService.FinalReportSummary;
import com.huatai.careeragent.report.pdf.PdfExportService;
import com.huatai.careeragent.report.pdf.PdfExportService.PdfDownload;
import com.huatai.careeragent.report.pdf.PdfExportService.PdfExportResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class FinalReportController {
    private final FinalReportService reportService;
    private final PdfExportService pdfExportService;

    public FinalReportController(FinalReportService reportService, PdfExportService pdfExportService) {
        this.reportService = reportService;
        this.pdfExportService = pdfExportService;
    }

    @GetMapping
    public ApiResponse<List<FinalReportSummary>> list(CurrentUser currentUser) {
        return ApiResponse.ok(reportService.list(currentUser.userId()));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<FinalReportResponse> get(CurrentUser currentUser, @PathVariable Long reportId) {
        return ApiResponse.ok(reportService.get(currentUser.userId(), reportId));
    }

    @PostMapping("/refresh")
    public ApiResponse<FinalReportResponse> refresh(CurrentUser currentUser,
                                                     @Valid @RequestBody RefreshReportRequest request) {
        return ApiResponse.ok(reportService.refresh(currentUser.userId(), request.taskId()));
    }

    @PostMapping("/{reportId}/pdf")
    public ApiResponse<PdfExportResponse> exportPdf(CurrentUser currentUser, @PathVariable Long reportId) {
        return ApiResponse.ok(pdfExportService.export(currentUser.userId(), reportId));
    }

    @GetMapping("/{reportId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(CurrentUser currentUser, @PathVariable Long reportId) {
        PdfDownload download = pdfExportService.download(currentUser.userId(), reportId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(download.content().length)
                .body(download.content());
    }

    public record RefreshReportRequest(@NotNull Long taskId) { }
}
