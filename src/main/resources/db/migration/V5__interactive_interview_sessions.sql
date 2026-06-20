CREATE TABLE interview_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    resume_id BIGINT NOT NULL REFERENCES resumes(id),
    job_id BIGINT NOT NULL REFERENCES jobs(id),
    status VARCHAR(32) NOT NULL,
    question_ids JSONB NOT NULL,
    current_question_index INTEGER NOT NULL DEFAULT 0,
    follow_up_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_interview_sessions_user_created
    ON interview_sessions(user_id, created_at DESC);

CREATE TABLE interview_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    session_id BIGINT NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_id BIGINT REFERENCES interview_questions(id),
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_interview_messages_session_sequence UNIQUE(session_id, sequence_no)
);

CREATE INDEX idx_interview_messages_session_sequence
    ON interview_messages(session_id, sequence_no);
