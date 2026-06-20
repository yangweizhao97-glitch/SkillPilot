package com.huatai.careeragent.interview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewSessionTest {
    @Test
    void advancesQuestionsAndResetsFollowUpCount() {
        InterviewSession session = new InterviewSession(1L, 2L, 3L, List.of(10L, 11L));

        session.recordFollowUp();
        assertThat(session.getFollowUpCount()).isEqualTo(1);

        session.advance();

        assertThat(session.currentQuestionId()).isEqualTo(11L);
        assertThat(session.getCurrentQuestionIndex()).isEqualTo(1);
        assertThat(session.getFollowUpCount()).isZero();
        assertThat(session.hasNextQuestion()).isFalse();
    }

    @Test
    void finishesSession() {
        InterviewSession session = new InterviewSession(1L, 2L, 3L, List.of(10L));

        session.finish();

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.FINISHED);
        assertThat(session.getFinishedAt()).isNotNull();
    }
}
