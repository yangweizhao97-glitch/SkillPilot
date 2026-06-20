package com.huatai.careeragent.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.schema.SchemaRepairService;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.resume.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class InteractiveInterviewService {
    private static final Logger log = LoggerFactory.getLogger(InteractiveInterviewService.class);
    private static final String CLOSING_MESSAGE = "本轮面试已结束。你的回答已经保存，可以返回会话列表查看记录。";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final InterviewQuestionRepository questionRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final LlmClient llmClient;
    private final SchemaRepairService schemaRepairService;
    private final ObjectMapper objectMapper;

    public InteractiveInterviewService(InterviewSessionRepository sessionRepository,
                                       InterviewMessageRepository messageRepository,
                                       InterviewQuestionRepository questionRepository,
                                       ResumeRepository resumeRepository, JobRepository jobRepository,
                                       LlmClient llmClient, SchemaRepairService schemaRepairService,
                                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.llmClient = llmClient;
        this.schemaRepairService = schemaRepairService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSessionResponse create(Long userId, Long resumeId, Long jobId) {
        requireResources(userId, resumeId, jobId);
        List<InterviewQuestion> available = questionRepository
                .findByUserIdAndResumeIdAndJobIdOrderByCreatedAtDescIdDesc(userId, resumeId, jobId);
        if (available.isEmpty()) {
            throw new BusinessException("INTERVIEW_QUESTIONS_REQUIRED",
                    "请先完成职业分析并生成面试题", HttpStatus.CONFLICT);
        }
        Long latestTaskId = available.stream().map(InterviewQuestion::getTaskId)
                .filter(Objects::nonNull).findFirst().orElse(null);
        List<InterviewQuestion> selected = latestTaskId == null
                ? available.reversed()
                : questionRepository.findByUserIdAndTaskIdOrderByCreatedAtAscIdAsc(userId, latestTaskId);
        List<Long> questionIds = selected.stream()
                .limit(6)
                .map(InterviewQuestion::getId)
                .toList();
        InterviewSession session = sessionRepository.save(new InterviewSession(userId, resumeId, jobId, questionIds));
        InterviewQuestion first = requireQuestion(userId, session.currentQuestionId());
        addMessage(session, first.getId(), InterviewMessageRole.INTERVIEWER, first.getQuestionText());
        return response(session);
    }

    @Transactional(readOnly = true)
    public List<InterviewSessionSummary> list(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(InterviewSessionSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewSessionResponse get(Long userId, Long sessionId) {
        return response(requireSession(userId, sessionId));
    }

    @Transactional
    public InterviewSessionResponse answer(Long userId, Long sessionId, String answer) {
        InterviewSession session = requireSessionForUpdate(userId, sessionId);
        requireInProgress(session);
        InterviewQuestion current = requireQuestion(userId, session.currentQuestionId());
        addMessage(session, current.getId(), InterviewMessageRole.CANDIDATE, answer.trim());

        if (session.getFollowUpCount() == 0) {
            FollowUpDecision decision = followUpDecision(session, current, answer.trim());
            if (decision.followUp() && !decision.message().isBlank()) {
                session.recordFollowUp();
                addMessage(session, current.getId(), InterviewMessageRole.INTERVIEWER, decision.message());
                return response(session);
            }
        }
        moveNextOrFinish(session);
        return response(session);
    }

    @Transactional
    public InterviewSessionResponse finish(Long userId, Long sessionId) {
        InterviewSession session = requireSessionForUpdate(userId, sessionId);
        if (session.getStatus() == InterviewSessionStatus.IN_PROGRESS) {
            session.finish();
            addMessage(session, session.currentQuestionId(), InterviewMessageRole.INTERVIEWER, CLOSING_MESSAGE);
        }
        return response(session);
    }

    private FollowUpDecision followUpDecision(InterviewSession session, InterviewQuestion question, String answer) {
        try {
            String context = objectMapper.writeValueAsString(Map.of(
                    "question", question.getQuestionText(),
                    "expectedPoints", question.getExpectedPoints(),
                    "candidateAnswer", answer
            ));
            String traceId = "interview_" + session.getId() + "_" + UUID.randomUUID().toString().replace("-", "");
            var response = llmClient.complete(LlmRequest.secured(
                    "You are a concise technical interviewer. Return strict JSON only.",
                    "Decide whether one useful follow-up is needed. Return followUp=true with one short Chinese "
                            + "question only when the answer is vague or misses an important expected point. "
                            + "Otherwise return followUp=false and message as an empty string.",
                    List.of(context), traceId, true
            ));
            var validated = schemaRepairService.validateOrRepair(
                    "interview_turn.schema.json", response.content(), traceId
            ).value();
            return new FollowUpDecision(validated.path("followUp").asBoolean(false),
                    validated.path("message").asText(""));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize interview turn", exception);
        } catch (RuntimeException exception) {
            log.warn("Interview follow-up decision failed; advancing to next question: sessionId={}, reason={}",
                    session.getId(), exception.getMessage());
            return new FollowUpDecision(false, "");
        }
    }

    private void moveNextOrFinish(InterviewSession session) {
        if (session.hasNextQuestion()) {
            session.advance();
            InterviewQuestion next = requireQuestion(session.getUserId(), session.currentQuestionId());
            addMessage(session, next.getId(), InterviewMessageRole.INTERVIEWER, next.getQuestionText());
        } else {
            session.finish();
            addMessage(session, session.currentQuestionId(), InterviewMessageRole.INTERVIEWER, CLOSING_MESSAGE);
        }
    }

    private void addMessage(InterviewSession session, Long questionId, InterviewMessageRole role, String content) {
        int nextSequence = messageRepository.maxSequence(session.getId()) + 1;
        messageRepository.save(new InterviewMessage(
                session.getUserId(), session.getId(), questionId, role, content, nextSequence
        ));
    }

    private InterviewSession requireSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException("INTERVIEW_SESSION_NOT_FOUND",
                        "模拟面试会话不存在", HttpStatus.NOT_FOUND));
    }

    private InterviewSession requireSessionForUpdate(Long userId, Long sessionId) {
        return sessionRepository.findLockedByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException("INTERVIEW_SESSION_NOT_FOUND",
                        "模拟面试会话不存在", HttpStatus.NOT_FOUND));
    }

    private InterviewQuestion requireQuestion(Long userId, Long questionId) {
        return questionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new BusinessException("INTERVIEW_QUESTION_NOT_FOUND",
                        "面试题不存在", HttpStatus.NOT_FOUND));
    }

    private void requireResources(Long userId, Long resumeId, Long jobId) {
        if (resumeRepository.findByIdAndUserId(resumeId, userId).isEmpty()) {
            throw new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND);
        }
        if (jobRepository.findByIdAndUserId(jobId, userId).isEmpty()) {
            throw new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND);
        }
    }

    private void requireInProgress(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new BusinessException("INTERVIEW_SESSION_FINISHED", "本轮模拟面试已经结束", HttpStatus.CONFLICT);
        }
    }

    private InterviewSessionResponse response(InterviewSession session) {
        List<InterviewMessageResponse> messages = messageRepository
                .findByUserIdAndSessionIdOrderBySequenceNoAsc(session.getUserId(), session.getId()).stream()
                .map(InterviewMessageResponse::from)
                .toList();
        return new InterviewSessionResponse(
                session.getId(), session.getResumeId(), session.getJobId(), session.getStatus(),
                session.getCurrentQuestionIndex() + 1, session.getQuestionIds().size(), messages,
                session.getCreatedAt(), session.getUpdatedAt(), session.getFinishedAt()
        );
    }

    private record FollowUpDecision(boolean followUp, String message) {
        FollowUpDecision {
            message = followUp && message != null ? message.trim() : "";
        }
    }

    public record InterviewMessageResponse(Long messageId, Long questionId, InterviewMessageRole role,
                                           String content, int sequenceNo, Instant createdAt) {
        static InterviewMessageResponse from(InterviewMessage message) {
            return new InterviewMessageResponse(message.getId(), message.getQuestionId(), message.getRole(),
                    message.getContent(), message.getSequenceNo(), message.getCreatedAt());
        }
    }

    public record InterviewSessionResponse(Long sessionId, Long resumeId, Long jobId,
                                           InterviewSessionStatus status, int currentQuestion,
                                           int totalQuestions, List<InterviewMessageResponse> messages,
                                           Instant createdAt, Instant updatedAt, Instant finishedAt) {
    }

    public record InterviewSessionSummary(Long sessionId, Long resumeId, Long jobId,
                                          InterviewSessionStatus status, int currentQuestion,
                                          int totalQuestions, Instant createdAt, Instant updatedAt,
                                          Instant finishedAt) {
        static InterviewSessionSummary from(InterviewSession session) {
            return new InterviewSessionSummary(session.getId(), session.getResumeId(), session.getJobId(),
                    session.getStatus(), session.getCurrentQuestionIndex() + 1, session.getQuestionIds().size(),
                    session.getCreatedAt(), session.getUpdatedAt(), session.getFinishedAt());
        }
    }
}
