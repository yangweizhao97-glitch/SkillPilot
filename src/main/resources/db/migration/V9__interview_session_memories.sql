CREATE TABLE interview_session_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    session_id BIGINT NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    resume_id BIGINT NOT NULL REFERENCES resumes(id),
    job_id BIGINT NOT NULL REFERENCES jobs(id),
    memory_json JSONB NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_interview_session_memory UNIQUE(session_id)
);

CREATE INDEX idx_interview_memory_user_resources_updated
    ON interview_session_memories(user_id, resume_id, job_id, updated_at DESC);
