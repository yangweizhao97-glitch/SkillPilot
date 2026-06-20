package com.huatai.careeragent.report;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.report.FinalReportService.FinalReportResponse;
import com.huatai.careeragent.report.FinalReportService.FinalReportSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class FinalReportController {
    private final FinalReportService reportService;

    public FinalReportController(FinalReportService reportService) {
        this.reportService = reportService;
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
        return ApiResponse.ok(reportService.refresh(currentUser.userId(), request.resumeId(), request.jobId()));
    }

    public record RefreshReportRequest(@NotNull Long resumeId, @NotNull Long jobId) { }
}
