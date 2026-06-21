package com.huatai.careeragent.report;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.interview.InterviewQuestion;
import com.huatai.careeragent.interview.InterviewQuestionRepository;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FinalReportService {
    private final FinalReportRepository finalReportRepository;
    private final JobMatchReportRepository jobMatchRepository;
    private final ResumeAnalysisReportRepository resumeAnalysisRepository;
    private final InterviewQuestionRepository questionRepository;
    private final AgentTaskRepository taskRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;

    public FinalReportService(FinalReportRepository finalReportRepository,
                              JobMatchReportRepository jobMatchRepository,
                              ResumeAnalysisReportRepository resumeAnalysisRepository,
                              InterviewQuestionRepository questionRepository,
                              AgentTaskRepository taskRepository, ResumeRepository resumeRepository,
                              JobRepository jobRepository) {
        this.finalReportRepository = finalReportRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
        this.questionRepository = questionRepository;
        this.taskRepository = taskRepository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public FinalReportResponse generateForTask(Long taskId) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Career task not found: " + taskId));
        return generate(task.getUserId(), task.getResumeId(), task.getJobId(), task.getId());
    }

    @Transactional
    public FinalReportResponse refresh(Long userId, Long taskId) {
        AgentTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException("CAREER_TASK_NOT_FOUND", "Career task not found",
                        HttpStatus.NOT_FOUND));
        return generate(userId, task.getResumeId(), task.getJobId(), taskId);
    }

    @Transactional(readOnly = true)
    public List<FinalReportSummary> list(Long userId) {
        return finalReportRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(FinalReportSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public FinalReportResponse get(Long userId, Long reportId) {
        return finalReportRepository.findByIdAndUserId(reportId, userId)
                .map(FinalReportResponse::from)
                .orElseThrow(() -> new BusinessException(
                        "REPORT_NOT_FOUND", "Report not found", HttpStatus.NOT_FOUND
                ));
    }

    private FinalReportResponse generate(Long userId, Long resumeId, Long jobId, Long taskId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND));
        Job job = jobRepository.findLockedByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND));

        if (taskId == null) {
            throw new BusinessException("TASK_ID_REQUIRED", "Final reports must be generated from one career task",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        JobMatchReport match = jobMatchRepository.findByUserIdAndTaskId(userId, taskId).orElse(null);
        ResumeAnalysisReport analysis = resumeAnalysisRepository.findByUserIdAndTaskId(userId, taskId).orElse(null);
        List<InterviewQuestion> questions = questionRepository
                .findByUserIdAndTaskIdOrderByCreatedAtAscIdAsc(userId, taskId);

        Map<String, Object> matchSection = reportSection(match);
        Map<String, Object> analysisSection = reportSection(analysis);
        Map<String, Object> questionSection = questionSection(questions);
        boolean complete = match != null && analysis != null && !questions.isEmpty();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "1.0");
        report.put("status", complete ? "COMPLETE" : "PARTIAL");
        report.put("generatedAt", Instant.now().toString());
        report.put("resume", Map.of("resumeId", resume.getId(), "title", resume.getTitle()));
        report.put("job", resourceJob(job));
        report.put("jobMatch", matchSection);
        report.put("resumeAnalysis", analysisSection);
        report.put("interviewQuestions", questionSection);
        report.put("citations", citations(match, analysis, questions));

        int version = finalReportRepository.maxVersion(resumeId, jobId) + 1;
        return FinalReportResponse.from(finalReportRepository.save(
                new FinalReport(userId, taskId, resumeId, jobId, version, report)
        ));
    }

    private Map<String, Object> resourceJob(Job job) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("jobId", job.getId());
        value.put("company", job.getCompany());
        value.put("position", job.getPosition());
        return value;
    }

    private Map<String, Object> reportSection(JobMatchReport report) {
        return report == null ? missing("Job match result is not available") : Map.of(
                "status", "AVAILABLE", "reportId", report.getId(), "version", report.getVersion(),
                "schemaVersion", report.getSchemaVersion(), "data", report.getResultJson()
        );
    }

    private Map<String, Object> reportSection(ResumeAnalysisReport report) {
        return report == null ? missing("Resume analysis result is not available") : Map.of(
                "status", "AVAILABLE", "reportId", report.getId(), "version", report.getVersion(),
                "schemaVersion", report.getSchemaVersion(), "data", report.getResultJson()
        );
    }

    private Map<String, Object> questionSection(List<InterviewQuestion> questions) {
        if (questions.isEmpty()) return missing("Interview questions are not available");
        List<Map<String, Object>> items = questions.stream().map(question -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", question.getId());
            item.put("question", question.getQuestionText());
            item.put("questionType", question.getQuestionType().name());
            item.put("difficulty", question.getDifficulty().name());
            item.put("expectedPoints", question.getExpectedPoints());
            item.put("citations", question.getCitations());
            item.put("noCitationReason", question.getNoCitationReason());
            return item;
        }).toList();
        return Map.of("status", "AVAILABLE", "count", items.size(), "items", items);
    }

    private Map<String, Object> missing(String reason) {
        return Map.of("status", "MISSING", "reason", reason);
    }

    private List<String> citations(JobMatchReport match, ResumeAnalysisReport analysis,
                                   List<InterviewQuestion> questions) {
        Set<String> values = new LinkedHashSet<>();
        if (match != null) addCitations(match.getResultJson().get("citations"), values);
        if (analysis != null) addCitations(analysis.getResultJson().get("citations"), values);
        questions.forEach(question -> values.addAll(question.getCitations()));
        return new ArrayList<>(values);
    }

    private void addCitations(Object raw, Set<String> target) {
        if (raw instanceof List<?> list) list.forEach(value -> target.add(String.valueOf(value)));
    }

    public record FinalReportSummary(Long reportId, Long taskId, Long resumeId, Long jobId, int version,
                                     String status, String resumeTitle, String company, String position,
                                     Instant createdAt) {
        static FinalReportSummary from(FinalReport report) {
            Map<String, Object> json = report.getReportJson();
            Map<?, ?> resume = (Map<?, ?>) json.get("resume");
            Map<?, ?> job = (Map<?, ?>) json.get("job");
            return new FinalReportSummary(report.getId(), report.getTaskId(), report.getResumeId(), report.getJobId(),
                    report.getVersion(), String.valueOf(json.get("status")), String.valueOf(resume.get("title")),
                    nullable(job.get("company")), String.valueOf(job.get("position")), report.getCreatedAt());
        }
    }

    public record FinalReportResponse(Long reportId, Long taskId, Long resumeId, Long jobId, int version,
                                      Map<String, Object> report, String exportStatus,
                                      Instant createdAt) {
        static FinalReportResponse from(FinalReport report) {
            return new FinalReportResponse(report.getId(), report.getTaskId(), report.getResumeId(), report.getJobId(),
                    report.getVersion(), report.getReportJson(), report.getExportStatus(),
                    report.getCreatedAt());
        }
    }

    private static String nullable(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
