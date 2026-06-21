package com.huatai.careeragent.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewQuestionServiceTest {
    private final InterviewQuestionRepository repository = mock(InterviewQuestionRepository.class);
    private final InterviewQuestionService service = new InterviewQuestionService(
            repository, mock(ResumeRepository.class), mock(JobRepository.class), new ObjectMapper()
    );

    @Test
    void returnsCompatibleAnswerForHistoricalQuestionAndEnforcesOwnership() {
        InterviewQuestion question = new InterviewQuestion(
                7L, 11L, 12L, 13L, "如何保证消息消费幂等？",
                QuestionType.SYSTEM_DESIGN, QuestionDifficulty.HARD,
                List.of("幂等键", "唯一约束", "并发控制"), List.of("jd:12"), null
        );
        ReflectionTestUtils.setField(question, "id", 21L);
        when(repository.findByIdAndUserId(21L, 7L)).thenReturn(Optional.of(question));
        when(repository.findByIdAndUserId(21L, 8L)).thenReturn(Optional.empty());

        var answer = service.answer(7L, 21L);

        assertThat(answer.answerOutline()).containsExactly("幂等键", "唯一约束", "并发控制");
        assertThat(answer.referenceAnswer()).contains("幂等键", "并发控制");
        assertThat(answer.scoringRubric()).extracting(rule -> rule.get("weight"))
                .containsExactly(34, 33, 33);
        assertThatThrownBy(() -> service.answer(8L, 21L)).isInstanceOf(BusinessException.class);
    }
}
