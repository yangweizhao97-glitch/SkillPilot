package com.huatai.careeragent.interview;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class InterviewMemoryService {
    private static final int MAX_ITEMS = 6;
    private static final int MAX_ITEM_LENGTH = 180;

    private final InterviewSessionMemoryRepository repository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;

    public InterviewMemoryService(InterviewSessionMemoryRepository repository,
                                  ResumeRepository resumeRepository, JobRepository jobRepository) {
        this.repository = repository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public void record(InterviewSession session, InterviewQuestion question,
                       int score, Map<String, Object> evaluation) {
        InterviewSessionMemory memory = repository.findBySessionIdAndUserId(session.getId(), session.getUserId())
                .orElse(null);
        Map<String, Object> previous = memory == null ? Map.of() : memory.getMemoryJson();
        int answerCount = number(previous.get("answerCount"));
        int scoreTotal = number(previous.get("scoreTotal"));
        List<String> strengths = merge(strings(previous.get("strengths")), strings(evaluation.get("strengths")));
        List<String> improvements = merge(
                strings(previous.get("improvementAreas")), strings(evaluation.get("improvements"))
        );
        List<String> topics = merge(strings(previous.get("topics")), List.of(question.getQuestionType().name()));
        int nextCount = answerCount + 1;
        int nextTotal = scoreTotal + score;
        int average = Math.round((float) nextTotal / nextCount);
        Map<String, Object> snapshot = Map.of(
                "answerCount", nextCount,
                "scoreTotal", nextTotal,
                "averageScore", average,
                "strengths", strengths,
                "improvementAreas", improvements,
                "topics", topics,
                "summary", summary(nextCount, average, strengths, improvements)
        );
        if (memory == null) {
            repository.save(new InterviewSessionMemory(
                    session.getUserId(), session.getId(), session.getResumeId(), session.getJobId(), snapshot
            ));
        } else {
            memory.update(snapshot);
        }
    }

    @Transactional(readOnly = true)
    public MemorySnapshot get(Long userId, Long resumeId, Long jobId) {
        requireResources(userId, resumeId, jobId);
        List<InterviewSessionMemory> memories = repository
                .findTop3ByUserIdAndResumeIdAndJobIdOrderByUpdatedAtDescIdDesc(userId, resumeId, jobId);
        int answerCount = 0;
        int scoreTotal = 0;
        List<String> strengths = List.of();
        List<String> improvements = List.of();
        List<String> topics = List.of();
        int maxRevision = 0;
        for (InterviewSessionMemory memory : memories.reversed()) {
            Map<String, Object> value = memory.getMemoryJson();
            answerCount += number(value.get("answerCount"));
            scoreTotal += number(value.get("scoreTotal"));
            strengths = merge(strengths, strings(value.get("strengths")));
            improvements = merge(improvements, strings(value.get("improvementAreas")));
            topics = merge(topics, strings(value.get("topics")));
            maxRevision = Math.max(maxRevision, memory.getRevision());
        }
        int average = answerCount == 0 ? 0 : Math.round((float) scoreTotal / answerCount);
        return new MemorySnapshot(!memories.isEmpty(), memories.size(), answerCount, average,
                strengths, improvements, topics, summary(answerCount, average, strengths, improvements), maxRevision);
    }

    @Transactional
    public void clear(Long userId, Long resumeId, Long jobId) {
        requireResources(userId, resumeId, jobId);
        repository.deleteByUserIdAndResumeIdAndJobId(userId, resumeId, jobId);
    }

    private void requireResources(Long userId, Long resumeId, Long jobId) {
        if (resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty()) {
            throw new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND);
        }
        if (jobRepository.findByIdAndUserId(jobId, userId).isEmpty()) {
            throw new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND);
        }
    }

    private List<String> merge(List<String> existing, List<String> added) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        existing.forEach(value -> add(values, value));
        added.forEach(value -> add(values, value));
        List<String> result = new ArrayList<>(values);
        return List.copyOf(result.subList(Math.max(0, result.size() - MAX_ITEMS), result.size()));
    }

    private void add(LinkedHashSet<String> target, String value) {
        if (value == null || value.isBlank()) return;
        String normalized = value.trim();
        target.add(normalized.length() <= MAX_ITEM_LENGTH ? normalized : normalized.substring(0, MAX_ITEM_LENGTH));
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String summary(int count, int average, List<String> strengths, List<String> improvements) {
        if (count == 0) return "暂无可用的历史面试记忆";
        String strength = strengths.isEmpty() ? "尚未形成稳定优势" : strengths.getLast();
        String improvement = improvements.isEmpty() ? "继续补充具体证据" : improvements.getLast();
        return "已评估%d次回答，平均分%d；近期优势：%s；优先改进：%s".formatted(
                count, average, strength, improvement
        );
    }

    public record MemorySnapshot(boolean available, int sessionCount, int answerCount, int averageScore,
                                 List<String> strengths, List<String> improvementAreas,
                                 List<String> topics, String summary, int revision) {
        public Map<String, Object> promptContext() {
            return Map.of(
                    "answerCount", answerCount,
                    "averageScore", averageScore,
                    "strengths", strengths,
                    "improvementAreas", improvementAreas,
                    "topics", topics,
                    "summary", summary
            );
        }
    }
}
