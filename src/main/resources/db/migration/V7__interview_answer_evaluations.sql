CREATE TABLE interview_answer_evaluations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    session_id BIGINT NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES interview_questions(id),
    answer_message_id BIGINT NOT NULL REFERENCES interview_messages(id) ON DELETE CASCADE,
    overall_score INTEGER NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    result_json JSONB NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_interview_evaluation_answer UNIQUE(answer_message_id)
);

CREATE INDEX idx_interview_evaluations_session_created
    ON interview_answer_evaluations(session_id, created_at, id);

CREATE INDEX idx_interview_evaluations_user_session
    ON interview_answer_evaluations(user_id, session_id);
