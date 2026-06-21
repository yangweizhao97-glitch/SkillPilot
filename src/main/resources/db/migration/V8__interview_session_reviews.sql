CREATE TABLE interview_session_reviews (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    session_id BIGINT NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    overall_score INTEGER NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    evaluated_answers INTEGER NOT NULL CHECK (evaluated_answers > 0),
    result_json JSONB NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    generation_source VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_interview_session_review UNIQUE(session_id)
);

CREATE INDEX idx_interview_session_reviews_user_session
    ON interview_session_reviews(user_id, session_id);
