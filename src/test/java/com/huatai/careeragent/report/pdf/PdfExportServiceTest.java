package com.huatai.careeragent.report.pdf;

import com.huatai.careeragent.learning.LearningPlanRepository;
import com.huatai.careeragent.report.FinalReport;
import com.huatai.careeragent.report.FinalReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfExportServiceTest {
    @TempDir Path tempDir;

    @Test
    void exportsWithoutAnUnfinishedLearningPlan() {
        FinalReportRepository reports = mock(FinalReportRepository.class);
        LearningPlanRepository plans = mock(LearningPlanRepository.class);
        PdfReportRenderer renderer = mock(PdfReportRenderer.class);
        PdfExportProperties properties = properties();
        PdfExportService service = new PdfExportService(reports, plans, renderer, properties);
        FinalReport report = report();
        when(reports.findByIdAndUserId(10L, 3L)).thenReturn(Optional.of(report));
        when(plans.findByUserIdAndTaskIdAndGenerationStatus(3L, 9L, "READY"))
                .thenReturn(Optional.empty());
        when(renderer.render(report, Optional.empty())).thenReturn("%PDF-test".getBytes());

        TransactionSynchronizationManager.initSynchronization();
        try {
            PdfExportService.PdfExportResponse response = service.export(3L, 10L);
            assertThat(response.fileName()).isEqualTo("career-report-10-v2.pdf");
            verify(plans).findByUserIdAndTaskIdAndGenerationStatus(3L, 9L, "READY");
            verify(renderer).render(report, Optional.empty());
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void downloadsWithAStableFriendlyFileName() throws Exception {
        FinalReportRepository reports = mock(FinalReportRepository.class);
        PdfExportProperties properties = properties();
        PdfExportService service = new PdfExportService(reports, mock(LearningPlanRepository.class),
                mock(PdfReportRenderer.class), properties);
        FinalReport report = report();
        Path stored = tempDir.resolve("3/random-storage-name.pdf");
        Files.createDirectories(stored.getParent());
        Files.writeString(stored, "%PDF-test");
        when(report.getExportStatus()).thenReturn("EXPORTED");
        when(report.getExportPath()).thenReturn("3/random-storage-name.pdf");
        when(reports.findByIdAndUserId(10L, 3L)).thenReturn(Optional.of(report));

        PdfExportService.PdfDownload download = service.download(3L, 10L);

        assertThat(download.fileName()).isEqualTo("career-report-10-v2.pdf");
        assertThat(download.content()).startsWith("%PDF".getBytes());
    }

    private PdfExportProperties properties() {
        PdfExportProperties properties = new PdfExportProperties();
        properties.setExportDir(tempDir.toString());
        return properties;
    }

    private FinalReport report() {
        FinalReport report = mock(FinalReport.class);
        when(report.getId()).thenReturn(10L);
        when(report.getTaskId()).thenReturn(9L);
        when(report.getVersion()).thenReturn(2);
        return report;
    }
}
