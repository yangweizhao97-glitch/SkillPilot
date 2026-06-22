CREATE TABLE tutor_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(160) NOT NULL,
    resume_id BIGINT REFERENCES resumes(id),
    job_id BIGINT REFERENCES jobs(id),
    question_id BIGINT REFERENCES interview_questions(id),
    evaluation_id BIGINT REFERENCES interview_answer_evaluations(id),
    learning_plan_id BIGINT REFERENCES learning_plans(id),
    processing BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tutor_sessions_user_updated ON tutor_sessions(user_id, updated_at DESC);

CREATE TABLE tutor_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    session_id BIGINT NOT NULL REFERENCES tutor_sessions(id) ON DELETE CASCADE,
    role VARCHAR(24) NOT NULL,
    content TEXT NOT NULL,
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tutor_messages_session_sequence UNIQUE(session_id, sequence_no)
);

CREATE INDEX idx_tutor_messages_session_sequence ON tutor_messages(session_id, sequence_no);
