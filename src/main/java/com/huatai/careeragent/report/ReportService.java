package com.huatai.careeragent.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final JobMatchReportRepository jobMatchRepository;
    private final ResumeAnalysisReportRepository resumeAnalysisRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public ReportService(JobMatchReportRepository jobMatchRepository, ResumeAnalysisReportRepository resumeAnalysisRepository,
                         ResumeRepository resumeRepository, JobRepository jobRepository, ObjectMapper objectMapper) {
        this.jobMatchRepository = jobMatchRepository;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobMatchReportResponse saveJobMatch(Long userId, Long taskId, Long resumeId, Long jobId, JsonNode result) {
        jobRepository.findLockedByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND));
        if (taskId != null) {
            jobMatchRepository.deleteByUserIdAndTaskId(userId, taskId);
            jobMatchRepository.flush();
        }
        int version = jobMatchRepository.maxVersion(resumeId, jobId) + 1;
        return JobMatchReportResponse.from(jobMatchRepository.save(
                new JobMatchReport(userId, taskId, resumeId, jobId, version, toMap(result))
        ));
    }

    @Transactional
    public ResumeAnalysisReportResponse saveResumeAnalysis(Long userId, Long taskId, Long resumeId, JsonNode result) {
        resumeRepository.findLockedByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND));
        if (taskId != null) {
            resumeAnalysisRepository.deleteByUserIdAndTaskId(userId, taskId);
            resumeAnalysisRepository.flush();
        }
        int version = resumeAnalysisRepository.maxVersion(resumeId) + 1;
        return ResumeAnalysisReportResponse.from(resumeAnalysisRepository.save(
                new ResumeAnalysisReport(userId, taskId, resumeId, version, toMap(result))
        ));
    }

    @Transactional(readOnly = true)
    public List<JobMatchReportResponse> listJobMatches(Long userId, Long jobId) {
        requireJob(userId, jobId);
        return jobMatchRepository.findByUserIdAndJobIdOrderByVersionDesc(userId, jobId).stream()
                .map(JobMatchReportResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ResumeAnalysisReportResponse> listResumeAnalyses(Long userId, Long resumeId) {
        requireResume(userId, resumeId);
        return resumeAnalysisRepository.findByUserIdAndResumeIdOrderByVersionDesc(userId, resumeId).stream()
                .map(ResumeAnalysisReportResponse::from).toList();
    }

    private void requireResume(Long userId, Long resumeId) {
        if (resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty()) {
            throw new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND);
        }
    }

    private void requireJob(Long userId, Long jobId) {
        if (jobRepository.findByIdAndUserId(jobId, userId).isEmpty()) {
            throw new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND);
        }
    }

    private Map<String, Object> toMap(JsonNode result) {
        return objectMapper.convertValue(result, new TypeReference<>() { });
    }

    public record JobMatchReportResponse(Long reportId, Long resumeId, Long jobId, int version,
                                         Map<String, Object> result, String schemaVersion, Instant createdAt) {
        static JobMatchReportResponse from(JobMatchReport report) {
            return new JobMatchReportResponse(report.getId(), report.getResumeId(), report.getJobId(), report.getVersion(),
                    report.getResultJson(), report.getSchemaVersion(), report.getCreatedAt());
        }
    }

    public record ResumeAnalysisReportResponse(Long reportId, Long resumeId, int version,
                                                Map<String, Object> result, String schemaVersion, Instant createdAt) {
        static ResumeAnalysisReportResponse from(ResumeAnalysisReport report) {
            return new ResumeAnalysisReportResponse(report.getId(), report.getResumeId(), report.getVersion(),
                    report.getResultJson(), report.getSchemaVersion(), report.getCreatedAt());
        }
    }
}
