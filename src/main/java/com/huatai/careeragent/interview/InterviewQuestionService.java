package com.huatai.careeragent.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

@Service
public class InterviewQuestionService {
    private final InterviewQuestionRepository repository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public InterviewQuestionService(InterviewQuestionRepository repository, ResumeRepository resumeRepository,
                                    JobRepository jobRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<InterviewQuestionResponse> save(Long userId, Long resumeId, Long jobId, Long taskId, JsonNode result) {
        List<InterviewQuestion> questions = new ArrayList<>();
        for (JsonNode item : result.path("questions")) {
            List<String> expectedPoints = strings(item.path("expectedPoints"));
            questions.add(new InterviewQuestion(
                    userId, resumeId, jobId, taskId, item.path("question").asText(),
                    QuestionType.valueOf(item.path("questionType").asText()),
                    QuestionDifficulty.valueOf(item.path("difficulty").asText()),
                    expectedPoints, strings(item.path("citations")),
                    item.path("noCitationReason").isNull() ? null : item.path("noCitationReason").asText(null),
                    strings(item.path("answerOutline")), item.path("referenceAnswer").asText(null),
                    rubric(item.path("scoringRubric"), expectedPoints), strings(item.path("commonMistakes")),
                    strings(item.path("followUpCandidates"))
            ));
        }
        return repository.saveAll(questions).stream().map(InterviewQuestionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InterviewQuestionAnswerResponse answer(Long userId, Long questionId) {
        InterviewQuestion question = repository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new BusinessException("INTERVIEW_QUESTION_NOT_FOUND",
                        "面试题不存在", HttpStatus.NOT_FOUND));
        List<String> outline = question.getAnswerOutline().isEmpty()
                ? question.getExpectedPoints() : question.getAnswerOutline();
        String referenceAnswer = question.getReferenceAnswer();
        if (referenceAnswer == null || referenceAnswer.isBlank()) {
            referenceAnswer = "建议围绕以下要点组织回答：" + String.join("、", outline) + "。";
        }
        List<Map<String, Object>> rubric = question.getScoringRubric().isEmpty()
                ? defaultRubric(question.getExpectedPoints()) : question.getScoringRubric();
        return new InterviewQuestionAnswerResponse(question.getId(), outline, referenceAnswer, rubric,
                question.getCommonMistakes(), question.getFollowUpCandidates(), question.getCitations());
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

    private List<Map<String, Object>> maps(JsonNode array) {
        if (!array.isArray()) return List.of();
        return objectMapper.convertValue(array, new TypeReference<>() { });
    }

    private List<Map<String, Object>> rubric(JsonNode value, List<String> expectedPoints) {
        List<Map<String, Object>> rubric = maps(value);
        int total = rubric.stream().map(item -> item.get("weight")).mapToInt(weight -> weight instanceof Number number
                ? number.intValue() : 0).sum();
        boolean valid = !rubric.isEmpty() && total == 100 && rubric.stream().allMatch(item ->
                item.get("criterion") instanceof String criterion && !criterion.isBlank()
                        && item.get("weight") instanceof Number number && number.intValue() > 0);
        return valid ? rubric : defaultRubric(expectedPoints);
    }

    private List<Map<String, Object>> defaultRubric(List<String> expectedPoints) {
        if (expectedPoints.isEmpty()) return List.of();
        int base = 100 / expectedPoints.size();
        int remainder = 100 - base * expectedPoints.size();
        List<Map<String, Object>> rubric = new ArrayList<>();
        for (int index = 0; index < expectedPoints.size(); index++) {
            rubric.add(Map.of("criterion", expectedPoints.get(index),
                    "weight", base + (index == 0 ? remainder : 0)));
        }
        return List.copyOf(rubric);
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

    public record InterviewQuestionAnswerResponse(Long questionId, List<String> answerOutline,
                                                  String referenceAnswer,
                                                  List<Map<String, Object>> scoringRubric,
                                                  List<String> commonMistakes,
                                                  List<String> followUpCandidates,
                                                  List<String> citations) { }
}
