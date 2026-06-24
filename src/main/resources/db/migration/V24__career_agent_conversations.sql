CREATE TABLE career_agent_conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_career_agent_conversations_user_updated
    ON career_agent_conversations(user_id, updated_at DESC);

CREATE TABLE career_agent_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id BIGINT NOT NULL REFERENCES career_agent_conversations(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    message_type VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    task_id BIGINT REFERENCES agent_tasks(id),
    report_id BIGINT REFERENCES final_reports(id),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_career_agent_messages_conversation_created
    ON career_agent_messages(conversation_id, created_at ASC, id ASC);
