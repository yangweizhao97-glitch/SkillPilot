package com.huatai.careeragent.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.ResumeRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class InterviewQuestionService {
    private final InterviewQuestionRepository repository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;

    public InterviewQuestionService(InterviewQuestionRepository repository, ResumeRepository resumeRepository,
                                    JobRepository jobRepository) {
        this.repository = repository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public List<InterviewQuestionResponse> save(Long userId, Long resumeId, Long jobId, JsonNode result) {
        List<InterviewQuestion> questions = new ArrayList<>();
        for (JsonNode item : result.path("questions")) {
            questions.add(new InterviewQuestion(
                    userId, resumeId, jobId, item.path("question").asText(),
                    QuestionType.valueOf(item.path("questionType").asText()),
                    QuestionDifficulty.valueOf(item.path("difficulty").asText()),
                    strings(item.path("expectedPoints")), strings(item.path("citations")),
                    item.path("noCitationReason").isNull() ? null : item.path("noCitationReason").asText(null)
            ));
        }
        return repository.saveAll(questions).stream().map(InterviewQuestionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewQuestionResponse> list(Long userId, Long resumeId, Long jobId,
                                                QuestionDifficulty difficulty, QuestionType questionType) {
        requireResources(userId, resumeId, jobId);
        Specification<InterviewQuestion> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (resumeId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("resumeId"), resumeId));
        if (jobId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("jobId"), jobId));
        if (difficulty != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("difficulty"), difficulty));
        if (questionType != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("questionType"), questionType));
        return repository.findAll(spec, org.springframework.data.domain.Sort.by("createdAt").descending()).stream()
                .map(InterviewQuestionResponse::from).toList();
    }

    private void requireResources(Long userId, Long resumeId, Long jobId) {
        if (resumeId != null && resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty())
            throw new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND);
        if (jobId != null && jobRepository.findByIdAndUserId(jobId, userId).isEmpty())
            throw new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND);
    }

    private List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    public record InterviewQuestionResponse(Long questionId, Long resumeId, Long jobId, String question,
                                            QuestionType questionType, QuestionDifficulty difficulty,
                                            List<String> expectedPoints, List<String> citations,
                                            String noCitationReason, Instant createdAt) {
        static InterviewQuestionResponse from(InterviewQuestion question) {
            return new InterviewQuestionResponse(question.getId(), question.getResumeId(), question.getJobId(),
                    question.getQuestionText(), question.getQuestionType(), question.getDifficulty(),
                    question.getExpectedPoints(), question.getCitations(), question.getNoCitationReason(),
                    question.getCreatedAt());
        }
    }
}
